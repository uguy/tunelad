package org.tunelad.media.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.tunelad.media.AudioTags;
import org.tunelad.media.MediaStore;
import org.tunelad.media.MediaTags;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Reads tags with an ffprobe process. ffprobe needs a seekable file for some formats (m4a keeps its index at the end),
 * so the audio goes to a temporary file first. File and process work blocks, so it all runs on the bounded elastic
 * scheduler. Whatever goes wrong, the tags are simply absent: the upload they belong to is fine without them.
 */
@Slf4j
@Component
class FfprobeMediaTags implements MediaTags {

    private static final int MAX_TAG_LENGTH = 2000;

    private final MediaStore store;
    private final FfprobeProperties properties;
    private final JsonMapper json = JsonMapper.builder().build();

    FfprobeMediaTags(MediaStore store, FfprobeProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    @Override
    public Mono<AudioTags> read(String key) {
        return Mono.usingWhen(
                        Mono.fromCallable(() -> Files.createTempFile("tunelad-tags-", ".audio"))
                                .subscribeOn(Schedulers.boundedElastic()),
                        file -> DataBufferUtils.write(store.read(key), file).then(Mono.fromCallable(() -> probe(file))
                                .subscribeOn(Schedulers.boundedElastic())),
                        file -> Mono.fromRunnable(() -> delete(file)).subscribeOn(Schedulers.boundedElastic()),
                        (file, error) -> Mono.fromRunnable(() -> delete(file)).subscribeOn(Schedulers.boundedElastic()),
                        file -> Mono.fromRunnable(() -> delete(file)).subscribeOn(Schedulers.boundedElastic()))
                .onErrorResume(error -> {
                    log.warn("Could not read the tags of audio {}: {}", key, error.toString());
                    return Mono.just(AudioTags.NONE);
                });
    }

    private AudioTags probe(Path file) throws IOException, InterruptedException {
        if (Files.size(file) == 0) {
            return AudioTags.NONE;
        }
        Process process;
        try {
            process = new ProcessBuilder(properties.path(), "-v", "error", "-print_format", "json", "-show_entries",
                    "format_tags:stream_tags", file.toString()).redirectErrorStream(false).start();
        } catch (IOException e) {
            log.warn("ffprobe could not be started ({}): tags are not read", properties.path());
            return AudioTags.NONE;
        }
        try {
            process.getOutputStream().close();
            var errors = Mono.fromCallable(() -> new String(process.getErrorStream().readAllBytes()))
                    .subscribeOn(Schedulers.boundedElastic()).toFuture();
            var output = process.getInputStream().readAllBytes();
            if (!process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("ffprobe did not finish within {}", properties.timeout());
                return AudioTags.NONE;
            }
            if (process.exitValue() != 0) {
                log.debug("ffprobe could not read the file: {}", errors.getNow(""));
                return AudioTags.NONE;
            }
            return parse(json.readTree(output));
        } finally {
            process.destroyForcibly();
        }
    }

    private static AudioTags parse(JsonNode root) {
        var nodes = new java.util.ArrayList<JsonNode>();
        nodes.add(root.path("format").path("tags"));
        root.path("streams").forEach(stream -> nodes.add(stream.path("tags")));
        return new AudioTags(tag(nodes, "title"), tag(nodes, "artist"), tag(nodes, "album"), tag(nodes, "comment"));
    }

    /** The first non-blank value of the tag in the format's then the streams' tags, whatever the case of its name. */
    private static String tag(List<JsonNode> nodes, String name) {
        for (var node : nodes) {
            for (var field : node.properties()) {
                if (field.getKey().toLowerCase(Locale.ROOT).equals(name)) {
                    var value = clean(field.getValue().asString(""));
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private static String clean(String value) {
        var cleaned = value.replaceAll("\\p{Cntrl}", " ").strip();
        return cleaned.isEmpty() ? null : cleaned.substring(0, Math.min(cleaned.length(), MAX_TAG_LENGTH));
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete the temporary file {}", file, e);
        }
    }
}
