package com.assistant.application.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AiRuntimeConfigService {

    private final StringRedisTemplate stringRedisTemplate;

    public AiRuntimeConfigService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String getProvider(String chatId, String defaultProvider) {
        String raw = stringRedisTemplate.opsForValue().get(buildProviderKey(chatId));
        if (raw == null || raw.isBlank()) {
            return normalizeProvider(defaultProvider);
        }
        return normalizeProvider(raw);
    }

    public String getModel(String chatId, String defaultModel) {
        String raw = stringRedisTemplate.opsForValue().get(buildModelKey(chatId));
        if (raw == null || raw.isBlank()) {
            return defaultModel == null ? "" : defaultModel.trim();
        }
        return raw.trim();
    }

    public void setProvider(String chatId, String provider) {
        stringRedisTemplate.opsForValue().set(buildProviderKey(chatId), normalizeProvider(provider));
    }

    public void clearProvider(String chatId) {
        stringRedisTemplate.delete(buildProviderKey(chatId));
    }

    public void setModel(String chatId, String model) {
        stringRedisTemplate.opsForValue().set(buildModelKey(chatId), model == null ? "" : model.trim());
    }

    public void clearModel(String chatId) {
        stringRedisTemplate.delete(buildModelKey(chatId));
    }

    private String normalizeProvider(String provider) {
        if (provider == null) {
            return "";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private String buildProviderKey(String chatId) {
        return "chat:" + chatId + ":ai_provider";
    }

    private String buildModelKey(String chatId) {
        return "chat:" + chatId + ":ai_model";
    }
}
