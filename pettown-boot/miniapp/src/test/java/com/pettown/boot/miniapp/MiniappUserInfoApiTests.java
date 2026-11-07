package com.pettown.boot.miniapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pettown.common.security.config.PetTownToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MiniappUserInfoApiTests {
    private static final String RICH_USER = "pettown-me-owner-000000001";
    private static final String BARE_USER = "pettown-me-owner-000000002";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private String richToken;
    private String bareToken;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source,nickname,avatar) VALUES (?,?,?,?,?,?)",
                RICH_USER, "13800138051", "fixture-hash", "MINI_PROGRAM", "豆豆妈",
                "http://localhost:8082/api/miniapp/files/11111111-1111-4111-8111-111111111111.png");
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                BARE_USER, "13800138052", "fixture-hash", "UNKNOWN");
        richToken = PetTownToken.USER.createLoginSession(RICH_USER);
        bareToken = PetTownToken.USER.createLoginSession(BARE_USER);
    }

    @AfterEach
    void tearDown() {
        PetTownToken.USER.logoutByTokenValue(richToken);
        PetTownToken.USER.logoutByTokenValue(bareToken);
    }

    @Test
    void returnsFullProfileWithSourceAndRegisteredTime() throws Exception {
        mvc.perform(get("/api/miniapp/user/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + richToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.userId").value(RICH_USER))
                .andExpect(jsonPath("$.data.phone").value("13800138051"))
                .andExpect(jsonPath("$.data.nickname").value("豆豆妈"))
                .andExpect(jsonPath("$.data.avatarUrl")
                        .value("http://localhost:8082/api/miniapp/files/11111111-1111-4111-8111-111111111111.png"))
                .andExpect(jsonPath("$.data.source").value("MINI_PROGRAM"))
                .andExpect(jsonPath("$.data.registeredTime").isNotEmpty());
    }

    @Test
    void bareProfileStillReportsSourceAndRegisteredTime() throws Exception {
        mvc.perform(get("/api/miniapp/user/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + bareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(BARE_USER))
                .andExpect(jsonPath("$.data.nickname").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.avatarUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.source").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.registeredTime").isNotEmpty());
    }

    @Test
    void rejectsAnonymousRequest() throws Exception {
        mvc.perform(get("/api/miniapp/user/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    void rejectsForgedAndRevokedTokens() throws Exception {
        mvc.perform(get("/api/miniapp/user/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer forged-token-value"))
                .andExpect(status().isUnauthorized());
        PetTownToken.USER.logoutByTokenValue(richToken);
        mvc.perform(get("/api/miniapp/user/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + richToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"));
    }
}
