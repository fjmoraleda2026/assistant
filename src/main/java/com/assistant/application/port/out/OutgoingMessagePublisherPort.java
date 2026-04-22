package com.assistant.application.port.out;

public interface OutgoingMessagePublisherPort {
    void publishOutgoing(String chatId, String response);
}




