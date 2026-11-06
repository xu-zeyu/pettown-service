package com.pettown.miniapp.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.file.service.FileService;
import com.pettown.miniapp.file.config.MiniappFileProperties;
import com.pettown.miniapp.file.vo.FileUploadResponse;
import com.pettown.miniapp.file.vo.ImageContent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class MiniappFileServiceTests {
    private static final String USER = "user-0001";
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] WEBP = {82, 73, 70, 70, 24, 0, 0, 0, 87, 69, 66, 80};

    private FakeFileService files;
    private FakeUploadQuota quota;

    @BeforeEach
    void setUp() {
        files = new FakeFileService();
        quota = new FakeUploadQuota();
    }

    @Test
    void uploadsWithRandomKeyAndBuildsApplicationUrl() {
        MiniappFileService service = service("https://files.example.test", files);
        FileUploadResponse response = service.upload(USER, "头像.PNG", PNG);
        assertThat(response.fileId()).matches("[0-9a-f-]{36}\\.png");
        assertThat(response.url()).isEqualTo("https://files.example.test/api/miniapp/files/" + response.fileId());
        assertThat(files.store).containsKey("miniapp/images/" + response.fileId());
    }

    @Test
    void normalizesJpegToJpgAndMapsContentTypes() {
        MiniappFileService service = service("https://files.example.test", files);
        String jpg = service.upload(USER, "a.jpeg", JPG).fileId();
        assertThat(jpg).endsWith(".jpg");
        assertThat(service.read(jpg).contentType()).isEqualTo("image/jpeg");
        assertThat(service.read(service.upload(USER, "a.png", PNG).fileId()).contentType()).isEqualTo("image/png");
        assertThat(service.read(service.upload(USER, "a.webp", WEBP).fileId()).contentType()).isEqualTo("image/webp");
    }

    @Test
    void stripsTrailingSlashOfBaseUrl() {
        MiniappFileService service = service("https://files.example.test/", files);
        assertThat(service.upload(USER, "a.png", PNG).url())
                .startsWith("https://files.example.test/api/miniapp/files/");
    }

    @Test
    void rejectsEmptyOversizeAndUnknownFormat() {
        MiniappFileService service = service("https://files.example.test", files);
        assertThatBusinessFails(service, USER, "a.png", new byte[0]);
        byte[] oversize = new byte[MAX_BYTES + 1];
        assertThatBusinessFails(service, USER, "a.png", oversize);
        assertThatBusinessFails(service, USER, "a.gif", PNG);
        assertThatBusinessFails(service, USER, "no-extension", PNG);
        assertThatBusinessFails(service, USER, null, PNG);
    }

    @Test
    void rejectsContentMismatchedWithExtensionAndFakeWebp() {
        MiniappFileService service = service("https://files.example.test", files);
        assertThatBusinessFails(service, USER, "a.jpg", PNG);
        assertThatBusinessFails(service, USER, "a.png", JPG);
        byte[] avi = {82, 73, 70, 70, 24, 0, 0, 0, 65, 86, 73, 32};
        assertThatBusinessFails(service, USER, "a.webp", avi);
        assertThat(quota.reserved.get()).isZero();
    }

    @Test
    void rejectsWhenDailyQuotaExhausted() {
        quota.limit = 1;
        MiniappFileService service = service("https://files.example.test", files);
        service.upload(USER, "a.png", PNG);
        assertThatThrownBy(() -> service.upload(USER, "b.png", PNG))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上限");
    }

    @Test
    void releasesQuotaWhenStorageFails() {
        files.failUpload = true;
        MiniappFileService service = service("https://files.example.test", files);
        assertThatThrownBy(() -> service.upload(USER, "a.png", PNG))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCommonErrorCode().getCode())
                .isEqualTo("503");
        assertThat(quota.reserved.get()).isZero();
    }

    @Test
    void rejectsWhenFileServiceMissing() {
        MiniappFileService service = service("https://files.example.test", null);
        assertThatThrownBy(() -> service.upload(USER, "a.png", PNG))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件服务未配置");
        assertThat(quota.reserved.get()).isZero();
    }

    @Test
    void rejectsWhenBaseUrlMissingOrUntrusted() {
        for (String baseUrl : new String[]{"", "http://evil.example.test", "ftp://files.example.test", "not a url"}) {
            MiniappFileService service = service(baseUrl, files);
            assertThatThrownBy(() -> service.upload(USER, "a.png", PNG))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("访问基址");
        }
    }

    @Test
    void servesStoredImageAndRejectsUnknownOrMalformedIds() {
        MiniappFileService service = service("https://files.example.test", files);
        String fileId = service.upload(USER, "a.webp", WEBP).fileId();
        ImageContent image = service.read(fileId);
        assertThat(image.content()).isEqualTo(WEBP);
        assertThat(image.contentType()).isEqualTo("image/webp");
        assertThatThrownBy(() -> service.read("99999999-9999-4999-8999-999999999999.png"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCommonErrorCode().getCode())
                .isEqualTo("404");
        for (String bad : new String[]{null, "../secret", "abc.jpg", "miniapp/images/x.png", "ZZZZZZZZ-ZZZZ-4ZZZ-8ZZZ-ZZZZZZZZZZZZ.png"}) {
            assertThatThrownBy(() -> service.read(bad))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getCommonErrorCode().getCode())
                    .isEqualTo("404");
        }
    }

    private void assertThatBusinessFails(MiniappFileService service, String user, String filename, byte[] content) {
        assertThatThrownBy(() -> service.upload(user, filename, content))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCommonErrorCode().getCode())
                .isEqualTo("400");
    }

    private MiniappFileService service(String baseUrl, FileService storage) {
        var beans = new DefaultListableBeanFactory();
        if (storage != null) {
            beans.registerSingleton("fileService", storage);
        }
        return new MiniappFileService(beans.getBeanProvider(FileService.class), quota,
                new MiniappFileProperties(baseUrl));
    }

    static class FakeFileService implements FileService {
        final Map<String, byte[]> store = new ConcurrentHashMap<>();
        boolean failUpload;

        @Override
        public String upload(byte[] content, String originalFilename) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] download(String fileUrl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void uploadObject(String objectKey, byte[] content) {
            if (failUpload) {
                throw new IllegalStateException("存储故障");
            }
            store.put(objectKey, content);
        }

        @Override
        public byte[] downloadObject(String objectKey) {
            return store.get(objectKey);
        }
    }

    static class FakeUploadQuota implements UploadQuota {
        final AtomicInteger reserved = new AtomicInteger();
        int limit = 50;

        @Override
        public String reserve(String userId) {
            int used = reserved.incrementAndGet();
            if (used > limit) {
                reserved.decrementAndGet();
                throw new BusinessException(ErrorCodeEnum.TOO_MANY_REQUESTS, "今日图片上传已达上限");
            }
            return "reservation-" + userId;
        }

        @Override
        public void release(String reservationKey) {
            if (reserved.get() > 0) {
                reserved.decrementAndGet();
            }
        }
    }
}
