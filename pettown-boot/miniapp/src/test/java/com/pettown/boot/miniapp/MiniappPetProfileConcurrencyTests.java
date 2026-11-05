package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.pettown.common.security.config.PetTownToken;
import java.util.ArrayList;
import java.util.List;
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
class MiniappPetProfileConcurrencyTests {
    private static final String OWNER = "pettown-concurrent-owner-0001";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private String token;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO user_account(id,phone,password_hash,source) VALUES (?,?,?,?)",
                OWNER, "13800138002", "fixture-hash", "MINI_PROGRAM");
        for (int i = 0; i < 19; i++) {
            jdbc.update("INSERT INTO pet(name,species,owner_id,created_time,updated_time) VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    "Pet " + i, "CAT", OWNER);
        }
        token = PetTownToken.USER.createLoginSession(OWNER);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM pet WHERE owner_id = ?", OWNER);
        jdbc.update("DELETE FROM user_account WHERE id = ?", OWNER);
        PetTownToken.USER.logoutByTokenValue(token);
    }

    @Test
    void concurrentCreatesDoNotExceedTwentyPets() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var tasks = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 2; i++) {
                tasks.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(post("/api/miniapp/pets")
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"name":"并发宠物","species":"CAT"}
                                            """))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = List.of(tasks.get(0).get(10, TimeUnit.SECONDS),
                    tasks.get(1).get(10, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet WHERE owner_id = ?", Integer.class, OWNER))
                    .isEqualTo(20);
        } finally {
            pool.shutdownNow();
        }
    }
}
