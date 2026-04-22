package com.assistant.application.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ConversationModeService {

    private static final String MODE_STATELESS = "STATELESS";
    private static final String MODE_SESSION = "SESSION";

    private final StringRedisTemplate stringRedisTemplate;

    public ConversationModeService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void setStateless(String chatId) {
        stringRedisTemplate.opsForValue().set(buildModeKey(chatId), MODE_STATELESS);
    }

    public void setSessionMode(String chatId) {
        stringRedisTemplate.opsForValue().set(buildModeKey(chatId), MODE_SESSION);
    }

    public boolean isStateless(String chatId) {
        String mode = stringRedisTemplate.opsForValue().get(buildModeKey(chatId));
        return mode != null && MODE_STATELESS.equals(mode.trim().toUpperCase(Locale.ROOT));
    }

    private String buildModeKey(String chatId) {
        return "chat:" + chatId + ":context_mode";
    }
}
