package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettown.business.user.entity.User;
import com.pettown.business.user.mapper.JdbcUserMapper;
import com.pettown.common.security.config.PetTownToken;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
class AuthApiTests {
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private JdbcTemplate jdbcTemplate;
    @MockitoBean private JdbcUserMapper userMapper;

    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @BeforeEach
    void setUp() {
        users.clear();
        when(userMapper.findByPhone(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(users.get(invocation.getArgument(0))));
        when(userMapper.insert(anyString(), anyString())).thenAnswer(invocation -> {
            String phone = invocation.getArgument(0);
            User user = new User(ids.incrementAndGet(), phone, invocation.getArgument(1));
            users.put(phone, user);
            return user;
        });
    }

    @Test
    void registerAndLogin() throws Exception {
        String phone = "13800138000";
        ResponseEntity<String> registered = post("/api/miniapp/auth/register", phone, "strongPass123");
        assertThat(registered.getStatusCode().value()).isEqualTo(201);
        assertThat(registered.getBody()).contains("\"code\":\"200\"", "\"phone\":\"" + phone + "\"", "\"token\":");
        assertThat(registered.getBody()).doesNotContain("strongPass123", "passwordHash");
        assertThat(users.get(phone).passwordHash()).startsWith("$2");
        JsonNode registeredData = objectMapper.readTree(registered.getBody()).get("data");
        assertThat(PetTownToken.USER.getLoginIdByToken(registeredData.get("token").asText()))
                .isEqualTo(registeredData.get("userId").asText());

        ResponseEntity<String> loggedIn = post("/api/miniapp/auth/login", phone, "strongPass123");
        assertThat(loggedIn.getStatusCode().value()).isEqualTo(200);
        assertThat(loggedIn.getBody()).contains("\"token\":", "\"userId\":");
        assertThat(post("/api/miniapp/auth/register", phone, "strongPass123").getStatusCode().value()).isEqualTo(400);
        assertThat(post("/api/miniapp/auth/login", phone, "wrongPass123").getStatusCode().value()).isEqualTo(400);
        assertThat(post("/api/miniapp/auth/login", "13900139000", "strongPass123").getStatusCode().value()).isEqualTo(400);
        assertThat(post("/api/miniapp/auth/login", "not-a-phone", "strongPass123").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void exposesOpenApiForApifox() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode document = objectMapper.readTree(response.getBody());
        assertThat(document.path("info").path("title").asText()).isEqualTo("PetTown 小程序接口");
        assertThat(document.path("paths").path("/api/miniapp/auth/register")
                .path("post").path("summary").asText()).isEqualTo("手机号注册");
        assertThat(document.path("paths").path("/api/miniapp/auth/login")
                .path("post").path("summary").asText()).isEqualTo("手机号密码登录");
    }

    private ResponseEntity<String> post(String path, String phone, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(path,
                new HttpEntity<>("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}", headers),
                String.class);
    }
}
