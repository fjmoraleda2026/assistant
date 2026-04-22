package com.assistant.infrastructure.persistence;

import com.assistant.domain.model.AssistantProjectFact;
import com.assistant.domain.model.FactStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssistantProjectFactRepository extends JpaRepository<AssistantProjectFact, UUID> {

    List<AssistantProjectFact> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    List<AssistantProjectFact> findByProjectIdAndStatusOrderByUpdatedAtDesc(UUID projectId, FactStatus status);

    Optional<AssistantProjectFact> findByIdAndProjectId(UUID id, UUID projectId);

    List<AssistantProjectFact> findByProjectIdAndSourceOrderByUpdatedAtDesc(UUID projectId, String source);

    boolean existsByProjectIdAndFactIgnoreCase(UUID projectId, String fact);

    long countByProjectIdAndStatus(UUID projectId, FactStatus status);
}
