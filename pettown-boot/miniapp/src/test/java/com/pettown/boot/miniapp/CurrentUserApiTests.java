package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pettown.business.user.mapper.UserMapper;
import com.pettown.common.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CurrentUserApiTests {
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserMapper userMapper;
    @MockitoBean private Cache cache;

    @BeforeEach
    void setUp() {
        userMapper.delete(new QueryWrapper<>());
        when(cache.compareAndDelete("pettown:verification-code:login_register:13800138000", "000007"))
                .thenReturn(true);
    }

    @Test
    void currentUserRequiresValidTokenAndReturnsNoPassword() throws Exception {
        assertThat(getMe(null).getStatusCode().value()).isEqualTo(401);
        assertThat(getMe("invalid-token").getStatusCode().value()).isEqualTo(401);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = restTemplate.postForEntity("/api/miniapp/auth/login",
                new HttpEntity<>("{\"phone\":\"13800138000\",\"password\":\"strongPass123\",\"verificationCode\":\"000007\"}", headers),
                String.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        JsonNode loginData = objectMapper.readTree(login.getBody()).path("data");
        String token = loginData.path("token").asText();

        ResponseEntity<String> current = getMe(token);
        assertThat(current.getStatusCode().value()).isEqualTo(200);
        JsonNode data = objectMapper.readTree(current.getBody()).path("data");
        assertThat(data.path("userId").asText()).isEqualTo(loginData.path("userId").asText());
        assertThat(data.path("phone").asText()).isEqualTo("13800138000");
        assertThat(current.getBody()).doesNotContain("passwordHash", "strongPass123", "token");

        userMapper.deleteById(loginData.path("userId").asText());
        assertThat(getMe(token).getStatusCode().value()).isEqualTo(404);
    }

    private ResponseEntity<String> getMe(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.set("Authorization", "Bearer " + token);
        }
        return restTemplate.exchange("/api/miniapp/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }
}
