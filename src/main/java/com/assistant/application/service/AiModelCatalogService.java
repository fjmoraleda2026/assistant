package com.assistant.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class AiModelCatalogService {

    private final Map<String, Set<String>> allowedModelsByProvider;
    private final Map<String, String> defaultModelByProvider;

    public AiModelCatalogService(
            @Value("${app.ai.allowed-models.gemini:}") String geminiModels,
            @Value("${app.ai.allowed-models.grok:}") String grokModels,
            @Value("${app.ai.allowed-models.chatgpt:}") String chatgptModels,
            @Value("${app.ai.allowed-models.deepseek:}") String deepseekModels,
            @Value("${app.ai.allowed-models.ollama:}") String ollamaModels,
            @Value("${app.ai.allowed-models.mock:}") String mockModels,
            @Value("${app.ai.default-models.gemini:}") String geminiDefault,
            @Value("${app.ai.default-models.grok:}") String grokDefault,
            @Value("${app.ai.default-models.chatgpt:}") String chatgptDefault,
            @Value("${app.ai.default-models.deepseek:}") String deepseekDefault,
            @Value("${app.ai.default-models.ollama:}") String ollamaDefault,
            @Value("${app.ai.default-models.mock:}") String mockDefault
    ) {
        this.allowedModelsByProvider = Map.of(
                "gemini", parseCsv(geminiModels),
                "grok", parseCsv(grokModels),
                "chatgpt", parseCsv(chatgptModels),
                "deepseek", parseCsv(deepseekModels),
                "ollama", parseCsv(ollamaModels),
                "mock", parseCsv(mockModels)
        );
        this.defaultModelByProvider = Map.of(
                "gemini", normalize(geminiDefault),
                "grok", normalize(grokDefault),
                "chatgpt", normalize(chatgptDefault),
                "deepseek", normalize(deepseekDefault),
                "ollama", normalize(ollamaDefault),
                "mock", normalize(mockDefault)
        );
    }

    public boolean isModelAllowed(String provider, String model) {
        String normalizedModel = normalize(model);
        if (normalizedModel.isBlank()) {
            return false;
        }

        Set<String> allowed = allowedModelsByProvider.get(normalize(provider));
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        return allowed.contains(normalizedModel);
    }

    public String allowedModelsHint(String provider) {
        Set<String> allowed = allowedModelsByProvider.get(normalize(provider));
        if (allowed == null || allowed.isEmpty()) {
            return "cualquiera";
        }
        return String.join(", ", allowed);
    }

    public String defaultModelForProvider(String provider, String fallbackModel) {
        String providerDefault = defaultModelByProvider.get(normalize(provider));
        if (providerDefault == null || providerDefault.isBlank()) {
            return fallbackModel == null ? "" : fallbackModel.trim();
        }
        return providerDefault;
    }

    public String resolveEffectiveModel(String provider, String requestedModel, String fallbackModel) {
        String normalizedRequested = normalize(requestedModel);
        if (isModelAllowed(provider, normalizedRequested)) {
            return requestedModel == null ? "" : requestedModel.trim();
        }

        String providerDefault = defaultModelForProvider(provider, fallbackModel);
        if (isModelAllowed(provider, providerDefault)) {
            return providerDefault;
        }

        return fallbackModel == null ? "" : fallbackModel.trim();
    }

    private Set<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(raw.split(","))
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
