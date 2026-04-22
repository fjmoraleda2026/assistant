package com.assistant.infrastructure.adapter.out.ai.support;

import com.assistant.domain.dto.AiToolCall;
import com.assistant.domain.dto.AiToolResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AiToolResponseParser {

    private final ObjectMapper objectMapper;

    public AiToolResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiToolResponse parse(String content, String providerLabel) {
        String jsonCandidate = extractJsonObject(content);
        if (jsonCandidate == null) {
            return AiToolResponse.withoutTools(content);
        }

        String cleanJson = jsonCandidate.replaceAll(",\\s*([}\\]])", "$1");
        try {
            JsonNode root = objectMapper.readTree(cleanJson);
            String response = root.path("response").asText(content);
            List<AiToolCall> calls = new ArrayList<>();

            JsonNode toolCalls = root.path("toolCalls");
            if (toolCalls.isArray()) {
                for (JsonNode callNode : toolCalls) {
                    String toolName = callNode.path("toolName").asText("");
                    if (toolName.isBlank()) {
                        continue;
                    }

                    JsonNode argsNode = callNode.path("arguments");
                    Map<String, Object> arguments = argsNode.isObject()
                            ? objectMapper.convertValue(argsNode, new TypeReference<Map<String, Object>>() {
                            })
                            : Collections.emptyMap();

                    calls.add(new AiToolCall(toolName, arguments));
                }
            }

            return new AiToolResponse(response, calls);
        } catch (Exception ex) {
            log.debug("{} devolvio contenido no JSON para tools. Se usa respuesta directa.", providerLabel);
            return AiToolResponse.withoutTools(content);
        }
    }

    private String extractJsonObject(String content) {
        if (content == null) {
            return null;
        }

        String trimmed = content.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }

        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }

        return null;
    }
}
