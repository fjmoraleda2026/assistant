package com.assistant.application.service;

import com.assistant.domain.model.CommandCatalog;
import com.assistant.infrastructure.persistence.CommandCatalogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class HelpCommandService {

    private final CommandCatalogRepository commandCatalogRepository;
    private final ConversationModeService conversationModeService;
    private final ProjectSessionContextService projectSessionContextService;

    public HelpCommandService(
            CommandCatalogRepository commandCatalogRepository,
            ConversationModeService conversationModeService,
            ProjectSessionContextService projectSessionContextService
    ) {
        this.commandCatalogRepository = commandCatalogRepository;
        this.conversationModeService = conversationModeService;
        this.projectSessionContextService = projectSessionContextService;
    }

    public String buildHelpMessage(String chatId) {
        List<CommandCatalog> commands = commandCatalogRepository.findByEnabledTrueOrderBySortOrderAscCommandKeyAsc();
        boolean statelessMode = conversationModeService.isStateless(chatId);
        Optional<UUID> activeProjectId = projectSessionContextService.getActiveProjectId(chatId);
        Optional<UUID> activeSessionId = projectSessionContextService.getActiveSessionId(chatId);

        if (commands.isEmpty()) {
            return "Comandos disponibles:\n"
                    + "/help - Muestra esta ayuda\n"
                    + "/ai <proveedor|reset> - Alias de /ia\n"
                    + "/ia <proveedor|reset> - Selecciona proveedor de IA para este chat\n"
                    + "/modelo <nombre-modelo|reset> - Selecciona modelo para este chat\n"
                    + "/proyectos - Lista tus proyectos\n"
                    + "/hechos [filtros] - Lista hechos del proyecto activo\n"
                    + "/hecho confirmar <id> - Confirma un hecho propuesto\n"
                    + "/sesiones - Lista las sesiones del proyecto activo\n"
                    + "/stateless - Activa modo sin memoria";
        }

        StringBuilder builder = new StringBuilder("Comandos disponibles:\n");
        builder.append("Modo actual: ")
                .append(statelessMode ? "STATELESS" : "SESSION")
                .append(" | Proyecto activo: ")
                .append(activeProjectId.map(UUID::toString).orElse("no"))
                .append(" | Sesion activa: ")
                .append(activeSessionId.map(UUID::toString).orElse("no"))
                .append("\n\n");

        for (CommandCatalog command : commands) {
            if (!isVisibleForCurrentContext(command, statelessMode, activeProjectId.isPresent(), activeSessionId.isPresent())) {
                continue;
            }

            builder.append("- ")
                    .append(command.getSyntax())
                    .append("\n  ")
                    .append(command.getDescription());
            if (command.getExample() != null && !command.getExample().isBlank()) {
                builder.append("\n  Ejemplo: ").append(command.getExample());
            }
            builder.append("\n");
        }

        if (builder.toString().equals("Comandos disponibles:\n" +
                "Modo actual: " + (statelessMode ? "STATELESS" : "SESSION") +
                " | Proyecto activo: " + activeProjectId.map(UUID::toString).orElse("no") +
                " | Sesion activa: " + activeSessionId.map(UUID::toString).orElse("no") +
                "\n\n")) {
            builder.append("No hay comandos habilitados para el contexto actual.\n");
        }

        return builder.toString().trim();
    }

    private boolean isVisibleForCurrentContext(
            CommandCatalog command,
            boolean statelessMode,
            boolean hasProject,
            boolean hasSession
    ) {
        if (command.isRequiresProject() && !hasProject) {
            return false;
        }
        if (command.isRequiresSession() && !hasSession) {
            return false;
        }

        String allowedModes = command.getAllowedModes() == null
                ? "BOTH"
                : command.getAllowedModes().trim().toUpperCase(Locale.ROOT);

        if ("BOTH".equals(allowedModes)) {
            return true;
        }

        if (statelessMode) {
            return "STATELESS".equals(allowedModes);
        }
        return "SESSION".equals(allowedModes);
    }
}
