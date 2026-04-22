package com.assistant.application.service;

import com.assistant.application.port.in.HandleTelegramMessageUseCase;
import com.assistant.application.port.out.IncomingMessagePublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TelegramMessageIngestionService implements HandleTelegramMessageUseCase {

    private final IncomingMessagePublisherPort incomingMessagePublisherPort;

    @Override
    public void receiveMessage(String chatId, String text) {
        incomingMessagePublisherPort.publishIncoming(chatId, text);
    }
}



