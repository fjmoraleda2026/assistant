package com.assistant.infrastructure.adapter.out.ai.config;

public record OpenAiProviderConfig(
        String providerLabel,
        String baseUrl,
        AiProviderAuthConfig authConfig
) {
}
