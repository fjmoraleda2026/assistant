package com.assistant.application.service;

import com.assistant.application.port.out.AiProviderPort;
import com.assistant.application.port.out.ToolExecutorPort;
import com.assistant.infrastructure.persistence.AssistantProjectRepository;
import com.assistant.domain.dto.AiToolCall;
import com.assistant.domain.dto.AiToolResponse;
import com.assistant.domain.dto.ToolExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolOrchestrationService {

    private final ToolExecutorPort toolExecutorPort;
    private final McpAuditService mcpAuditService;
    private final McpRateLimitService mcpRateLimitService;
    private final McpRuntimeConfigService mcpRuntimeConfigService;
    private final ProjectSessionContextService projectSessionContextService;
    private final AssistantProjectRepository assistantProjectRepository;

    @Value("${app.mcp.enabled:true}")
    private boolean mcpGlobalEnabled;

    @Value("${app.mcp.max-iterations:2}")
    private int maxIterations;

    @Value("${app.mcp.max-tool-calls:2}")
    private int maxToolCalls;

    @Value("${app.mcp.tool-timeout-ms:4000}")
    private long toolTimeoutMs;

    @Value("#{'${app.mcp.allowed-tools:filesystem.read,postgres.query}'.split(',')}")
    private List<String> allowedTools;

    public String processWithMcp(
            String chatId,
            String prompt,
            String baseContext,
            AiProviderPort aiProvider,
            String model
    ) {
        // Check if MCP is enabled for this chat
        Boolean mcpEnabled = mcpRuntimeConfigService.isEnabled(chatId);
        boolean shouldUseMcp = (mcpEnabled != null) ? mcpEnabled : mcpGlobalEnabled;

        if (!shouldUseMcp) {
            log.debug("MCP disabled for chat {}. Using standard response.", chatId);
            return aiProvider.generateResponse(baseContext, prompt, model);
        }

        // Check rate limit
        if (!mcpRateLimitService.checkAndIncrement(chatId)) {
            log.warn("MCP rate limit exceeded for chat {}", chatId);
            String message = "⚠️ Límite de herramientas alcanzado esta hora. Intenta más tarde o usa /mcp status para ver disponibilidad.";
            return message;
        }

        String workingPrompt = prompt;
        String finalResponse = "";
        List<ToolExecutionResult> lastToolResults = Collections.emptyList();
        // Limitar contexto en llamadas MCP para evitar superar TPM del proveedor.
        String trimmedBaseContext = baseContext != null && baseContext.length() > 1500
                ? baseContext.substring(baseContext.length() - 1500)
                : baseContext;

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            // Primera iteracion: contexto (trimado) para que la IA decida tools.
            // Iteraciones siguientes: contexto minimo para reducir tokens (el prompt ya lleva los resultados).
            String contextForIteration = iteration == 1 ? trimmedBaseContext : "Eres un asistente. Responde usando los resultados de herramientas proporcionados.";

            List<AiToolCall> toolCalls;
            if (iteration == 1) {
                // Primera iteracion: puede decidir usar herramientas
                AiToolResponse aiToolResponse = aiProvider.generateResponseWithTools(contextForIteration, workingPrompt, model);
                finalResponse = aiToolResponse.getResponseText();
                toolCalls = aiToolResponse.getToolCalls() == null ? Collections.emptyList() : aiToolResponse.getToolCalls();
            } else {
                // Iteraciones siguientes: ya tenemos resultados, solo necesitamos texto
                // Usamos generateResponse directamente para evitar que pre-deteccion de SQL se dispare
                // sobre el prompt compuesto que contiene la consulta original
                finalResponse = aiProvider.generateResponse(contextForIteration, workingPrompt, model);
                finalResponse = recoverPlainResponseIfBlank(
                        finalResponse,
                        contextForIteration,
                        workingPrompt,
                        aiProvider,
                        model,
                        iteration
                );
                String writeIntegrityFallback = buildFilesystemWriteIntegrityFallbackIfNeeded(finalResponse, lastToolResults);
                if (writeIntegrityFallback != null) {
                    return writeIntegrityFallback;
                }
                String fallback = buildFilesystemFallbackIfNeeded(finalResponse, lastToolResults);
                if (fallback != null) {
                    return fallback;
                }
                return finalResponse;
            }

            if (toolCalls.isEmpty()) {
                finalResponse = recoverPlainResponseIfBlank(
                        finalResponse,
                        contextForIteration,
                        workingPrompt,
                        aiProvider,
                        model,
                        iteration
                );
                String finalText = finalResponse.isBlank() ? "No se obtuvo respuesta." : finalResponse;
                String integrityResult = buildFilesystemWriteIntegrityFallbackIfNeeded(finalText, Collections.emptyList());
                return integrityResult != null ? integrityResult : finalText;
            }

            List<AiToolCall> limitedCalls = toolCalls.stream().limit(maxToolCalls).toList();
            List<ToolExecutionResult> toolResults = executeToolCalls(chatId, limitedCalls);
            lastToolResults = toolResults;
            workingPrompt = buildFollowupPrompt(prompt, toolResults);
            log.info("MCP iteration {} completed for chat {} with {} tool calls", iteration, chatId, limitedCalls.size());
        }

        finalResponse = recoverPlainResponseIfBlank(
                finalResponse,
                trimmedBaseContext,
                workingPrompt,
                aiProvider,
                model,
                maxIterations
        );
        String finalText = finalResponse.isBlank() ? "No se obtuvo respuesta." : finalResponse;
        String integrityResult = buildFilesystemWriteIntegrityFallbackIfNeeded(finalText, lastToolResults);
        return integrityResult != null ? integrityResult : finalText;
    }

    private String recoverPlainResponseIfBlank(
            String response,
            String context,
            String prompt,
            AiProviderPort aiProvider,
            String model,
            int iteration
    ) {
        if (response != null && !response.isBlank()) {
            return response;
        }

        try {
            String recovered = aiProvider.generateResponse(context, prompt, model);
            if (recovered != null && !recovered.isBlank()) {
                log.warn("MCP iteration {} produjo respuesta vacia; se recupero con llamada directa", iteration);
                return recovered;
            }
        } catch (Exception ex) {
            log.warn("No se pudo recuperar respuesta vacia en MCP iteration {}: {}", iteration, ex.getMessage());
        }

        return response;
    }

    private List<ToolExecutionResult> executeToolCalls(String chatId, List<AiToolCall> toolCalls) {
        List<ToolExecutionResult> results = new ArrayList<>();
        Set<String> allowedSet = normalizeAllowedTools();
        String projectBasePath = resolveProjectBasePath(chatId);

        for (AiToolCall toolCall : toolCalls) {
            String toolName = toolCall.getToolName() == null ? "" : toolCall.getToolName().trim();
            Map<String, Object> args = enrichToolArguments(toolName, toolCall.getArguments(), projectBasePath);

            if (!allowedSet.contains(toolName.toLowerCase())) {
                String errorMsg = "Tool no permitida por configuracion.";
                results.add(new ToolExecutionResult(toolName, false, errorMsg));
                mcpAuditService.recordToolExecution(chatId, toolName, "", errorMsg, 0, false);
                log.warn("MCP tool blocked by whitelist. chatId={} tool={}", chatId, toolName);
                continue;
            }

            try {
                long startTime = System.currentTimeMillis();
                String toolOutput = CompletableFuture
                        .supplyAsync(() -> toolExecutorPort.executeTool(toolName, args))
                        .orTimeout(toolTimeoutMs, TimeUnit.MILLISECONDS)
                        .join();
                long durationMs = System.currentTimeMillis() - startTime;
                boolean successfulOutput = isToolOutputSuccessful(toolOutput);

                results.add(new ToolExecutionResult(toolName, successfulOutput, toolOutput));
                mcpAuditService.recordToolExecution(chatId, toolName, args.toString(), toolOutput, durationMs, successfulOutput);
            } catch (CompletionException ex) {
                String reason = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                String errorMsg = "Error ejecutando tool: " + reason;
                results.add(new ToolExecutionResult(toolName, false, errorMsg));
                mcpAuditService.recordToolExecution(chatId, toolName, args.toString(), errorMsg, 0, false);
                log.error("MCP tool execution failed. chatId={} tool={}", chatId, toolName, ex);
            }
        }

        return results;
    }

    private Map<String, Object> enrichToolArguments(String toolName, Map<String, Object> originalArgs, String projectBasePath) {
        Map<String, Object> args = originalArgs == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(originalArgs);
        String normalizedToolName = toolName == null ? "" : toolName.toLowerCase();
        if (projectBasePath != null
                && normalizedToolName.startsWith("filesystem.")
                && !args.containsKey("basePath")) {
            args.put("basePath", projectBasePath);
        }
        return args;
    }

    private String resolveProjectBasePath(String chatId) {
        return projectSessionContextService.getActiveProjectId(chatId)
                .flatMap(projectId -> assistantProjectRepository.findById(projectId))
                .map(project -> project.getBasePath() == null ? "" : project.getBasePath().trim())
                .filter(path -> !path.isBlank())
                .orElse(null);
    }

    private String buildFollowupPrompt(String originalPrompt, List<ToolExecutionResult> toolResults) {
        String resultBlock = toolResults.stream()
                .map(this::formatToolResult)
                .collect(Collectors.joining("\n"));

        return "Pregunta original del usuario:\n" + originalPrompt +
                "\n\nResultados de herramientas MCP:\n" + resultBlock +
            "\n\nReglas obligatorias de respuesta:\n"
            + "- Solo confirma acciones realmente exitosas (lineas [OK]).\n"
            + "- Si una accion de escritura/creacion fallo ([ERROR]), explica el fallo y no digas que se completo.\n"
            + "- Si no hubo ejecucion de escritura exitosa, no afirmes 'archivo/directorio creado'.\n"
            + "\nCon esta informacion, responde al usuario de forma final y clara.";
    }

    private String formatToolResult(ToolExecutionResult result) {
        String status = result.isSuccess() ? "OK" : "ERROR";
        return "- [" + status + "] " + result.getToolName() + ": " + result.getOutput();
    }

    private String buildFilesystemFallbackIfNeeded(String aiResponse, List<ToolExecutionResult> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return null;
        }

        boolean suspiciousAiResponse = aiResponse == null
                || aiResponse.isBlank()
                || containsNoContentClaim(aiResponse);

        if (!suspiciousAiResponse) {
            return null;
        }

        for (ToolExecutionResult result : toolResults) {
            String toolName = result.getToolName() == null ? "" : result.getToolName().toLowerCase();
            String output = result.getOutput();
            if (result.isSuccess()
                    && toolName.contains("filesystem.read")
                    && output != null
                    && !output.isBlank()
                    && !output.toLowerCase().startsWith("error:")) {
                return "He leido el archivo correctamente. Contenido:\n" + output;
            }
        }

        return null;
    }

    private String buildFilesystemWriteIntegrityFallbackIfNeeded(String aiResponse, List<ToolExecutionResult> toolResults) {
        if (aiResponse == null) {
            return null;
        }

        String normalizedResponse = aiResponse.toLowerCase();
        boolean claimsCreation = normalizedResponse.contains("directorio creado")
                || normalizedResponse.contains("carpeta creada")
                || normalizedResponse.contains("archivo creado")
                || normalizedResponse.contains("archivo escrito")
                || normalizedResponse.contains("archivo generado")
                || normalizedResponse.contains("creado correctamente")
                || normalizedResponse.contains("escrito correctamente")
                || normalizedResponse.contains("se ha creado");

        if (!claimsCreation) {
            return null;
        }

        if (toolResults == null || toolResults.isEmpty()) {
            return "No se puede confirmar la creacion porque no se ejecuto ninguna herramienta de escritura (filesystem.write).";
        }

        boolean hasSuccessfulWrite = toolResults.stream().anyMatch(result -> {
            String toolName = result.getToolName() == null ? "" : result.getToolName().toLowerCase();
            return result.isSuccess() && toolName.contains("filesystem.write");
        });

        if (hasSuccessfulWrite) {
            return null;
        }

        String toolSummary = toolResults.stream()
                .map(this::formatToolResult)
                .collect(Collectors.joining("\n"));

        return "No se puede confirmar la creacion porque no hubo una escritura exitosa en filesystem.write.\n"
                + "Resultados reales de herramientas:\n"
                + toolSummary;
    }

    private boolean isToolOutputSuccessful(String toolOutput) {
        if (toolOutput == null) {
            return false;
        }
        String normalized = toolOutput.trim().toLowerCase();
        return !normalized.startsWith("error:") && !normalized.startsWith("error ");
    }

    private boolean containsNoContentClaim(String aiResponse) {
        String normalized = aiResponse == null ? "" : aiResponse.toLowerCase();
        return normalized.contains("no parece contener")
                || normalized.contains("no ha proporcionado contenido")
                || normalized.contains("no se ha encontrado informacion relevante")
                || normalized.contains("no se ha encontrado información relevante")
                || normalized.contains("no puedo ayudarte con ese archivo")
                || normalized.contains("no contiene informacion relevante")
                || normalized.contains("no contiene información relevante");
    }

    private Set<String> normalizeAllowedTools() {
        return allowedTools.stream()
                .map(value -> value == null ? "" : value.trim().toLowerCase())
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
