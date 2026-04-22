package com.assistant.infrastructure.adapter.out.persistence;

import com.assistant.domain.model.SessionSummary;
import com.assistant.application.port.out.SessionSummaryPersistencePort;
import com.assistant.infrastructure.persistence.SessionSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaSessionSummaryAdapter implements SessionSummaryPersistencePort {

    private final SessionSummaryRepository repository;

    @Override
    public Optional<SessionSummary> findById(String chatId) {
        return repository.findById(chatId);
    }

    @Override
    public SessionSummary save(SessionSummary summary) {
        return repository.save(summary);
    }
}




