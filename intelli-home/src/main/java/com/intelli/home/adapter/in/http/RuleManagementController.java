package com.intelli.home.adapter.in.http;

import com.intelli.home.application.service.*;
import com.intelli.home.domain.event.DeviceEvent;
import com.intelli.home.domain.rule.RuleDefinition;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleManagementController {
  private final RuleManagementService rules;
  private final RuleConfigService config;

  public record Trial(
      DeviceEvent sourceEvent,
      Map<String, Object> effectiveProperties,
      RuleDefinition definition) {}

  @GetMapping
  public Object list() {
    return rules.list();
  }

  @PutMapping("/{code}/config")
  public Object update(
      @PathVariable String code,
      @RequestHeader("If-Match") long expected,
      @RequestBody RuleDefinition definition) {
    rules.update(code, expected, definition);
    config.refresh();
    return rules.describe(code);
  }

  @PostMapping("/{code}/evaluate")
  public Object trial(@PathVariable String code, @RequestBody Trial input) {
    return rules.trial(code, input.sourceEvent(), input.effectiveProperties(), input.definition());
  }

  @GetMapping("/{code}/revisions")
  public Object revisions(@PathVariable String code) {
    return rules.revisions(code);
  }
}
