package com.dreamspace.api.persistence.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Registers data access adapters owned by the HTTP API. */
@Configuration
@MapperScan("com.dreamspace.api.persistence")
public class ApiPersistenceConfiguration {
}
