package org.tunelad.infrastructure.db;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// H2 console is not available when using Spring WebFlux, default to starting the server on another port
// Navigate to http://localhost:8082 without /h2-console
@ConditionalOnClass(org.h2.tools.Server.class)
@Component
public class H2 {

	private org.h2.tools.Server webServer;

	private org.h2.tools.Server tcpServer;

	@EventListener(org.springframework.context.event.ContextRefreshedEvent.class)
	public void start() throws java.sql.SQLException {
		this.webServer = org.h2.tools.Server.createWebServer("-webPort", "8082", "-tcpAllowOthers").start();
		this.tcpServer = org.h2.tools.Server.createTcpServer("-tcpPort", "9092", "-tcpAllowOthers").start();
	}

	@EventListener(org.springframework.context.event.ContextClosedEvent.class)
	public void stop() {
		this.tcpServer.stop();
		this.webServer.stop();
	}
}
