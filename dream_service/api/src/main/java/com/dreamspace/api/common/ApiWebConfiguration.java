package com.dreamspace.api.common;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiWebConfiguration implements WebMvcConfigurer {
  private final AdminPermissionInterceptor adminPermissionInterceptor;
  private final List<String> allowedOrigins;

  public ApiWebConfiguration(AdminPermissionInterceptor adminPermissionInterceptor,
      @Value("${dream-space.security.allowed-origins:}") String configuredOrigins) {
    this.adminPermissionInterceptor = adminPermissionInterceptor;
    this.allowedOrigins = configuredOrigins == null || configuredOrigins.isBlank()
        ? List.of()
        : java.util.Arrays.stream(configuredOrigins.split(","))
            .map(String::trim).filter(value -> !value.isBlank()).toList();
  }

  @Override public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(adminPermissionInterceptor).addPathPatterns("/manage_web/**");
  }

  @Override public void addCorsMappings(CorsRegistry registry) {
    if (!allowedOrigins.isEmpty()) {
      registry.addMapping("/**").allowedOrigins(allowedOrigins.toArray(String[]::new))
          .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
          .allowedHeaders("Content-Type", "X-Request-Id", "X-CSRF-Token", "Last-Event-ID")
          .allowCredentials(true).maxAge(3600);
    }
  }
}
