package com.pettown.common.file.config;

import com.pettown.common.file.properties.OssProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OssProperties.class)
@ConditionalOnProperty(prefix = "pettown.file", name = "enabled", havingValue = "true")
public class OssConfig {
}
