package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.file.service.FileService;
import com.pettown.common.security.config.PetTownToken;
import com.pettown.miniapp.file.service.UploadQuota;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MiniappFileUploadApiTests {
    private static final String OWNER = "pettown-file-owner-0000000001";
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 74, 70};
    private static final byte[] WEBP = {82, 73, 70, 70, 24, 0, 0, 0, 87, 69, 66, 80, 86, 80, 56};

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private InMemoryUploadQuota quota;
    @Autowired private InMemoryFileService files;
    private String token;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OWNER, "13800138071", "fixture-hash", "MINI_PROGRAM");
        token = PetTownToken.USER.createLoginSession(OWNER);
        quota.reset();
        files.reset();
    }

    @AfterEach
    void tearDown() {
        PetTownToken.USER.logoutByTokenValue(token);
    }

    @Test
    void uploadsSupportedFormatsAndServesImagesBackWithSafeHeaders() throws Exception {
        assertRoundTrip(PNG, "png", "image/png");
        assertRoundTrip(JPG, "jpg", "image/jpeg");
        assertRoundTrip(WEBP, "webp", "image/webp");
    }

    @Test
    void normalizesJpegExtensionToJpg() throws Exception {
        JsonNode data = uploadOk(new MockMultipartFile("file", "avatar.jpeg", "image/jpeg", JPG));
        assertThat(data.path("fileId").asText()).matches("[0-9a-f-]{36}\\.jpg");
    }

    @Test
    void rejectsOversizeImageWithoutConsumingQuota() throws Exception {
        byte[] oversize = new byte[MAX_BYTES + 1];
        System.arraycopy(PNG, 0, oversize, 0, PNG.length);
        mvc.perform(multipart("/api/miniapp/files/upload")
                        .file(new MockMultipartFile("file", "big.png", "image/png", oversize))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
        assertThat(quota.usedToday(OWNER)).isZero();
    }

    @Test
    void rejectsUnsupportedExtension() throws Exception {
        mvc.perform(multipart("/api/miniapp/files/upload")
                        .file(new MockMultipartFile("file", "anim.gif", "image/gif", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsContentMismatchedWithExtension() throws Exception {
        mvc.perform(multipart("/api/miniapp/files/upload")
                        .file(new MockMultipartFile("file", "fake.jpg", "image/jpeg", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsRiffContainerThatIsNotWebp() throws Exception {
        byte[] avi = Arrays.copyOf(WEBP, WEBP.length);
        avi[8] = 'A';
        avi[9] = 'V';
        avi[10] = 'I';
        mvc.perform(multipart("/api/miniapp/files/upload")
                        .file(new MockMultipartFile("file", "clip.webp", "image/webp", avi))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        mvc.perform(multipart("/api/miniapp/files/upload")
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnonymousUploadAndRead() throws Exception {
        mvc.perform(multipart("/api/miniapp/files/upload")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/miniapp/files/11111111-1111-1111-1111-111111111111.png"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAdminTokenOnUploadAndRead() throws Exception {
        String adminToken = PetTownToken.ADMIN.createLoginSession(100L);
        try {
            mvc.perform(multipart("/api/miniapp/files/upload")
                            .file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isUnauthorized());
            JsonNode data = uploadOk(new MockMultipartFile("file", "a.png", "image/png", PNG));
            mvc.perform(get(data.path("url").asText().replace("http://localhost:8082", ""))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isUnauthorized());
        } finally {
            PetTownToken.ADMIN.logoutByTokenValue(adminToken);
        }
    }

    @Test
    void enforcesDailyUploadLimitOfFifty() throws Exception {
        for (int i = 0; i < 50; i++) {
            mvc.perform(multipart("/api/miniapp/files/upload")
                            .file(new MockMultipartFile("file", "u" + i + ".png", "image/png", PNG))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());
        }
        mvc.perform(multipart("/api/miniapp/files/upload")
                        .file(new MockMultipartFile("file", "overflow.png", "image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void storesRandomObjectKeyWithoutTrustingOriginalFilename() throws Exception {
        JsonNode data = uploadOk(new MockMultipartFile("file", "../../evil.png", "image/png", PNG));
        assertThat(data.path("fileId").asText()).matches("[0-9a-f-]{36}\\.png");
        assertThat(data.path("url").asText())
                .isEqualTo("http://localhost:8082/api/miniapp/files/" + data.path("fileId").asText());
    }

    @Test
    void returnsNotFoundForUnknownOrMalformedFileId() throws Exception {
        mvc.perform(get("/api/miniapp/files/99999999-9999-4999-8999-999999999999.png")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/miniapp/files/abc.jpg")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void blocksDirectoryTraversalWithoutServingImageContent() throws Exception {
        // Sa-Token 防火墙在入口拦截路径穿越（纯文本“非法请求”），即使绕过也只应得到 404，绝不返回图片内容。
        var response = mvc.perform(get("/api/miniapp/files/..%2Fsecret")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isIn(200, 404);
        assertThat(response.getContentType()).doesNotContain("image");
    }

    private void assertRoundTrip(byte[] payload, String extension, String contentType) throws Exception {
        JsonNode data = uploadOk(new MockMultipartFile("file", "photo." + extension, contentType, payload));
        String fileId = data.path("fileId").asText();
        assertThat(fileId).matches("[0-9a-f-]{36}\\." + extension);
        mvc.perform(get("/api/miniapp/files/{fileId}", fileId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentType(contentType))
                .andExpect(content().bytes(payload));
    }

    private JsonNode uploadOk(MockMultipartFile file) throws Exception {
        String body = mvc.perform(multipart("/api/miniapp/files/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.fileId").isNotEmpty())
                .andExpect(jsonPath("$.data.url").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FileTestDoubles {
        @Bean @Primary
        InMemoryFileService fileService() {
            return new InMemoryFileService();
        }

        @Bean @Primary
        InMemoryUploadQuota uploadQuota() {
            return new InMemoryUploadQuota();
        }
    }

    static class InMemoryFileService implements FileService {
        private final Map<String, byte[]> store = new ConcurrentHashMap<>();

        void reset() {
            store.clear();
        }

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
            store.put(objectKey, content);
        }

        @Override
        public byte[] downloadObject(String objectKey) {
            return store.get(objectKey);
        }
    }

    static class InMemoryUploadQuota implements UploadQuota {
        private static final int LIMIT = 50;
        private final Map<String, AtomicInteger> used = new ConcurrentHashMap<>();

        void reset() {
            used.clear();
        }

        int usedToday(String userId) {
            AtomicInteger count = used.get(userId);
            return count == null ? 0 : count.get();
        }

        @Override
        public String reserve(String userId) {
            int count = used.computeIfAbsent(userId, key -> new AtomicInteger()).incrementAndGet();
            if (count > LIMIT) {
                used.get(userId).decrementAndGet();
                throw new BusinessException(ErrorCodeEnum.TOO_MANY_REQUESTS, "今日图片上传已达 50 张上限");
            }
            return userId;
        }

        @Override
        public void release(String reservationKey) {
            AtomicInteger count = used.get(reservationKey);
            if (count != null && count.get() > 0) {
                count.decrementAndGet();
            }
        }
    }
}
