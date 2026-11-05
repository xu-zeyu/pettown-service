package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pettown.business.user.entity.User;
import com.pettown.business.user.mapper.UserMapper;
import com.pettown.common.commonEnum.UserSourceEnum;
import com.pettown.common.security.config.PetTownToken;
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
class MiniappPetProfileApiTests {
    private static final String OWNER = "pettown-test-pet-owner-000001";
    @Autowired private MockMvc mvc;
    @Autowired private UserMapper users;
    @Autowired private JdbcTemplate jdbc;
    private String token;

    @BeforeEach
    void setUp() {
        users.insert(new User(OWNER, "13800138001", "fixture-hash", UserSourceEnum.MINI_PROGRAM));
        token = PetTownToken.USER.createLoginSession(OWNER);
    }

    @Test
    void createsCompletePetProfileForCurrentUser() throws Exception {
        mvc.perform(post("/api/miniapp/pets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"豆豆","species":"CAT","breed":"British Shorthair",
                                 "gender":"FEMALE","birthDate":"2024-05-01","weightKg":4.25,
                                 "sterilized":true,"avatarUrl":"https://example.test/pet.png",
                                 "ownerId":"other-user"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.ownerId").value(OWNER))
                .andExpect(jsonPath("$.data.species").value("CAT"))
                .andExpect(jsonPath("$.data.breed").value("British Shorthair"))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.weightKg").value(4.25))
                .andExpect(jsonPath("$.data.birthDate").value("2024-05-01"))
                .andExpect(jsonPath("$.data.sterilized").value(true))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.test/pet.png"))
                .andExpect(jsonPath("$.data.createdTime").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet WHERE owner_id = ?", Integer.class, OWNER))
                .isEqualTo(1);
    }

    @Test
    void rejectsAnonymousAndInvalidProfilesWithoutWriting() throws Exception {
        mvc.perform(post("/api/miniapp/pets").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"豆豆","species":"CAT"}
                                """))
                .andExpect(status().isUnauthorized());
        for (String body : new String[]{
                """
                {"name":" ","species":"CAT"}
                """,
                """
                {"name":"豆豆"}
                """,
                """
                {"name":"豆豆","species":"CAT","weightKg":-0.01}
                """,
                """
                {"name":"豆豆","species":"DOG","weightKg":200.01}
                """,
                """
                {"name":"豆豆","species":"BIRD"}
                """}) {
            mvc.perform(post("/api/miniapp/pets")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet WHERE owner_id = ?", Integer.class, OWNER))
                .isZero();
        mvc.perform(post("/api/miniapp/pets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(31) + "\",\"species\":\"OTHER\"}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet WHERE owner_id = ?", Integer.class, OWNER))
                .isZero();
    }

    @Test
    void refusesTheTwentyFirstPetWithDistinctCode() throws Exception {
        for (int i = 0; i < 20; i++) {
            jdbc.update("INSERT INTO pet(name,species,owner_id,created_time,updated_time) VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    "Pet " + i, "CAT", OWNER);
        }
        mvc.perform(post("/api/miniapp/pets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"超额宠物","species":"DOG"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet WHERE owner_id = ?", Integer.class, OWNER))
                .isEqualTo(20);
    }

    @Test
    void acceptsInclusiveWeightBoundaries() throws Exception {
        for (String weight : new String[]{"0.00", "200.00"}) {
            mvc.perform(post("/api/miniapp/pets")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"边界\",\"species\":\"OTHER\",\"weightKg\":" + weight + "}"))
                    .andExpect(status().isCreated());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pet WHERE owner_id = ?", Integer.class, OWNER))
                .isEqualTo(2);
    }
}
