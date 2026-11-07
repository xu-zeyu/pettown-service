package com.pettown.boot.miniapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pettown.common.security.config.PetTownToken;
import java.time.LocalDate;
import java.time.ZoneId;
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
class MiniappHomeSummaryApiTests {
    private static final String OWNER_A = "pettown-home-owner-00000001";
    private static final String OWNER_B = "pettown-home-owner-00000002";
    private static final String OWNER_C = "pettown-home-owner-00000003";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private String tokenA;
    private String tokenC;

    @BeforeEach
    void setUp() {
        for (String[] user : new String[][]{{OWNER_A, "13800138061"}, {OWNER_B, "13800138062"}, {OWNER_C, "13800138063"}}) {
            jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                    user[0], user[1], "fixture-hash", "MINI_PROGRAM");
        }
        tokenA = PetTownToken.USER.createLoginSession(OWNER_A);
        tokenC = PetTownToken.USER.createLoginSession(OWNER_C);
    }

    @AfterEach
    void tearDown() {
        PetTownToken.USER.logoutByTokenValue(tokenA);
        PetTownToken.USER.logoutByTokenValue(tokenC);
    }

    @Test
    void aggregatesOwnPetsCardsAndUpcomingReminders() throws Exception {
        long petOne = insertPet(OWNER_A, "豆豆", "CAT", "https://example.test/a.png");
        long petTwo = insertPet(OWNER_A, "毛毛", "DOG", null);
        long foreignPet = insertPet(OWNER_B, "别人家", "DOG", null);
        LocalDate today = LocalDate.now(ZONE);
        insertReminder(petOne, today.plusDays(2), false);
        insertReminder(petTwo, today.plusDays(6), false);
        insertReminder(petOne, today.plusDays(7), false);
        insertReminder(petOne, today.minusDays(1), false);
        insertReminder(petOne, null, false);
        insertReminder(foreignPet, today.plusDays(1), false);

        mvc.perform(get("/api/miniapp/home/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.petCount").value(2))
                .andExpect(jsonPath("$.data.petCards.length()").value(2))
                .andExpect(jsonPath("$.data.petCards[0].id").value(petOne))
                .andExpect(jsonPath("$.data.petCards[0].name").value("豆豆"))
                .andExpect(jsonPath("$.data.petCards[0].species").value("CAT"))
                .andExpect(jsonPath("$.data.petCards[0].avatarUrl").value("https://example.test/a.png"))
                .andExpect(jsonPath("$.data.petCards[1].name").value("毛毛"))
                .andExpect(jsonPath("$.data.petCards[1].avatarUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.upcomingReminderCount").value(2));
    }

    @Test
    void emptyProfileReturnsEmptyCollectionsWithoutError() throws Exception {
        mvc.perform(get("/api/miniapp/home/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.petCount").value(0))
                .andExpect(jsonPath("$.data.petCards").isEmpty())
                .andExpect(jsonPath("$.data.upcomingReminderCount").value(0));
    }

    @Test
    void excludesSoftDeletedPetsAndRecords() throws Exception {
        long livePet = insertPet(OWNER_A, "豆豆", "CAT", null);
        long deletedPet = insertPet(OWNER_A, "已删", "DOG", null);
        jdbc.update("UPDATE pet SET deleted = 1 WHERE id = ?", deletedPet);
        LocalDate today = LocalDate.now(ZONE);
        insertReminder(livePet, today.plusDays(1), false);
        insertReminder(livePet, today.plusDays(3), true);
        insertReminder(deletedPet, today.plusDays(1), false);

        mvc.perform(get("/api/miniapp/home/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.petCount").value(1))
                .andExpect(jsonPath("$.data.upcomingReminderCount").value(1));
    }

    @Test
    void rejectsAnonymousRequest() throws Exception {
        mvc.perform(get("/api/miniapp/home/summary"))
                .andExpect(status().isUnauthorized());
    }

    private long insertPet(String ownerId, String name, String species, String avatarUrl) {
        jdbc.update("INSERT INTO pet(name,species,owner_id,breed,gender,birth_date,weight_kg,sterilized,avatar_url,created_time,updated_time) "
                        + "VALUES (?,?,?,?,?,DATE '2024-05-01',4.25,TRUE,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                name, species, ownerId, "Mixed", "FEMALE", avatarUrl);
        return jdbc.queryForObject("SELECT id FROM pet WHERE owner_id = ? AND name = ?", Long.class, ownerId, name);
    }

    private void insertReminder(long petId, LocalDate nextReminderDate, boolean deleted) {
        jdbc.update("INSERT INTO pet_health_record(pet_id,record_type,title,record_date,notes,next_reminder_date,deleted,created_time,updated_time) "
                        + "VALUES (?,?,?,CURRENT_DATE,NULL,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                petId, "VACCINE", "疫苗", nextReminderDate, deleted ? 1 : 0);
    }
}
