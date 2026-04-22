package com.assistant.application.service.command;

import com.assistant.application.service.ConversationModeService;
import com.assistant.application.service.ProjectSessionContextService;
import com.assistant.domain.model.AssistantProject;
import com.assistant.domain.model.AssistantSession;
import com.assistant.infrastructure.persistence.AssistantProjectRepository;
import com.assistant.infrastructure.persistence.AssistantSessionRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class StatefulCommandHandler implements CommandHandler {

    private final ConversationModeService conversationModeService;
    private final ProjectSessionContextService projectSessionContextService;
    private final AssistantProjectRepository assistantProjectRepository;
    private final AssistantSessionRepository assistantSessionRepository;

    public StatefulCommandHandler(
            ConversationModeService conversationModeService,
            ProjectSessionContextService projectSessionContextService,
            AssistantProjectRepository assistantProjectRepository,
            AssistantSessionRepository assistantSessionRepository
    ) {
        this.conversationModeService = conversationModeService;
        this.projectSessionContextService = projectSessionContextService;
        this.assistantProjectRepository = assistantProjectRepository;
        this.assistantSessionRepository = assistantSessionRepository;
    }

    @Override
    public String commandKey() {
        return "stateful";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        conversationModeService.setSessionMode(request.chatId());

        AssistantProject activeProject = ensureActiveProject(request.chatId());
        AssistantSession activeSession = ensureActiveSession(request.chatId(), activeProject.getId());

        return CommandResponse.handled(
            "Modo session activado. Proyecto activo: "
                + activeProject.getName()
                + " | Sesion activa: "
                + activeSession.getName()
                + " (rol="
                + activeSession.getAiRole()
                + ")."
        );
    }

        private AssistantProject ensureActiveProject(String chatId) {
        Optional<AssistantProject> active = projectSessionContextService.getActiveProjectId(chatId)
            .flatMap(id -> assistantProjectRepository.findByIdAndChatId(id, chatId));
        if (active.isPresent()) {
            return active.get();
        }

        AssistantProject project = assistantProjectRepository.findByChatIdAndNameIgnoreCase(chatId, "default")
            .orElseGet(() -> assistantProjectRepository.save(
                AssistantProject.builder()
                    .chatId(chatId)
                    .name("default")
                    .description("Proyecto por defecto creado automaticamente")
                    .build()
            ));

        projectSessionContextService.setActiveProjectId(chatId, project.getId());
        return project;
        }

        private AssistantSession ensureActiveSession(String chatId, UUID projectId) {
        Optional<AssistantSession> active = projectSessionContextService.getActiveSessionId(chatId)
            .flatMap(assistantSessionRepository::findById)
            .filter(session -> projectId.equals(session.getProjectId()));
        if (active.isPresent()) {
            return active.get();
        }

        AssistantSession session = assistantSessionRepository.findByProjectIdAndNameIgnoreCase(projectId, "main")
            .map(existing -> {
                existing.setStatus("active");
                existing.setClosedAt(null);
                return assistantSessionRepository.save(existing);
            })
            .orElseGet(() -> assistantSessionRepository.save(
                AssistantSession.builder()
                    .projectId(projectId)
                    .name("main")
                    .aiRole("generic")
                    .status("active")
                    .build()
            ));

        projectSessionContextService.setActiveSessionId(chatId, session.getId());
        return session;
        }
}
