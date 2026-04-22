/**
 * Objetivo: Controlar el flujo de memoria entre Redis (corto plazo) y Postgres (largo plazo).
 * Usuario: AssistantRequestUseCaseService.
 */
package com.assistant.application.service;

import com.assistant.application.port.in.CompressConversationSummaryUseCase;
import com.assistant.application.port.out.ChatMessageStorePort;
import com.assistant.application.port.out.ConversationContextPort;
import com.assistant.application.port.out.SessionSummaryPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService implements ConversationContextPort {

    private final ChatMessageStorePort chatMessageStorePort;
    private final CompressConversationSummaryUseCase compressConversationSummaryUseCase;
    private final SessionSummaryPersistencePort sessionSummaryPersistencePort;

    @Value("${app.memory.redis-threshold:20}")
    private int redisThreshold;

    @Value("${app.memory.overlap-size:5}")
    private int overlapSize;

    @Override
    public void updateMemory(String chatId, String prompt, String response) {
        chatMessageStorePort.appendMessage(chatId, "User: " + prompt);
        chatMessageStorePort.appendMessage(chatId, "Assistant: " + response);

        Long currentSize = chatMessageStorePort.size(chatId);
        if (currentSize != null && currentSize >= redisThreshold) {
            log.info("Umbral de Redis ({} msgs) alcanzado para {}. Ejecutando sumarizaciÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³n con overlap.", currentSize, chatId);
            
            // Calculamos cuÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ntos mensajes debemos remover para dejar solo el 'overlap'
            long messagesToRemove = currentSize - overlapSize;
            List<String> toSummarize = new ArrayList<>();
            
            for (int i = 0; i < messagesToRemove; i++) {
                String msg = chatMessageStorePort.popOldest(chatId);
                if (msg != null) toSummarize.add(msg);
            }

            if (!toSummarize.isEmpty()) {
                log.debug("Enviando {} mensajes antiguos a ChatSummaryService.", toSummarize.size());
                CompletableFuture.runAsync(() -> compressConversationSummaryUseCase.compress(chatId, toSummarize));
            }
        }
    }

    @Override
    public String getFullContext(String chatId) {
        String persistentSummary = sessionSummaryPersistencePort.findById(chatId)
                .map(s -> s.getSummary())
                .orElse("Sin historial previo.");

        List<String> recentMessages = chatMessageStorePort.range(chatId, 0, -1);
        
        StringBuilder context = new StringBuilder();
        context.append("RESUMEN HISTÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“RICO:\n").append(persistentSummary).append("\n\n");
        context.append("MENSAJES RECIENTES:\n");
        if (recentMessages != null) {
            recentMessages.forEach(m -> context.append(m).append("\n"));
        }
        
        return context.toString();
    }
}



