package com.pettown.boot.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class AppOpenApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    void exportsPetOperationsWithUserAuthenticationWhileTheDocumentIsPublic() throws Exception {
        String content = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var document = json.readTree(content);
        assertThat(document.path("info").path("title").asText()).isEqualTo("PetTown App 接口");
        assertThat(document.path("info").path("version").asText()).isEqualTo("0.0.1");
        var scheme = document.path("components").path("securitySchemes").path("bearerAuth");
        assertThat(scheme.path("type").asText()).isEqualTo("http");
        assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.has("bearerFormat")).isFalse();
        assertThat(document.path("security").size()).isZero();
        int operations = 0;
        for (var entry : document.path("paths").properties()) {
            assertThat(entry.getKey()).startsWith("/api/pets");
            for (var operation : entry.getValue()) {
                assertThat(operation.path("summary").asText()).isNotBlank();
                assertThat(operation.path("tags").get(0).asText()).isEqualTo("App 宠物管理");
                assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue();
                operations++;
            }
        }
        assertThat(operations).isEqualTo(3);
    }
}
