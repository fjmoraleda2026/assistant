package com.assistant.infrastructure.adapter.in.telegram;

import com.assistant.application.port.in.HandleTelegramMessageUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final HandleTelegramMessageUseCase handleTelegramMessageUseCase;

    @Value("#{'${app.telegram.allowed-chat-ids}'.split(',')}")
    private List<String> allowedChatIds;

    @PostMapping("${app.telegram.webhook-path}")
    public void receiveUpdate(@RequestBody Update update) {
        Message message = null;

        if (update.hasMessage()) {
            message = update.getMessage();
        } else if (update.hasEditedMessage()) {
            message = update.getEditedMessage();
        }

        if (message == null || message.getText() == null) {
            log.warn("Telegram update recibido sin texto vÃƒÂ¡lido: {}", update);
            return;
        }

        String chatId = message.getChatId().toString();

        if (!allowedChatIds.contains(chatId)) {
            log.warn("Chat ID no permitido: {}", chatId);
            return;
        }

        handleTelegramMessageUseCase.receiveMessage(chatId, message.getText());
    }
}


