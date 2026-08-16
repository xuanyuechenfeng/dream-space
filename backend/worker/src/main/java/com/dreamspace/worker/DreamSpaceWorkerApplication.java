package com.dreamspace.worker;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.dreamspace.persistence.config.DreamSpaceProperties;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@EnableConfigurationProperties(DreamSpaceProperties.class)
@MapperScan("com.dreamspace.persistence.auth,com.dreamspace.persistence.inspiration,com.dreamspace.persistence.generation,com.dreamspace.persistence.quota,com.dreamspace.persistence.admin")
public class DreamSpaceWorkerApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(DreamSpaceWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
