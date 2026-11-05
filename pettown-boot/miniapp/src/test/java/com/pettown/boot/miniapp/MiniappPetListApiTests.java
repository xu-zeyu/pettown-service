package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
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
class MiniappPetListApiTests {
    private static final String OWNER_A = "pettown-list-owner-0000000001";
    private static final String OWNER_B = "pettown-list-owner-0000000002";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OWNER_A, "13800138011", "fixture-hash", "MINI_PROGRAM");
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OWNER_B, "13800138012", "fixture-hash", "MINI_PROGRAM");
        tokenA = PetTownToken.USER.createLoginSession(OWNER_A);
        tokenB = PetTownToken.USER.createLoginSession(OWNER_B);
    }

    @AfterEach
    void tearDown() {
        PetTownToken.USER.logoutByTokenValue(tokenA);
        PetTownToken.USER.logoutByTokenValue(tokenB);
    }

    @Test
    void returnsOnlyCurrentUsersPetsInStableNewestFirstPages() throws Exception {
        insertPet(OWNER_A, "较早", "2024-01-01 10:00:00");
        insertPet(OWNER_A, "较新一", "2024-01-02 10:00:00");
        insertPet(OWNER_A, "较新二", "2024-01-02 10:00:00");
        insertPet(OWNER_B, "他人的宠物", "2024-01-03 10:00:00");

        mvc.perform(get("/api/miniapp/pets?page=1&size=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.list[0].name").value("较新二"))
                .andExpect(jsonPath("$.data.list[1].name").value("较新一"))
                .andExpect(jsonPath("$.data.list[0].ownerId").value(OWNER_A));
        mvc.perform(get("/api/miniapp/pets?page=2&size=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.list[0].name").value("较早"));
        mvc.perform(get("/api/miniapp/pets?page=1&size=10&ownerId=" + OWNER_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].name").value("他人的宠物"))
                .andExpect(jsonPath("$.data.list[0].ownerId").value(OWNER_B));
    }

    @Test
    void fallsBackToDefaultsForInvalidPageAndSizeIndependently() throws Exception {
        for (int i = 0; i < 11; i++) {
            insertPet(OWNER_A, "宠物" + i, "2024-01-01 10:00:00");
        }
        for (String query : new String[]{"", "?page=0&size=0", "?page=abc&size=xyz",
                "?page=2147483648&size=2147483648", "?page=-1&size=-1", "?page=1&size=1001"}) {
            mvc.perform(get("/api/miniapp/pets" + query)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(11))
                    .andExpect(jsonPath("$.data.list.length()").value(10));
        }
        mvc.perform(get("/api/miniapp/pets?page=2&size=bad")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.list.length()").value(1));
        mvc.perform(get("/api/miniapp/pets?page=bad&size=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.list.length()").value(2));
    }

    @Test
    void returnsEmptyPageAndRejectsAnonymousAccess() throws Exception {
        mvc.perform(get("/api/miniapp/pets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.list.length()").value(0));
        mvc.perform(get("/api/miniapp/pets"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet", Integer.class)).isZero();
    }

    private void insertPet(String ownerId, String name, String createdTime) {
        jdbc.update("INSERT INTO pet(name,species,owner_id,created_time,updated_time) "
                        + "VALUES (?,?,?,CAST(? AS TIMESTAMP),CAST(? AS TIMESTAMP))",
                name, "CAT", ownerId, createdTime, createdTime);
    }
}
