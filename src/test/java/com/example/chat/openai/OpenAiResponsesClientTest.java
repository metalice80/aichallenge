package com.example.chat.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.chat.config.OpenAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiResponsesClientTest {

    private MockRestServiceServer server;
    private OpenAiResponsesClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiResponsesClient(
                builder.baseUrl("https://api.openai.com").build(),
                new OpenAiProperties("https://api.openai.com", "test-key", "gpt-test"));
    }

    @Test
    void sendsConfiguredModelAndReturnsOutputText() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("gpt-test"))
                .andExpect(jsonPath("$.input").value("Hello"))
                .andRespond(withSuccess("""
                        {
                          "output": [
                            {
                              "type": "message",
                              "content": [
                                {"type": "output_text", "text": "Hello back"}
                              ]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        String response = client.createResponse("Hello");

        assertThat(response).isEqualTo("Hello back");
        server.verify();
    }

    @Test
    void mapsOpenAiErrorStatusToApiException() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.createResponse("Hello"))
                .isInstanceOf(OpenAiApiException.class)
                .hasMessage("OpenAI API request failed with status 404");
        server.verify();
    }

    @Test
    void rejectsResponseWithoutOutputText() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("{\"output\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.createResponse("Hello"))
                .isInstanceOf(OpenAiServiceException.class)
                .hasMessage("OpenAI API response does not contain output text");
        server.verify();
    }

    @Test
    void requiresApiKeyBeforeSendingRequest() {
        OpenAiResponsesClient clientWithoutKey = new OpenAiResponsesClient(
                RestClient.create("https://api.openai.com"),
                new OpenAiProperties("https://api.openai.com", "", "gpt-test"));

        assertThatThrownBy(() -> clientWithoutKey.createResponse("Hello"))
                .isInstanceOf(OpenAiConfigurationException.class)
                .hasMessage("OPENAI_API_KEY environment variable is not configured");
    }
}
