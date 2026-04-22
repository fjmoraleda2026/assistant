package com.assistant.application.service;

import com.assistant.domain.model.AssistantProjectFact;
import com.assistant.domain.model.FactStatus;
import com.assistant.domain.model.FactType;
import com.assistant.infrastructure.persistence.AssistantProjectFactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectFactServiceDeduplicationTest {

    private AssistantProjectFactRepository repository;
    private ProjectFactService service;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        repository = mock(AssistantProjectFactRepository.class);
        service = new ProjectFactService(repository);
        projectId = UUID.randomUUID();
    }

    @Test
    void shouldStoreCandidateWhenItDoesNotExist() {
        when(repository.existsByProjectIdAndFactIgnoreCase(eq(projectId), any())).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<AssistantProjectFact> result = service.storeProposedFacts(projectId, "fuente", List.of(
                new ProjectFactCandidate("El proyecto usa PostgreSQL", FactType.DATABASE, 90, "test")
        ));

        assertEquals(1, result.size());
        verify(repository, times(1)).save(any());
    }

    @Test
    void shouldSkipDuplicateFact() {
        when(repository.existsByProjectIdAndFactIgnoreCase(eq(projectId), eq("El proyecto usa PostgreSQL"))).thenReturn(true);

        List<AssistantProjectFact> result = service.storeProposedFacts(projectId, "fuente", List.of(
                new ProjectFactCandidate("El proyecto usa PostgreSQL", FactType.DATABASE, 90, "test")
        ));

        assertTrue(result.isEmpty());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldSkipCandidateWithNullOrBlankFact() {
        List<AssistantProjectFact> resultNull = service.storeProposedFacts(projectId, "fuente", List.of(
                new ProjectFactCandidate(null, FactType.GENERIC, 80, "test")
        ));
        List<AssistantProjectFact> resultBlank = service.storeProposedFacts(projectId, "fuente", List.of(
                new ProjectFactCandidate("   ", FactType.GENERIC, 80, "test")
        ));

        assertTrue(resultNull.isEmpty());
        assertTrue(resultBlank.isEmpty());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldSkipFactTooShort() {
        List<AssistantProjectFact> result = service.storeProposedFacts(projectId, "fuente", List.of(
                new ProjectFactCandidate("corto", FactType.GENERIC, 80, "test")
        ));

        assertTrue(result.isEmpty());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldStoreMixedCandidatesPartially() {
        UUID projectId = this.projectId;
        when(repository.existsByProjectIdAndFactIgnoreCase(eq(projectId), eq("El proyecto usa PostgreSQL"))).thenReturn(true);
        when(repository.existsByProjectIdAndFactIgnoreCase(eq(projectId), eq("Usamos Kafka para mensajeria"))).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<AssistantProjectFact> result = service.storeProposedFacts(projectId, "fuente", List.of(
                new ProjectFactCandidate("El proyecto usa PostgreSQL", FactType.DATABASE, 90, "test"),
                new ProjectFactCandidate("Usamos Kafka para mensajeria", FactType.STACK, 85, "test")
        ));

        assertEquals(1, result.size());
        verify(repository, times(1)).save(any());
    }

    @Test
    void shouldPersistProposedStatusAndCorrectSource() {
        when(repository.existsByProjectIdAndFactIgnoreCase(any(), any())).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.storeProposedFacts(projectId, "mensaje original del usuario", List.of(
                new ProjectFactCandidate("Usamos arquitectura hexagonal", FactType.ARCHITECTURE, 88, "ai-assisted-extraction")
        ));

        ArgumentCaptor<AssistantProjectFact> captor = ArgumentCaptor.forClass(AssistantProjectFact.class);
        verify(repository).save(captor.capture());
        AssistantProjectFact saved = captor.getValue();

        assertEquals(FactStatus.PROPOSED, saved.getStatus());
        assertEquals("ai-assisted-extraction", saved.getSource());
        assertEquals(FactType.ARCHITECTURE, saved.getFactType());
        assertEquals(88, saved.getConfidence());
    }

    @Test
    void shouldReturnEmptyForNullProjectId() {
        List<AssistantProjectFact> result = service.storeProposedFacts(null, "fuente", List.of(
                new ProjectFactCandidate("Hecho valido de prueba", FactType.STACK, 85, "test")
        ));

        assertTrue(result.isEmpty());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldReturnEmptyForEmptyCandidates() {
        List<AssistantProjectFact> result = service.storeProposedFacts(projectId, "fuente", List.of());

        assertTrue(result.isEmpty());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldCreateConfirmedDatabaseFactFromStructuredMetadata() {
        when(repository.findByProjectIdAndSourceOrderByUpdatedAtDesc(projectId, "project-metadata:database")).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.syncConfirmedProjectDatabaseFact(projectId, "assistant_pro", "public");

        ArgumentCaptor<AssistantProjectFact> captor = ArgumentCaptor.forClass(AssistantProjectFact.class);
        verify(repository).save(captor.capture());
        AssistantProjectFact saved = captor.getValue();

        assertEquals(FactStatus.CONFIRMED, saved.getStatus());
        assertEquals(FactType.DATABASE, saved.getFactType());
        assertEquals("project-metadata:database", saved.getSource());
        assertEquals("La base de datos del proyecto es assistant_pro y el schema principal es public", saved.getFact());
    }
}
