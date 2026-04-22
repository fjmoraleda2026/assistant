package com.assistant.application.service;

import com.assistant.domain.model.AssistantProjectFact;
import com.assistant.domain.model.FactStatus;
import com.assistant.infrastructure.persistence.AssistantProjectFactRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectFactsContextService {

    private static final int MAX_FACTS = 15;

    private final AssistantProjectFactRepository assistantProjectFactRepository;

    public ProjectFactsContextService(AssistantProjectFactRepository assistantProjectFactRepository) {
        this.assistantProjectFactRepository = assistantProjectFactRepository;
    }

    public String buildConfirmedFactsBlock(UUID projectId) {
        if (projectId == null) {
            return "";
        }

        List<AssistantProjectFact> facts = assistantProjectFactRepository
            .findByProjectIdAndStatusOrderByUpdatedAtDesc(projectId, FactStatus.CONFIRMED);

        if (facts.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder("HECHOS CONFIRMADOS DEL PROYECTO:\n");
        int count = 0;
        for (AssistantProjectFact fact : facts) {
            if (fact.getFact() == null || fact.getFact().isBlank()) {
                continue;
            }
            builder.append("- ").append(fact.getFact().trim()).append("\n");
            count++;
            if (count >= MAX_FACTS) {
                break;
            }
        }

        if (count == 0) {
            return "";
        }

        return builder.append("\n").toString();
    }
}
