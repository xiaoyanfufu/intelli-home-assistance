package com.intelli.home.adapter.in.http;

import com.intelli.home.application.service.PageService;
import com.intelli.home.domain.home.PageDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pages")
@RequiredArgsConstructor
public class PageController {
  private final PageService pages;

  public record RestoreRequest(long revision) {}

  @GetMapping
  public Object list() {
    return pages.list();
  }

  @PostMapping
  public Object create(@RequestBody PageDefinition definition) {
    return pages.create(definition);
  }

  @GetMapping("/{id}")
  public Object get(@PathVariable String id) {
    return pages.require(id);
  }

  @PutMapping("/{id}")
  public Object replace(
      @PathVariable String id,
      @RequestHeader("If-Match") long revision,
      @RequestBody PageDefinition definition) {
    return pages.replace(id, revision, definition);
  }

  @DeleteMapping("/{id}")
  public Object archive(@PathVariable String id, @RequestHeader("If-Match") long revision) {
    return pages.archive(id, revision);
  }

  @GetMapping("/{id}/revisions")
  public Object revisions(@PathVariable String id) {
    return pages.revisions(id);
  }

  @PostMapping("/{id}/restore")
  public Object restore(
      @PathVariable String id,
      @RequestHeader("If-Match") long expected,
      @RequestBody RestoreRequest request) {
    return pages.restore(id, expected, request.revision());
  }
}
