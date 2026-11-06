package com.pettown.miniapp.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 小程序图片访问基址；仅接受 https 地址或测试用 localhost。 */
@ConfigurationProperties(prefix = "pettown.file")
public record MiniappFileProperties(String publicBaseUrl) {
}
