/**
 * Objetivo: Consolidar mensajes antiguos en un resumen persistente.
 * Usuario: ConversationMemoryService / Gemini API.
 */
package com.assistant.application.service;

import com.assistant.domain.model.SessionSummary;
import com.assistant.application.port.in.CompressConversationSummaryUseCase;
import com.assistant.application.port.out.SessionSummaryPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSummaryService implements CompressConversationSummaryUseCase {

    private final SessionSummaryPersistencePort sessionSummaryPersistencePort;
    private final MemoryMetricsService metricsService;

    @Override
    @Transactional
    public void compress(String chatId, List<String> messages) {
        log.info("Iniciando sumarizaciÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³n para chat: {}", chatId);
        
        SessionSummary current = sessionSummaryPersistencePort.findById(chatId)
                .orElse(new SessionSummary(chatId, "Inicio de conversaciÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³n.", LocalDateTime.now(), 0L));

        // TODO: IntegraciÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³n real con Gemini para resumir
        String newSummary = "Resumen actualizado incluyendo: " + String.join(" | ", messages);
        
        current.setSummary(newSummary);
        current.setLastUpdate(LocalDateTime.now());
        current.setMessageCountProcessed(current.getMessageCountProcessed() + messages.size());
        
        sessionSummaryPersistencePort.save(current);
        metricsService.recordCompression(messages.size(), messages.size() * 100); // EstimaciÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³n
    }
}



