package com.dreamspace.worker;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class DreamSpaceWorkerApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(DreamSpaceWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
