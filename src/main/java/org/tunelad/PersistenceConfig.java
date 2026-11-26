package org.tunelad;

import com.zaxxer.hikari.HikariDataSource;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.tunelad.infrastructure.ReactiveTransactional;

import javax.sql.DataSource;

/**
 * The application reads and writes through R2DBC; JDBC only serves what has no reactive option, the Spring Modulith
 * Event Publication Registry (Liquibase makes its own connection). Spring Boot configures neither a JDBC
 * {@link DataSource} once an R2DBC connection factory is there, nor a single transaction manager, so this does.
 * <p>
 * It lives in the root package so that every module test, which loads one module only, gets it.
 * <ul>
 * <li>the JDBC pool: a {@link JdbcConnectionDetails} bean (a Testcontainers service connection, say) wins over
 * {@code spring.datasource.*}.</li>
 * <li>the reactive transaction manager, over R2DBC, for reactive services annotated
 * {@link ReactiveTransactional @ReactiveTransactional}.</li>
 * <li>the blocking one, over JDBC, is the default: the Event Publication Registry and
 * {@code @ApplicationModuleListener} methods use unqualified, blocking {@code @Transactional}.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class PersistenceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(DataSourceProperties properties, ObjectProvider<JdbcConnectionDetails> details) {
        var connection = details.getIfAvailable();
        var url = connection != null ? connection.getJdbcUrl() : properties.getUrl();
        var username = connection != null ? connection.getUsername() : properties.getUsername();
        var password = connection != null ? connection.getPassword() : properties.getPassword();
        return DataSourceBuilder.create().type(HikariDataSource.class)
                .url(url).username(username).password(password).build();
    }

    @Bean(ReactiveTransactional.MANAGER)
    ReactiveTransactionManager reactiveTransactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    @Bean
    @Primary
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
