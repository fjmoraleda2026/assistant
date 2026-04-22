package com.assistant.application.service.command;

import com.assistant.application.service.ProjectSessionContextService;
import com.assistant.domain.model.AssistantSession;
import com.assistant.infrastructure.persistence.AssistantSessionRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SessionsCommandHandler implements CommandHandler {

    private final ProjectSessionContextService projectSessionContextService;
    private final AssistantSessionRepository assistantSessionRepository;

    public SessionsCommandHandler(
            ProjectSessionContextService projectSessionContextService,
            AssistantSessionRepository assistantSessionRepository
    ) {
        this.projectSessionContextService = projectSessionContextService;
        this.assistantSessionRepository = assistantSessionRepository;
    }

    @Override
    public String commandKey() {
        return "sesiones";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        Optional<UUID> activeProjectId = projectSessionContextService.getActiveProjectId(request.chatId());
        if (activeProjectId.isEmpty()) {
            return CommandResponse.handled("No hay proyecto activo. Usa /proyecto <nombre|id> o /proyecto crear <nombre>.");
        }

        List<AssistantSession> sessions = assistantSessionRepository.findByProjectIdOrderByUpdatedAtDesc(activeProjectId.get());
        if (sessions.isEmpty()) {
            return CommandResponse.handled("No hay sesiones en el proyecto activo. Crea una con /sesion nueva <nombre> [rol].");
        }

        Optional<UUID> activeSessionId = projectSessionContextService.getActiveSessionId(request.chatId());
        StringBuilder builder = new StringBuilder("Sesiones del proyecto activo:\n");
        for (AssistantSession session : sessions) {
            boolean active = activeSessionId.isPresent() && activeSessionId.get().equals(session.getId());
            builder.append(active ? "* " : "- ")
                    .append(session.getName())
                    .append(" [")
                    .append(session.getId())
                    .append("] role=")
                    .append(session.getAiRole())
                    .append(" status=")
                    .append(session.getStatus())
                    .append(active ? " (activa)" : "")
                    .append("\n");
        }
        builder.append("Usa /sesion <nombre|id> para activarla.");
        return CommandResponse.handled(builder.toString().trim());
    }
}
