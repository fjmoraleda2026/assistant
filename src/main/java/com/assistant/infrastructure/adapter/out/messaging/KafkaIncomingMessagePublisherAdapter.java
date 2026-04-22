package com.assistant.infrastructure.adapter.out.messaging;

import com.assistant.application.port.out.IncomingMessagePublisherPort;
import com.assistant.domain.dto.ChatEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaIncomingMessagePublisherAdapter implements IncomingMessagePublisherPort {

    private final KafkaTemplate<String, ChatEvent> kafkaTemplate;

    @Value("${app.kafka.inbound-topic}")
    private String inboundTopic;

    @Override
    public void publishIncoming(String chatId, String prompt) {
        ChatEvent event = new ChatEvent(chatId, prompt, System.currentTimeMillis());
        kafkaTemplate.send(inboundTopic, chatId, event);
        log.info("Mensaje entrante publicado en Kafka topic={} chatId={}", inboundTopic, chatId);
    }
}
