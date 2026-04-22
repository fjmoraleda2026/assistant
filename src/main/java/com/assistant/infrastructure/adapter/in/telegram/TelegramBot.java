package com.assistant.infrastructure.adapter.in.telegram;

import com.assistant.application.port.in.HandleTelegramMessageUseCase;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final HandleTelegramMessageUseCase handleTelegramMessageUseCase;

    @Value("${app.telegram.bot-token}")
    private String botToken;

    @Value("${app.telegram.bot-username}")
    private String botUsername;

    @Value("#{'${app.telegram.allowed-chat-ids}'.split(',')}")
    private List<String> allowedChatIds;

    public TelegramBot(HandleTelegramMessageUseCase handleTelegramMessageUseCase) {
        this.handleTelegramMessageUseCase = handleTelegramMessageUseCase;
    }

    @PostConstruct
    public void init() {
        log.info("TelegramBot inicializado con username: {} y token configurado: {}", botUsername, botToken != null && !botToken.isEmpty());
        log.info("Chat IDs permitidos: {}", allowedChatIds);
    }

    @Override
    public void onUpdateReceived(Update update) {
        log.info("Update recibido de Telegram: {}", update);

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

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
}


