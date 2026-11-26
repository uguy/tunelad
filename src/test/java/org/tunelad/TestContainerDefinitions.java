package org.tunelad;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** What every application test needs around it: PostgreSQL, and a stand-in for the identity provider. */
@TestConfiguration(proxyBeanMethods = false)
@Import({PostgresContainerDefinition.class, TestSecurityConfiguration.class})
public class TestContainerDefinitions {
}
