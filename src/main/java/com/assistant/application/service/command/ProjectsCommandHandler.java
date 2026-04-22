package com.assistant.application.service.command;

import com.assistant.application.service.ProjectSessionContextService;
import com.assistant.domain.model.AssistantProject;
import com.assistant.infrastructure.persistence.AssistantProjectRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProjectsCommandHandler implements CommandHandler {

    private final AssistantProjectRepository assistantProjectRepository;
    private final ProjectSessionContextService projectSessionContextService;

    public ProjectsCommandHandler(
            AssistantProjectRepository assistantProjectRepository,
            ProjectSessionContextService projectSessionContextService
    ) {
        this.assistantProjectRepository = assistantProjectRepository;
        this.projectSessionContextService = projectSessionContextService;
    }

    @Override
    public String commandKey() {
        return "proyectos";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        List<AssistantProject> projects = assistantProjectRepository.findByChatIdOrderByUpdatedAtDesc(request.chatId());
        if (projects.isEmpty()) {
            return CommandResponse.handled("No tienes proyectos. Crea uno con: /proyecto crear <nombre>");
        }

        Optional<UUID> activeProjectId = projectSessionContextService.getActiveProjectId(request.chatId());
        StringBuilder builder = new StringBuilder("Tus proyectos:\n");
        for (AssistantProject project : projects) {
            boolean active = activeProjectId.isPresent() && activeProjectId.get().equals(project.getId());
            builder.append(active ? "* " : "- ")
                    .append(project.getName())
                    .append(" [")
                    .append(project.getId())
                    .append("]")
                    .append(active ? " (activo)" : "");
            if (project.getBasePath() != null && !project.getBasePath().isBlank()) {
                builder.append(" ruta=").append(project.getBasePath());
            }
            if (project.getDatabaseName() != null && !project.getDatabaseName().isBlank()) {
                builder.append(" db=").append(project.getDatabaseName());
                if (project.getDatabaseSchema() != null && !project.getDatabaseSchema().isBlank()) {
                    builder.append("/").append(project.getDatabaseSchema());
                }
            }
            builder
                    .append("\n");
        }

        builder.append("Usa /proyecto <nombre|id>, /proyecto info, /proyecto ruta <path> o /proyecto bd <database> [schema].");
        return CommandResponse.handled(builder.toString().trim());
    }
}
