/**
 * Objetivo: Encapsular las llamadas a Google Generative AI (Gemini) vÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­a Spring AI.
 * Usuario: AssistantRequestUseCaseService.
 * Casos de Uso: Generar respuestas de IA con contexto conversacional completo.
 */
package com.assistant.infrastructure.adapter.out.ai.provider;

import com.assistant.domain.dto.AiToolResponse;
import com.assistant.infrastructure.adapter.out.ai.client.OpenAiCompatibleChatClient;
import com.assistant.infrastructure.adapter.out.ai.config.AiProviderAuthConfig;
import com.assistant.infrastructure.adapter.out.ai.config.AiProvidersProperties;
import com.assistant.infrastructure.adapter.out.ai.config.OpenAiProviderConfig;
import com.assistant.infrastructure.adapter.out.ai.support.AiToolResponseParser;
import com.assistant.infrastructure.adapter.out.ai.support.AiToolRoutingSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GeminiAiAdapter implements com.assistant.application.service.AiProviderService {

    private final OpenAiCompatibleChatClient chatClient;
    private final AiToolResponseParser toolResponseParser;
    private final OpenAiProviderConfig providerConfig;

    public GeminiAiAdapter(
            OpenAiCompatibleChatClient chatClient,
            AiToolResponseParser toolResponseParser,
            AiProvidersProperties aiProvidersProperties
    ) {
        this.chatClient = chatClient;
        this.toolResponseParser = toolResponseParser;
        AiProvidersProperties.ProviderProperties config = aiProvidersProperties.getGemini();
        this.providerConfig = new OpenAiProviderConfig(
            "GEMINI",
            config.getBaseUrl(),
            new AiProviderAuthConfig(config)
        );
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    /**
     * EnvÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­a el contexto y el prompt del usuario a Gemini y retorna la respuesta.
     *
     * @param systemContext Contexto completo (resumen histÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³rico + mensajes recientes de Redis)
     * @param userPrompt    El mensaje del usuario
     * @return La respuesta generada por Gemini
     */
    public String generateResponse(String systemContext, String userPrompt, String model) {
        log.debug("Llamando a Gemini. Contexto size: {}, Prompt size: {}",
                systemContext.length(), userPrompt.length());
        try {
            String response = chatClient.generate(
                    providerConfig,
                    model,
                    systemContext,
                    userPrompt
            );
            if (response == null || response.isBlank()) {
                throw new IllegalStateException("GEMINI_EMPTY_RESPONSE");
            }

            log.info("Respuesta generada por Gemini con tamaÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â±o: {}", response.length());
            return response;

        } catch (IllegalStateException e) {
            log.error("Error de autenticación o configuración en Gemini: {}", e.getMessage());
            String code = e.getMessage() != null ? e.getMessage().split(":")[0] : "GEMINI_AUTH_FAILED";
            throw new IllegalStateException(code, e);
        } catch (Exception e) {
            log.error("Error al llamar a Gemini", e);
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (message.contains("http 429") || message.contains("resource_exhausted") || message.contains("quota")) {
                throw new IllegalStateException("AI_QUOTA_EXCEEDED", e);
            }
            throw new IllegalStateException("AI_CALL_FAILED", e);
        }
    }

    @Override
    public AiToolResponse generateResponseWithTools(String systemContext, String userPrompt, String model) {
        AiToolResponse directToolCall = AiToolRoutingSupport.detectDirectToolCall(userPrompt, "Gemini", log);
        if (directToolCall != null) {
            return directToolCall;
        }

        String content = generateResponse(AiToolRoutingSupport.TOOL_INSTRUCTIONS, userPrompt, model);
        return toolResponseParser.parse(content, "Gemini");
    }
}









