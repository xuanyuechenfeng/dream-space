package com.dreamspace.worker;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.dreamspace.persistence.config.DreamSpaceProperties;
import com.dreamspace.persistence.config.PersistenceConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(DreamSpaceProperties.class)
@Import(PersistenceConfiguration.class)
@EnableScheduling
public class DreamSpaceWorkerApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(DreamSpaceWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
