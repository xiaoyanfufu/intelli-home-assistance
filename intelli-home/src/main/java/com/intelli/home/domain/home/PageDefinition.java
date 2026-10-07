package com.intelli.home.domain.home;

import java.util.List;
import java.util.Map;

public record PageDefinition(int schemaVersion, String title, List<Widget> components) {
  public record Widget(
      String id, String type, int x, int y, int width, int height, Map<String, Object> binding) {}
}
