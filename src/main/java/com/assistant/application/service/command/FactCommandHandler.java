package com.assistant.application.service.command;

import com.assistant.application.service.ProjectFactService;
import com.assistant.application.service.ProjectSessionContextService;
import com.assistant.domain.model.AssistantProjectFact;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
public class FactCommandHandler implements CommandHandler {

    private final ProjectSessionContextService projectSessionContextService;
    private final ProjectFactService projectFactService;

    public FactCommandHandler(
            ProjectSessionContextService projectSessionContextService,
            ProjectFactService projectFactService
    ) {
        this.projectSessionContextService = projectSessionContextService;
        this.projectFactService = projectFactService;
    }

    @Override
    public String commandKey() {
        return "hecho";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        Optional<UUID> activeProjectId = projectSessionContextService.getActiveProjectId(request.chatId());
        if (activeProjectId.isEmpty()) {
            return CommandResponse.handled("No hay proyecto activo. Usa /proyecto <nombre|id> o /proyecto crear <nombre>.");
        }

        String args = request.arguments() == null ? "" : request.arguments().trim();
        if (args.isBlank()) {
            return CommandResponse.handled("Uso: /hecho confirmar <id> | /hecho rechazar <id> | /hecho add <texto>");
        }

        String[] parts = args.split("\\s+", 2);
        String action = parts[0].toLowerCase(Locale.ROOT);
        String payload = parts.length > 1 ? parts[1].trim() : "";

        return switch (action) {
            case "confirmar" -> handleConfirm(activeProjectId.get(), payload);
            case "rechazar" -> handleReject(activeProjectId.get(), payload);
            case "add" -> handleAdd(activeProjectId.get(), payload);
            default -> CommandResponse.handled("Uso: /hecho confirmar <id> | /hecho rechazar <id> | /hecho add <texto>");
        };
    }

    private CommandResponse handleConfirm(UUID projectId, String payload) {
        Optional<UUID> factId = CommandArgumentUtils.asUuid(payload);
        if (factId.isEmpty()) {
            return CommandResponse.handled("Debes indicar un id valido. Ejemplo: /hecho confirmar 123e4567-e89b-12d3-a456-426614174000");
        }

        Optional<AssistantProjectFact> fact = projectFactService.confirmFact(projectId, factId.get());
        if (fact.isEmpty()) {
            return CommandResponse.handled("No encontre ese hecho dentro del proyecto activo.");
        }
        return CommandResponse.handled("Hecho confirmado: " + fact.get().getFact() + " | tipo=" + fact.get().getFactType());
    }

    private CommandResponse handleReject(UUID projectId, String payload) {
        Optional<UUID> factId = CommandArgumentUtils.asUuid(payload);
        if (factId.isEmpty()) {
            return CommandResponse.handled("Debes indicar un id valido. Ejemplo: /hecho rechazar 123e4567-e89b-12d3-a456-426614174000");
        }

        Optional<AssistantProjectFact> fact = projectFactService.rejectFact(projectId, factId.get());
        if (fact.isEmpty()) {
            return CommandResponse.handled("No encontre ese hecho dentro del proyecto activo.");
        }
        return CommandResponse.handled("Hecho rechazado: " + fact.get().getFact() + " | tipo=" + fact.get().getFactType());
    }

    private CommandResponse handleAdd(UUID projectId, String payload) {
        if (payload.isBlank()) {
            return CommandResponse.handled("Debes indicar el texto del hecho. Ejemplo: /hecho add El proyecto usa PostgreSQL.");
        }

        Optional<AssistantProjectFact> fact = projectFactService.createProposedFact(projectId, payload, "manual-command", payload);
        if (fact.isEmpty()) {
            return CommandResponse.handled("Ese hecho ya existe o no es valido.");
        }

        return CommandResponse.handled("Hecho propuesto: " + fact.get().getFact());
    }
}