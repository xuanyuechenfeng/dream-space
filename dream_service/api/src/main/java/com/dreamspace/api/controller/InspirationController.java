package com.dreamspace.api.controller;

import com.dreamspace.api.service.InspirationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dream_web/inspirations")
public class InspirationController {
  private final InspirationService service;
  public InspirationController(InspirationService service) { this.service = service; }
  @GetMapping InspirationService.Page list(@RequestParam(required = false) String category, @RequestParam(name = "q", required = false) String q, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "24") int pageSize) { return service.list(category, q, page, pageSize); }
  @GetMapping("/{slug}") InspirationService.Item detail(@PathVariable String slug) { return service.detail(slug); }
}
