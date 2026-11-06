package com.pettown.miniapp.file.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MiniappFileProperties.class)
public class MiniappFileConfig {
}
