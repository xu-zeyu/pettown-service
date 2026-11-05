package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettown.business.user.mapper.UserMapper;
import com.pettown.business.user.service.LoginFailureGuard;
import com.pettown.business.verification.service.VerificationCodeService;
import com.pettown.common.commonEnum.UserSourceEnum;
import com.pettown.common.commonEnum.VerificationCodeTypeEnum;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.security.config.PetTownToken;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationSourceApiTests {
    private static final String PHONE = "13800138000";
    private static final String LOGIN_BODY = """
            {"phone":"13800138000","password":"strongPass123","verificationCode":"000007"}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserMapper userMapper;
    @MockitoBean private VerificationCodeService verificationCodeService;
    @MockitoBean private LoginFailureGuard loginFailureGuard;

    @Test
    void registersFromRequestUserAgentWithoutSourceInBody() throws Exception {
        login("Mozilla/5.0 (Android 14) MicroMessenger/8.0 miniProgram");

        assertThat(userMapper.findByPhone(PHONE).orElseThrow().getSource())
                .isEqualTo(UserSourceEnum.MINI_PROGRAM);
    }

    @Test
    void keepsOriginalRegistrationSourceOnLaterLogins() throws Exception {
        login("Mozilla/5.0 (iPhone)");
        login("Mozilla/5.0 (Android 14)");

        assertThat(userMapper.findByPhone(PHONE).orElseThrow().getSource()).isEqualTo(UserSourceEnum.IOS);
        assertThat(userMapper.selectCount()).isEqualTo(1);
    }

    @Test
    void locksExistingAccountAfterFiveWrongPasswordsWithoutRegisteringAgain() throws Exception {
        login("Mozilla/5.0 (iPhone)");
        AtomicInteger failures = new AtomicInteger();
        doAnswer(call -> {
            if (failures.incrementAndGet() >= 5) {
                throw new BusinessException(ErrorCodeEnum.LOGIN_LOCKED, "密码连续错误 5 次，账号暂锁 15 分钟");
            }
            return null;
        }).when(loginFailureGuard).recordPasswordFailure(PHONE);
        doAnswer(call -> {
            if (failures.get() >= 5) {
                throw new BusinessException(ErrorCodeEnum.LOGIN_LOCKED, "密码连续错误 5 次，账号暂锁 15 分钟");
            }
            return null;
        }).when(loginFailureGuard).assertNotLocked(PHONE);
        String wrong = """
                {"phone":"13800138000","password":"wrongPass123","verificationCode":"000007"}
                """;
        for (int attempt = 1; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/miniapp/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/miniapp/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("423"));
        mockMvc.perform(post("/api/miniapp/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isLocked());
        assertThat(userMapper.selectCount()).isEqualTo(1);
    }

    @Test
    void distinguishesValidationCodeAndPasswordFailures() throws Exception {
        mockMvc.perform(post("/api/miniapp/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"13800138000","verificationCode":"000007"}
                        """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("4001"));

        doThrow(new BusinessException(ErrorCodeEnum.FAIL, "验证码错误或已过期"))
                .when(verificationCodeService).verifyAndConsume(PHONE, VerificationCodeTypeEnum.LOGIN_REGISTER, "111111");
        mockMvc.perform(post("/api/miniapp/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"13800138000","password":"strongPass123","verificationCode":"111111"}
                        """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("4002"));
        verify(loginFailureGuard, never()).recordPasswordFailure(PHONE);

        login("Mozilla/5.0 (iPhone)");
        mockMvc.perform(post("/api/miniapp/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"13800138000","password":"wrongPass123","verificationCode":"000007"}
                        """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("4003"));
        verify(loginFailureGuard).recordPasswordFailure(PHONE);
        assertThat(userMapper.selectCount()).isEqualTo(1);
    }

    @Test
    void issuesSevenDayUserToken() throws Exception {
        String response = mockMvc.perform(post("/api/miniapp/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).path("data").path("token").asText();
        assertThat(PetTownToken.USER.getTokenTimeout(token)).isBetween(604790L, 604800L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "curl/8.0")
    void registersWithUnknownSourceWhenUserAgentCannotBeIdentified(String userAgent) throws Exception {
        login(userAgent);

        assertThat(userMapper.findByPhone(PHONE).orElseThrow().getSource()).isEqualTo(UserSourceEnum.UNKNOWN);
    }

    private void login(String userAgent) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/miniapp/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY);
        if (userAgent != null) {
            request.header(HttpHeaders.USER_AGENT, userAgent);
        }
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }
}
