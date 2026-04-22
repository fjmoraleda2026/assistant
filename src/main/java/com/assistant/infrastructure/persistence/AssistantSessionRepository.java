package com.assistant.infrastructure.persistence;

import com.assistant.domain.model.AssistantSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssistantSessionRepository extends JpaRepository<AssistantSession, UUID> {

    List<AssistantSession> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    Optional<AssistantSession> findByProjectIdAndNameIgnoreCase(UUID projectId, String name);

    Optional<AssistantSession> findByIdAndProjectId(UUID id, UUID projectId);
}
