package com.dreamspace.api.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

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
  @ExceptionHandler({AsyncRequestNotUsableException.class, AsyncRequestTimeoutException.class})
  void asyncRequestEnded(Exception e) {
    // SSE clients commonly close or reconnect while the server is writing. The
    // response is already committed as text/event-stream, so do not write JSON.
  }
  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<ApiError> uploadTooLarge(Exception e, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ApiError("UPLOAD_TOO_LARGE", "上传文件超过大小限制", null, requestId(request)));
  }
  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest request) {
    String requestId = requestId(request);
    LOG.error("Unhandled API error requestId={} method={} path={}", requestId,
        request.getMethod(), request.getRequestURI(), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ApiError("INTERNAL_ERROR", "服务暂时不可用", null, requestId));
  }
  private String requestId(HttpServletRequest request) {
    String value = request.getHeader("X-Request-Id"); return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
  }
}
