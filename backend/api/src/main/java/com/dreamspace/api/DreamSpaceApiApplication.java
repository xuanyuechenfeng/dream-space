package com.dreamspace.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.dreamspace.persistence.config.DreamSpaceProperties;
import org.springframework.context.annotation.Import;
import com.dreamspace.persistence.config.PersistenceConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(DreamSpaceProperties.class)
@Import(PersistenceConfiguration.class)
@EnableScheduling
public class DreamSpaceApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(DreamSpaceApiApplication.class, args);
    }
}
