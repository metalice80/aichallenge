package com.example.chat.openai;

import com.example.chat.config.OpenAiProperties;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OpenAiResponsesClient {

    private final RestClient restClient;
    private final OpenAiProperties properties;

    public OpenAiResponsesClient(RestClient restClient, OpenAiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public String createResponse(String input) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new OpenAiConfigurationException(
                    "OPENAI_API_KEY environment variable is not configured");
        }

        ResponsesResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResponsesRequest(properties.model(), input))
                    .retrieve()
                    .onStatus(status -> status.isError(), (request, upstreamResponse) -> {
                        throw new OpenAiApiException(upstreamResponse.getStatusCode().value());
                    })
                    .body(ResponsesResponse.class);
        } catch (OpenAiApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new OpenAiServiceException("Failed to call OpenAI Responses API", exception);
        }

        return extractText(response);
    }

    private String extractText(ResponsesResponse response) {
        if (response == null || response.output() == null) {
            throw new OpenAiServiceException("OpenAI API returned an invalid response");
        }

        StringBuilder text = new StringBuilder();
        for (OutputItem output : response.output()) {
            if (output.content() == null) {
                continue;
            }
            for (ContentItem content : output.content()) {
                if (!"output_text".equals(content.type()) || !StringUtils.hasText(content.text())) {
                    continue;
                }
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(content.text());
            }
        }

        if (text.isEmpty()) {
            throw new OpenAiServiceException("OpenAI API response does not contain output text");
        }
        return text.toString();
    }

    private record ResponsesRequest(String model, String input) {
    }

    private record ResponsesResponse(List<OutputItem> output) {
    }

    private record OutputItem(String type, List<ContentItem> content) {
    }

    private record ContentItem(String type, String text) {
    }
}
