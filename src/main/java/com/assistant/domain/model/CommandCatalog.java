package com.assistant.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "command_catalog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommandCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "command_key", nullable = false, unique = true, length = 120)
    private String commandKey;

    @Column(nullable = false, length = 240)
    private String syntax;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String example;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(name = "requires_project", nullable = false)
    private boolean requiresProject;

    @Column(name = "requires_session", nullable = false)
    private boolean requiresSession;

    @Column(name = "allowed_modes", nullable = false, length = 40)
    private String allowedModes;

    @Column(name = "min_role", length = 40)
    private String minRole;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
