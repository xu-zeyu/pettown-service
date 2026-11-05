package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MiniappPetEditApiTests {
    private static final String OWNER_A = "pettown-edit-owner-0000000001";
    private static final String OWNER_B = "pettown-edit-owner-0000000002";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    private String tokenA;
    private String tokenB;
    private long petId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OWNER_A, "13800138031", "fixture-hash", "MINI_PROGRAM");
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OWNER_B, "13800138032", "fixture-hash", "MINI_PROGRAM");
        jdbc.update("INSERT INTO pet(name,species,owner_id,breed,gender,birth_date,weight_kg,sterilized,avatar_url,created_time,updated_time) "
                        + "VALUES (?,?,?,?,?,DATE '2024-05-01',4.25,TRUE,?,TIMESTAMP '2024-01-01 10:00:00',TIMESTAMP '2024-01-01 10:00:00')",
                "豆豆", "CAT", OWNER_A, "British Shorthair", "FEMALE", "https://example.test/old.png");
        petId = jdbc.queryForObject("SELECT id FROM pet WHERE owner_id = ?", Long.class, OWNER_A);
        tokenA = PetTownToken.USER.createLoginSession(OWNER_A);
        tokenB = PetTownToken.USER.createLoginSession(OWNER_B);
    }

    @AfterEach
    void tearDown() {
        PetTownToken.USER.logoutByTokenValue(tokenA);
        PetTownToken.USER.logoutByTokenValue(tokenB);
    }

    @Test
    void ownerCanReplaceCompleteProfileWithoutChangingOwnerOrCreationTime() throws Exception {
        mvc.perform(put("/api/miniapp/pets/{id}", petId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"球球","species":"DOG","breed":"Corgi","gender":"MALE",
                                 "birthDate":"2023-02-03","weightKg":8.50,"sterilized":false,
                                 "avatarUrl":"https://example.test/new.png","ownerId":"other-user"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(petId))
                .andExpect(jsonPath("$.data.ownerId").value(OWNER_A))
                .andExpect(jsonPath("$.data.name").value("球球"))
                .andExpect(jsonPath("$.data.species").value("DOG"))
                .andExpect(jsonPath("$.data.breed").value("Corgi"))
                .andExpect(jsonPath("$.data.gender").value("MALE"))
                .andExpect(jsonPath("$.data.birthDate").value("2023-02-03"))
                .andExpect(jsonPath("$.data.weightKg").value(8.50))
                .andExpect(jsonPath("$.data.sterilized").value(false))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.test/new.png"))
                .andExpect(jsonPath("$.data.createdTime").value("2024-01-01T10:00:00"));
        assertThat(jdbc.queryForObject("SELECT owner_id FROM pet WHERE id = ?", String.class, petId))
                .isEqualTo(OWNER_A);
        assertThat(jdbc.queryForObject("SELECT name FROM pet WHERE id = ?", String.class, petId))
                .isEqualTo("球球");
        assertThat(jdbc.queryForObject("SELECT updated_time > created_time FROM pet WHERE id = ?", Boolean.class, petId))
                .isTrue();
    }

    @Test
    void omittedOptionalFieldsClearPreviousValues() throws Exception {
        mvc.perform(put("/api/miniapp/pets/{id}", petId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"豆豆","species":"OTHER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.breed").doesNotExist())
                .andExpect(jsonPath("$.data.weightKg").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT breed FROM pet WHERE id = ?", String.class, petId)).isNull();
        assertThat(jdbc.queryForObject("SELECT weight_kg FROM pet WHERE id = ?", Object.class, petId)).isNull();
        assertThat(jdbc.queryForObject("SELECT sterilized FROM pet WHERE id = ?", Object.class, petId)).isNull();
        assertThat(jdbc.queryForObject("SELECT species FROM pet WHERE id = ?", String.class, petId))
                .isEqualTo("OTHER");
    }

    @Test
    void foreignAndMissingPetsHaveIdenticalErrorsAndNeverChangeData() throws Exception {
        JsonNode foreign = editError(tokenB, petId);
        JsonNode missing = editError(tokenB, petId + 100000);
        assertThat(foreign.path("code").asText()).isEqualTo("404");
        assertThat(foreign).isEqualTo(missing);
        assertThat(foreign.path("msg").asText()).isEqualTo("宠物不存在");
        assertThat(jdbc.queryForObject("SELECT name FROM pet WHERE id = ?", String.class, petId))
                .isEqualTo("豆豆");
        assertThat(jdbc.queryForObject("SELECT owner_id FROM pet WHERE id = ?", String.class, petId))
                .isEqualTo(OWNER_A);
    }

    @Test
    void rejectsAnonymousAndInvalidPayloadWithoutChangingData() throws Exception {
        mvc.perform(put("/api/miniapp/pets/{id}", petId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"球球","species":"DOG"}
                                """))
                .andExpect(status().isUnauthorized());
        for (String body : new String[]{
                "{\"name\":\" \",\"species\":\"CAT\"}",
                "{\"name\":\"球球\"}",
                "{\"name\":\"球球\",\"species\":\"BIRD\"}",
                "{\"name\":\"球球\",\"species\":\"DOG\",\"weightKg\":200.01}"}) {
            mvc.perform(put("/api/miniapp/pets/{id}", petId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT name FROM pet WHERE id = ?", String.class, petId))
                .isEqualTo("豆豆");
    }

    private JsonNode editError(String token, long id) throws Exception {
        String body = mvc.perform(put("/api/miniapp/pets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"恶意修改","species":"DOG"}
                                """))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }
}
