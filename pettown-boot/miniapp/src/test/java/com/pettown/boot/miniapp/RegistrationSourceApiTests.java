package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pettown.business.user.mapper.UserMapper;
import com.pettown.business.verification.service.VerificationCodeService;
import com.pettown.common.commonEnum.UserSourceEnum;
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
    @Autowired private UserMapper userMapper;
    @MockitoBean private VerificationCodeService verificationCodeService;

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
