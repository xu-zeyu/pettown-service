package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.pettown.common.security.config.PetTownToken;
import com.pettown.miniapp.pet.service.MiniappPetProfileService;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class MiniappPetDeleteConcurrencyTests {
    private static final String OWNER = "pettown-delete-race-owner-00001";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MiniappPetProfileService service;
    private String token;
    private long petId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OWNER, "13800138063", "fixture-hash", "MINI_PROGRAM");
        jdbc.update("INSERT INTO pet(name,species,owner_id,created_time,updated_time) "
                + "VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", "豆豆", "CAT", OWNER);
        petId = jdbc.queryForObject("SELECT id FROM pet WHERE owner_id = ?", Long.class, OWNER);
        token = PetTownToken.USER.createLoginSession(OWNER);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM pet_health_record WHERE pet_id = ?", petId);
        jdbc.update("DELETE FROM pet WHERE id = ?", petId);
        jdbc.update("DELETE FROM user_account WHERE id = ?", OWNER);
        PetTownToken.USER.logoutByTokenValue(token);
    }

    @Test
    void concurrentCreateAndDeleteCannotLeaveAnActiveHealthRecord() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var deleting = pool.submit(() -> {
                start.await();
                return mvc.perform(delete("/api/miniapp/pets/{id}", petId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andReturn().getResponse().getStatus();
            });
            var creating = pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/miniapp/pets/{id}/health-records", petId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OTHER\",\"title\":\"并发记录\",\"recordDate\":\""
                                + LocalDate.now() + "\"}"))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(deleting.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(creating.get(10, TimeUnit.SECONDS)).isIn(201, 404);
            assertThat(jdbc.queryForObject("SELECT deleted FROM pet WHERE id = ?", Integer.class, petId))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pet_health_record WHERE pet_id = ? AND deleted = 0", Integer.class,
                    petId)).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failureAfterDeletingHealthRecordsRollsBackBothTables() {
        jdbc.update("INSERT INTO pet_health_record(pet_id,record_type,title,record_date,created_time,updated_time) "
                + "VALUES (?,'OTHER','原记录',CURRENT_DATE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", petId);
        jdbc.execute("ALTER TABLE pet ADD CONSTRAINT chk_pet_delete_rollback "
                + "CHECK (deleted = 0 OR owner_id <> '" + OWNER + "')");
        try {
            assertThatThrownBy(() -> service.delete(OWNER, petId)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT deleted FROM pet WHERE id = ?", Integer.class, petId)).isZero();
            assertThat(jdbc.queryForObject("SELECT deleted FROM pet_health_record WHERE pet_id = ?", Integer.class,
                    petId)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE pet DROP CONSTRAINT chk_pet_delete_rollback");
        }
    }
}
