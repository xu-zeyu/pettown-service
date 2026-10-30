package com.pettown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PetApiTests {
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createAndReadPetThroughHttp() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> created = restTemplate.postForEntity("/api/pets",
                new HttpEntity<>("{\"name\":\"豆豆\",\"species\":\"猫\"}", headers), String.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody()).contains("\"code\":\"200\"", "\"name\":\"豆豆\"");
        assertThat(created.getHeaders().getLocation()).isNotNull();
        assertThat(created.getHeaders().getFirst("X-Trace-Id")).isNotBlank();

        ResponseEntity<String> found = restTemplate.getForEntity(created.getHeaders().getLocation().toString(), String.class);
        assertThat(found.getStatusCode().value()).isEqualTo(200);
        assertThat(found.getBody()).contains("\"species\":\"猫\"");

        ResponseEntity<String> listed = restTemplate.getForEntity("/api/pets", String.class);
        assertThat(listed.getStatusCode().value()).isEqualTo(200);
        assertThat(listed.getBody()).contains("\"name\":\"豆豆\"", "\"species\":\"猫\"");

        ResponseEntity<String> missing = restTemplate.getForEntity("/api/pets/999999", String.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(missing.getBody()).contains("\"code\":\"404\"");

        ResponseEntity<String> invalid = restTemplate.exchange("/api/pets", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"\",\"species\":\"猫\"}", headers), String.class);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(invalid.getBody()).contains("宠物名称不能为空");
    }
}
