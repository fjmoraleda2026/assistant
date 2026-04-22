package com.assistant.infrastructure.adapter.out.ai.config;

public record AiProviderAuthConfig(
        String authMode,
        String apiKey,
        String oauth2Token,
        String oauth2TokenUrl,
        String oauth2ClientId,
        String oauth2ClientSecret,
        String oauth2Scope,
        String oauth2Audience
) {

        public AiProviderAuthConfig(AiProvidersProperties.ProviderProperties providerProperties) {
                this(
                                providerProperties != null ? providerProperties.getAuthMode() : null,
                                providerProperties != null ? providerProperties.getApiKey() : null,
                                providerProperties != null ? providerProperties.getOauth2Token() : null,
                                providerProperties != null && providerProperties.getOauth2() != null ? providerProperties.getOauth2().getTokenUrl() : null,
                                providerProperties != null && providerProperties.getOauth2() != null ? providerProperties.getOauth2().getClientId() : null,
                                providerProperties != null && providerProperties.getOauth2() != null ? providerProperties.getOauth2().getClientSecret() : null,
                                providerProperties != null && providerProperties.getOauth2() != null ? providerProperties.getOauth2().getScope() : null,
                                providerProperties != null && providerProperties.getOauth2() != null ? providerProperties.getOauth2().getAudience() : null
                );
        }
}
