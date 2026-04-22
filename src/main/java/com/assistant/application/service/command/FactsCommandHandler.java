package com.assistant.application.service.command;

import com.assistant.application.service.ProjectFactService;
import com.assistant.application.service.ProjectSessionContextService;
import com.assistant.domain.model.AssistantProjectFact;
import com.assistant.domain.model.FactStatus;
import com.assistant.domain.model.FactType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class FactsCommandHandler implements CommandHandler {

    private static final int MAX_LIST_SIZE = 10;

    private final ProjectSessionContextService projectSessionContextService;
    private final ProjectFactService projectFactService;

    public FactsCommandHandler(
            ProjectSessionContextService projectSessionContextService,
            ProjectFactService projectFactService
    ) {
        this.projectSessionContextService = projectSessionContextService;
        this.projectFactService = projectFactService;
    }

    @Override
    public String commandKey() {
        return "hechos";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        Optional<UUID> activeProjectId = projectSessionContextService.getActiveProjectId(request.chatId());
        if (activeProjectId.isEmpty()) {
            return CommandResponse.handled("No hay proyecto activo. Usa /proyecto <nombre|id> o /proyecto crear <nombre>.");
        }

        FactFilters filters = parseFilters(request.arguments());
        if (filters.invalid()) {
            return CommandResponse.handled(
                    "Uso: /hechos [propuestos|confirmados|rechazados] [stack|restriccion|rol|proveedor|modelo|database|arquitectura|generic]"
            );
        }

        if (filters.status().isPresent() || filters.factType().isPresent()) {
            List<AssistantProjectFact> filteredFacts = projectFactService.listFacts(
                    activeProjectId.get(),
                    filters.status(),
                    filters.factType(),
                    MAX_LIST_SIZE
            );
            return CommandResponse.handled(buildFilteredResponse(filteredFacts, filters));
        }

        List<AssistantProjectFact> proposedFacts = projectFactService.listFacts(activeProjectId.get(), FactStatus.PROPOSED, MAX_LIST_SIZE);
        List<AssistantProjectFact> confirmedFacts = projectFactService.listFacts(activeProjectId.get(), FactStatus.CONFIRMED, MAX_LIST_SIZE);

        StringBuilder builder = new StringBuilder("Hechos del proyecto:\n");
        appendSection(builder, "Propuestos", proposedFacts);
        appendSection(builder, "Confirmados", confirmedFacts);

        if (proposedFacts.isEmpty() && confirmedFacts.isEmpty()) {
            builder.append("No hay hechos registrados todavia.\n");
        }

        builder.append("Usa /hecho confirmar <id> o /hecho rechazar <id> para revisar candidatos.");
        return CommandResponse.handled(builder.toString().trim());
    }

    private String buildFilteredResponse(List<AssistantProjectFact> facts, FactFilters filters) {
        StringBuilder builder = new StringBuilder("Hechos filtrados");
        if (filters.status().isPresent() || filters.factType().isPresent()) {
            builder.append(" (");
            filters.status().ifPresent(value -> builder.append("estado=").append(value));
            if (filters.status().isPresent() && filters.factType().isPresent()) {
                builder.append(", ");
            }
            filters.factType().ifPresent(value -> builder.append("tipo=").append(value));
            builder.append(")");
        }
        builder.append(":\n");

        if (facts.isEmpty()) {
            builder.append("- ninguno\n");
        } else {
            appendSection(builder, "Resultados", facts);
        }

        builder.append("Uso rapido: /hechos confirmados | /hechos propuestos database");
        return builder.toString().trim();
    }

    private FactFilters parseFilters(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return new FactFilters(Optional.empty(), Optional.empty(), false);
        }

        Optional<FactStatus> status = Optional.empty();
        Optional<FactType> factType = Optional.empty();

        for (String token : arguments.trim().split("\\s+")) {
            Optional<FactStatus> parsedStatus = projectFactService.parseStatusFilter(token);
            if (parsedStatus.isPresent()) {
                if (status.isPresent() && status.get() != parsedStatus.get()) {
                    return new FactFilters(Optional.empty(), Optional.empty(), true);
                }
                status = parsedStatus;
                continue;
            }

            Optional<FactType> parsedType = projectFactService.parseTypeFilter(token);
            if (parsedType.isPresent()) {
                if (factType.isPresent() && factType.get() != parsedType.get()) {
                    return new FactFilters(Optional.empty(), Optional.empty(), true);
                }
                factType = parsedType;
                continue;
            }

            return new FactFilters(Optional.empty(), Optional.empty(), true);
        }

        return new FactFilters(status, factType, false);
    }

    private void appendSection(StringBuilder builder, String title, List<AssistantProjectFact> facts) {
        builder.append(title).append(":\n");
        if (facts.isEmpty()) {
            builder.append("- ninguno\n");
            return;
        }

        for (AssistantProjectFact fact : facts) {
            builder.append("- ")
                    .append(fact.getId())
                    .append(" | ")
                    .append(fact.getFactType())
                    .append(" | ")
                    .append(originLabel(fact.getSource()))
                    .append(" | ")
                    .append(fact.getFact());
            if (fact.getConfidence() != null) {
                builder.append(" | conf=").append(fact.getConfidence());
            }
            builder.append("\n");
        }
    }

    private String originLabel(String source) {
        if (source == null) return "?";
        return switch (source) {
            case "auto-user-text" -> "heuristic";
            case "ai-assisted-extraction" -> "ai";
            case "manual-command" -> "manual";
            default -> source;
        };
    }

    private record FactFilters(Optional<FactStatus> status, Optional<FactType> factType, boolean invalid) {
    }
}