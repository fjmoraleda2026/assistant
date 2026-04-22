package com.assistant.infrastructure.persistence;

import com.assistant.domain.model.AssistantProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssistantProjectRepository extends JpaRepository<AssistantProject, UUID> {

    List<AssistantProject> findByChatIdOrderByUpdatedAtDesc(String chatId);

    Optional<AssistantProject> findByChatIdAndNameIgnoreCase(String chatId, String name);

    Optional<AssistantProject> findByIdAndChatId(UUID id, String chatId);
}
