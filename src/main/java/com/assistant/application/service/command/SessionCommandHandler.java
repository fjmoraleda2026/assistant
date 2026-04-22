package com.assistant.application.service.command;

import com.assistant.application.service.ProjectSessionContextService;
import com.assistant.domain.model.AssistantSession;
import com.assistant.infrastructure.persistence.AssistantSessionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class SessionCommandHandler implements CommandHandler {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "architect", "developer", "analyst", "personal_assistant", "reviewer", "devops", "generic"
    );

    private final ProjectSessionContextService projectSessionContextService;
    private final AssistantSessionRepository assistantSessionRepository;

    public SessionCommandHandler(
            ProjectSessionContextService projectSessionContextService,
            AssistantSessionRepository assistantSessionRepository
    ) {
        this.projectSessionContextService = projectSessionContextService;
        this.assistantSessionRepository = assistantSessionRepository;
    }

    @Override
    public String commandKey() {
        return "sesion";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        Optional<UUID> activeProjectId = projectSessionContextService.getActiveProjectId(request.chatId());
        if (activeProjectId.isEmpty()) {
            return CommandResponse.handled("No hay proyecto activo. Usa /proyecto <nombre|id> o /proyecto crear <nombre>.");
        }

        String args = request.arguments() == null ? "" : request.arguments().trim();
        if (args.isBlank()) {
            return CommandResponse.handled("Uso: /sesion nueva <nombre> [rol] | /sesion <nombre|id> | /sesion cerrar");
        }

        String argsLower = args.toLowerCase();
        if (argsLower.startsWith("nueva ")) {
            return createSession(activeProjectId.get(), request.chatId(), args.substring("nueva ".length()).trim());
        }
        if (argsLower.equals("cerrar")) {
            return closeActiveSession(request.chatId(), activeProjectId.get());
        }

        return selectSession(request.chatId(), activeProjectId.get(), args);
    }

    private CommandResponse createSession(UUID projectId, String chatId, String payload) {
        if (payload.isBlank()) {
            return CommandResponse.handled("Debes indicar nombre. Ejemplo: /sesion nueva sprint-1 developer");
        }

        String[] parts = payload.split("\\s+");
        String roleCandidate = parts.length > 1 ? parts[parts.length - 1].toLowerCase() : "generic";
        boolean hasRole = ALLOWED_ROLES.contains(roleCandidate);
        String role = hasRole ? roleCandidate : "generic";
        String name = hasRole
                ? payload.substring(0, payload.length() - roleCandidate.length()).trim()
                : payload;

        if (name.isBlank()) {
            return CommandResponse.handled("Nombre de sesion invalido. Ejemplo: /sesion nueva sprint-1 developer");
        }

        Optional<AssistantSession> existing = assistantSessionRepository.findByProjectIdAndNameIgnoreCase(projectId, name);
        AssistantSession session;
        if (existing.isPresent()) {
            session = existing.get();
            session.setStatus("active");
            session.setClosedAt(null);
            session = assistantSessionRepository.save(session);
        } else {
            session = assistantSessionRepository.save(
                    AssistantSession.builder()
                            .projectId(projectId)
                            .name(name)
                            .aiRole(role)
                            .status("active")
                            .build()
            );
        }

        projectSessionContextService.setActiveSessionId(chatId, session.getId());
        return CommandResponse.handled(
                "Sesion activa: " + session.getName() + " [" + session.getId() + "] role=" + session.getAiRole()
        );
    }

    private CommandResponse selectSession(String chatId, UUID projectId, String reference) {
        Optional<AssistantSession> byId = CommandArgumentUtils.asUuid(reference)
                .flatMap(id -> assistantSessionRepository.findByIdAndProjectId(id, projectId));

        Optional<AssistantSession> session = byId.isPresent()
                ? byId
                : assistantSessionRepository.findByProjectIdAndNameIgnoreCase(projectId, reference);

        if (session.isEmpty()) {
            return CommandResponse.handled("No encontre esa sesion. Usa /sesiones para ver las disponibles.");
        }

        projectSessionContextService.setActiveSessionId(chatId, session.get().getId());
        return CommandResponse.handled("Sesion activa: " + session.get().getName() + " role=" + session.get().getAiRole());
    }

    private CommandResponse closeActiveSession(String chatId, UUID projectId) {
        Optional<UUID> activeSessionId = projectSessionContextService.getActiveSessionId(chatId);
        if (activeSessionId.isEmpty()) {
            return CommandResponse.handled("No hay sesion activa para cerrar.");
        }

        Optional<AssistantSession> session = assistantSessionRepository.findByIdAndProjectId(activeSessionId.get(), projectId);
        if (session.isEmpty()) {
            projectSessionContextService.clearActiveSessionId(chatId);
            return CommandResponse.handled("La sesion activa no pertenece al proyecto actual. Se ha limpiado el contexto local.");
        }

        AssistantSession toClose = session.get();
        toClose.setStatus("closed");
        toClose.setClosedAt(LocalDateTime.now());
        assistantSessionRepository.save(toClose);
        projectSessionContextService.clearActiveSessionId(chatId);
        return CommandResponse.handled("Sesion cerrada: " + toClose.getName());
    }
}
