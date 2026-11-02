package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettown.business.user.mapper.UserMapper;
import com.pettown.common.cache.Cache;
import com.pettown.common.verificationEnum.VerificationCodeTypeEnum;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginApiTests {
    private static final String PHONE = "13800138000";
    private static final String LOGIN_KEY = "pettown:verification-code:login_register:" + PHONE;
    private final Map<String, Object> codes = new ConcurrentHashMap<>();

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserMapper userMapper;
    @MockitoBean private Cache cache;

    @BeforeEach
    void setUp() {
        userMapper.delete(new QueryWrapper<>());
        doAnswer(invocation -> {
            codes.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(cache).set(anyString(), any(), any(Duration.class));
        when(cache.compareAndDelete(anyString(), any()))
                .thenAnswer(invocation -> codes.remove(invocation.getArgument(0), invocation.getArgument(1)));
    }

    @Test
    void registersOnFirstLoginAndAuthenticatesExistingUsers() throws Exception {
        ResponseEntity<String> first = login(generate(VerificationCodeTypeEnum.LOGIN_REGISTER), "strongPass123");
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        JsonNode firstData = objectMapper.readTree(first.getBody()).path("data");
        String userId = firstData.path("userId").asText();
        assertThat(userId).matches("pettown[0-9a-f]{24}");
        assertThat(firstData.path("token").asText()).isNotBlank();
        assertThat(userMapper.selectById(userId).getPasswordHash()).startsWith("$2");
        assertThat(codes).doesNotContainKey(LOGIN_KEY);

        ResponseEntity<String> second = login(generate(VerificationCodeTypeEnum.LOGIN_REGISTER), "strongPass123");
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(second.getBody()).path("data").path("userId").asText()).isEqualTo(userId);
        assertThat(userMapper.selectCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"phone\":\"13800138000\",\"password\":\"strongPass123\"}",
            "{\"phone\":\"13800138000\",\"password\":\"strongPass123\",\"verificationCode\":null}",
            "{\"phone\":\"13800138000\",\"password\":\"strongPass123\",\"verificationCode\":\"\"}",
            "{\"phone\":\"13800138000\",\"password\":\"strongPass123\",\"verificationCode\":\"12345\"}",
            "{\"phone\":\"13800138000\",\"password\":\"strongPass123\",\"verificationCode\":\"abcdef\"}"
    })
    void rejectsMissingOrMalformedCodeBeforeCreatingAUser(String json) {
        assertRejected(post("/api/miniapp/auth/login", json));
        assertThat(userMapper.selectCount()).isZero();
    }

    @Test
    void wrongCodeDoesNotCreateAUserOrConsumeTheCorrectCode() {
        String correct = generate(VerificationCodeTypeEnum.LOGIN_REGISTER);
        String wrong = "000000".equals(correct) ? "000001" : "000000";

        assertRejected(login(wrong, "strongPass123"));
        assertThat(userMapper.selectCount()).isZero();
        assertThat(codes).containsEntry(LOGIN_KEY, correct);
        assertThat(login(correct, "strongPass123").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void expiredCodeDoesNotCreateAUser() {
        String code = generate(VerificationCodeTypeEnum.LOGIN_REGISTER);
        codes.remove(LOGIN_KEY);

        assertRejected(login(code, "strongPass123"));
        assertThat(userMapper.selectCount()).isZero();
    }

    @Test
    void resetPasswordCodeCannotBeUsedToLoginOrRegister() {
        String code = generate(VerificationCodeTypeEnum.RESET_PASSWORD);

        assertRejected(login(code, "strongPass123"));
        assertThat(userMapper.selectCount()).isZero();
        assertThat(codes).containsEntry("pettown:verification-code:reset_password:" + PHONE, code);
    }

    @Test
    void existingUserCannotReuseAConsumedCodeOrBypassPasswordVerification() {
        String code = generate(VerificationCodeTypeEnum.LOGIN_REGISTER);
        assertThat(login(code, "strongPass123").getStatusCode().value()).isEqualTo(200);
        assertRejected(login(code, "strongPass123"));

        String next = generate(VerificationCodeTypeEnum.LOGIN_REGISTER);
        assertRejected(login(next, "wrongPass123"));
        assertThat(codes).doesNotContainKey(LOGIN_KEY);
        assertThat(userMapper.selectCount()).isEqualTo(1);
    }

    @Test
    void concurrentLoginsCannotConsumeTheSameCodeTwice() {
        String code = generate(VerificationCodeTypeEnum.LOGIN_REGISTER);
        var first = CompletableFuture.supplyAsync(() -> login(code, "strongPass123").getStatusCode().value());
        var second = CompletableFuture.supplyAsync(() -> login(code, "strongPass123").getStatusCode().value());

        assertThat(java.util.List.of(first.join(), second.join())).containsExactlyInAnyOrder(200, 400);
        assertThat(userMapper.selectCount()).isEqualTo(1);
    }

    @Test
    void redisFailureDoesNotCreateAUserOrIssueAToken() {
        when(cache.compareAndDelete(anyString(), any())).thenThrow(new IllegalStateException("Redis unavailable"));

        ResponseEntity<String> response = login("000007", "strongPass123");
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).doesNotContain("\"token\"");
        assertThat(userMapper.selectCount()).isZero();
    }

    private String generate(VerificationCodeTypeEnum type) {
        ResponseEntity<String> response = post("/api/miniapp/verification/code",
                "{\"phone\":\"" + PHONE + "\",\"type\":\"" + type.name() + "\"}");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return (String) codes.get("pettown:verification-code:" + type.getValue() + ":" + PHONE);
    }

    private ResponseEntity<String> login(String code, String password) {
        return post("/api/miniapp/auth/login", "{\"phone\":\"" + PHONE + "\",\"password\":\"" + password
                + "\",\"verificationCode\":\"" + code + "\"}");
    }

    private void assertRejected(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).doesNotContain("\"token\"");
    }

    private ResponseEntity<String> post(String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(path, new HttpEntity<>(json, headers), String.class);
    }
}
