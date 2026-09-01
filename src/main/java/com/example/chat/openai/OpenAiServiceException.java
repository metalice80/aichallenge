package com.example.chat.openai;

public class OpenAiServiceException extends RuntimeException {

    public OpenAiServiceException(String message) {
        super(message);
    }

    public OpenAiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
