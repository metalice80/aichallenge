package com.example.chat.openai;

public class OpenAiApiException extends RuntimeException {

    private final int statusCode;

    public OpenAiApiException(int statusCode) {
        super("OpenAI API request failed with status " + statusCode);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
