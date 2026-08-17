package com.dreamspace.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
  private final AuthService service; private final boolean secure;
  public AuthController(AuthService service, com.dreamspace.common.persistence.config.DreamSpaceProperties props) { this.service = service; this.secure = props.isProduction(); }
  @PostMapping("/codes") AuthService.CodeResponse codes(@RequestBody AuthService.CodeRequest body) { return service.sendCode(body); }
  @PostMapping("/login") AuthService.SessionResponse login(@RequestBody AuthService.LoginRequest body, HttpServletResponse response) { var result = service.login(body); CookieSupport.set(response, CookieSupport.USER, result.token(), java.time.Duration.between(java.time.Instant.now(), result.expiresAt()), secure); return result.response(); }
  @GetMapping("/session") AuthService.SessionResponse session(HttpServletRequest request) { return service.session(CookieSupport.read(request, CookieSupport.USER)); }
  @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) void logout(HttpServletRequest request, HttpServletResponse response) { service.logout(CookieSupport.read(request, CookieSupport.USER)); CookieSupport.clear(response, CookieSupport.USER, secure); }
}
