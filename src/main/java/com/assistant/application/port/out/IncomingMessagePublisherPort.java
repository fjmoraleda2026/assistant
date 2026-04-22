package com.assistant.application.port.out;

public interface IncomingMessagePublisherPort {
    void publishIncoming(String chatId, String prompt);
}




