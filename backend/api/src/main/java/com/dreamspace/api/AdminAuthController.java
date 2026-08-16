package com.dreamspace.api;

import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {
  private final AdminAuthService service; private final boolean secure;
  public AdminAuthController(AdminAuthService service, com.dreamspace.persistence.config.DreamSpaceProperties props) { this.service = service; secure = props.isProduction(); }
  @PostMapping("/codes") AdminAuthService.CodeResponse codes(@RequestBody AdminAuthService.CodeRequest body) { return service.sendCode(body); }
  @PostMapping("/login") AdminAuthService.SessionResponse login(@RequestBody AdminAuthService.LoginRequest body, HttpServletResponse response) { var r = service.login(body); CookieSupport.set(response, CookieSupport.ADMIN, r.token(), java.time.Duration.between(java.time.Instant.now(), r.expiresAt()), secure); return r.response(); }
  @GetMapping("/session") AdminAuthService.SessionResponse session(HttpServletRequest request) { return service.session(CookieSupport.read(request, CookieSupport.ADMIN)); }
  @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) void logout(HttpServletRequest request, HttpServletResponse response) { service.logout(CookieSupport.read(request, CookieSupport.ADMIN)); CookieSupport.clear(response, CookieSupport.ADMIN, secure); }
}
