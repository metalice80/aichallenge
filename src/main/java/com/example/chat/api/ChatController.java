package com.example.chat.api;

import com.example.chat.openai.OpenAiResponsesClient;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final OpenAiResponsesClient openAiClient;

    public ChatController(OpenAiResponsesClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(openAiClient.createResponse(request.message()));
    }
}
