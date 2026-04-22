package com.assistant.application.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class RolePromptTemplateService {

    private final Map<String, String> fallbackTemplates = new HashMap<>();

    public RolePromptTemplateService() {
        fallbackTemplates.put("architect", "Actua como arquitecto de software. Prioriza decisiones de diseño, trade-offs, escalabilidad y mantenibilidad.");
        fallbackTemplates.put("developer", "Actua como desarrollador. Prioriza soluciones implementables, pasos concretos y calidad de codigo.");
        fallbackTemplates.put("analyst", "Actua como analista funcional. Prioriza requisitos, reglas de negocio y criterios de aceptacion.");
        fallbackTemplates.put("personal_assistant", "Actua como asistente personal. Prioriza claridad, accion y comunicacion breve.");
        fallbackTemplates.put("reviewer", "Actua como revisor tecnico. Prioriza riesgos, defectos y recomendaciones verificables.");
        fallbackTemplates.put("devops", "Actua como especialista DevOps. Prioriza operacion, despliegue, observabilidad y fiabilidad.");
        fallbackTemplates.put("generic", "Actua como asistente tecnico util y preciso.");
    }

    public String resolveTemplate(String role) {
        String normalizedRole = normalize(role);
        String fromFile = loadFromFile(normalizedRole);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }
        return fallbackTemplates.getOrDefault(normalizedRole, fallbackTemplates.get("generic"));
    }

    private String loadFromFile(String role) {
        ClassPathResource resource = new ClassPathResource("prompts/roles/" + role + ".md");
        if (!resource.exists()) {
            return null;
        }
        try {
            byte[] bytes = resource.getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return null;
        }
    }

    private String normalize(String role) {
        if (role == null || role.isBlank()) {
            return "generic";
        }
        return role.trim().toLowerCase(Locale.ROOT);
    }
}
