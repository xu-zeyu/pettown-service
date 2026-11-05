package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class MiniappPetDetailApiTests {
    private static final String OWNER_A = "pettown-detail-owner-000000001";
    private static final String OWNER_B = "pettown-detail-owner-000000002";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OWNER_A, "13800138021", "fixture-hash", "MINI_PROGRAM");
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OWNER_B, "13800138022", "fixture-hash", "MINI_PROGRAM");
        tokenA = PetTownToken.USER.createLoginSession(OWNER_A);
        tokenB = PetTownToken.USER.createLoginSession(OWNER_B);
    }

    @AfterEach
    void tearDown() {
        PetTownToken.USER.logoutByTokenValue(tokenA);
        PetTownToken.USER.logoutByTokenValue(tokenB);
    }

    @Test
    void returnsCompleteOwnProfile() throws Exception {
        long id = insertProfile(OWNER_A);
        mvc.perform(get("/api/miniapp/pets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.ownerId").value(OWNER_A))
                .andExpect(jsonPath("$.data.name").value("豆豆"))
                .andExpect(jsonPath("$.data.species").value("CAT"))
                .andExpect(jsonPath("$.data.breed").value("British Shorthair"))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.birthDate").value("2024-05-01"))
                .andExpect(jsonPath("$.data.weightKg").value(4.25))
                .andExpect(jsonPath("$.data.sterilized").value(true))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.test/pet.png"))
                .andExpect(jsonPath("$.data.createdTime").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedTime").isNotEmpty());
    }

    @Test
    void hidesForeignPetWithSameErrorAsMissingPet() throws Exception {
        long id = insertProfile(OWNER_A);
        JsonNode foreign = responseFor(tokenB, id);
        JsonNode missing = responseFor(tokenB, id + 100000);
        assertThat(foreign.path("code").asText()).isEqualTo("404");
        assertThat(foreign).isEqualTo(missing);
        assertThat(foreign.path("msg").asText()).isEqualTo("宠物不存在");
        assertThat(foreign.path("data").isNull()).isTrue();
    }

    @Test
    void rejectsAnonymousDetailAccess() throws Exception {
        long id = insertProfile(OWNER_A);
        mvc.perform(get("/api/miniapp/pets/{id}", id))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode responseFor(String token, long id) throws Exception {
        String body = mvc.perform(get("/api/miniapp/pets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private long insertProfile(String ownerId) {
        jdbc.update("INSERT INTO pet(name,species,owner_id,breed,gender,birth_date,weight_kg,sterilized,avatar_url,created_time,updated_time) "
                        + "VALUES (?,?,?,?,?,DATE '2024-05-01',4.25,TRUE,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "豆豆", "CAT", ownerId, "British Shorthair", "FEMALE", "https://example.test/pet.png");
        return jdbc.queryForObject("SELECT id FROM pet WHERE owner_id = ?", Long.class, ownerId);
    }
}
