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
class MiniappPetHealthRecordListApiTests {
    private static final String OWNER_A = "pettown-health-list-owner-00001";
    private static final String OWNER_B = "pettown-health-list-owner-00002";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    private String tokenA;
    private String tokenB;
    private long petA;
    private long petB;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OWNER_A, "13800138051", "fixture-hash", "MINI_PROGRAM");
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OWNER_B, "13800138052", "fixture-hash", "MINI_PROGRAM");
        jdbc.update("INSERT INTO pet(name,species,owner_id,created_time,updated_time) VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "豆豆", "CAT", OWNER_A);
        jdbc.update("INSERT INTO pet(name,species,owner_id,created_time,updated_time) VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "球球", "DOG", OWNER_B);
        petA = jdbc.queryForObject("SELECT id FROM pet WHERE owner_id = ?", Long.class, OWNER_A);
        petB = jdbc.queryForObject("SELECT id FROM pet WHERE owner_id = ?", Long.class, OWNER_B);
        tokenA = PetTownToken.USER.createLoginSession(OWNER_A);
        tokenB = PetTownToken.USER.createLoginSession(OWNER_B);
    }

    @AfterEach
    void tearDown() {
        PetTownToken.USER.logoutByTokenValue(tokenA);
        PetTownToken.USER.logoutByTokenValue(tokenB);
    }

    @Test
    void returnsOnlyOwnedPetsRecordsInStableDateDescendingPages() throws Exception {
        insertRecord(petA, "VACCINE", "较早", "2024-01-01");
        insertRecord(petA, "DEWORM", "较新一", "2024-02-01");
        insertRecord(petA, "WEIGHT", "较新二", "2024-02-01");
        insertRecord(petB, "OTHER", "他人的记录", "2024-03-01");

        mvc.perform(get("/api/miniapp/pets/{id}/health-records?page=1&size=2", petA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.list[0].title").value("较新二"))
                .andExpect(jsonPath("$.data.list[1].title").value("较新一"))
                .andExpect(jsonPath("$.data.list[0].petId").value(petA));
        mvc.perform(get("/api/miniapp/pets/{id}/health-records?page=2&size=2", petA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.list[0].title").value("较早"));
        mvc.perform(get("/api/miniapp/pets/{id}/health-records", petB)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].title").value("他人的记录"));
    }

    @Test
    void filtersByTypeAndReturnsEmptyPageWhenNoMatch() throws Exception {
        insertRecord(petA, "VACCINE", "疫苗一", "2024-01-01");
        insertRecord(petA, "VACCINE", "疫苗二", "2024-02-01");
        insertRecord(petA, "DEWORM", "驱虫", "2024-03-01");
        mvc.perform(get("/api/miniapp/pets/{id}/health-records?type=VACCINE&page=1&size=1", petA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.list[0].title").value("疫苗二"));
        mvc.perform(get("/api/miniapp/pets/{id}/health-records?type=OTHER", petA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.list.length()").value(0));
    }

    @Test
    void foreignAndMissingPetsReturnIdenticalNotFound() throws Exception {
        insertRecord(petA, "VACCINE", "隐私记录", "2024-01-01");
        JsonNode foreign = getError(tokenB, petA);
        JsonNode missing = getError(tokenB, petA + 100000);
        assertThat(foreign.path("code").asText()).isEqualTo("404");
        assertThat(foreign.path("msg").asText()).isEqualTo("宠物不存在");
        assertThat(foreign).isEqualTo(missing);
        mvc.perform(get("/api/miniapp/pets/{id}/health-records?type=BIRD", petA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404"));
    }

    @Test
    void invalidTypeIsBadRequestAndInvalidPaginationFallsBackToDefaults() throws Exception {
        for (int i = 0; i < 11; i++) {
            insertRecord(petA, "OTHER", "记录" + i, "2024-01-01");
        }
        mvc.perform(get("/api/miniapp/pets/{id}/health-records?type=BIRD", petA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
        for (String query : new String[]{"", "?page=0&size=0", "?page=abc&size=xyz",
                "?page=2147483648&size=2147483648", "?page=-1&size=-1", "?page=1&size=1001"}) {
            mvc.perform(get("/api/miniapp/pets/{id}/health-records" + query, petA)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(11))
                    .andExpect(jsonPath("$.data.list.length()").value(10));
        }
        mvc.perform(get("/api/miniapp/pets/{id}/health-records?page=2&size=bad", petA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(1));
        mvc.perform(get("/api/miniapp/pets/{id}/health-records?page=bad&size=2", petA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(2));
    }

    @Test
    void rejectsAnonymousAccess() throws Exception {
        mvc.perform(get("/api/miniapp/pets/{id}/health-records", petA))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode getError(String token, long id) throws Exception {
        String body = mvc.perform(get("/api/miniapp/pets/{id}/health-records?ownerId=" + OWNER_A, id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private void insertRecord(long petId, String type, String title, String date) {
        jdbc.update("INSERT INTO pet_health_record(pet_id,record_type,title,record_date,created_time,updated_time) "
                        + "VALUES (?,?,?,CAST(? AS DATE),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                petId, type, title, date);
    }
}
