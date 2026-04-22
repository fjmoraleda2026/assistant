/**
 * Objetivo: Consumidor de Kafka para mensajes entrantes de Telegram.
 */
package com.assistant.infrastructure.adapter.in.kafka;

import com.assistant.domain.dto.ChatEvent;
import com.assistant.application.port.in.HandleAssistantRequestUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramConsumer {

    private final HandleAssistantRequestUseCase handleAssistantRequestUseCase;

    @KafkaListener(
        topics = "${app.kafka.inbound-topic}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(ChatEvent event) {
        log.info("Evento Kafka recibido: {}", event.getChatId());
        handleAssistantRequestUseCase.processRequest(event.getChatId(), event.getPrompt());
    }
}


