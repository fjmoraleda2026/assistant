package com.assistant.application.port.out;

import com.assistant.domain.model.SessionSummary;

import java.util.Optional;

public interface SessionSummaryPersistencePort {
    Optional<SessionSummary> findById(String chatId);

    SessionSummary save(SessionSummary summary);
}




