package com.dreamspace.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.main.web-application-type=none")
class ApiApplicationContextTest {
    @Test
    void bootsApiContext() {
        assertThat(true).isTrue();
    }
}
