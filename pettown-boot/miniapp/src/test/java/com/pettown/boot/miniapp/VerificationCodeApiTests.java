package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettown.common.verificationEnum.VerificationCodeTypeEnum;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
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
class VerificationCodeApiTests {
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private RedissonClient redissonClient;
    private RBucket<Object> bucket;

    @BeforeEach
    void setUp() {
        bucket = mock(RBucket.class);
        when(redissonClient.getBucket(anyString())).thenReturn(bucket);
    }

    @ParameterizedTest
    @EnumSource(VerificationCodeTypeEnum.class)
    void generatesAndCachesWithoutLoginOrExposingTheCode(VerificationCodeTypeEnum type) throws Exception {
        ResponseEntity<String> response = generate("{\"phone\":\"13800138000\",\"type\":\"" + type.name() + "\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("code").asText()).isEqualTo("200");
        assertThat(body.path("data").isNull()).isTrue();
        verify(redissonClient).getBucket("pettown:verification-code:" + type.getValue() + ":13800138000");
        ArgumentCaptor<Object> code = ArgumentCaptor.forClass(Object.class);
        verify(bucket).set(code.capture(), eq(300_000L), eq(TimeUnit.MILLISECONDS));
        assertThat(code.getValue()).isInstanceOf(String.class);
        assertThat((String) code.getValue()).matches("[0-9]{6}");
        assertThat(response.getBody()).doesNotContain((String) code.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"phone\":\"\",\"type\":\"LOGIN_REGISTER\"}",
            "{\"phone\":\"123\",\"type\":\"LOGIN_REGISTER\"}",
            "{\"phone\":\"13800138000\"}",
            "{\"phone\":\"13800138000\",\"type\":null}",
            "{\"phone\":\"13800138000\",\"type\":\"UNKNOWN\"}",
            "{\"phone\":\"13800138000\",\"type\":\"LOGIN\"}",
            "{\"phone\":\"13800138000\",\"type\":\"REGISTER\"}"
    })
    void invalidParametersDoNotWriteRedis(String json) {
        assertThat(generate(json).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(redissonClient);
    }

    @Test
    void redisFailureDoesNotReportSuccess() {
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(bucket).set(any(), anyLong(), any(TimeUnit.class));

        ResponseEntity<String> response = generate("{\"phone\":\"13800138000\",\"type\":\"LOGIN_REGISTER\"}");
        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }

    private ResponseEntity<String> generate(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/miniapp/verification/code",
                new HttpEntity<>(json, headers), String.class);
    }
}
