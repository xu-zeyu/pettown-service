package com.pettown.boot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class MiniappOpenApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @Test
    void exportsOnlyThisClientWithDocumentedOperationsAndCorrectAuthentication() throws Exception {
        JsonNode document = document();
        assertThat(document.path("info").path("title").asText()).isEqualTo("PetTown 小程序接口");
        assertThat(document.path("info").path("version").asText()).isEqualTo("0.0.1");
        JsonNode scheme = document.path("components").path("securitySchemes").path("bearerAuth");
        assertThat(scheme.path("type").asText()).isEqualTo("http");
        assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.has("bearerFormat")).isFalse();
        assertThat(document.path("security").size()).isZero();
        JsonNode paths = document.path("paths");
        int operations = 0;
        for (var entry : paths.properties()) {
            assertThat(entry.getKey()).startsWith("/api/miniapp/");
            for (JsonNode operation : entry.getValue()) {
                assertThat(operation.path("summary").asText()).isNotBlank();
                assertThat(operation.path("tags").size()).isPositive();
                operations++;
            }
        }
        assertThat(operations).isEqualTo(3);
        assertThat(paths.path("/api/miniapp/auth/login").path("post").path("security").size()).isZero();
        assertThat(paths.path("/api/miniapp/user/me").path("get").path("security").get(0).has("bearerAuth")).isTrue();
    }

    @Test
    void keepsVerificationPublicAndDocumentsVerificationPurpose() throws Exception {
        JsonNode document = document();
        var verification = document.path("paths").path("/api/miniapp/verification/code").path("post");
        assertThat(verification.path("security").size()).isZero();
        assertThat(document.path("components").path("schemas").path("VerificationCodeRequest")
                .path("properties").path("type").path("description").asText()).contains("验证码用途");
    }

    private JsonNode document() throws Exception {
        String content = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(content);
    }
}
