package com.assistant.application.port.in;

public interface HandleTelegramMessageUseCase {
    void receiveMessage(String chatId, String text);
}




