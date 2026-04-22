package com.assistant.application.port.in;

public interface HandleAssistantRequestUseCase {
    void processRequest(String chatId, String prompt);
}




