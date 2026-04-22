package com.assistant.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class McpRateLimitService {

    private final McpRuntimeConfigService mcpRuntimeConfigService;

    @Value("${app.mcp.max-calls-per-hour:10}")
    private int maxCallsPerHour;

    public McpRateLimitService(McpRuntimeConfigService mcpRuntimeConfigService) {
        this.mcpRuntimeConfigService = mcpRuntimeConfigService;
    }

    /**
     * Check if tool calls are allowed and increment counter
     * @return true if allowed, false if rate limit exceeded
     */
    public boolean checkAndIncrement(String chatId) {
        long currentCount = mcpRuntimeConfigService.getToolCallCount(chatId);
        if (currentCount >= maxCallsPerHour) {
            return false;
        }
        mcpRuntimeConfigService.incrementToolCallCount(chatId);
        return true;
    }

    /**
     * Get remaining tool calls for this hour
     */
    public long getRemainingCalls(String chatId) {
        long currentCount = mcpRuntimeConfigService.getToolCallCount(chatId);
        return Math.max(0, maxCallsPerHour - currentCount);
    }

    /**
     * Get rate limit message
     */
    public String getRateLimitMessage(String chatId) {
        long remaining = getRemainingCalls(chatId);
        return String.format("Límite de herramientas: %d/%d usadas esta hora. %d disponibles.", 
            maxCallsPerHour - remaining, maxCallsPerHour, remaining);
    }
}
