package com.dreamspace.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/generation")
public class GenerationController {
  private final GenerationService service;
  private final AuthService auth;

  public GenerationController(GenerationService service, AuthService auth) {
    this.service = service;
    this.auth = auth;
  }

  @GetMapping("/options")
  GenerationService.Options options(HttpServletRequest request) { user(request); return service.options(); }

  @GetMapping("/quota")
  GenerationService.QuotaView quota(HttpServletRequest request) { return service.quota(user(request)); }

  @GetMapping("/sessions")
  SessionsResponse sessions(HttpServletRequest request) { return new SessionsResponse(service.listSessions(user(request))); }

  @PostMapping("/sessions")
  @ResponseStatus(HttpStatus.CREATED)
  GenerationService.SessionDetail createSession(@RequestBody(required = false) GenerationService.Draft draft, HttpServletRequest request) {
    return service.createSession(user(request), draft);
  }

  @GetMapping("/sessions/{sessionId}")
  GenerationService.SessionDetail session(@PathVariable String sessionId, HttpServletRequest request) {
    return service.getSession(user(request), sessionId);
  }

  @PatchMapping("/sessions/{sessionId}")
  GenerationService.SessionDetail rename(@PathVariable String sessionId, @RequestBody RenameRequest body, HttpServletRequest request) {
    return service.renameSession(user(request), sessionId, body == null ? null : body.title());
  }

  @PatchMapping("/sessions/{sessionId}/draft")
  GenerationService.SessionDetail draft(@PathVariable String sessionId, @RequestBody GenerationService.Draft body, HttpServletRequest request) {
    return service.updateDraft(user(request), sessionId, body);
  }

  @DeleteMapping("/sessions/{sessionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteSession(@PathVariable String sessionId, HttpServletRequest request) { service.deleteSession(user(request), sessionId); }

  @PostMapping("/tasks")
  GenerationService.SubmitResponse submit(@RequestBody GenerationService.TaskRequest body, HttpServletRequest request) {
    return service.submit(user(request), body);
  }

  @GetMapping("/tasks/{taskId}")
  GenerationService.TaskView task(@PathVariable String taskId, HttpServletRequest request) { return service.getTask(user(request), taskId); }

  @PostMapping("/tasks/{taskId}/cancel")
  GenerationService.TaskView cancel(@PathVariable String taskId, HttpServletRequest request) { return service.cancel(user(request), taskId); }

  @PostMapping("/tasks/{taskId}/retry")
  GenerationService.SubmitResponse retry(@PathVariable String taskId, HttpServletRequest request) { return service.retry(user(request), taskId); }

  @GetMapping(value = "/tasks/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter events(@PathVariable String taskId, @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
      @RequestParam(value = "after", required = false, defaultValue = "0") long after, HttpServletRequest request) {
    long cursor = after;
    if (lastEventId != null && !lastEventId.isBlank()) {
      try { cursor = Long.parseLong(lastEventId); } catch (NumberFormatException ignored) { cursor = after; }
    }
    return service.events(user(request), taskId, cursor);
  }

  @GetMapping("/results/{resultId}/content")
  ResponseEntity<byte[]> content(@PathVariable String resultId, HttpServletRequest request) { return binary(user(request), resultId, false); }

  @GetMapping("/results/{resultId}/thumbnail")
  ResponseEntity<byte[]> thumbnail(@PathVariable String resultId, HttpServletRequest request) { return binary(user(request), resultId, true); }

  private ResponseEntity<byte[]> binary(String userId, String resultId, boolean thumbnail) {
    var data = service.result(userId, resultId, thumbnail);
    String contentType = data.contentType() == null || data.contentType().isBlank()
        ? MediaType.APPLICATION_OCTET_STREAM_VALUE : data.contentType();
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).contentLength(data.bytes().length)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline").header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header("X-Content-Type-Options", "nosniff").body(data.bytes());
  }

  private String user(HttpServletRequest request) {
    var session = auth.session(CookieSupport.read(request, CookieSupport.USER));
    if (!session.authenticated() || session.user() == null)
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
    return session.user().id();
  }

  public record SessionsResponse(List<GenerationService.SessionSummary> items) {}
  public record RenameRequest(String title) {}
}
