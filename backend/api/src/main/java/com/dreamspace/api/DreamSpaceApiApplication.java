package com.dreamspace.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.dreamspace.persistence.config.DreamSpaceProperties;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@EnableConfigurationProperties(DreamSpaceProperties.class)
@MapperScan("com.dreamspace.persistence.auth,com.dreamspace.persistence.inspiration,com.dreamspace.persistence.generation,com.dreamspace.persistence.quota,com.dreamspace.persistence.admin")
public class DreamSpaceApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(DreamSpaceApiApplication.class, args);
    }
}
