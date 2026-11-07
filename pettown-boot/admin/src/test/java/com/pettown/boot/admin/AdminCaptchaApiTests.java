package com.pettown.boot.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettown.admin.verification.service.CaptchaRateLimiter;
import com.pettown.admin.verification.service.CaptchaStore;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCaptchaApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private InMemoryRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
    }

    @Test
    void issuesCaptchaIdAndDecodablePngImage() throws Exception {
        JsonNode first = captcha();
        JsonNode second = captcha();
        assertThat(first.path("captchaId").asText()).isNotBlank();
        assertThat(second.path("captchaId").asText()).isNotEqualTo(first.path("captchaId").asText());
        byte[] image = Base64.getDecoder().decode(first.path("imageBase64").asText());
        assertThat(image.length).isGreaterThan(8);
        assertThat(image[0]).isEqualTo((byte) 137);
        assertThat(image[1]).isEqualTo((byte) 80);
        assertThat(image[2]).isEqualTo((byte) 78);
        assertThat(image[3]).isEqualTo((byte) 71);
    }

    @Test
    void enforcesPerIpLimitOfTwentyPerMinute() throws Exception {
        for (int i = 0; i < 20; i++) {
            mvc.perform(get("/api/admin/verification/captcha")).andExpect(status().isOk());
        }
        mvc.perform(get("/api/admin/verification/captcha"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void forgedXRealIpHeaderCannotBypassTheLimit() throws Exception {
        for (int i = 0; i < 20; i++) {
            mvc.perform(get("/api/admin/verification/captcha")
                            .header("X-Real-IP", "198.51.100." + i))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/admin/verification/captcha")
                        .header("X-Real-IP", "198.51.100.99"))
                .andExpect(status().isTooManyRequests());
    }

    private JsonNode captcha() throws Exception {
        String body = mvc.perform(get("/api/admin/verification/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.captchaId").isNotEmpty())
                .andExpect(jsonPath("$.data.imageBase64").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CaptchaTestDoubles {
        @Bean @Primary
        InMemoryStore captchaStore() {
            return new InMemoryStore();
        }

        @Bean @Primary
        InMemoryRateLimiter captchaRateLimiter() {
            return new InMemoryRateLimiter();
        }
    }

    static class InMemoryStore implements CaptchaStore {
        private final Map<String, String> tickets = new ConcurrentHashMap<>();

        @Override
        public void save(String captchaId, String code) {
            tickets.put(captchaId, code);
        }

        @Override
        public String pop(String captchaId) {
            return tickets.remove(captchaId);
        }
    }

    static class InMemoryRateLimiter implements CaptchaRateLimiter {
        private static final int LIMIT = 20;
        private final AtomicInteger used = new AtomicInteger();

        void reset() {
            used.set(0);
        }

        @Override
        public void checkAndRecord(String clientIp) {
            if (used.incrementAndGet() > LIMIT) {
                throw new BusinessException(ErrorCodeEnum.TOO_MANY_REQUESTS, "验证码请求过于频繁");
            }
        }
    }
}
