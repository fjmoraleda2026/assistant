package com.assistant.application.port.out;

public interface ConversationContextPort {
    void updateMemory(String chatId, String prompt, String response);

    String getFullContext(String chatId);
}




