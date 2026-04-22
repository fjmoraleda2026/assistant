package com.assistant.application.service.command;

import com.assistant.application.service.ProjectSessionContextService;
import com.assistant.application.service.ProjectFactService;
import com.assistant.domain.model.AssistantProject;
import com.assistant.infrastructure.persistence.AssistantProjectRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ProjectCommandHandler implements CommandHandler {

    private final AssistantProjectRepository assistantProjectRepository;
    private final ProjectSessionContextService projectSessionContextService;
    private final ProjectFactService projectFactService;

    public ProjectCommandHandler(
            AssistantProjectRepository assistantProjectRepository,
            ProjectSessionContextService projectSessionContextService,
            ProjectFactService projectFactService
    ) {
        this.assistantProjectRepository = assistantProjectRepository;
        this.projectSessionContextService = projectSessionContextService;
        this.projectFactService = projectFactService;
    }

    @Override
    public String commandKey() {
        return "proyecto";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        String args = request.arguments() == null ? "" : request.arguments().trim();
        if (args.isBlank()) {
            return CommandResponse.handled("Uso: /proyecto crear <nombre>, /proyecto <nombre|id>, /proyecto info, /proyecto ruta <path> o /proyecto bd <database> [schema]");
        }

        if ("info".equalsIgnoreCase(args)) {
            return projectInfo(request.chatId());
        }

        if (args.toLowerCase().startsWith("crear ")) {
            return createProject(request.chatId(), args.substring("crear ".length()).trim());
        }

        if (args.toLowerCase().startsWith("ruta ")) {
            return updateProjectPath(request.chatId(), args.substring("ruta ".length()).trim());
        }

        if (args.toLowerCase().startsWith("bd ")) {
            return updateProjectDatabase(request.chatId(), args.substring("bd ".length()).trim());
        }

        return selectProject(request.chatId(), args);
    }

    private CommandResponse createProject(String chatId, String name) {
        if (name.isBlank()) {
            return CommandResponse.handled("Debes indicar un nombre. Ejemplo: /proyecto crear backend-memoria");
        }

        Optional<AssistantProject> existing = assistantProjectRepository.findByChatIdAndNameIgnoreCase(chatId, name);
        if (existing.isPresent()) {
            projectSessionContextService.setActiveProjectId(chatId, existing.get().getId());
            projectSessionContextService.clearActiveSessionId(chatId);
            return CommandResponse.handled("El proyecto ya existia y se ha activado: " + existing.get().getName());
        }

        AssistantProject project = assistantProjectRepository.save(
                AssistantProject.builder()
                        .chatId(chatId)
                        .name(name)
                        .build()
        );

        projectSessionContextService.setActiveProjectId(chatId, project.getId());
        projectSessionContextService.clearActiveSessionId(chatId);
        return CommandResponse.handled(
                "Proyecto creado y activado: " + project.getName() + " [" + project.getId() + "]"
        );
    }

    private CommandResponse selectProject(String chatId, String reference) {
        Optional<AssistantProject> byId = CommandArgumentUtils.asUuid(reference)
                .flatMap(id -> assistantProjectRepository.findByIdAndChatId(id, chatId));

        Optional<AssistantProject> project = byId.isPresent()
                ? byId
                : assistantProjectRepository.findByChatIdAndNameIgnoreCase(chatId, reference);

        if (project.isEmpty()) {
            return CommandResponse.handled("No encontre ese proyecto. Usa /proyectos para ver los disponibles.");
        }

        projectSessionContextService.setActiveProjectId(chatId, project.get().getId());
        projectSessionContextService.clearActiveSessionId(chatId);
        return CommandResponse.handled("Proyecto activo: " + project.get().getName());
    }

    private CommandResponse updateProjectPath(String chatId, String rawPath) {
        Optional<AssistantProject> activeProject = resolveActiveProject(chatId);
        if (activeProject.isEmpty()) {
            return noActiveProjectResponse(chatId);
        }

        String normalizedPath = normalizeProjectPath(rawPath);
        if (normalizedPath == null) {
            return CommandResponse.handled("Debes indicar una ruta valida. Ejemplo: /proyecto ruta C:/Proyectos/mi-app");
        }

        AssistantProject project = activeProject.get();
        project.setBasePath(normalizedPath);
        assistantProjectRepository.save(project);
        projectFactService.syncConfirmedProjectBasePathFact(project.getId(), normalizedPath);
        return CommandResponse.handled("Ruta base actualizada para '" + project.getName() + "': " + normalizedPath);
    }

    private CommandResponse updateProjectDatabase(String chatId, String rawValue) {
        Optional<AssistantProject> activeProject = resolveActiveProject(chatId);
        if (activeProject.isEmpty()) {
            return noActiveProjectResponse(chatId);
        }

        String[] parts = rawValue == null ? new String[0] : rawValue.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) {
            return CommandResponse.handled("Debes indicar la base de datos. Ejemplo: /proyecto bd assistant_pro public");
        }

        String databaseName = normalizeProjectMetadata(parts[0], 120);
        String schema = parts.length > 1 ? normalizeProjectMetadata(parts[1], 120) : null;
        if (databaseName == null) {
            return CommandResponse.handled("Nombre de base de datos invalido. Ejemplo: /proyecto bd assistant_pro public");
        }

        AssistantProject project = activeProject.get();
        project.setDatabaseName(databaseName);
        project.setDatabaseSchema(schema);
        assistantProjectRepository.save(project);
        projectFactService.syncConfirmedProjectDatabaseFact(project.getId(), databaseName, schema);

        String suffix = schema == null ? "" : " (schema=" + schema + ")";
        return CommandResponse.handled("Metadata de BD actualizada para '" + project.getName() + "': " + databaseName + suffix);
    }

    private CommandResponse projectInfo(String chatId) {
        Optional<AssistantProject> activeProject = resolveActiveProject(chatId);
        if (activeProject.isEmpty()) {
            return noActiveProjectResponse(chatId);
        }

        AssistantProject project = activeProject.get();
        StringBuilder builder = new StringBuilder("Proyecto activo:\n");
        builder.append("- nombre: ").append(project.getName()).append("\n");
        builder.append("- id: ").append(project.getId()).append("\n");
        builder.append("- ruta: ").append(project.getBasePath() == null || project.getBasePath().isBlank() ? "no configurada" : project.getBasePath()).append("\n");
        builder.append("- base de datos: ").append(project.getDatabaseName() == null || project.getDatabaseName().isBlank() ? "no configurada" : project.getDatabaseName()).append("\n");
        builder.append("- schema: ").append(project.getDatabaseSchema() == null || project.getDatabaseSchema().isBlank() ? "no configurado" : project.getDatabaseSchema()).append("\n");
        builder.append("Uso: /proyecto ruta <path> | /proyecto bd <database> [schema]");
        return CommandResponse.handled(builder.toString().trim());
    }

    private Optional<AssistantProject> resolveActiveProject(String chatId) {
        Optional<UUID> activeProjectId = projectSessionContextService.getActiveProjectId(chatId);
        if (activeProjectId.isEmpty()) {
            return Optional.empty();
        }
        return assistantProjectRepository.findByIdAndChatId(activeProjectId.get(), chatId);
    }

    private CommandResponse noActiveProjectResponse(String chatId) {
        if (projectSessionContextService.getActiveProjectId(chatId).isEmpty()) {
            return CommandResponse.handled("No hay proyecto activo. Selecciona uno con /proyecto <nombre|id>.");
        }
        return CommandResponse.handled("El proyecto activo ya no existe. Usa /proyectos para elegir otro.");
    }

    private String normalizeProjectPath(String rawPath) {
        if (rawPath == null) {
            return null;
        }

        String normalized = rawPath.trim().replace('\\', '/');
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }

        if (normalized.isBlank() || normalized.length() > 512) {
            return null;
        }

        return normalized;
    }

    private String normalizeProjectMetadata(String rawValue, int maxLength) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank() || normalized.length() > maxLength) {
            return null;
        }
        return normalized;
    }
}
