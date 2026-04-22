/**
 * Objetivo: Consumir proveedor DeepSeek usando endpoint OpenAI-compatible.
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
public class DeepSeekAiAdapter implements com.assistant.application.service.AiProviderService {

    private final OpenAiCompatibleChatClient chatClient;
    private final AiToolResponseParser toolResponseParser;
    private final OpenAiProviderConfig providerConfig;

    public DeepSeekAiAdapter(
            OpenAiCompatibleChatClient chatClient,
            AiToolResponseParser toolResponseParser,
            AiProvidersProperties aiProvidersProperties
    ) {
        this.chatClient = chatClient;
        this.toolResponseParser = toolResponseParser;
        AiProvidersProperties.ProviderProperties config = aiProvidersProperties.getDeepseek();
        this.providerConfig = new OpenAiProviderConfig(
            "DEEPSEEK",
            config.getBaseUrl(),
            new AiProviderAuthConfig(config)
        );
    }

    @Override
    public String providerName() {
        return "deepseek";
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
                throw new IllegalStateException("DEEPSEEK_EMPTY_RESPONSE");
            }
            return response;

        } catch (IllegalStateException e) {
            log.error("Error de autenticación o configuración en DeepSeek: {}", e.getMessage());
            String code = e.getMessage() != null ? e.getMessage().split(":")[0] : "DEEPSEEK_AUTH_FAILED";
            throw new IllegalStateException(code, e);
        } catch (Exception e) {
            log.error("Error al llamar a DeepSeek", e);
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (message.contains("http 429") || message.contains("resource_exhausted") || message.contains("quota")) {
                throw new IllegalStateException("AI_QUOTA_EXCEEDED", e);
            }
            throw new IllegalStateException("AI_CALL_FAILED", e);
        }
    }

    @Override
    public AiToolResponse generateResponseWithTools(String systemContext, String userPrompt, String model) {
        AiToolResponse directToolCall = AiToolRoutingSupport.detectDirectToolCall(userPrompt, "DeepSeek", log);
        if (directToolCall != null) {
            return directToolCall;
        }

        String content = generateResponse(AiToolRoutingSupport.TOOL_INSTRUCTIONS, userPrompt, model);
        return toolResponseParser.parse(content, "DeepSeek");
    }
}
