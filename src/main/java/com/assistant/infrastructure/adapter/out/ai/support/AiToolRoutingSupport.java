package com.assistant.infrastructure.adapter.out.ai.support;

import com.assistant.domain.dto.AiToolCall;
import com.assistant.domain.dto.AiToolResponse;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AiToolRoutingSupport {

    private static final Pattern SQL_PROMPT_PATTERN =
            Pattern.compile("(?i)consulta\\s+sql\\s*:\\s*(.+)", Pattern.DOTALL);

    private static final Pattern FILE_PROMPT_PATTERN =
            Pattern.compile("(?i)(?:leer?|lee|read|abrir?|mostrar?|muestra|ver|show)\\s+(?:archivo\\s+|file\\s+)?([\\w.\\-/]+\\.[\\w]{1,10})",
                    Pattern.DOTALL);

    private AiToolRoutingSupport() {
    }

    public static final String TOOL_INSTRUCTIONS = "Tienes acceso a la herramienta filesystem.read para leer archivos.\n"
            + "Si el usuario pide leer un archivo, responde UNICAMENTE con este JSON exacto:\n"
            + "{\"response\":\"\",\"toolCalls\":[{\"toolName\":\"filesystem.read\",\"arguments\":{\"path\":\"NOMBRE_ARCHIVO\"}}]}\n"
            + "Ejemplo 'leer README_GEMINI.md': {\"response\":\"\",\"toolCalls\":[{\"toolName\":\"filesystem.read\",\"arguments\":{\"path\":\"README_GEMINI.md\"}}]}\n"
            + "Si NO necesitas herramientas: {\"response\":\"tu respuesta\",\"toolCalls\":[]}";

    public static AiToolResponse detectDirectToolCall(String userPrompt, String providerLabel, Logger log) {
        if (userPrompt == null) {
            return null;
        }

        String trimmedPrompt = userPrompt.trim();

        Matcher sqlMatcher = SQL_PROMPT_PATTERN.matcher(trimmedPrompt);
        if (sqlMatcher.find()) {
            String sql = sqlMatcher.group(1).trim();
            log.info("{}: SQL detectado por patron 'consulta sql:', usando postgres.query directamente", providerLabel);
            return new AiToolResponse("", List.of(new AiToolCall("postgres.query", Map.of("sql", sql))));
        }

        String upper = trimmedPrompt.toUpperCase();
        if (upper.startsWith("SELECT ") || upper.startsWith("SELECT\n")) {
            log.info("{}: SQL detectado (SELECT), usando postgres.query directamente", providerLabel);
            return new AiToolResponse("", List.of(new AiToolCall("postgres.query", Map.of("sql", trimmedPrompt))));
        }

        Matcher fileMatcher = FILE_PROMPT_PATTERN.matcher(trimmedPrompt);
        if (fileMatcher.find()) {
            String fileName = fileMatcher.group(1).trim();
            log.info("{}: archivo detectado por patron, usando filesystem.read directamente: {}", providerLabel, fileName);
            return new AiToolResponse("", List.of(new AiToolCall("filesystem.read", Map.of("path", fileName))));
        }

        return null;
    }
}
