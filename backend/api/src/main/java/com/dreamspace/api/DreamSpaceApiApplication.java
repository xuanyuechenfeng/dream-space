package com.dreamspace.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.dreamspace.persistence.config.DreamSpaceProperties;
import org.springframework.context.annotation.Import;
import com.dreamspace.persistence.config.PersistenceConfiguration;

@SpringBootApplication
@EnableConfigurationProperties(DreamSpaceProperties.class)
@Import(PersistenceConfiguration.class)
public class DreamSpaceApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(DreamSpaceApiApplication.class, args);
    }
}
