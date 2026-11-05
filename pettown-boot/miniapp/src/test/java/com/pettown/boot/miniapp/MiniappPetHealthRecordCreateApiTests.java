package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class MiniappPetHealthRecordCreateApiTests {
    private static final String OWNER_A = "pettown-health-owner-000000001";
    private static final String OWNER_B = "pettown-health-owner-000000002";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    private String tokenA;
    private String tokenB;
    private long petId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OWNER_A, "13800138041", "fixture-hash", "MINI_PROGRAM");
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OWNER_B, "13800138042", "fixture-hash", "MINI_PROGRAM");
        jdbc.update("INSERT INTO pet(name,species,owner_id,created_time,updated_time) VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "豆豆", "CAT", OWNER_A);
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
    void createsCompleteRecordForOwnedPet() throws Exception {
        String recordDate = LocalDate.now().minusDays(1).toString();
        mvc.perform(post("/api/miniapp/pets/{id}/health-records", petId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"VACCINE\",\"title\":\"狂犬疫苗\",\"recordDate\":\"" + recordDate
                                + "\",\"notes\":\"第一针\",\"nextReminderDate\":\"2027-01-01\",\"ownerId\":\"" + OWNER_B + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.petId").value(petId))
                .andExpect(jsonPath("$.data.type").value("VACCINE"))
                .andExpect(jsonPath("$.data.title").value("狂犬疫苗"))
                .andExpect(jsonPath("$.data.recordDate").value(recordDate))
                .andExpect(jsonPath("$.data.notes").value("第一针"))
                .andExpect(jsonPath("$.data.nextReminderDate").value("2027-01-01"))
                .andExpect(jsonPath("$.data.createdTime").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedTime").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet_health_record WHERE pet_id = ?", Integer.class, petId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT record_type FROM pet_health_record WHERE pet_id = ?", String.class, petId))
                .isEqualTo("VACCINE");
        assertThat(jdbc.queryForObject("SELECT title FROM pet_health_record WHERE pet_id = ?", String.class, petId))
                .isEqualTo("狂犬疫苗");
        assertThat(jdbc.queryForObject("SELECT notes FROM pet_health_record WHERE pet_id = ?", String.class, petId))
                .isEqualTo("第一针");
    }

    @Test
    void acceptsTodayAndEverySupportedType() throws Exception {
        String today = LocalDate.now().toString();
        for (String type : new String[]{"VACCINE", "DEWORM", "WEIGHT", "OTHER"}) {
            mvc.perform(post("/api/miniapp/pets/{id}/health-records", petId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"" + type + "\",\"title\":\"今日记录\",\"recordDate\":\"" + today + "\"}"))
                    .andExpect(status().isCreated());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet_health_record WHERE pet_id = ?", Integer.class, petId))
                .isEqualTo(4);
    }

    @Test
    void foreignAndMissingPetsHaveSameErrorAndDoNotCreateRecords() throws Exception {
        JsonNode foreign = createError(tokenB, petId);
        JsonNode missing = createError(tokenB, petId + 100000);
        assertThat(foreign.path("code").asText()).isEqualTo("404");
        assertThat(foreign).isEqualTo(missing);
        assertThat(foreign.path("msg").asText()).isEqualTo("宠物不存在");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet_health_record", Integer.class)).isZero();
    }

    @Test
    void rejectsFutureDateAndInvalidPayloadWithoutWriting() throws Exception {
        String yesterday = LocalDate.now().minusDays(1).toString();
        String tomorrow = LocalDate.now().plusDays(1).toString();
        for (String body : new String[]{
                "{\"type\":\"VACCINE\",\"title\":\"未来记录\",\"recordDate\":\"" + tomorrow + "\"}",
                "{\"type\":\"VACCINE\",\"title\":\" \",\"recordDate\":\"" + yesterday + "\"}",
                "{\"type\":\"VACCINE\",\"title\":\"缺失日期\"}",
                "{\"title\":\"缺失类型\",\"recordDate\":\"" + yesterday + "\"}",
                "{\"type\":\"BIRD\",\"title\":\"非法类型\",\"recordDate\":\"" + yesterday + "\"}",
                "{\"type\":\"WEIGHT\",\"title\":\"" + "x".repeat(101) + "\",\"recordDate\":\"" + yesterday + "\"}",
                "{\"type\":\"OTHER\",\"title\":\"备注过长\",\"recordDate\":\"" + yesterday
                        + "\",\"notes\":\"" + "x".repeat(2001) + "\"}"}) {
            mvc.perform(post("/api/miniapp/pets/{id}/health-records", petId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet_health_record", Integer.class)).isZero();
    }

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mvc.perform(post("/api/miniapp/pets/{id}/health-records", petId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OTHER\",\"title\":\"记录\",\"recordDate\":\""
                                + LocalDate.now() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode createError(String token, long id) throws Exception {
        String body = mvc.perform(post("/api/miniapp/pets/{id}/health-records", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OTHER\",\"title\":\"越权\",\"recordDate\":\""
                                + LocalDate.now().minusDays(1) + "\"}"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }
}
