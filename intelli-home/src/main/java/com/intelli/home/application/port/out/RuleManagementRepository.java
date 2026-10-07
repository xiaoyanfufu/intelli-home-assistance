package com.intelli.home.application.port.out;

import com.intelli.home.domain.home.ManagedRule;
import java.util.List;

public interface RuleManagementRepository {
  ManagedRule find(String code);

  boolean update(String code, long expected, ManagedRule rule);

  void recordRevision(ManagedRule rule);

  List<ManagedRule> revisions(String code);
}
