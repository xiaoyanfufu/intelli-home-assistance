package com.intelli.home.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.home.application.port.out.PageRepository;
import com.intelli.home.domain.home.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PageService {
  private final PageRepository pages;
  private final DeviceCatalogService devices;
  private final ObjectMapper json;

  public DashboardPage require(String id) {
    var page = pages.find(id);
    if (page == null) throw new IllegalArgumentException("Unknown page");
    return page;
  }

  public List<DashboardPage> list() {
    return pages.list();
  }

  public void validate(PageDefinition definition) {
    if (definition == null
        || definition.schemaVersion() != 1
        || definition.title() == null
        || definition.title().isBlank()
        || definition.title().length() > 128
        || definition.components() == null
        || definition.components().size() > 30)
      throw new IllegalArgumentException("Invalid page definition");
    try {
      if (json.writeValueAsBytes(definition).length > 65536)
        throw new IllegalArgumentException("Page too large");
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid page JSON");
    }
    var ids = new HashSet<String>();
    for (var widget : definition.components()) {
      if (widget == null
          || widget.type() == null
          || widget.id() == null
          || !widget.id().matches("[a-zA-Z][a-zA-Z0-9_-]{0,63}")
          || !ids.add(widget.id())
          || widget.x() < 0
          || widget.y() < 0
          || widget.width() < 1
          || widget.height() < 1
          || (long) widget.x() + widget.width() > 12
          || widget.y() > 1000
          || widget.height() > 1000
          || widget.binding() == null)
        throw new IllegalArgumentException("Invalid widget layout or ID");
      var allowed =
          switch (widget.type()) {
            case "device-property" -> Set.of("deviceKey", "property");
            case "history-chart" -> Set.of("deviceKey", "property", "rangeSeconds", "interval");
            case "alert-list" -> Set.of("deviceKey", "limit");
            case "recommendation" -> Set.of("deviceKey");
            default -> throw new IllegalArgumentException("Unknown component");
          };
      if (!allowed.containsAll(widget.binding().keySet()))
        throw new IllegalArgumentException("Unsupported binding");
      var key = widget.binding().get("deviceKey");
      if (key != null) {
        if (!(key instanceof String s))
          throw new IllegalArgumentException("Invalid device binding");
        devices.require(s);
      } else if (!widget.type().equals("alert-list"))
        throw new IllegalArgumentException("Device required");
      if (widget.type().equals("device-property") || widget.type().equals("history-chart")) {
        Object property = widget.binding().get("property");
        if (!(property instanceof String p)
            || !devices.capabilities(key.toString()).properties().containsKey(p))
          throw new IllegalArgumentException("Unknown property binding");
        if (widget.type().equals("history-chart")
            && !devices.capabilities(key.toString()).properties().get(p).queryable())
          throw new IllegalArgumentException("Property not queryable");
      }
      if (widget.binding().containsKey("rangeSeconds")) {
        var range = widget.binding().get("rangeSeconds");
        if (!(range instanceof Number n) || n.longValue() < 1 || n.longValue() > 31L * 86400)
          throw new IllegalArgumentException("Invalid chart range");
      }
      if (widget.binding().containsKey("interval")
          && !Set.of("raw", "minute", "hour").contains(widget.binding().get("interval")))
        throw new IllegalArgumentException("Invalid chart interval");
      if (widget.binding().containsKey("limit")) {
        var limit = widget.binding().get("limit");
        if (!(limit instanceof Number n) || n.intValue() < 1 || n.intValue() > 100)
          throw new IllegalArgumentException("Invalid alert limit");
      }
    }
  }

  @Transactional
  public DashboardPage create(PageDefinition definition) {
    validate(definition);
    var page =
        new DashboardPage(
            UUID.randomUUID().toString(), 1, false, System.currentTimeMillis(), definition);
    pages.create(page);
    pages.recordRevision(page);
    return page;
  }

  @Transactional
  public DashboardPage replace(String id, long expected, PageDefinition definition) {
    validate(definition);
    var old = require(id);
    var page =
        new DashboardPage(id, expected + 1, old.archived(), System.currentTimeMillis(), definition);
    if (!pages.replace(page, expected))
      throw new VersionConflictException("Page changed; reload before editing");
    pages.recordRevision(page);
    return page;
  }

  @Transactional
  public DashboardPage archive(String id, long expected) {
    var old = require(id);
    var page =
        new DashboardPage(id, expected + 1, true, System.currentTimeMillis(), old.definition());
    if (!pages.replace(page, expected))
      throw new VersionConflictException("Page revision conflict");
    pages.recordRevision(page);
    return page;
  }

  public List<Long> revisions(String id) {
    require(id);
    return pages.revisions(id);
  }

  @Transactional
  public DashboardPage restore(String id, long expected, long revision) {
    var definition = pages.revision(id, revision);
    if (definition == null) throw new IllegalArgumentException("Unknown page revision");
    validate(definition);
    var page = new DashboardPage(id, expected + 1, false, System.currentTimeMillis(), definition);
    if (!pages.replace(page, expected))
      throw new VersionConflictException("Page revision conflict");
    pages.recordRevision(page);
    return page;
  }
}
