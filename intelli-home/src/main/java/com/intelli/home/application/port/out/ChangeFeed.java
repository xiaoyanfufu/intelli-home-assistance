package com.intelli.home.application.port.out;

import com.intelli.home.domain.home.ChangeEvent;
import java.util.List;

public interface ChangeFeed {
  void append(ChangeEvent event);

  List<ChangeEvent> after(long cursor, int limit);

  long latest();

  long earliest();

  int cleanup(long before, int limit);
}
