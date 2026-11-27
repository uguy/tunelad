package org.tunelad.live.internal;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The Grafana dashboards that ship with the repository: they parse, and what they query exists. */
class DashboardsTest {

    private static final Path DIR = Path.of("docker/observability/grafana/provisioning/dashboards");
    private static final Pattern LIVE_METRIC = Pattern.compile("tunelad_live_[a-z_]+");
    private final JsonMapper json = JsonMapper.builder().build();

    private List<Path> dashboards() throws IOException {
        try (Stream<Path> files = Files.list(DIR)) {
            return files.filter(f -> f.toString().endsWith(".json")).sorted().toList();
        }
    }

    @Test
    void every_dashboard_parses_and_has_a_title_a_uid_and_panels() throws IOException {
        assertThat(dashboards()).extracting(p -> p.getFileName().toString())
                .contains("overview.json", "http.json", "jvm.json", "live.json");
        for (var file : dashboards()) {
            var dashboard = json.readTree(Files.readString(file));
            assertThat(dashboard.path("title").asString()).as(file + " title").isNotBlank();
            assertThat(dashboard.path("uid").asString()).as(file + " uid").isNotBlank();
            assertThat(dashboard.path("panels").size()).as(file + " panels").isPositive();
        }
    }

    @Test
    void uids_are_unique() throws IOException {
        var uids = new HashSet<String>();
        for (var file : dashboards()) {
            assertThat(uids.add(json.readTree(Files.readString(file)).path("uid").asString())).as(file.toString()).isTrue();
        }
    }

    @Test
    void every_query_is_scoped_to_the_application_variable() throws IOException {
        for (var file : dashboards()) {
            var dashboard = json.readTree(Files.readString(file));
            assertThat(variables(dashboard)).as(file + " variables").contains("application");
            for (var expr : exprs(dashboard)) {
                if (expr.contains("{") && expr.matches(".*(http_|tunelad_|jvm_|hikaricp_|r2dbc_|process_|system_).*")) {
                    assertThat(expr).as(file + ": " + expr).contains("application=\"$application\"");
                }
            }
        }
    }

    @Test
    void the_live_metrics_the_dashboards_query_are_the_ones_the_application_registers() throws IOException {
        var registry = new SimpleMeterRegistry();
        var metrics = new LiveMetrics(registry);
        LiveMetrics.TYPES.forEach(type -> {
            metrics.published(type);
            metrics.delivered(type);
        });
        var exported = new HashSet<String>();
        for (Meter meter : registry.getMeters()) {
            var name = meter.getId().getName().replace('.', '_');
            exported.add(meter.getId().getType() == Meter.Type.COUNTER ? name + "_total" : name);
        }
        var used = new HashSet<String>();
        for (var file : dashboards()) {
            for (var expr : exprs(json.readTree(Files.readString(file)))) {
                LIVE_METRIC.matcher(expr).results().forEach(m -> used.add(m.group()));
            }
        }

        assertThat(used).isNotEmpty().isSubsetOf(exported);
        assertThat(used).containsExactlyInAnyOrder("tunelad_live_connections",
                "tunelad_live_events_published_total", "tunelad_live_events_delivered_total");
    }

    @Test
    void latency_panels_leave_the_live_stream_out() throws IOException {
        for (var name : List.of("http.json", "overview.json")) {
            var dashboard = json.readTree(Files.readString(DIR.resolve(name)));
            for (var expr : exprs(dashboard)) {
                if (expr.contains("http_server_requests_seconds_bucket") || expr.contains("http_server_requests_seconds_sum")) {
                    assertThat(expr).as(name).contains("uri!=\"/api/live\"");
                }
            }
        }
    }

    private static Set<String> variables(JsonNode dashboard) {
        var names = new HashSet<String>();
        dashboard.path("templating").path("list").forEach(v -> names.add(v.path("name").asString()));
        return names;
    }

    private static List<String> exprs(JsonNode dashboard) {
        var found = new java.util.ArrayList<String>();
        collect(dashboard.path("panels"), found);
        return found;
    }

    private static void collect(JsonNode panels, List<String> found) {
        for (var panel : panels) {
            panel.path("targets").forEach(t -> {
                if (t.has("expr")) found.add(t.path("expr").asString());
            });
            collect(panel.path("panels"), found);
        }
    }
}
