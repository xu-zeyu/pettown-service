package com.pettown.boot.miniapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MiniappUserProfileApiTests {
    private static final String OWNER = "pettown-profile-owner-000001";
    private static final String AVATAR = "http://localhost:8082/api/miniapp/files/11111111-1111-4111-8111-111111111111.png";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private String token;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OWNER, "13800138041", "fixture-hash", "MINI_PROGRAM");
        token = PetTownToken.USER.createLoginSession(OWNER);
    }

    @AfterEach
    void tearDown() {
        PetTownToken.USER.logoutByTokenValue(token);
    }

    @Test
    void updatesNicknameAndAvatarAndMeReturnsNewValues() throws Exception {
        mvc.perform(put("/api/miniapp/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"豆豆妈\",\"avatarUrl\":\"" + AVATAR + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("豆豆妈"))
                .andExpect(jsonPath("$.data.avatarUrl").value(AVATAR));
        mvc.perform(get("/api/miniapp/user/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(OWNER))
                .andExpect(jsonPath("$.data.phone").value("13800138041"))
                .andExpect(jsonPath("$.data.nickname").value("豆豆妈"))
                .andExpect(jsonPath("$.data.avatarUrl").value(AVATAR));
    }

    @Test
    void partialUpdateKeepsTheOtherField() throws Exception {
        mvc.perform(put("/api/miniapp/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"豆豆妈\",\"avatarUrl\":\"" + AVATAR + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/miniapp/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"新昵称\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.avatarUrl").value(AVATAR));
    }

    @Test
    void rejectsBlankOversizeOrSensitiveNickname() throws Exception {
        mvc.perform(put("/api/miniapp/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/miniapp/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/miniapp/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"一二三四五六七八九十一二三四五六七八九十一\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/miniapp/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"我是傻逼\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAvatarNotFromOwnUploadEndpoint() throws Exception {
        for (String bad : new String[]{
                "https://evil.example.test/a.png",
                "http://localhost:8082/api/miniapp/files/../secret",
                "http://localhost:8082/api/miniapp/files/not-a-uuid.png",
                "http://evil.example.test/api/miniapp/files/11111111-1111-4111-8111-111111111111.png"}) {
            mvc.perform(put("/api/miniapp/user/profile")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"avatarUrl\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsEmptyUpdate() throws Exception {
        mvc.perform(put("/api/miniapp/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/miniapp/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnonymousUpdate() throws Exception {
        mvc.perform(put("/api/miniapp/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"豆豆妈\"}"))
                .andExpect(status().isUnauthorized());
    }
}
