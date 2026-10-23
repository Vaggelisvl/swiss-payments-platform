package dev.vrailas.payments.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs without a key, so it covers the catalogue fallback end to end. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "assistant.gemini.api-key=")
class ExplainApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void answersFromCatalogueWithoutKey() throws Exception {
        mvc.perform(post("/api/v1/exceptions/explain").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"processor\",\"code\":\"DENYLISTED_ACCOUNT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("CATALOGUE"))
                .andExpect(jsonPath("$.severity").value("HIGH"));
    }

    @Test
    void rejectsMissingCode() throws Exception {
        mvc.perform(post("/api/v1/exceptions/explain").contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"processor\"}"))
                .andExpect(status().isBadRequest());
    }
}
