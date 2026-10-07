package com.intelli.home.application.port.out;

import com.intelli.home.domain.home.WeatherSnapshot;

public interface WeatherGateway {
  WeatherSnapshot fetch();
}
