package com.dreamspace.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> api(ApiException e, HttpServletRequest request) {
    return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage(), null, requestId(request)));
  }
  @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(Exception e, HttpServletRequest request) {
    return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "请求参数无效", null, requestId(request)));
  }
  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> malformed(Exception e, HttpServletRequest request) {
    return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "请求参数无效", null, requestId(request)));
  }
  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("INTERNAL_ERROR", "服务暂时不可用", null, requestId(request)));
  }
  private String requestId(HttpServletRequest request) {
    String value = request.getHeader("X-Request-Id"); return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
  }
}
