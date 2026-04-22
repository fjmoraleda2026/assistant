/**
 * Objetivo: Entidad para el almacenamiento persistente del resumen de la sesión.
 * Usuario: JPA / Hibernate / PostgreSQL.
 * Casos de Uso: Recuperación de contexto histórico cuando la memoria en Redis expira.
 */
package com.assistant.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "session_summaries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class SessionSummary {

    @Id
    @Column(name = "chat_id", length = 50)
    private String chatId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(name = "last_update")
    private LocalDateTime lastUpdate;

    @Column(name = "message_count_processed")
    private Long messageCountProcessed;
}
