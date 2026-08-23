package com.dreamspace.api.controller;

import com.dreamspace.api.common.AdminPermission;
import com.dreamspace.api.service.AdminTasksService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/manage_web/tasks")
@AdminPermission
public class AdminTasksController {
  private final AdminTasksService service;

  public AdminTasksController(AdminTasksService service) { this.service = service; }

  @GetMapping
  AdminTasksService.Page list(@RequestParam(required = false) String status,
      @RequestParam(required = false) String model, @RequestParam(required = false) String query,
      @RequestParam(required = false) String createdFrom,
      @RequestParam(required = false) String createdTo,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return service.list(status, model, query, createdFrom, createdTo, page, pageSize);
  }

  @GetMapping("/results/{resultId}/content")
  ResponseEntity<byte[]> content(@PathVariable String resultId) { return binary(resultId, false); }

  @GetMapping("/results/{resultId}/thumbnail")
  ResponseEntity<byte[]> thumbnail(@PathVariable String resultId) { return binary(resultId, true); }

  @GetMapping("/reconciliation/runs")
  AdminTasksService.ReconciliationResponse reconciliation() { return service.reconciliation(); }

  @GetMapping("/{taskId}")
  AdminTasksService.TaskDetail detail(@PathVariable String taskId) { return service.get(taskId); }

  private ResponseEntity<byte[]> binary(String resultId, boolean thumbnail) {
    var data = service.readResult(resultId, thumbnail);
    String extension = data.contentType().toLowerCase().endsWith("png") ? "png" : "webp";
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(data.contentType()))
        .contentLength(data.bytes().length)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"generation-result." + extension + "\"")
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
        .header("X-Content-Type-Options", "nosniff").body(data.bytes());
  }
}
