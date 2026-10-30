package com.pettown.common.file.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aliyun.oss")
public record OssProperties(String endpoint, String accessKeyId, String accessKeySecret,
                            String bucketName, String region) {
}
