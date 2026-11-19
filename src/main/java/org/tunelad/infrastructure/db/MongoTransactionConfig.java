package org.tunelad.infrastructure.db;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.tunelad.infrastructure.ReactiveTransactional;

/**
 * MongoDB transaction managers. Spring Boot does not configure one, and transactions need MongoDB to run as a
 * replica set.
 * <ul>
 * <li>the reactive one, for reactive services annotated {@link ReactiveTransactional @ReactiveTransactional}.</li>
 * <li>the blocking {@link MongoTransactionManager} is the default: the Spring Modulith Event Publication Registry
 * and {@code @ApplicationModuleListener} methods use unqualified, blocking {@code @Transactional}.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class MongoTransactionConfig {

    @Bean(ReactiveTransactional.MANAGER)
    ReactiveMongoTransactionManager reactiveTransactionManager(ReactiveMongoDatabaseFactory factory) {
        return new ReactiveMongoTransactionManager(factory);
    }

    @Bean
    @Primary
    MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }
}
