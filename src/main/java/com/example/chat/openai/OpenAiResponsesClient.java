package com.example.chat.openai;

import com.example.chat.config.OpenAiProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OpenAiResponsesClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiResponsesClient.class);
    private static final Pattern AUTHORIZATION_PATTERN =
            Pattern.compile("(?i)Bearer\\s+[^\\s,;]+");
    private static final Pattern OPENAI_KEY_PATTERN =
            Pattern.compile("(?i)sk-[a-z0-9_-]{8,}");

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiResponsesClient(
            RestClient restClient, OpenAiProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
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
                        logUpstreamError(upstreamResponse);
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

    private void logUpstreamError(ClientHttpResponse response) throws IOException {
        OpenAiError error = readOpenAiError(response);
        LOGGER.warn(
                "OpenAI API request failed: http.status={}, error.message={}, "
                        + "error.type={}, error.code={}, x-request-id={}",
                response.getStatusCode().value(),
                sanitizeForLog(error.message()),
                sanitizeForLog(error.type()),
                sanitizeForLog(error.code()),
                sanitizeForLog(response.getHeaders().getFirst("x-request-id")));
    }

    private OpenAiError readOpenAiError(ClientHttpResponse response) {
        try {
            OpenAiErrorEnvelope envelope =
                    objectMapper.readValue(response.getBody(), OpenAiErrorEnvelope.class);
            return envelope == null || envelope.error() == null
                    ? OpenAiError.EMPTY
                    : envelope.error();
        } catch (IOException exception) {
            return OpenAiError.EMPTY;
        }
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }

        String sanitized = value;
        if (StringUtils.hasText(properties.apiKey())) {
            sanitized = sanitized.replace(properties.apiKey(), "[REDACTED]");
        }
        sanitized = AUTHORIZATION_PATTERN.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        sanitized = OPENAI_KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED]");
        return sanitized.replace('\r', ' ').replace('\n', ' ');
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiErrorEnvelope(OpenAiError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiError(String message, String type, String code) {

        private static final OpenAiError EMPTY = new OpenAiError(null, null, null);
    }

    private record ResponsesResponse(List<OutputItem> output) {
    }

    private record OutputItem(String type, List<ContentItem> content) {
    }

    private record ContentItem(String type, String text) {
    }
}
