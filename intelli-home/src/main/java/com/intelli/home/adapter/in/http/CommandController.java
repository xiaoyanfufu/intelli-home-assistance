package com.intelli.home.adapter.in.http;

import com.intelli.home.application.service.CommandService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommandController {
  private final CommandService commands;

  public record Request(String action, Map<String, Object> parameters) {}

  @PostMapping("/devices/{key}/commands")
  public Object create(
      @PathVariable String key,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestBody Request request) {
    return ResponseEntity.accepted()
        .body(commands.submit(key, idempotencyKey, request.action(), request.parameters()));
  }

  @GetMapping("/commands/{id}")
  public Object get(@PathVariable String id) {
    return commands.require(id);
  }

  @GetMapping("/devices/{key}/commands")
  public Object list(@PathVariable String key) {
    return commands.list(key);
  }
}
