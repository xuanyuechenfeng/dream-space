package com.dreamspace.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import org.springframework.context.annotation.Import;
import com.dreamspace.common.persistence.config.SharedPersistenceConfiguration;
import com.dreamspace.api.persistence.config.ApiPersistenceConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(DreamSpaceProperties.class)
@Import({SharedPersistenceConfiguration.class, ApiPersistenceConfiguration.class})
@EnableScheduling
public class DreamSpaceApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(DreamSpaceApiApplication.class, args);
    }
}
