package com.assistant.infrastructure.adapter.out.ai.client;

import com.assistant.infrastructure.adapter.out.ai.auth.AiAuthenticationSupport;
import com.assistant.infrastructure.adapter.out.ai.config.AuthenticationResult;
import com.assistant.infrastructure.adapter.out.ai.config.OpenAiProviderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleChatClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiAuthenticationSupport authenticationSupport;

    public OpenAiCompatibleChatClient(ObjectMapper objectMapper, AiAuthenticationSupport authenticationSupport) {
        this.restClient = RestClient.builder().build();
        this.objectMapper = objectMapper;
        this.authenticationSupport = authenticationSupport;
    }

    public String generate(
            OpenAiProviderConfig providerConfig,
            String model,
            String systemContext,
            String userPrompt
    ) throws Exception {
        AuthenticationResult authResult = authenticationSupport.authenticate(
                providerConfig.providerLabel(),
                providerConfig.authConfig()
        );

        Object payload = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemContext),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        String rawResponse = restClient.post()
                                .uri(providerConfig.baseUrl() + "/chat/completions")
                .headers(headers -> {
                    authenticationSupport.applyAuthToHeaders(headers, authResult);
                    headers.set("Content-Type", "application/json");
                })
                .body(payload)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(rawResponse);
        return root.path("choices").path(0).path("message").path("content").asText();
    }
}
