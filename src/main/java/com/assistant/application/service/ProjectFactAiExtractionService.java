package com.assistant.application.service;

import com.assistant.application.port.out.AiProviderPort;
import com.assistant.domain.model.FactType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class ProjectFactAiExtractionService {

    private static final String EXTRACTION_SOURCE = "ai-assisted-extraction";
    private static final String EXTRACTION_PROMPT = """
            Extrae solo hechos explicitos, estables y utiles para memoria de proyecto.
            Reglas:
            - Ignora preguntas, hipotesis, ideas tentativas o preferencias debiles.
            - Devuelve como maximo 3 hechos.
            - Cada hecho debe ser corto, declarativo y reutilizable.
            - Usa solo estos factType: STACK, CONSTRAINT, ROLE, PROVIDER, MODEL, DATABASE, ARCHITECTURE, GENERIC.
            - confidence debe ser un entero entre 70 y 100.
            - Responde UNICAMENTE con JSON valido con esta forma exacta:
            {
              "facts": [
                {
                  "fact": "El proyecto usa PostgreSQL",
                  "factType": "DATABASE",
                  "confidence": 92
                }
              ]
            }
            Si no hay hechos claros, responde {"facts":[]}.
            """;

    private final ObjectMapper objectMapper;

    @Value("${app.memory.fact-ai-extraction.enabled:true}")
    private boolean enabled;

    @Value("${app.memory.fact-ai-extraction.min-prompt-length:25}")
    private int minPromptLength;

    @Value("${app.memory.fact-ai-extraction.max-prompt-length:1200}")
    private int maxPromptLength;

    @Value("${app.memory.fact-ai-extraction.skip-if-heuristic-count:1}")
    private int skipIfHeuristicCount;

    public ProjectFactAiExtractionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ProjectFactCandidate> extractCandidates(AiProviderPort aiProvider, String model, String userText, int heuristicFactsFound) {
        if (!enabled || aiProvider == null || model == null || userText == null) {
            return List.of();
        }

        if (heuristicFactsFound >= skipIfHeuristicCount) {
            log.debug("Saltando extraccion IA: la heuristica ya encontro {} candidatos (umbral={})", heuristicFactsFound, skipIfHeuristicCount);
            return List.of();
        }

        String trimmed = userText.trim();
        if (trimmed.length() < minPromptLength || trimmed.length() > maxPromptLength || trimmed.startsWith("/")) {
            return List.of();
        }

        try {
            String response = aiProvider.generateResponse(EXTRACTION_PROMPT, trimmed, model);
            return parseCandidates(response);
        } catch (Exception exception) {
            log.debug("No se pudieron extraer hechos con IA: {}", exception.getMessage());
            return List.of();
        }
    }

    private List<ProjectFactCandidate> parseCandidates(String rawResponse) {
        String jsonCandidate = extractJsonObject(rawResponse);
        if (jsonCandidate == null) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonCandidate.replaceAll(",\\s*([}\\]])", "$1"));
            JsonNode factsNode = root.path("facts");
            if (!factsNode.isArray()) {
                return List.of();
            }

            List<ProjectFactCandidate> candidates = new ArrayList<>();
            for (JsonNode factNode : factsNode) {
                String fact = factNode.path("fact").asText("").trim();
                if (fact.isBlank()) {
                    continue;
                }

                String rawType = factNode.path("factType").asText("GENERIC").trim();
                FactType factType = parseFactType(rawType);
                int confidence = normalizeConfidence(factNode.path("confidence").asInt(80));
                candidates.add(new ProjectFactCandidate(fact, factType, confidence, EXTRACTION_SOURCE));
            }
            return candidates;
        } catch (Exception exception) {
            log.debug("La respuesta de extraccion de hechos no era JSON valido: {}", exception.getMessage());
            return List.of();
        }
    }

    private FactType parseFactType(String rawType) {
        try {
            return FactType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return FactType.GENERIC;
        }
    }

    private int normalizeConfidence(int confidence) {
        if (confidence < 70) {
            return 70;
        }
        return Math.min(confidence, 100);
    }

    private String extractJsonObject(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return null;
    }
}