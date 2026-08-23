package com.dreamspace.api.controller;

import com.dreamspace.api.common.CookieSupport;
import com.dreamspace.api.service.AuthService;
import com.dreamspace.api.service.CaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dream_web/auth")
public class AuthController {
  private final AuthService service;
  private final CaptchaService captcha;
  private final boolean secure;
  public AuthController(AuthService service, CaptchaService captcha,
      com.dreamspace.common.persistence.config.DreamSpaceProperties props) {
    this.service = service;
    this.captcha = captcha;
    this.secure = props.security().secureCookies();
  }
  @PostMapping("/codes") AuthService.CodeResponse codes(@RequestBody AuthService.CodeRequest body) { return service.sendCode(body); }
  @PostMapping("/login") AuthService.SessionResponse login(@RequestBody AuthService.LoginRequest body, HttpServletResponse response) { var result = service.login(body); CookieSupport.set(response, CookieSupport.USER, result.token(), java.time.Duration.between(java.time.Instant.now(), result.expiresAt()), secure); return result.response(); }
  @GetMapping("/captcha") CaptchaService.CaptchaResponse captcha(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    return captcha.issue(request.getRemoteAddr() + "|" + (userAgent == null ? "" : userAgent));
  }
  @PostMapping("/password-login") AuthService.SessionResponse passwordLogin(@RequestBody AuthService.PasswordLoginRequest body, HttpServletResponse response) { var result = service.passwordLogin(body); CookieSupport.set(response, CookieSupport.USER, result.token(), java.time.Duration.between(java.time.Instant.now(), result.expiresAt()), secure); return result.response(); }
  @GetMapping("/session") AuthService.SessionResponse session(HttpServletRequest request) { return service.session(CookieSupport.read(request, CookieSupport.USER)); }
  @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) void logout(HttpServletRequest request, HttpServletResponse response) { service.logout(CookieSupport.read(request, CookieSupport.USER)); CookieSupport.clear(response, CookieSupport.USER, secure); }
}
