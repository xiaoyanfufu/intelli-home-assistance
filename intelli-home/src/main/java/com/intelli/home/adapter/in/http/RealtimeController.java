package com.intelli.home.adapter.in.http;

import com.intelli.home.application.port.out.ChangeFeed;
import com.intelli.home.application.service.RealtimeService;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class RealtimeController {
  private final ChangeFeed changes;
  private final RealtimeService realtime;
  private final Semaphore slots = new Semaphore(8);
  private final ExecutorService workers =
      Executors.newFixedThreadPool(
          8,
          r -> {
            var thread = new Thread(r, "home-sse");
            thread.setDaemon(true);
            return thread;
          });

  @GetMapping("/snapshot")
  public Object snapshot() {
    return realtime.baseline();
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(
      @RequestHeader(value = "Last-Event-ID", required = false) String lastId,
      @RequestParam(required = false) Long cursor,
      @RequestParam(required = false) String deviceKey) {
    long after = lastId == null ? (cursor == null ? changes.latest() : cursor) : parse(lastId);
    if (after < 0) throw new IllegalArgumentException("Invalid cursor");
    if (!slots.tryAcquire())
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Too many streams");
    var emitter = new SseEmitter(60000L);
    var closed = new AtomicBoolean();
    emitter.onCompletion(() -> closed.set(true));
    emitter.onTimeout(() -> closed.set(true));
    emitter.onError(e -> closed.set(true));
    workers.execute(
        () -> {
          try {
            long position = after;
            long heartbeat = 0;
            while (!closed.get() && !Thread.currentThread().isInterrupted()) {
              long min = changes.earliest(), max = changes.latest();
              if (position > max || (min > 0 && position < min - 1)) {
                emitter.send(
                    SseEmitter.event()
                        .name("sync.required")
                        .data(Map.of("reason", "CURSOR_EXPIRED")));
                break;
              }
              var batch = changes.after(position, 100);
              for (var event : batch) {
                if (deviceKey == null || deviceKey.equals(event.deviceKey()))
                  emitter.send(
                      SseEmitter.event()
                          .id(Long.toString(event.id()))
                          .name(event.type())
                          .data(event));
                position = event.id();
              }
              if (System.currentTimeMillis() - heartbeat > 10000) {
                emitter.send(
                    SseEmitter.event()
                        .id(Long.toString(position))
                        .name("heartbeat")
                        .data(Map.of("cursor", position)));
                heartbeat = System.currentTimeMillis();
              }
              Thread.sleep(batch.size() == 100 ? 10 : 300);
            }
            emitter.complete();
          } catch (Exception e) {
            if (!closed.get()) emitter.completeWithError(e);
          } finally {
            slots.release();
          }
        });
    return emitter;
  }

  private long parse(String value) {
    try {
      return Long.parseLong(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid cursor");
    }
  }

  @PreDestroy
  public void stop() {
    workers.shutdownNow();
  }
}
