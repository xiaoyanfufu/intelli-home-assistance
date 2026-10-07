package com.intelli.home.config;

import com.intelli.home.domain.rule.*;
import java.util.List;
import org.springframework.context.annotation.*;

@Configuration
public class RuleConfiguration {
  @Bean
  Rule fireRule() {
    return new FireRiskRule();
  }

  @Bean
  Rule offlineRule() {
    return new OfflineRule();
  }

  @Bean
  RuleEngine ruleEngine(List<Rule> rules) {
    return new RuleEngine(rules);
  }
}
