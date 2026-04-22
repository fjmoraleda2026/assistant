/**
 * Objetivo: Gestionar la memoria hÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­brida y registrar mÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â©tricas de eficiencia.
 * Usuario: AssistantRequestUseCaseService / Prometheus.
 * Casos de Uso: Registro de ahorro de tokens y eventos de compresiÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³n.
 */
package com.assistant.application.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MemoryMetricsService {

    private final Counter tokensSavedCounter;
    private final Counter summarizationEventsCounter;

    public MemoryMetricsService(MeterRegistry registry) {
        this.tokensSavedCounter = Counter.builder("assistant.tokens.saved")
                .description("Total de tokens ahorrados por sumarizaciÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³n")
                .register(registry);
                
        this.summarizationEventsCounter = Counter.builder("assistant.memory.summarization.events")
                .description("NÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âºmero de veces que se ha comprimido la memoria")
                .register(registry);
    }

    public void recordCompression(int messageCount, int estimatedTokensSaved) {
        log.info("MÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â°TRICA: Comprimiendo {} mensajes. Ahorro estimado: {} tokens", messageCount, estimatedTokensSaved);
        this.summarizationEventsCounter.increment();
        this.tokensSavedCounter.increment(estimatedTokensSaved);
    }
}



