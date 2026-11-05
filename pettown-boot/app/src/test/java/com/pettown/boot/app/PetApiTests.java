package com.pettown.boot.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.pettown.common.security.config.PetTownToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PetApiTests {
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createAndReadPetThroughHttp() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(PetTownToken.USER.createLoginSession("fixture-pet-owner"));
        ResponseEntity<String> created = restTemplate.postForEntity("/api/pets",
                new HttpEntity<>("{\"name\":\"豆豆\",\"species\":\"猫\"}", headers), String.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody()).contains("\"code\":\"200\"", "\"name\":\"豆豆\"");
        assertThat(created.getHeaders().getLocation()).isNotNull();
        assertThat(created.getHeaders().getFirst("X-Trace-Id")).isNotBlank();

        ResponseEntity<String> found = restTemplate.exchange(created.getHeaders().getLocation().toString(),
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(found.getStatusCode().value()).isEqualTo(200);
        assertThat(found.getBody()).contains("\"species\":\"猫\"");

        ResponseEntity<String> listed = restTemplate.exchange("/api/pets", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(listed.getStatusCode().value()).isEqualTo(200);
        assertThat(listed.getBody()).contains("\"name\":\"豆豆\"", "\"species\":\"猫\"");

        ResponseEntity<String> missing = restTemplate.exchange("/api/pets/999999", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(missing.getBody()).contains("\"code\":\"404\"");

        ResponseEntity<String> invalid = restTemplate.exchange("/api/pets", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"\",\"species\":\"猫\"}", headers), String.class);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(invalid.getBody()).contains("宠物名称不能为空");
    }

    @Test
    void isolatesPetsBetweenUsersAndHidesExistenceOnCrossUserAccess() {
        HttpHeaders ownerHeaders = new HttpHeaders();
        ownerHeaders.setContentType(MediaType.APPLICATION_JSON);
        ownerHeaders.setBearerAuth(PetTownToken.USER.createLoginSession("fixture-pet-owner-a"));
        restTemplate.postForEntity("/api/pets", new HttpEntity<>("{\"name\":\"豆豆\",\"species\":\"猫\"}", ownerHeaders), String.class);
        ResponseEntity<String> created = restTemplate.postForEntity("/api/pets",
                new HttpEntity<>("{\"name\":\"球球\",\"species\":\"狗\"}", ownerHeaders), String.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        String ownerPetLocation = created.getHeaders().getLocation().toString();

        HttpHeaders otherHeaders = new HttpHeaders();
        otherHeaders.setBearerAuth(PetTownToken.USER.createLoginSession("fixture-pet-owner-b"));
        // 列表隔离：他人宠物不可见
        ResponseEntity<String> otherList = restTemplate.exchange("/api/pets", HttpMethod.GET,
                new HttpEntity<>(otherHeaders), String.class);
        assertThat(otherList.getStatusCode().value()).isEqualTo(200);
        assertThat(otherList.getBody()).doesNotContain("豆豆", "球球");
        // 越权访问与不存在返回同一 404，不泄露存在性
        ResponseEntity<String> crossUser = restTemplate.exchange(ownerPetLocation, HttpMethod.GET,
                new HttpEntity<>(otherHeaders), String.class);
        assertThat(crossUser.getStatusCode().value()).isEqualTo(404);
        assertThat(crossUser.getBody()).contains("\"code\":\"404\"", "宠物不存在");
        ResponseEntity<String> missing = restTemplate.exchange("/api/pets/999999", HttpMethod.GET,
                new HttpEntity<>(otherHeaders), String.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(missing.getBody()).contains("\"code\":\"404\"", "宠物不存在");
        assertThat(missing.getBody().replaceAll("\"data\":[^}]*}", ""))
                .isEqualTo(crossUser.getBody().replaceAll("\"data\":[^}]*}", ""));
        // 归属用户本人仍可访问
        ResponseEntity<String> ownDetail = restTemplate.exchange(ownerPetLocation, HttpMethod.GET,
                new HttpEntity<>(ownerHeaders), String.class);
        assertThat(ownDetail.getStatusCode().value()).isEqualTo(200);
        assertThat(ownDetail.getBody()).contains("\"name\":\"球球\"");
    }

    @Test
    void keepsOwnershipIsolationUnderConcurrentCreates() throws Exception {
        int users = 4;
        int petsPerUser = 5;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(users);
        try {
            var barrier = new java.util.concurrent.CyclicBarrier(users);
            var futures = new java.util.ArrayList<java.util.concurrent.Callable<Void>>();
            for (int u = 0; u < users; u++) {
                String userId = "fixture-concurrent-owner-" + u;
                futures.add(() -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setBearerAuth(PetTownToken.USER.createLoginSession(userId));
                    barrier.await();
                    for (int p = 0; p < petsPerUser; p++) {
                        ResponseEntity<String> response = restTemplate.postForEntity("/api/pets",
                                new HttpEntity<>("{\"name\":\"" + userId + "-宠物" + p + "\",\"species\":\"猫\"}", headers),
                                String.class);
                        assertThat(response.getStatusCode().value()).isEqualTo(201);
                    }
                    return null;
                });
            }
            for (var future : pool.invokeAll(futures)) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
        for (int u = 0; u < users; u++) {
            String userId = "fixture-concurrent-owner-" + u;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(PetTownToken.USER.createLoginSession(userId));
            ResponseEntity<String> list = restTemplate.exchange("/api/pets", HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            assertThat(list.getStatusCode().value()).isEqualTo(200);
            // 每个用户恰好看到自己的 5 只宠物，看不到任何他人的宠物
            for (int p = 0; p < petsPerUser; p++) {
                assertThat(list.getBody()).containsOnlyOnce(userId + "-宠物" + p);
            }
            for (int other = 0; other < users; other++) {
                if (other != u) {
                    assertThat(list.getBody()).doesNotContain("fixture-concurrent-owner-" + other + "-");
                }
            }
        }
    }

    @Test
    void rejectsAnonymousAndAdminTokensForAllPetOperations() {
        ResponseEntity<String> anonymous = restTemplate.getForEntity("/api/pets", String.class);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);
        assertThat(anonymous.getBody()).contains("\"code\":\"401\"");
        assertThat(restTemplate.getForEntity("/api/pets/1", String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.postForEntity("/api/pets", "{}", String.class).getStatusCode().value()).isEqualTo(401);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(PetTownToken.ADMIN.createLoginSession(1L));
        assertThat(restTemplate.exchange("/api/pets", HttpMethod.GET, new HttpEntity<>(headers), String.class)
                .getStatusCode().value()).isEqualTo(401);
    }
}
