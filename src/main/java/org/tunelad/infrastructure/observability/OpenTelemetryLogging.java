package org.tunelad.infrastructure.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Connects the logback appender declared in {@code logback-spring.xml} to the OpenTelemetry SDK Spring Boot builds, so
 * that log records are exported with the other signals. Logback starts before the Spring context: the appender holds
 * the records logged until this runs, then sends them.
 */
@Component
class OpenTelemetryLogging implements InitializingBean {

    private final OpenTelemetry openTelemetry;

    OpenTelemetryLogging(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void afterPropertiesSet() {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
