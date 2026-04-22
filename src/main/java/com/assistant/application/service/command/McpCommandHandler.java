package com.assistant.application.service.command;

import com.assistant.application.service.McpAuditService;
import com.assistant.application.service.McpRateLimitService;
import com.assistant.application.service.McpRuntimeConfigService;
import com.assistant.application.service.AiRuntimeConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class McpCommandHandler implements CommandHandler {

    private final McpRuntimeConfigService mcpRuntimeConfigService;
    private final McpRateLimitService mcpRateLimitService;
    private final McpAuditService mcpAuditService;
    private final AiRuntimeConfigService aiRuntimeConfigService;

    @Value("${app.mcp.enabled:true}")
    private boolean mcpGlobalEnabled;

    @Value("${app.mcp.max-iterations:2}")
    private int maxIterations;

    @Value("${app.mcp.max-tool-calls:2}")
    private int maxToolCalls;

    @Value("${app.mcp.tool-timeout-ms:4000}")
    private int toolTimeoutMs;

    @Value("${app.mcp.allowed-tools:filesystem.read,postgres.query}")
    private String allowedTools;

    public McpCommandHandler(McpRuntimeConfigService mcpRuntimeConfigService,
                            McpRateLimitService mcpRateLimitService,
                            McpAuditService mcpAuditService,
                            AiRuntimeConfigService aiRuntimeConfigService) {
        this.mcpRuntimeConfigService = mcpRuntimeConfigService;
        this.mcpRateLimitService = mcpRateLimitService;
        this.mcpAuditService = mcpAuditService;
        this.aiRuntimeConfigService = aiRuntimeConfigService;
    }

    @Override
    public String commandKey() {
        return "mcp";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        String chatId = request.chatId();
        String arguments = request.arguments() == null ? "" : request.arguments().trim();

        if (arguments.isEmpty()) {
            return CommandResponse.handled(buildStatusMessage(chatId));
        }

        String[] parts = arguments.split("\\s+");
        String subcommand = parts[0].toLowerCase();

        return switch (subcommand) {
            case "status" -> CommandResponse.handled(buildStatusMessage(chatId));
            case "enable" -> CommandResponse.handled(handleEnable(chatId));
            case "disable" -> CommandResponse.handled(handleDisable(chatId));
            case "tools" -> CommandResponse.handled(buildToolsMessage());
            case "logs" -> {
                int limit = 5;
                if (parts.length > 1) {
                    try {
                        limit = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {}
                }
                yield CommandResponse.handled(buildLogsMessage(chatId, limit));
            }
            case "reset" -> CommandResponse.handled(handleReset(chatId));
            default -> CommandResponse.handled(buildHelpMessage());
        };
    }

    private String handleEnable(String chatId) {
        mcpRuntimeConfigService.enable(chatId);
        String warning = buildModelCompatibilityWarning(chatId);
        return "✅ MCP habilitado para este chat\n\nHerramientas disponibles:\n"
            + formatToolsList()
            + warning;
    }

    private String handleDisable(String chatId) {
        mcpRuntimeConfigService.disable(chatId);
        return "⛔ MCP deshabilitado para este chat\n\n" +
               "Para reactivar: /mcp enable";
    }

    private String handleReset(String chatId) {
        mcpRuntimeConfigService.clearEnabled(chatId);
        return "🔄 Configuración MCP reseteada\n\n" +
               "Ahora usa configuración global (app default)";
    }

    private String buildStatusMessage(String chatId) {
        Boolean enabled = mcpRuntimeConfigService.isEnabled(chatId);
        String enabledStatus;
        
        if (enabled == null) {
            enabledStatus = mcpGlobalEnabled ? "✅ Activo (global)" : "⛔ Inactivo (global)";
        } else {
            enabledStatus = enabled ? "✅ Activo (per-chat)" : "⛔ Inactivo (per-chat)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 **Estado MCP**\n\n");
        sb.append("- Estado: ").append(enabledStatus).append("\n");
        sb.append("- Herramientas: ").append(countTools()).append("\n");
        sb.append("- Max iteraciones: ").append(maxIterations).append("\n");
        sb.append("- Max llamadas/iter: ").append(maxToolCalls).append("\n");
        sb.append("- Timeout: ").append(toolTimeoutMs).append("ms\n\n");
        sb.append(mcpRateLimitService.getRateLimitMessage(chatId)).append("\n\n");
        sb.append("**Comandos disponibles:**\n");
        sb.append("- /mcp enable - Activar MCP\n");
        sb.append("- /mcp disable - Desactivar MCP\n");
        sb.append("- /mcp tools - Listar herramientas\n");
        sb.append("- /mcp logs [n] - Ver últimas n ejecuciones (default 5)\n");
        sb.append("- /mcp reset - Volver a config global\n");

        String warning = buildModelCompatibilityWarning(chatId);
        if (!warning.isBlank()) {
            sb.append(warning);
        }

        return sb.toString();
    }

    private String buildToolsMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("🛠️ **Herramientas MCP**\n\n");
        sb.append(formatToolsList());
        sb.append("\n**Nota:** Las herramientas se ejecutan automáticamente cuando el IA las necesita.\n");
        sb.append("Puedes ver el historio con: /mcp logs");
        return sb.toString();
    }

    private String formatToolsList() {
        String[] tools = allowedTools.split(",");
        StringBuilder sb = new StringBuilder();
        for (String tool : tools) {
            String trimmed = tool.trim();
            String description = switch (trimmed) {
                case "filesystem.read" -> "Leer archivos del sistema";
                case "filesystem.write" -> "Escribir/actualizar archivos";
                case "filesystem.list" -> "Listar contenido de directorios";
                case "postgres.query" -> "Ejecutar consultas SQL (SELECT)";
                case "postgres.describe" -> "Inspeccionar estructura de tablas";
                case "http.get" -> "Peticiones HTTP GET";
                case "http.post" -> "Peticiones HTTP POST";
                case "project-facts.query" -> "Consultar hechos del proyecto";
                default -> "Herramienta personalizada";
            };
            sb.append("- **").append(trimmed).append("**: ").append(description).append("\n");
        }
        return sb.toString();
    }

    private String buildLogsMessage(String chatId, int limit) {
        List<McpAuditService.ToolExecutionRecord> records = 
            mcpAuditService.getLastExecutions(chatId, limit);

        if (records.isEmpty()) {
            return "📋 Sin registros de ejecuciones MCP aún";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 **Últimas ").append(Math.min(limit, records.size()));
        sb.append(" ejecuciones MCP:**\n\n");

        for (int i = 0; i < records.size(); i++) {
            McpAuditService.ToolExecutionRecord record = records.get(i);
            sb.append(i + 1).append(". ").append(record.formatForDisplay()).append("\n");
            if (!record.getArguments().isEmpty()) {
                sb.append("   Args: ").append(truncate(record.getArguments(), 50)).append("\n");
            }
        }

        return sb.toString();
    }

    private String buildHelpMessage() {
        return "❓ Comando MCP no reconocido\n\n" +
               "Usa: /mcp <status|enable|disable|tools|logs|reset>\n\n" +
               "Ejemplos:\n" +
               "- /mcp status\n" +
               "- /mcp enable\n" +
               "- /mcp logs 10\n";
    }

    private int countTools() {
        return allowedTools.split(",").length;
    }

    private String buildModelCompatibilityWarning(String chatId) {
        String provider = aiRuntimeConfigService.getProvider(chatId, "mock");
        String model = aiRuntimeConfigService.getModel(chatId, "");

        // Models known to struggle with postgres/SQL tool calls
        Set<String> weakSqlModels = Set.of("llama-3.1-8b-instant", "llama-3.1-70b-versatile");
        // Models with no tool calling support
        Set<String> noToolModels = Set.of("mock-default", "mock");

        if ("mock".equalsIgnoreCase(provider)) {
            return "\n⚠️ Aviso: el proveedor 'mock' no ejecuta herramientas reales.\n";
        }
        if (noToolModels.contains(model.toLowerCase())) {
            return "\n⚠️ Aviso: modelo '" + model + "' no soporta tool calling.\n";
        }
        if (weakSqlModels.contains(model.toLowerCase())) {
            return "\n⚠️ Aviso: '" + model + "' puede tener dificultades generando SQL.\n"
                    + "  Recomendado para MCP con BD: /modelo llama-3.3-70b-versatile\n"
                    + "  O cambia proveedor: /ia gemini\n";
        }
        return "";
    }

    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
}
