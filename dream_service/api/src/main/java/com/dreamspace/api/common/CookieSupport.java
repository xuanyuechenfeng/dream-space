package com.dreamspace.api.common;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

public final class CookieSupport {
  public static final String USER = "dreamspace_session";
  public static final String ADMIN = "dreamspace_admin_session";
  private CookieSupport() {}
  public static String read(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies(); if (cookies == null) return null;
    for (Cookie c : cookies) if (name.equals(c.getName())) return c.getValue(); return null;
  }
  public static void set(HttpServletResponse response, String name, String value, Duration maxAge, boolean secure) {
    response.addHeader("Set-Cookie", ResponseCookie.from(name, value).httpOnly(true).path("/").sameSite("Lax").secure(secure).maxAge(maxAge).build().toString());
  }
  public static void clear(HttpServletResponse response, String name, boolean secure) { set(response, name, "", Duration.ZERO, secure); }
}
