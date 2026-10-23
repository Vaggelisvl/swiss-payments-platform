package dev.vrailas.payments.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"assistant.gemini.api-key=", "assistant.access-token=s3cret"})
class AccessTokenTest {

    private static final String BODY = "{\"source\":\"processor\",\"code\":\"SAME_ACCOUNT\"}";

    @Autowired
    MockMvc mvc;

    @Test
    void apiNeedsTheToken() throws Exception {
        mvc.perform(post("/api/v1/exceptions/explain").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/exceptions/explain").header("X-Access-Token", "wrong").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/exceptions/explain").header("X-Access-Token", "s3cret").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void healthStaysOpen() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
