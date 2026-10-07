package com.intelli.home.adapter.out.persistence;

import static com.intelli.home.adapter.out.persistence.JsonRows.*;

import com.intelli.home.adapter.out.persistence.mapper.PageMapper;
import com.intelli.home.application.port.out.PageRepository;
import com.intelli.home.domain.home.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisPageRepository implements PageRepository {
  private final PageMapper mapper;
  private final JsonRows json;

  private DashboardPage page(Map<String, Object> row) {
    if (row == null) return null;
    Object flag = row.get("archived");
    boolean archived = flag instanceof Boolean b ? b : ((Number) flag).intValue() != 0;
    return new DashboardPage(
        text(row, "id"),
        number(row, "revision"),
        archived,
        number(row, "updated_at"),
        json.read(text(row, "definition"), PageDefinition.class));
  }

  public DashboardPage find(String id) {
    return page(mapper.find(id));
  }

  public List<DashboardPage> list() {
    return mapper.list().stream().map(this::page).toList();
  }

  public void create(DashboardPage page) {
    mapper.create(
        page.id(), page.definition().title(), json.write(page.definition()), page.updatedAt());
  }

  public boolean replace(DashboardPage page, long expected) {
    return mapper.replace(
            page.id(),
            page.definition().title(),
            json.write(page.definition()),
            page.revision(),
            expected,
            page.archived(),
            page.updatedAt())
        == 1;
  }

  public void recordRevision(DashboardPage page) {
    mapper.recordRevision(
        page.id(), page.revision(), json.write(page.definition()), page.updatedAt());
  }

  public PageDefinition revision(String id, long revision) {
    return json.read(mapper.revision(id, revision), PageDefinition.class);
  }

  public List<Long> revisions(String id) {
    return mapper.revisions(id);
  }
}
