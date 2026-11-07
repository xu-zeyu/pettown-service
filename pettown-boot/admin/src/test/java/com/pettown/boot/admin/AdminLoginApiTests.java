package com.pettown.boot.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettown.admin.auth.service.AdminLoginFailureGuard;
import com.pettown.admin.verification.service.CaptchaStore;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminLoginApiTests {
    private static final String PASSWORD = "Fixture-Login-123!";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private cn.dev33.satoken.stp.StpInterface stpInterface;
    @Autowired private InMemoryCaptchaStore captchas;
    @Autowired private InMemoryFailureGuard guard;

    @BeforeEach
    void setUp() {
        // 其他测试类的上下文会把各自的 StpInterface/SaTokenDao 注册到全局 SaManager，先恢复本上下文的实现，
        // 保证权限解析与本测试事务同连接（否则看不到未提交的夹具数据，权限校验间歇性 403）。
        cn.dev33.satoken.SaManager.setSaTokenDao(new cn.dev33.satoken.dao.SaTokenDaoDefaultImpl());
        cn.dev33.satoken.SaManager.setStpInterface(stpInterface);
        captchas.reset();
        guard.reset();
        insertAccount("login_user", false);
        insertAccount("login_initial", true);
        insertAccount("login_disabled", false, false);
    }

    @AfterEach
    void tearDown() {
        captchas.reset();
        guard.reset();
    }

    @Test
    void successfulLoginReturnsTokenAndRecordsLoginTime() throws Exception {
        JsonNode data = login("login_user", PASSWORD, okCaptcha(), "AB34");
        assertThat(data.path("token").asText()).isNotBlank();
        assertThat(data.path("account").path("passwordChangeRequired").asBoolean()).isFalse();
        jdbc.queryForObject("SELECT last_login_time IS NOT NULL FROM admin_account WHERE username = ?",
                Boolean.class, "login_user");
        mvc.perform(get("/api/admin/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + data.path("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.username").value("login_user"));
    }

    @Test
    void initialPasswordAccountReportsPasswordChangeRequired() throws Exception {
        JsonNode data = login("login_initial", PASSWORD, okCaptcha(), "AB34");
        assertThat(data.path("account").path("passwordChangeRequired").asBoolean()).isTrue();
    }

    @Test
    void wrongCaptchaIsRejectedBeforePasswordCheckAndDoesNotCountAsFailure() throws Exception {
        String ticket = okCaptcha();
        mvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("login_user", "Wrong-Password-123!", ticket, "WRONG")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4002"));
        // 验证码错误不计入密码失败。
        assertThat(guard.failures.get("login_user")).isNull();
        // 随后正确凭据仍可登录。
        JsonNode data = login("login_user", PASSWORD, okCaptcha(), "AB34");
        assertThat(data.path("token").asText()).isNotBlank();
    }

    @Test
    void unknownWrongPasswordAndDisabledAccountReturnTheSameError() throws Exception {
        for (String[] attempt : new String[][]{
                {"login_user", "Wrong-Password-123!"},
                {"login_unknown", PASSWORD},
                {"login_disabled", PASSWORD}}) {
            mvc.perform(post("/api/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(attempt[0], attempt[1], okCaptcha(), "AB34")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("400"))
                    .andExpect(jsonPath("$.msg").value("账号或密码错误"));
        }
    }

    @Test
    void fiveConsecutivePasswordFailuresLockTheAccountForThirtyMinutes() throws Exception {
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("login_user", "Wrong-Password-123!", okCaptcha(), "AB34")))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("login_user", "Wrong-Password-123!", okCaptcha(), "AB34")))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("423"));
        // 锁定后正确凭据同样拒绝。
        mvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("login_user", PASSWORD, okCaptcha(), "AB34")))
                .andExpect(status().isLocked());
    }

    private String okCaptcha() {
        String ticket = "ticket-" + System.nanoTime();
        captchas.save(ticket, "AB34");
        return ticket;
    }

    private JsonNode login(String username, String password, String ticket, String code) throws Exception {
        String body = mvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username, password, ticket, code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    private String body(String username, String password, String ticket, String code) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password
                + "\",\"captchaId\":\"" + ticket + "\",\"captchaCode\":\"" + code + "\"}";
    }

    private void insertAccount(String username, boolean passwordChangeRequired) {
        insertAccount(username, passwordChangeRequired, true);
    }

    private void insertAccount(String username, boolean passwordChangeRequired, boolean enabled) {
        jdbc.update("INSERT INTO admin_account(username,display_name,phone,password_hash,enabled,super_admin,"
                        + "password_change_required,session_version,created_time,updated_time) VALUES (?,?,?,?,?,?,?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                username, "登录夹具", "13800000000", new BCryptPasswordEncoder().encode(PASSWORD),
                enabled, false, passwordChangeRequired);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LoginTestDoubles {
        @Bean @Primary
        InMemoryCaptchaStore captchaStore() {
            return new InMemoryCaptchaStore();
        }

        @Bean @Primary
        InMemoryFailureGuard adminLoginFailureGuard() {
            return new InMemoryFailureGuard();
        }
    }

    static class InMemoryCaptchaStore implements CaptchaStore {
        private final Map<String, String> tickets = new ConcurrentHashMap<>();

        void reset() {
            tickets.clear();
        }

        @Override
        public void save(String captchaId, String code) {
            tickets.put(captchaId, code);
        }

        @Override
        public String pop(String captchaId) {
            return tickets.remove(captchaId);
        }
    }

    static class InMemoryFailureGuard implements AdminLoginFailureGuard {
        static final int LIMIT = 5;
        final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();

        void reset() {
            failures.clear();
        }

        @Override
        public void assertNotLocked(String username) {
            if (count(username) >= LIMIT) {
                throw new BusinessException(ErrorCodeEnum.LOGIN_LOCKED, "密码连续错误 5 次，账号暂锁 30 分钟");
            }
        }

        @Override
        public void recordPasswordFailure(String username) {
            if (failures.computeIfAbsent(username, key -> new AtomicInteger()).incrementAndGet() >= LIMIT) {
                throw new BusinessException(ErrorCodeEnum.LOGIN_LOCKED, "密码连续错误 5 次，账号暂锁 30 分钟");
            }
        }

        @Override
        public void clear(String username) {
            failures.remove(username);
        }

        private int count(String username) {
            AtomicInteger count = failures.get(username);
            return count == null ? 0 : count.get();
        }
    }
}
