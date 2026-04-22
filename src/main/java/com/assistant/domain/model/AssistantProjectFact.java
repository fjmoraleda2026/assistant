package com.assistant.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assistant_project_facts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistantProjectFact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String fact;

    @Enumerated(EnumType.STRING)
    @Column(name = "fact_type", nullable = false, length = 30)
    private FactType factType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FactStatus status;

    @Column(name = "confidence")
    private Integer confidence;

    @Column(name = "source", length = 120)
    private String source;

    @Column(name = "source_message", columnDefinition = "TEXT")
    private String sourceMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.factType == null) {
            this.factType = FactType.GENERIC;
        }
        if (this.status == null) {
            this.status = FactStatus.PROPOSED;
        }
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
