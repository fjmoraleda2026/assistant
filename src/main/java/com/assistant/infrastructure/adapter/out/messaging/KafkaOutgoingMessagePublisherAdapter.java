package com.assistant.infrastructure.adapter.out.messaging;

import com.assistant.application.port.out.OutgoingMessagePublisherPort;
import com.assistant.domain.dto.ChatEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOutgoingMessagePublisherAdapter implements OutgoingMessagePublisherPort {

    private final KafkaTemplate<String, ChatEvent> kafkaTemplate;

    @Value("${app.kafka.outbound-topic}")
    private String outboundTopic;

    @Override
    public void publishOutgoing(String chatId, String response) {
        ChatEvent event = new ChatEvent(chatId, response, System.currentTimeMillis());
        kafkaTemplate.send(outboundTopic, chatId, event);
        log.info("Respuesta publicada en Kafka topic={} chatId={}", outboundTopic, chatId);
    }
}
