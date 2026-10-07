package com.intelli.home.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

/** 注册 {@code intelli.*} 配置绑定类。 */
@Configuration
@ConfigurationPropertiesScan(basePackages = "com.intelli.home.config")
public class PropertiesConfig {}
