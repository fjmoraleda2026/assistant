package com.assistant.infrastructure.adapter.out.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProvidersProperties {

    private ProviderProperties gemini = new ProviderProperties("https://generativelanguage.googleapis.com/v1beta/openai");
    private ProviderProperties grok = new ProviderProperties("https://api.groq.com/openai/v1");
    private ProviderProperties chatgpt = new ProviderProperties("https://api.openai.com/v1");
    private ProviderProperties deepseek = new ProviderProperties("https://api.deepseek.com/v1");
    private ProviderProperties ollama = new ProviderProperties("http://localhost:11434/v1");

    @Getter
    @Setter
    public static class ProviderProperties {
        private String authMode = "api-key";
        private String apiKey = "";
        private String oauth2Token = "";
        private OAuth2Properties oauth2 = new OAuth2Properties();
        private String baseUrl;

        public ProviderProperties() {
        }

        public ProviderProperties(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    @Getter
    @Setter
    public static class OAuth2Properties {
        private String tokenUrl = "";
        private String clientId = "";
        private String clientSecret = "";
        private String scope = "";
        private String audience = "";
    }
}
