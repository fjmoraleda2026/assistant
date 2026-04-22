/**
 * Objetivo: Consumir Ollama local usando endpoint OpenAI-compatible.
 * Usuario: AssistantRequestUseCaseService.
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
public class OllamaAiAdapter implements com.assistant.application.service.AiProviderService {

    private final OpenAiCompatibleChatClient chatClient;
    private final AiToolResponseParser toolResponseParser;
    private final OpenAiProviderConfig providerConfig;

    public OllamaAiAdapter(
            OpenAiCompatibleChatClient chatClient,
            AiToolResponseParser toolResponseParser,
            AiProvidersProperties aiProvidersProperties
    ) {
        this.chatClient = chatClient;
        this.toolResponseParser = toolResponseParser;
        AiProvidersProperties.ProviderProperties config = aiProvidersProperties.getOllama();
        this.providerConfig = new OpenAiProviderConfig(
            "OLLAMA",
            config.getBaseUrl(),
            new AiProviderAuthConfig(config)
        );
    }

    @Override
    public String providerName() {
        return "ollama";
    }

    @Override
    public String generateResponse(String systemContext, String userPrompt, String model) {
        try {
            String response = chatClient.generate(
                    providerConfig,
                    model,
                    systemContext,
                    userPrompt
            );
            if (response == null || response.isBlank()) {
                throw new IllegalStateException("OLLAMA_EMPTY_RESPONSE");
            }
            return response;

        } catch (IllegalStateException e) {
            log.error("Error de configuración en Ollama: {}", e.getMessage());
            String code = e.getMessage() != null ? e.getMessage().split(":")[0] : "OLLAMA_FAILED";
            throw new IllegalStateException(code, e);
        } catch (Exception e) {
            log.error("Error al llamar a Ollama", e);
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (message.contains("connection refused") || message.contains("connect timed out")) {
                throw new IllegalStateException("OLLAMA_UNAVAILABLE", e);
            }
            throw new IllegalStateException("AI_CALL_FAILED", e);
        }
    }

    @Override
    public AiToolResponse generateResponseWithTools(String systemContext, String userPrompt, String model) {
        AiToolResponse directToolCall = AiToolRoutingSupport.detectDirectToolCall(userPrompt, "Ollama", log);
        if (directToolCall != null) {
            return directToolCall;
        }

        String content = generateResponse(AiToolRoutingSupport.TOOL_INSTRUCTIONS, userPrompt, model);
        return toolResponseParser.parse(content, "Ollama");
    }
}
