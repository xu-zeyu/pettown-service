package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettown.common.security.config.PetTownToken;
import java.time.LocalDate;
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
class MiniappPetDeleteApiTests {
    private static final String OWNER = "pettown-delete-owner-000000001";
    private static final String OTHER = "pettown-delete-owner-000000002";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    private String token;
    private String otherToken;
    private String adminToken;
    private long petId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OWNER, "13800138061", "fixture-hash", "MINI_PROGRAM");
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OTHER, "13800138062", "fixture-hash", "MINI_PROGRAM");
        jdbc.update("INSERT INTO pet(name,species,owner_id,created_time,updated_time) "
                + "VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", "豆豆", "CAT", OWNER);
        petId = jdbc.queryForObject("SELECT id FROM pet WHERE owner_id = ?", Long.class, OWNER);
        token = PetTownToken.USER.createLoginSession(OWNER);
        otherToken = PetTownToken.USER.createLoginSession(OTHER);
    }

    @AfterEach
    void tearDown() {
        PetTownToken.USER.logoutByTokenValue(token);
        PetTownToken.USER.logoutByTokenValue(otherToken);
        if (adminToken != null) {
            PetTownToken.ADMIN.logoutByTokenValue(adminToken);
        }
    }

    @Test
    void ownerCanDeletePet() throws Exception {
        jdbc.update("INSERT INTO pet_health_record(pet_id,record_type,title,record_date,created_time,updated_time) "
                + "VALUES (?,'VACCINE','疫苗',CURRENT_DATE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", petId);
        jdbc.update("INSERT INTO pet_health_record(pet_id,record_type,title,record_date,created_time,updated_time) "
                + "VALUES (?,'WEIGHT','体重',CURRENT_DATE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", petId);
        mvc.perform(delete("/api/miniapp/pets/{id}", petId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));

        assertThat(jdbc.queryForObject("SELECT deleted FROM pet WHERE id = ?", Integer.class, petId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet_health_record WHERE pet_id = ? AND deleted = 1",
                Integer.class, petId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet_health_record WHERE pet_id = ?", Integer.class,
                petId)).isEqualTo(2);
        mvc.perform(get("/api/miniapp/pets/{id}", petId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/miniapp/pets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/miniapp/pets/{id}/health-records", petId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/miniapp/pets/{id}/health-records", petId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OTHER\",\"title\":\"已删宠物\",\"recordDate\":\""
                                + LocalDate.now() + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/miniapp/pets/{id}", petId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名字\",\"species\":\"CAT\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void foreignMissingAndRepeatedDeletesHaveSameError() throws Exception {
        JsonNode foreign = deleteError(otherToken, petId);
        JsonNode missing = deleteError(otherToken, petId + 100000);
        assertThat(foreign).isEqualTo(missing);
        assertThat(foreign.path("code").asText()).isEqualTo("404");
        assertThat(jdbc.queryForObject("SELECT deleted FROM pet WHERE id = ?", Integer.class, petId)).isZero();

        mvc.perform(delete("/api/miniapp/pets/{id}", petId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(deleteError(token, petId)).isEqualTo(missing);
    }

    @Test
    void deletedPetFreesOneCreationSlot() throws Exception {
        for (int i = 0; i < 19; i++) {
            jdbc.update("INSERT INTO pet(name,species,owner_id,created_time,updated_time) "
                    + "VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", "宠物" + i, "CAT", OWNER);
        }
        mvc.perform(post("/api/miniapp/pets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"额外宠物\",\"species\":\"DOG\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/miniapp/pets/{id}", petId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/miniapp/pets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"额外宠物\",\"species\":\"DOG\"}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet WHERE owner_id = ? AND deleted = 0",
                Integer.class, OWNER)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet WHERE owner_id = ?", Integer.class, OWNER))
                .isEqualTo(21);
    }

    @Test
    void rejectsAnonymousAndAdminTokens() throws Exception {
        mvc.perform(delete("/api/miniapp/pets/{id}", petId))
                .andExpect(status().isUnauthorized());
        adminToken = PetTownToken.ADMIN.createLoginSession(100L);
        mvc.perform(delete("/api/miniapp/pets/{id}", petId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT deleted FROM pet WHERE id = ?", Integer.class, petId)).isZero();
    }

    private JsonNode deleteError(String bearer, long id) throws Exception {
        String body = mvc.perform(delete("/api/miniapp/pets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }
}
