package com.assistant.infrastructure.persistence;

import com.assistant.domain.model.SessionSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionSummaryRepository extends JpaRepository<SessionSummary, String> {
}
