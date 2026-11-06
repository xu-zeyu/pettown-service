package com.pettown.common.file.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.OSSObject;
import com.pettown.common.file.properties.OssProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "pettown.file", name = "enabled", havingValue = "true")
public class OssFileService implements FileService {
    private final OssProperties properties;

    public OssFileService(OssProperties properties) {
        this.properties = properties;
    }

    @Override
    public String upload(byte[] content, String originalFilename) {
        if (content == null || content.length == 0 || originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("文件内容和名称不能为空");
        }
        String extension = originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.')) : "";
        String key = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"))
                + "/" + UUID.randomUUID() + extension;
        OSS client = client();
        try {
            client.putObject(properties.bucketName(), key, new ByteArrayInputStream(content));
        } finally {
            client.shutdown();
        }
        return prefix() + key;
    }

    @Override
    public byte[] download(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(prefix()) || fileUrl.length() == prefix().length()) {
            throw new IllegalArgumentException("文件地址不属于当前 OSS 桶");
        }
        String key = fileUrl.substring(prefix().length());
        OSS client = client();
        try {
            OSSObject object = client.getObject(properties.bucketName(), key);
            try (InputStream stream = object.getObjectContent()) {
                return stream.readAllBytes();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("读取 OSS 文件失败", exception);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void uploadObject(String objectKey, byte[] content) {
        checkObjectKey(objectKey);
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("文件内容不能为空");
        }
        OSS client = client();
        try {
            client.putObject(properties.bucketName(), objectKey, new ByteArrayInputStream(content));
        } finally {
            client.shutdown();
        }
    }

    @Override
    public byte[] downloadObject(String objectKey) {
        checkObjectKey(objectKey);
        OSS client = client();
        try {
            OSSObject object = client.getObject(properties.bucketName(), objectKey);
            try (InputStream stream = object.getObjectContent()) {
                return stream.readAllBytes();
            }
        } catch (OSSException exception) {
            if ("NoSuchKey".equals(exception.getErrorCode())) {
                return null;
            }
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("读取 OSS 文件失败", exception);
        } finally {
            client.shutdown();
        }
    }

    private static void checkObjectKey(String objectKey) {
        if (objectKey == null || !objectKey.matches("miniapp/images/[0-9a-f-]{36}\\.(jpg|png|webp)")) {
            throw new IllegalArgumentException("文件对象标识无效");
        }
    }

    private OSS client() {
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setSignatureVersion(SignVersion.V4);
        return OSSClientBuilder.create()
                .endpoint(properties.endpoint())
                .credentialsProvider(new DefaultCredentialProvider(properties.accessKeyId(), properties.accessKeySecret()))
                .clientConfiguration(configuration)
                .region(properties.region())
                .build();
    }

    private String prefix() {
        return "https://" + properties.bucketName() + "." + properties.endpoint().replaceFirst("^https?://", "") + "/";
    }
}
