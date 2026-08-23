package com.dreamspace.worker.persistence.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Registers data access adapters owned by the background worker. */
@Configuration
@MapperScan("com.dreamspace.worker.persistence")
public class WorkerPersistenceConfiguration {
}
