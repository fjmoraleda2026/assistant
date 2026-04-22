package com.assistant.infrastructure.persistence;

import com.assistant.domain.model.CommandCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommandCatalogRepository extends JpaRepository<CommandCatalog, UUID> {

    List<CommandCatalog> findByEnabledTrueOrderBySortOrderAscCommandKeyAsc();

    Optional<CommandCatalog> findByCommandKeyAndEnabledTrue(String commandKey);
}
