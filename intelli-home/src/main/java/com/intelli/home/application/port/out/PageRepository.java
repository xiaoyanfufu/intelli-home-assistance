package com.intelli.home.application.port.out;

import com.intelli.home.domain.home.*;
import java.util.List;

public interface PageRepository {
  DashboardPage find(String id);

  List<DashboardPage> list();

  void create(DashboardPage page);

  boolean replace(DashboardPage page, long expectedRevision);

  void recordRevision(DashboardPage page);

  PageDefinition revision(String id, long revision);

  List<Long> revisions(String id);
}
