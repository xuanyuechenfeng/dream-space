package com.dreamspace.api;

import com.dreamspace.common.persistence.database.DatabaseEnums.AdminRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/inspirations")
@AdminPermission
public class AdminInspirationsController {
  private final AdminInspirationsService service;

  public AdminInspirationsController(AdminInspirationsService service) { this.service = service; }

  @GetMapping
  AdminInspirationsService.Page list(@RequestParam(required = false) String status,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String query,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return service.list(status, category, query, page, pageSize);
  }

  @GetMapping("/{id}")
  AdminInspirationsService.Item get(@PathVariable String id) { return service.get(id); }

  @PostMapping
  @AdminPermission(minimum = AdminRole.OPERATOR)
  AdminInspirationsService.Item create(@RequestBody AdminInspirationsService.Input input) {
    return service.create(input);
  }

  @PatchMapping("/{id}")
  @AdminPermission(minimum = AdminRole.OPERATOR)
  AdminInspirationsService.Item update(@PathVariable String id,
      @RequestBody AdminInspirationsService.Input input) {
    return service.update(id, input);
  }

  @PostMapping("/{id}/publish")
  @AdminPermission(minimum = AdminRole.OPERATOR)
  AdminInspirationsService.Item publish(@PathVariable String id,
      @RequestBody AdminInspirationsService.Transition input) {
    return service.transition(id, "published", input);
  }

  @PostMapping("/{id}/unpublish")
  @AdminPermission(minimum = AdminRole.OPERATOR)
  AdminInspirationsService.Item unpublish(@PathVariable String id,
      @RequestBody AdminInspirationsService.Transition input) {
    return service.transition(id, "archived", input);
  }
}
