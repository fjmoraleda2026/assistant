package com.assistant.application.service.command;

import com.assistant.application.service.AiModelCatalogService;
import com.assistant.application.service.AiRuntimeConfigService;
import com.assistant.application.service.ConversationModeService;
import com.assistant.application.service.McpRateLimitService;
import com.assistant.application.service.McpRuntimeConfigService;
import com.assistant.application.service.ProjectFactService;
import com.assistant.application.service.ProjectSessionContextService;
import com.assistant.domain.model.AssistantProject;
import com.assistant.domain.model.AssistantSession;
import com.assistant.domain.model.FactStatus;
import com.assistant.infrastructure.persistence.AssistantProjectRepository;
import com.assistant.infrastructure.persistence.AssistantSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class StatusCommandHandler implements CommandHandler {

    private final ConversationModeService conversationModeService;
    private final ProjectSessionContextService projectSessionContextService;
    private final AssistantProjectRepository assistantProjectRepository;
    private final AssistantSessionRepository assistantSessionRepository;
    private final ProjectFactService projectFactService;
    private final AiModelCatalogService aiModelCatalogService;
    private final AiRuntimeConfigService aiRuntimeConfigService;
    private final McpRuntimeConfigService mcpRuntimeConfigService;
    private final McpRateLimitService mcpRateLimitService;
    private final String defaultAiProvider;
    private final String defaultAiModel;
    private final boolean mcpGlobalEnabled;

    public StatusCommandHandler(
            ConversationModeService conversationModeService,
            ProjectSessionContextService projectSessionContextService,
            AssistantProjectRepository assistantProjectRepository,
            AssistantSessionRepository assistantSessionRepository,
            ProjectFactService projectFactService,
            AiModelCatalogService aiModelCatalogService,
            AiRuntimeConfigService aiRuntimeConfigService,
            McpRuntimeConfigService mcpRuntimeConfigService,
            McpRateLimitService mcpRateLimitService,
            @Value("${app.ai.provider:mock}") String defaultAiProvider,
            @Value("${app.ai.model:gemini-2.0-flash}") String defaultAiModel,
            @Value("${app.mcp.enabled:true}") boolean mcpGlobalEnabled
    ) {
        this.conversationModeService = conversationModeService;
        this.projectSessionContextService = projectSessionContextService;
        this.assistantProjectRepository = assistantProjectRepository;
        this.assistantSessionRepository = assistantSessionRepository;
        this.projectFactService = projectFactService;
        this.aiModelCatalogService = aiModelCatalogService;
        this.aiRuntimeConfigService = aiRuntimeConfigService;
        this.mcpRuntimeConfigService = mcpRuntimeConfigService;
        this.mcpRateLimitService = mcpRateLimitService;
        this.defaultAiProvider = defaultAiProvider;
        this.defaultAiModel = defaultAiModel;
        this.mcpGlobalEnabled = mcpGlobalEnabled;
    }

    @Override
    public String commandKey() {
        return "estado";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        String chatId = request.chatId();
        boolean stateless = conversationModeService.isStateless(chatId);
        String provider = aiRuntimeConfigService.getProvider(chatId, defaultAiProvider);
        String configuredModel = aiRuntimeConfigService.getModel(chatId, defaultAiModel);
        String effectiveModel = aiModelCatalogService.resolveEffectiveModel(provider, configuredModel, defaultAiModel);

        Optional<UUID> activeProjectId = projectSessionContextService.getActiveProjectId(chatId);
        Optional<AssistantProject> activeProject = activeProjectId.flatMap(id -> assistantProjectRepository.findByIdAndChatId(id, chatId));

        Optional<UUID> activeSessionId = projectSessionContextService.getActiveSessionId(chatId);
        Optional<AssistantSession> activeSession = activeSessionId.flatMap(assistantSessionRepository::findById);

        StringBuilder builder = new StringBuilder("Estado actual:\n");
        builder.append("- modo: ").append(stateless ? "STATELESS" : "SESSION").append("\n");
        builder.append("- ia: provider=").append(provider)
            .append(", model_configurado=").append(configuredModel)
            .append(", model_efectivo=").append(effectiveModel)
            .append("\n");
        if (!effectiveModel.equalsIgnoreCase(configuredModel)) {
            builder.append("- ia fallback: activo (modelo incompatible para provider, usando default)\n");
        }

        // MCP Status
        Boolean mcpEnabled = mcpRuntimeConfigService.isEnabled(chatId);
        boolean shouldUseMcp = (mcpEnabled != null) ? mcpEnabled : mcpGlobalEnabled;
        String mcpStatus = shouldUseMcp ? "✅ Activo" : "⛔ Inactivo";
        if (mcpEnabled != null) {
            mcpStatus += " (per-chat)";
        } else {
            mcpStatus += " (global)";
        }
        builder.append("- mcp: ").append(mcpStatus).append("\n");
        builder.append("  ").append(mcpRateLimitService.getRateLimitMessage(chatId)).append("\n");

        if (activeProject.isPresent()) {
            builder.append("- proyecto activo: ")
                    .append(activeProject.get().getName())
                    .append(" [")
                    .append(activeProject.get().getId())
                    .append("]\n");
            if (activeProject.get().getBasePath() != null && !activeProject.get().getBasePath().isBlank()) {
                builder.append("- proyecto ruta: ")
                        .append(activeProject.get().getBasePath())
                        .append("\n");
            }
            if (activeProject.get().getDatabaseName() != null && !activeProject.get().getDatabaseName().isBlank()) {
                builder.append("- proyecto bd: ")
                        .append(activeProject.get().getDatabaseName());
                if (activeProject.get().getDatabaseSchema() != null && !activeProject.get().getDatabaseSchema().isBlank()) {
                    builder.append(" (schema=")
                            .append(activeProject.get().getDatabaseSchema())
                            .append(")");
                }
                builder.append("\n");
            }
        } else {
            builder.append("- proyecto activo: ninguno\n");
        }

        if (activeProject.isPresent()) {
            long proposedCount = projectFactService.countFacts(activeProject.get().getId(), FactStatus.PROPOSED);
            long confirmedCount = projectFactService.countFacts(activeProject.get().getId(), FactStatus.CONFIRMED);
            builder.append("- hechos proyecto: ")
                    .append(confirmedCount)
                    .append(" confirmados, ")
                    .append(proposedCount)
                    .append(" pendientes\n");
        }

        if (activeSession.isPresent()) {
            builder.append("- sesion activa: ")
                    .append(activeSession.get().getName())
                    .append(" [")
                    .append(activeSession.get().getId())
                    .append("]\n")
                    .append("- rol sesion: ")
                    .append(activeSession.get().getAiRole())
                    .append("\n")
                    .append("- estado sesion: ")
                    .append(activeSession.get().getStatus())
                    .append("\n");
        } else {
            builder.append("- sesion activa: ninguna\n");
        }

        builder.append("Sugerencia: /help para ver comandos disponibles.");
        return CommandResponse.handled(builder.toString().trim());
    }
}
