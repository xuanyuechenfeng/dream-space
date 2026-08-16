package com.dreamspace.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

final class CookieSupport {
  static final String USER = "dreamspace_session";
  static final String ADMIN = "dreamspace_admin_session";
  private CookieSupport() {}
  static String read(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies(); if (cookies == null) return null;
    for (Cookie c : cookies) if (name.equals(c.getName())) return c.getValue(); return null;
  }
  static void set(HttpServletResponse response, String name, String value, Duration maxAge, boolean secure) {
    response.addHeader("Set-Cookie", ResponseCookie.from(name, value).httpOnly(true).path("/").sameSite("Lax").secure(secure).maxAge(maxAge).build().toString());
  }
  static void clear(HttpServletResponse response, String name, boolean secure) { set(response, name, "", Duration.ZERO, secure); }
}
