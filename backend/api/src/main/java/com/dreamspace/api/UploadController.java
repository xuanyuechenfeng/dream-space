package com.dreamspace.api;

import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/uploads/references")
public class UploadController {
  private final UploadService service; private final AuthService auth; public UploadController(UploadService service, AuthService auth) { this.service = service; this.auth = auth; }
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) UploadService.Response upload(@RequestPart("file") MultipartFile file, HttpServletRequest req) { var s = auth.session(CookieSupport.read(req, CookieSupport.USER)); if (!s.authenticated()) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录"); return service.create(s.user().id(), file); }
  @GetMapping("/{id}/content") ResponseEntity<byte[]> content(@PathVariable String id, HttpServletRequest req) { var s = auth.session(CookieSupport.read(req, CookieSupport.USER)); if (!s.authenticated()) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录"); var data = service.read(s.user().id(), id); return ResponseEntity.ok().contentType(MediaType.parseMediaType(data.contentType())).contentLength(data.bytes().length).header(HttpHeaders.CONTENT_DISPOSITION, "inline").header("Cache-Control", "private, no-store").header("X-Content-Type-Options", "nosniff").body(data.bytes()); }
}
