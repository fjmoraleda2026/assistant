package com.assistant.application.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class McpAuditService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public McpAuditService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Record a tool execution
     */
    public void recordToolExecution(String chatId, String toolName, String arguments, 
                                    String result, long durationMs, boolean success) {
        try {
            ToolExecutionRecord record = new ToolExecutionRecord();
            record.setToolName(toolName);
            record.setArguments(arguments);
            record.setResult(result);
            record.setTimestamp(Instant.now().toEpochMilli());
            record.setDurationMs(durationMs);
            record.setSuccess(success);

            String json = objectMapper.writeValueAsString(record);
            String key = buildAuditKey(chatId);
            
            // Store as sorted set with timestamp as score for easy retrieval
            stringRedisTemplate.opsForZSet().add(key, json, record.getTimestamp());
            
            // Keep only last 100 records per chat
            stringRedisTemplate.opsForZSet().removeRange(key, 0, -101);
            
            // Set expiration to 24 hours
            stringRedisTemplate.expire(key, java.time.Duration.ofHours(24));
        } catch (Exception e) {
            // Silently fail audit recording - don't break the request
        }
    }

    /**
     * Get last N tool executions
     */
    public List<ToolExecutionRecord> getLastExecutions(String chatId, int limit) {
        try {
            String key = buildAuditKey(chatId);
            Set<String> records = stringRedisTemplate.opsForZSet()
                    .reverseRange(key, 0, limit - 1);
            
            if (records == null) {
                return Collections.emptyList();
            }

            List<ToolExecutionRecord> result = new ArrayList<>();
            for (String json : records) {
                try {
                    ToolExecutionRecord record = objectMapper.readValue(json, ToolExecutionRecord.class);
                    result.add(record);
                } catch (Exception e) {
                    // Skip malformed records
                }
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Get execution count for a specific tool
     */
    public long getToolExecutionCount(String chatId, String toolName) {
        try {
            String key = buildAuditKey(chatId);
            Set<String> records = stringRedisTemplate.opsForZSet().range(key, 0, -1);
            
            if (records == null) {
                return 0;
            }

            long count = 0;
            for (String json : records) {
                try {
                    ToolExecutionRecord record = objectMapper.readValue(json, ToolExecutionRecord.class);
                    if (record.getToolName().equalsIgnoreCase(toolName)) {
                        count++;
                    }
                } catch (Exception e) {
                    // Skip malformed records
                }
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    private String buildAuditKey(String chatId) {
        return "chat:" + chatId + ":mcp_audit";
    }

    public static class ToolExecutionRecord {
        private String toolName;
        private String arguments;
        private String result;
        private long timestamp;
        private long durationMs;
        private boolean success;

        // Getters and setters
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }

        public String getArguments() { return arguments; }
        public void setArguments(String arguments) { this.arguments = arguments; }

        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String formatForDisplay() {
            String status = success ? "✅" : "❌";
            return String.format("%s %s (%dms)", status, toolName, durationMs);
        }
    }
}
