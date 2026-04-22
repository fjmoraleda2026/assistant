package com.assistant.application.service;

import com.assistant.domain.model.AssistantProjectFact;
import com.assistant.domain.model.FactStatus;
import com.assistant.domain.model.FactType;
import com.assistant.infrastructure.persistence.AssistantProjectFactRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProjectFactService {

    private static final String EXTRACTION_SOURCE = "auto-user-text";
    private static final String PROJECT_BASE_PATH_SOURCE = "project-metadata:path";
    private static final String PROJECT_DATABASE_SOURCE = "project-metadata:database";
    private static final int MAX_SOURCE_MESSAGE_LENGTH = 2000;
    private static final int MAX_FACT_LENGTH = 240;
    private static final List<Pattern> STRONG_FACT_PATTERNS = List.of(
            Pattern.compile("(?i)^(usamos|utilizamos|trabajamos con|vamos a usar|usaremos)\\b.+"),
            Pattern.compile("(?i)^(el proyecto usa|la aplicacion usa|la aplicación usa)\\b.+"),
            Pattern.compile("(?i)^(la base de datos|la bd) es\\b.+"),
            Pattern.compile("(?i)^el proveedor( activo)? es\\b.+"),
            Pattern.compile("(?i)^el modelo es\\b.+"),
            Pattern.compile("(?i)^el rol( de la sesion| de la sesión)? es\\b.+"),
            Pattern.compile("(?i)^sin oauth2\\b.*"),
            Pattern.compile("(?i)^solo api ?key\\b.*"),
            Pattern.compile("(?i)^solo apikey\\b.*"),
            Pattern.compile("(?i)^se usar[aá]\\b.+")
    );
    private static final List<String> UNCERTAIN_MARKERS = List.of(
            "?",
            "¿",
            "quiz",
            "tal vez",
            "a lo mejor",
            "creo que",
            "podria",
            "podría",
            "podriamos",
            "podríamos",
            "deberia",
            "debería",
            "seria mejor",
            "sería mejor",
            "me gustaria",
            "me gustaría"
    );

    private final AssistantProjectFactRepository assistantProjectFactRepository;

    public ProjectFactService(AssistantProjectFactRepository assistantProjectFactRepository) {
        this.assistantProjectFactRepository = assistantProjectFactRepository;
    }

    public List<AssistantProjectFact> extractProposedFacts(UUID projectId, String userText) {
        if (projectId == null || userText == null || userText.isBlank()) {
            return List.of();
        }

        List<ProjectFactCandidate> heuristicCandidates = new ArrayList<>();
        for (String candidate : extractCandidates(userText)) {
            heuristicCandidates.add(new ProjectFactCandidate(
                    candidate,
                    classifyFactType(candidate),
                    scoreConfidence(candidate),
                    EXTRACTION_SOURCE
            ));
        }

        return storeProposedFacts(projectId, userText, heuristicCandidates);
    }

    public List<AssistantProjectFact> storeProposedFacts(UUID projectId, String sourceMessage, List<ProjectFactCandidate> candidates) {
        if (projectId == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<AssistantProjectFact> createdFacts = new ArrayList<>();
        for (ProjectFactCandidate candidate : candidates) {
            String normalizedFact = normalizeCandidate(candidate.fact());
            if (normalizedFact == null || assistantProjectFactRepository.existsByProjectIdAndFactIgnoreCase(projectId, normalizedFact)) {
                continue;
            }

            AssistantProjectFact savedFact = assistantProjectFactRepository.save(
                    AssistantProjectFact.builder()
                            .projectId(projectId)
                            .fact(normalizedFact)
                            .factType(candidate.factType() == null ? classifyFactType(normalizedFact) : candidate.factType())
                            .status(FactStatus.PROPOSED)
                            .confidence(candidate.confidence())
                            .source(truncate(candidate.source(), 120))
                            .sourceMessage(truncate(sourceMessage, MAX_SOURCE_MESSAGE_LENGTH))
                            .build()
            );
            createdFacts.add(savedFact);
        }

        return createdFacts;
    }

    public List<AssistantProjectFact> listFacts(UUID projectId, FactStatus status, int limit) {
        return listFacts(projectId, Optional.ofNullable(status), Optional.empty(), limit);
    }

    public List<AssistantProjectFact> listFacts(
            UUID projectId,
            Optional<FactStatus> status,
            Optional<FactType> factType,
            int limit
    ) {
        if (projectId == null || status == null || limit <= 0) {
            return List.of();
        }

        return assistantProjectFactRepository.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .map(this::reclassifyIfGeneric)
                .filter(fact -> status.map(value -> fact.getStatus() == value).orElse(true))
                .filter(fact -> factType.map(value -> fact.getFactType() == value).orElse(true))
                .limit(limit)
                .toList();
    }

    public Optional<AssistantProjectFact> confirmFact(UUID projectId, UUID factId) {
        return transitionFact(projectId, factId, FactStatus.CONFIRMED);
    }

    public Optional<AssistantProjectFact> rejectFact(UUID projectId, UUID factId) {
        return transitionFact(projectId, factId, FactStatus.REJECTED);
    }

    public Optional<AssistantProjectFact> createProposedFact(UUID projectId, String factText, String source, String sourceMessage) {
        String normalizedFact = normalizeCandidate(factText);
        if (projectId == null || normalizedFact == null) {
            return Optional.empty();
        }
        if (assistantProjectFactRepository.existsByProjectIdAndFactIgnoreCase(projectId, normalizedFact)) {
            return Optional.empty();
        }

        return Optional.of(assistantProjectFactRepository.save(
                AssistantProjectFact.builder()
                        .projectId(projectId)
                        .fact(normalizedFact)
                        .factType(classifyFactType(normalizedFact))
                        .status(FactStatus.PROPOSED)
                        .confidence(100)
                        .source(truncate(source, 120))
                        .sourceMessage(truncate(sourceMessage, MAX_SOURCE_MESSAGE_LENGTH))
                        .build()
        ));
    }

    public long countFacts(UUID projectId, FactStatus status) {
        if (projectId == null || status == null) {
            return 0;
        }
        return assistantProjectFactRepository.countByProjectIdAndStatus(projectId, status);
    }

    public void syncConfirmedProjectBasePathFact(UUID projectId, String basePath) {
        if (projectId == null) {
            return;
        }

        List<AssistantProjectFact> existingFacts = assistantProjectFactRepository
                .findByProjectIdAndSourceOrderByUpdatedAtDesc(projectId, PROJECT_BASE_PATH_SOURCE);

        String normalizedPath = normalizeProjectBasePath(basePath);
        if (normalizedPath == null) {
            if (!existingFacts.isEmpty()) {
                assistantProjectFactRepository.deleteAll(existingFacts);
            }
            return;
        }

        String factText = "La ruta base del proyecto es " + normalizedPath;
        if (!existingFacts.isEmpty()) {
            AssistantProjectFact primaryFact = existingFacts.getFirst();
            primaryFact.setFact(factText);
            primaryFact.setFactType(FactType.GENERIC);
            primaryFact.setStatus(FactStatus.CONFIRMED);
            primaryFact.setConfidence(100);
            primaryFact.setSourceMessage("Ruta base estructurada del proyecto");
            assistantProjectFactRepository.save(primaryFact);
            if (existingFacts.size() > 1) {
                assistantProjectFactRepository.deleteAll(existingFacts.subList(1, existingFacts.size()));
            }
            return;
        }

        assistantProjectFactRepository.save(
                AssistantProjectFact.builder()
                        .projectId(projectId)
                        .fact(factText)
                        .factType(FactType.GENERIC)
                        .status(FactStatus.CONFIRMED)
                        .confidence(100)
                        .source(PROJECT_BASE_PATH_SOURCE)
                        .sourceMessage("Ruta base estructurada del proyecto")
                        .build()
        );
    }

    public void syncConfirmedProjectDatabaseFact(UUID projectId, String databaseName, String databaseSchema) {
        if (projectId == null) {
            return;
        }

        List<AssistantProjectFact> existingFacts = assistantProjectFactRepository
                .findByProjectIdAndSourceOrderByUpdatedAtDesc(projectId, PROJECT_DATABASE_SOURCE);

        String normalizedDbName = normalizeProjectMetadata(databaseName, 120);
        String normalizedSchema = normalizeProjectMetadata(databaseSchema, 120);
        if (normalizedDbName == null) {
            if (!existingFacts.isEmpty()) {
                assistantProjectFactRepository.deleteAll(existingFacts);
            }
            return;
        }

        String factText = normalizedSchema == null
                ? "La base de datos del proyecto es " + normalizedDbName
                : "La base de datos del proyecto es " + normalizedDbName + " y el schema principal es " + normalizedSchema;

        if (!existingFacts.isEmpty()) {
            AssistantProjectFact primaryFact = existingFacts.getFirst();
            primaryFact.setFact(factText);
            primaryFact.setFactType(FactType.DATABASE);
            primaryFact.setStatus(FactStatus.CONFIRMED);
            primaryFact.setConfidence(100);
            primaryFact.setSourceMessage("Metadata estructurada de base de datos del proyecto");
            assistantProjectFactRepository.save(primaryFact);
            if (existingFacts.size() > 1) {
                assistantProjectFactRepository.deleteAll(existingFacts.subList(1, existingFacts.size()));
            }
            return;
        }

        assistantProjectFactRepository.save(
                AssistantProjectFact.builder()
                        .projectId(projectId)
                        .fact(factText)
                        .factType(FactType.DATABASE)
                        .status(FactStatus.CONFIRMED)
                        .confidence(100)
                        .source(PROJECT_DATABASE_SOURCE)
                        .sourceMessage("Metadata estructurada de base de datos del proyecto")
                        .build()
        );
    }

    private Optional<AssistantProjectFact> transitionFact(UUID projectId, UUID factId, FactStatus targetStatus) {
        if (projectId == null || factId == null || targetStatus == null) {
            return Optional.empty();
        }

        return assistantProjectFactRepository.findByIdAndProjectId(factId, projectId)
                .map(fact -> {
                    reclassifyIfGeneric(fact);
                    fact.setStatus(targetStatus);
                    return assistantProjectFactRepository.save(fact);
                });
    }

    public Optional<FactStatus> parseStatusFilter(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "propuestos", "propuesto", "pending", "pendientes", "pendiente" -> Optional.of(FactStatus.PROPOSED);
            case "confirmados", "confirmado", "confirmed" -> Optional.of(FactStatus.CONFIRMED);
            case "rechazados", "rechazado", "rejected" -> Optional.of(FactStatus.REJECTED);
            default -> Optional.empty();
        };
    }

    public Optional<FactType> parseTypeFilter(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "stack", "tecnologia", "tecnologías", "tecnologias" -> Optional.of(FactType.STACK);
            case "constraint", "constraints", "restriccion", "restricción", "restricciones" -> Optional.of(FactType.CONSTRAINT);
            case "rol", "role" -> Optional.of(FactType.ROLE);
            case "provider", "proveedor" -> Optional.of(FactType.PROVIDER);
            case "model", "modelo" -> Optional.of(FactType.MODEL);
            case "database", "db", "base-de-datos", "base de datos" -> Optional.of(FactType.DATABASE);
            case "architecture", "arquitectura" -> Optional.of(FactType.ARCHITECTURE);
            case "generic", "generico", "genérico" -> Optional.of(FactType.GENERIC);
            default -> Optional.empty();
        };
    }

    private List<String> extractCandidates(String userText) {
        String[] fragments = userText.split("(?:\\r?\\n)+|(?<=[.!;])\\s+");
        List<String> candidates = new ArrayList<>();
        for (String fragment : fragments) {
            String candidate = normalizeCandidate(fragment);
            if (candidate == null || !looksLikeStrongFact(candidate)) {
                continue;
            }
            candidates.add(candidate);
        }

        return candidates.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private boolean looksLikeStrongFact(String candidate) {
        String normalized = candidate.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) {
            return false;
        }
        for (String uncertainMarker : UNCERTAIN_MARKERS) {
            if (normalized.contains(uncertainMarker)) {
                return false;
            }
        }
        return STRONG_FACT_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(candidate).matches());
    }

    private Integer scoreConfidence(String factText) {
        String normalized = factText.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("la base de datos es")
                || normalized.startsWith("el proveedor es")
                || normalized.startsWith("el proveedor activo es")
                || normalized.startsWith("el modelo es")
                || normalized.startsWith("el rol de la sesion es")
                || normalized.startsWith("el rol de la sesión es")
                || normalized.startsWith("solo api key")
                || normalized.startsWith("solo apikey")
                || normalized.startsWith("sin oauth2")) {
            return 95;
        }
        return 85;
    }

    private FactType classifyFactType(String factText) {
        String normalized = factText.toLowerCase(Locale.ROOT);

        if (normalized.contains("postgres") || normalized.contains("mysql") || normalized.contains("oracle")
                || normalized.contains("sql server") || normalized.contains("mongodb") || normalized.contains("redis")
                || normalized.startsWith("la base de datos") || normalized.startsWith("la bd")) {
            return FactType.DATABASE;
        }

        if (normalized.contains("proveedor") || normalized.contains("grok") || normalized.contains("gemini")
                || normalized.contains("chatgpt") || normalized.contains("openai") || normalized.contains("deepseek")
                || normalized.contains("ollama")) {
            return FactType.PROVIDER;
        }

        if (normalized.contains("modelo") || normalized.contains("gpt-") || normalized.contains("claude")
                || normalized.contains("gemini-") || normalized.contains("llama")) {
            return FactType.MODEL;
        }

        if (normalized.contains("rol de la sesion") || normalized.contains("rol de la sesión")
                || normalized.startsWith("el rol es") || normalized.contains("architect")
                || normalized.contains("developer") || normalized.contains("analyst")
                || normalized.contains("reviewer") || normalized.contains("devops")
                || normalized.contains("personal_assistant") || normalized.contains("generic")) {
            return FactType.ROLE;
        }

        if (normalized.contains("hexagonal") || normalized.contains("clean architecture")
                || normalized.contains("arquitectura") || normalized.contains("ddd")) {
            return FactType.ARCHITECTURE;
        }

        if (normalized.contains("sin oauth2") || normalized.contains("solo api key")
                || normalized.contains("solo apikey") || normalized.contains("no usamos")
                || normalized.contains("restric") || normalized.contains("sin ")) {
            return FactType.CONSTRAINT;
        }

        if (normalized.contains("spring") || normalized.contains("java") || normalized.contains("kafka")
                || normalized.contains("telegram") || normalized.contains("mcp") || normalized.contains("postgresql")
                || normalized.contains("redis") || normalized.contains("webflux") || normalized.contains("hibernate")) {
            return FactType.STACK;
        }

        return FactType.GENERIC;
    }

    private AssistantProjectFact reclassifyIfGeneric(AssistantProjectFact fact) {
        if (fact == null || fact.getFact() == null || fact.getFactType() != FactType.GENERIC) {
            return fact;
        }

        FactType refreshedType = classifyFactType(fact.getFact());
        if (refreshedType == FactType.GENERIC) {
            return fact;
        }

        fact.setFactType(refreshedType);
        return assistantProjectFactRepository.save(fact);
    }

    private String normalizeCandidate(String rawText) {
        if (rawText == null) {
            return null;
        }

        String normalized = rawText
                .replace('\u0000', ' ')
                .replaceAll("^[\\s\\-•*]+", "")
                .replaceAll("\\s+", " ")
                .trim();

        while (!normalized.isEmpty() && ".,;:".indexOf(normalized.charAt(normalized.length() - 1)) >= 0) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }

        if (normalized.length() < 8 || normalized.length() > MAX_FACT_LENGTH) {
            return null;
        }

        return normalized;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String normalizeProjectBasePath(String basePath) {
        if (basePath == null) {
            return null;
        }

        String normalized = basePath.trim().replace('\\', '/');
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }

        if (normalized.isBlank() || normalized.length() > 240) {
            return null;
        }

        return normalized;
    }

    private String normalizeProjectMetadata(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank() || normalized.length() > maxLength) {
            return null;
        }
        return normalized;
    }
}