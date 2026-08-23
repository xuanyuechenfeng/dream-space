package com.dreamspace.api.controller;

import com.dreamspace.api.common.ApiException;
import com.dreamspace.api.common.CookieSupport;
import com.dreamspace.api.service.AuthService;
import com.dreamspace.api.service.ModerationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dream_web/moderation")
public class ModerationController {
  private final ModerationService service;
  private final AuthService auth;

  public ModerationController(ModerationService service, AuthService auth) {
    this.service = service;
    this.auth = auth;
  }

  @GetMapping("/cases")
  List<ModerationService.UserCase> cases(HttpServletRequest request) {
    return service.userCases(user(request));
  }

  @PostMapping("/cases/{caseId}/appeals")
  @ResponseStatus(HttpStatus.CREATED)
  ModerationService.UserCase appeal(@PathVariable String caseId, @RequestBody AppealRequest body,
      HttpServletRequest request) {
    return service.appeal(user(request), caseId, body == null ? null : body.reason());
  }

  private String user(HttpServletRequest request) {
    var session = auth.session(CookieSupport.read(request, CookieSupport.USER));
    if (!session.authenticated() || session.user() == null)
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
    return session.user().id();
  }

  public record AppealRequest(String reason) {}
}
