package com.assistant.application.service;

import com.assistant.application.port.out.AiProviderPort;
import com.assistant.domain.model.FactType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectFactAiExtractionServiceTest {

    private ProjectFactAiExtractionService service;

    @BeforeEach
    void setUp() {
        service = new ProjectFactAiExtractionService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "minPromptLength", 10);
        ReflectionTestUtils.setField(service, "maxPromptLength", 1200);
        ReflectionTestUtils.setField(service, "skipIfHeuristicCount", 2);
    }

    @Test
    void shouldParseValidJsonWithSingleFact() {
        List<ProjectFactCandidate> candidates = service.extractCandidates(
                providerReturning("{\"facts\":[{\"fact\":\"El proyecto usa PostgreSQL\",\"factType\":\"DATABASE\",\"confidence\":90}]}"),
                "any-model", "el proyecto usa postgresql como base de datos", 0);

        assertEquals(1, candidates.size());
        assertEquals("El proyecto usa PostgreSQL", candidates.get(0).fact());
        assertEquals(FactType.DATABASE, candidates.get(0).factType());
        assertEquals(90, candidates.get(0).confidence());
    }

    @Test
    void shouldParseMultipleFactsFromJson() {
        String json = "{\"facts\":["
                + "{\"fact\":\"Usamos Spring Boot 3\",\"factType\":\"STACK\",\"confidence\":88},"
                + "{\"fact\":\"El proveedor activo es Grok\",\"factType\":\"PROVIDER\",\"confidence\":95}"
                + "]}";

        List<ProjectFactCandidate> candidates = service.extractCandidates(
                providerReturning(json), "model", "usamos spring boot y el proveedor activo es grok", 0);

        assertEquals(2, candidates.size());
        assertEquals(FactType.STACK, candidates.get(0).factType());
        assertEquals(FactType.PROVIDER, candidates.get(1).factType());
    }

    @Test
    void shouldReturnEmptyWhenJsonHasEmptyFacts() {
        List<ProjectFactCandidate> result = service.extractCandidates(
                providerReturning("{\"facts\":[]}"), "model", "hola como estas hoy", 0);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenResponseIsNotJson() {
        List<ProjectFactCandidate> result = service.extractCandidates(
                providerReturning("Lo siento, no entiendo la pregunta."), "model", "no es un hecho declarativo", 0);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenResponseIsMalformedJson() {
        List<ProjectFactCandidate> result = service.extractCandidates(
                providerReturning("{\"facts\":[{\"fact\":\"incompleto\""), "model", "mensaje valido de prueba", 0);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldExtractJsonEmbeddedInNarrativeText() {
        String response = "Aqui tienes los hechos extraidos:\n"
                + "{\"facts\":[{\"fact\":\"Sin OAuth2\",\"factType\":\"CONSTRAINT\",\"confidence\":95}]}\n"
                + "Espero que sirva.";

        List<ProjectFactCandidate> result = service.extractCandidates(
                providerReturning(response), "model", "no usamos oauth2 en este proyecto", 0);

        assertEquals(1, result.size());
        assertEquals("Sin OAuth2", result.get(0).fact());
        assertEquals(FactType.CONSTRAINT, result.get(0).factType());
    }

    @Test
    void shouldSkipWhenHeuristicCountMeetsThreshold() {
        AiProviderPort failingProvider = new AiProviderPort() {
            @Override
            public String providerName() { return "fail"; }
            @Override
            public String generateResponse(String sys, String user, String model) {
                throw new AssertionError("No deberia llamar a la IA si la heuristica ya encontro suficientes candidatos");
            }
        };

        List<ProjectFactCandidate> result = service.extractCandidates(
                failingProvider, "model", "usamos spring boot 3 con java 21", 2);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldCallAiWhenHeuristicCountIsBelowThreshold() {
        List<ProjectFactCandidate> result = service.extractCandidates(
                providerReturning("{\"facts\":[{\"fact\":\"Usamos Redis\",\"factType\":\"DATABASE\",\"confidence\":85}]}"),
                "model", "usamos redis para cache en sesiones", 1);

        assertEquals(1, result.size());
    }

    @Test
    void shouldNormalizeConfidenceBelowMinimumTo70() {
        List<ProjectFactCandidate> result = service.extractCandidates(
                providerReturning("{\"facts\":[{\"fact\":\"El modelo es llama\",\"factType\":\"MODEL\",\"confidence\":30}]}"),
                "model", "el modelo activo es llama 3", 0);

        assertEquals(70, result.get(0).confidence());
    }

    @Test
    void shouldFallbackToGenericForUnknownFactType() {
        List<ProjectFactCandidate> result = service.extractCandidates(
                providerReturning("{\"facts\":[{\"fact\":\"Algo desconocido\",\"factType\":\"UNKNOWN_TYPE\",\"confidence\":80}]}"),
                "model", "algo desconocido para el proyecto", 0);

        assertEquals(1, result.size());
        assertEquals(FactType.GENERIC, result.get(0).factType());
    }

    @Test
    void shouldReturnEmptyWhenDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);

        List<ProjectFactCandidate> result = service.extractCandidates(
                providerReturning("{\"facts\":[{\"fact\":\"Hecho\",\"factType\":\"STACK\",\"confidence\":90}]}"),
                "model", "usamos kafka en produccion", 0);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenPromptTooShort() {
        List<ProjectFactCandidate> result = service.extractCandidates(
                providerReturning("{\"facts\":[]}"), "model", "corto", 0);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenPromptIsCommand() {
        AiProviderPort failingProvider = new AiProviderPort() {
            @Override
            public String providerName() { return "fail"; }
            @Override
            public String generateResponse(String sys, String user, String model) {
                throw new AssertionError("No deberia llamar a la IA para comandos");
            }
        };

        List<ProjectFactCandidate> result = service.extractCandidates(
                failingProvider, "model", "/hechos confirmados", 0);

        assertTrue(result.isEmpty());
    }

    private AiProviderPort providerReturning(String response) {
        return new AiProviderPort() {
            @Override
            public String providerName() { return "test"; }
            @Override
            public String generateResponse(String sys, String user, String model) { return response; }
        };
    }
}
