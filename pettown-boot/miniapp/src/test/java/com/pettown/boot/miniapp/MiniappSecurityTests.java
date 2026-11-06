package com.pettown.boot.miniapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettown.business.verification.service.VerificationCodeService;
import com.pettown.business.user.service.LoginFailureGuard;
import com.pettown.miniapp.verification.service.VerificationRequestLimiter;
import com.pettown.common.result.Result;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.security.config.PetTownToken;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(MiniappSecurityTests.ProbeConfiguration.class)
@Transactional
class MiniappSecurityTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private VerificationCodeService verificationCodeService;
    @MockitoBean private LoginFailureGuard loginFailureGuard;
    @MockitoBean private VerificationRequestLimiter verificationRequestLimiter;

    @Test
    void loginAndVerificationRemainPublicAndUserInfoRequiresAUserToken() throws Exception {
        mvc.perform(post("/api/miniapp/verification/code").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"13800138000","type":"LOGIN_REGISTER"}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        String response = mvc.perform(post("/api/miniapp/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"13800138000","password":"Fixture-Password-123!","verificationCode":"000007"}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).path("data").path("token").asText();
        mvc.perform(get("/api/miniapp/user/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phone").value("13800138000"));
        mvc.perform(get("/api/miniapp/user/me"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    void logoutRevokesCurrentUserTokenAndRequiresAValidUserSession() throws Exception {
        String body = """
                {"phone":"13800138000","password":"Fixture-Password-123!","verificationCode":"000007"}
                """;
        String login = mvc.perform(post("/api/miniapp/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(login).path("data").path("token").asText();
        mvc.perform(post("/api/miniapp/auth/logout")).andExpect(status().isUnauthorized());
        String adminToken = PetTownToken.ADMIN.createLoginSession(100L);
        mvc.perform(post("/api/miniapp/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/miniapp/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("200"));
        mvc.perform(get("/api/miniapp/user/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/miniapp/pets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/miniapp/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        String freshLogin = mvc.perform(post("/api/miniapp/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String freshToken = json.readTree(freshLogin).path("data").path("token").asText();
        mvc.perform(get("/api/miniapp/user/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshToken))
                .andExpect(status().isOk());
    }

    @Test
    void redisOnlyVerificationEndpointIsPublicAndDoesNotClaimSmsWasSent() throws Exception {
        mvc.perform(post("/api/miniapp/verification/send").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"13800138000","type":"LOGIN_REGISTER"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("验证码已写入 Redis，未发送短信"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void rejectsMalformedPhoneAndAdminPurposeWithoutReservingQuota() throws Exception {
        mvc.perform(post("/api/miniapp/verification/send").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"bad","type":"LOGIN_REGISTER"}
                        """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/miniapp/verification/send").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"13800138000","type":"ADMIN_LOGIN"}
                        """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verificationRequestLimiter, verificationCodeService);
    }

    @Test
    void legacyAndNewPathsShareTheSameLimiter() throws Exception {
        String body = """
                {"phone":"13800138000","type":"LOGIN_REGISTER"}
                """;
        for (String path : new String[]{"/api/miniapp/verification/code", "/api/miniapp/verification/send"}) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
        verify(verificationRequestLimiter, times(2)).checkAndRecord("13800138000", "127.0.0.1");
    }

    @Test
    void returnsExplicitRateLimitCodeWithoutGeneratingCode() throws Exception {
        doThrow(new BusinessException(ErrorCodeEnum.TOO_MANY_REQUESTS, "同一手机号 60 秒内只能获取一次验证码"))
                .when(verificationRequestLimiter).checkAndRecord("13800138000", "127.0.0.1");
        mvc.perform(post("/api/miniapp/verification/send").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"13800138000","type":"LOGIN_REGISTER"}
                        """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("429"));
        verifyNoInteractions(verificationCodeService);
    }

    @Test
    void clientSuppliedForwardedHeaderCannotChooseTheIpQuotaKey() throws Exception {
        mvc.perform(post("/api/miniapp/verification/send")
                .header("Forwarded", "for=198.51.100.42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"13800138000","type":"LOGIN_REGISTER"}
                        """))
                .andExpect(status().isOk());
        verify(verificationRequestLimiter).checkAndRecord("13800138000", "127.0.0.1");
    }

    @Test
    void protectsNewEndpointsEvenOutsideTheClientPrefixAndPublicNamedPaths() throws Exception {
        for (String path : new String[]{"/api/miniapp/security-probe", "/public/security-probe", "/security-probe"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        fixtureUser("fixture-probe-user", "13800138111");
        String token = PetTownToken.USER.createLoginSession("fixture-probe-user");
        mvc.perform(get("/security-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void acceptsOnlyBearerHeadersAndRejectsAdminInvalidAndRevokedTokens() throws Exception {
        String path = "/api/miniapp/security-probe";
        fixtureUser("fixture-header-user", "13800138112");
        String token = PetTownToken.USER.createLoginSession("fixture-header-user");
        mvc.perform(get(path).cookie(new Cookie("Authorization", "Bearer " + token)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path).param("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer invalid-fixture-token"))
                .andExpect(status().isUnauthorized());
        String adminToken = PetTownToken.ADMIN.createLoginSession(100L);
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
        PetTownToken.USER.logoutByTokenValue(token);
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void completesAuthenticatedAsyncRequestsWithoutAuthenticatingAgain() throws Exception {
        fixtureUser("fixture-async-user", "13800138113");
        String token = PetTownToken.USER.createLoginSession("fixture-async-user");
        mvc.perform(get("/security-probe/async")).andExpect(status().isUnauthorized());
        var result = mvc.perform(get("/security-probe/async")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(jsonPath("$.data").value("ok"));
    }

    private void fixtureUser(String id, String phone) {
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                id, phone, "fixture-hash", "MINI_PROGRAM");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean ProbeController securityProbeController() { return new ProbeController(); }
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/api/miniapp/security-probe", "/public/security-probe", "/security-probe"})
        Result<String> protectedEndpoint() { return Result.success("ok"); }

        @GetMapping("/security-probe/async")
        Callable<Result<String>> asyncEndpoint() { return () -> Result.success("ok"); }
    }
}
