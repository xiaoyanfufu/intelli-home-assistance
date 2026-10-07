package com.intelli.home.adapter.in.http;

import com.intelli.home.application.service.DailyAdviceService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/daily-advice")
@RequiredArgsConstructor
public class DailyAdviceController {
  private final DailyAdviceService service;

  @PostMapping("/run")
  public Map<String, Object> run(@RequestParam(defaultValue = "false") boolean dryRun) {
    return service.run(dryRun);
  }
}
