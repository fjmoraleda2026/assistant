/**
 * Objetivo: Consumidor de Kafka para enviar respuestas a Telegram.
 */
package com.assistant.infrastructure.adapter.in.kafka;

import com.assistant.domain.dto.ChatEvent;
import com.assistant.infrastructure.adapter.in.telegram.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramSender {

    private final com.assistant.infrastructure.adapter.in.telegram.TelegramBot telegramBot;

    @KafkaListener(
        topics = "${app.kafka.outbound-topic}",
        groupId = "${spring.kafka.consumer.group-id}-sender"
    )
    public void onMessage(ChatEvent event) {
        log.info("Enviando respuesta a Telegram para chat {}: {}", event.getChatId(), event.getPrompt());
        try {
            SendMessage message = new SendMessage();
            message.setChatId(event.getChatId());
            message.setText(event.getPrompt());
            telegramBot.execute(message);
            log.info("Respuesta enviada exitosamente a Telegram");
        } catch (TelegramApiException e) {
            log.error("Error enviando mensaje a Telegram: {}", e.getMessage(), e);
        }
    }
}


