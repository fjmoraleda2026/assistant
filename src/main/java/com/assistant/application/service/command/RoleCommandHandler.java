package com.assistant.application.service.command;

import com.assistant.application.service.ProjectSessionContextService;
import com.assistant.domain.model.AssistantSession;
import com.assistant.infrastructure.persistence.AssistantSessionRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class RoleCommandHandler implements CommandHandler {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "architect", "developer", "analyst", "personal_assistant", "reviewer", "devops", "generic"
    );

    private final ProjectSessionContextService projectSessionContextService;
    private final AssistantSessionRepository assistantSessionRepository;

    public RoleCommandHandler(
            ProjectSessionContextService projectSessionContextService,
            AssistantSessionRepository assistantSessionRepository
    ) {
        this.projectSessionContextService = projectSessionContextService;
        this.assistantSessionRepository = assistantSessionRepository;
    }

    @Override
    public String commandKey() {
        return "rol";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        String role = request.arguments() == null ? "" : request.arguments().trim().toLowerCase();
        if (!ALLOWED_ROLES.contains(role)) {
            return CommandResponse.handled(
                    "Rol invalido. Usa: architect | developer | analyst | personal_assistant | reviewer | devops | generic"
            );
        }

        Optional<UUID> activeSessionId = projectSessionContextService.getActiveSessionId(request.chatId());
        if (activeSessionId.isEmpty()) {
            return CommandResponse.handled("No hay sesion activa. Usa /sesion nueva <nombre> [rol] o /sesion <nombre|id>.");
        }

        Optional<AssistantSession> session = assistantSessionRepository.findById(activeSessionId.get());
        if (session.isEmpty()) {
            projectSessionContextService.clearActiveSessionId(request.chatId());
            return CommandResponse.handled("La sesion activa ya no existe. Selecciona una nueva con /sesion <nombre|id>.");
        }

        AssistantSession toUpdate = session.get();
        toUpdate.setAiRole(role);
        toUpdate.setStatus("active");
        assistantSessionRepository.save(toUpdate);

        return CommandResponse.handled("Rol actualizado a '" + role + "' para la sesion " + toUpdate.getName());
    }
}
