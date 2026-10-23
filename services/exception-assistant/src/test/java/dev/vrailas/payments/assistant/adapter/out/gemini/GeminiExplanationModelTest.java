package dev.vrailas.payments.assistant.adapter.out.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vrailas.payments.assistant.domain.ExceptionCase;
import dev.vrailas.payments.assistant.domain.Explanation;
import dev.vrailas.payments.assistant.domain.ExplanationUnavailableException;
import dev.vrailas.payments.assistant.domain.Severity;
import dev.vrailas.payments.assistant.domain.Catalogue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiExplanationModelTest {

    private static final ExceptionCase CASE = new ExceptionCase("processor", "INSUFFICIENT_FUNDS", "balance too low", Map.of("debtor", "CH93…2957"));

    private MockRestServiceServer server;
    private GeminiExplanationModel model;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test");
        server = MockRestServiceServer.bindTo(builder).build();
        model = new GeminiExplanationModel(builder.build(), "gemini-test", new ObjectMapper());
    }

    @Test
    void sendsSchemaConstrainedRequestAndParsesAnswer() throws Exception {
        server.expect(requestTo("http://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").exists())
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(org.hamcrest.Matchers.containsString("INSUFFICIENT_FUNDS")))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"cause\\":\\"Balance too low\\",\\"suggestedAction\\":\\"Notify customer\\",\\"severity\\":\\"LOW\\"}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        Explanation result = model.explain(CASE, Catalogue.lookup(CASE));

        assertThat(result.cause()).isEqualTo("Balance too low");
        assertThat(result.severity()).isEqualTo(Severity.LOW);
        server.verify();
    }

    @Test
    void errorStatusMeansUnavailable() {
        server.expect(requestTo("http://gemini.test/v1beta/models/gemini-test:generateContent")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> model.explain(CASE, Catalogue.lookup(CASE))).isInstanceOf(ExplanationUnavailableException.class);
    }

    @Test
    void blockedOrMalformedAnswerMeansUnavailable() {
        server.expect(requestTo("http://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> model.explain(CASE, Catalogue.lookup(CASE))).isInstanceOf(ExplanationUnavailableException.class);

        server.reset();
        server.expect(requestTo("http://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"cause\\":\\"x\\",\\"suggestedAction\\":\\"y\\",\\"severity\\":\\"CRITICAL\\"}"}]}}]}
                        """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> model.explain(CASE, Catalogue.lookup(CASE))).isInstanceOf(ExplanationUnavailableException.class);
    }
}
