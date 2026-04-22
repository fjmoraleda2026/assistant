package com.assistant.application.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class McpRuntimeConfigService {

    private final StringRedisTemplate stringRedisTemplate;

    public McpRuntimeConfigService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Check if MCP is enabled for this chat (per-chat override)
     * @return true if explicitly enabled/disabled in Redis, null means use app default
     */
    public Boolean isEnabled(String chatId) {
        String raw = stringRedisTemplate.opsForValue().get(buildEnabledKey(chatId));
        if (raw == null || raw.isBlank()) {
            return null; // Use app default
        }
        return Boolean.parseBoolean(raw);
    }

    /**
     * Enable MCP for this chat
     */
    public void enable(String chatId) {
        stringRedisTemplate.opsForValue().set(buildEnabledKey(chatId), "true");
    }

    /**
     * Disable MCP for this chat
     */
    public void disable(String chatId) {
        stringRedisTemplate.opsForValue().set(buildEnabledKey(chatId), "false");
    }

    /**
     * Clear MCP config (revert to app default)
     */
    public void clearEnabled(String chatId) {
        stringRedisTemplate.delete(buildEnabledKey(chatId));
    }

    /**
     * Get tool call count for rate limiting
     */
    public long getToolCallCount(String chatId) {
        String raw = stringRedisTemplate.opsForValue().get(buildToolCallCountKey(chatId));
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Increment tool call counter (used for rate limiting)
     */
    public void incrementToolCallCount(String chatId) {
        stringRedisTemplate.opsForValue().increment(buildToolCallCountKey(chatId));
        // Set expiration to 1 hour
        stringRedisTemplate.expire(buildToolCallCountKey(chatId), java.time.Duration.ofHours(1));
    }

    /**
     * Reset tool call counter
     */
    public void resetToolCallCount(String chatId) {
        stringRedisTemplate.delete(buildToolCallCountKey(chatId));
    }

    private String buildEnabledKey(String chatId) {
        return "chat:" + chatId + ":mcp_enabled";
    }

    private String buildToolCallCountKey(String chatId) {
        return "chat:" + chatId + ":mcp_tool_calls";
    }
}
