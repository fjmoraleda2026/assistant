package com.assistant.application.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class ProjectSessionContextService {

    private final StringRedisTemplate stringRedisTemplate;

    public ProjectSessionContextService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Optional<UUID> getActiveProjectId(String chatId) {
        return getUuid(buildProjectKey(chatId));
    }

    public void setActiveProjectId(String chatId, UUID projectId) {
        stringRedisTemplate.opsForValue().set(buildProjectKey(chatId), projectId.toString());
    }

    public Optional<UUID> getActiveSessionId(String chatId) {
        return getUuid(buildSessionKey(chatId));
    }

    public void setActiveSessionId(String chatId, UUID sessionId) {
        stringRedisTemplate.opsForValue().set(buildSessionKey(chatId), sessionId.toString());
    }

    public void clearActiveSessionId(String chatId) {
        stringRedisTemplate.delete(buildSessionKey(chatId));
    }

    public void clearAllContext(String chatId) {
        stringRedisTemplate.delete(buildProjectKey(chatId));
        stringRedisTemplate.delete(buildSessionKey(chatId));
    }

    private Optional<UUID> getUuid(String key) {
        String raw = stringRedisTemplate.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private String buildProjectKey(String chatId) {
        return "chat:" + chatId + ":active_project";
    }

    private String buildSessionKey(String chatId) {
        return "chat:" + chatId + ":active_session";
    }
}
