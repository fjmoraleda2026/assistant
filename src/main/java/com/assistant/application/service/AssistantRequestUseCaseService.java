/**
 * Objetivo: Orquestar el flujo entre IA (Gemini), Memoria y Herramientas.
 */
package com.assistant.application.service;

import com.assistant.application.port.in.HandleAssistantRequestUseCase;
import com.assistant.application.port.out.AiProviderPort;
import com.assistant.application.port.out.ConversationContextPort;
import com.assistant.application.port.out.OutgoingMessagePublisherPort;
import com.assistant.application.service.command.CommandHandlerRegistry;
import com.assistant.application.service.command.CommandRequest;
import com.assistant.application.service.command.CommandResponse;
import com.assistant.domain.model.AssistantProjectFact;
import com.assistant.domain.model.AssistantSession;
import com.assistant.infrastructure.persistence.AssistantSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantRequestUseCaseService implements HandleAssistantRequestUseCase {

    private final ConversationContextPort conversationContextPort;
    private final List<AiProviderPort> aiProviders;
    private final OutgoingMessagePublisherPort outgoingMessagePublisherPort;
    private final McpToolOrchestrationService mcpToolOrchestrationService;
    private final CommandHandlerRegistry commandHandlerRegistry;
    private final ConversationModeService conversationModeService;
    private final AiModelCatalogService aiModelCatalogService;
    private final AiRuntimeConfigService aiRuntimeConfigService;
    private final ProjectSessionContextService projectSessionContextService;
    private final AssistantSessionRepository assistantSessionRepository;
    private final RolePromptTemplateService rolePromptTemplateService;
    private final ProjectFactsContextService projectFactsContextService;
    private final ProjectFactService projectFactService;
    private final ProjectFactAiExtractionService projectFactAiExtractionService;

    @Value("${app.ai.provider:mock}")
    private String defaultAiProvider;

    @Value("${app.ai.model:gemini-2.0-flash}")
    private String defaultAiModel;

    @Value("${app.mcp.enabled:false}")
    private boolean mcpEnabled;

    @Override
    public void processRequest(String chatId, String prompt) {
        log.info("Procesando solicitud para chat: {}", chatId);

        try {
            Optional<CommandRequest> commandRequest = parseCommand(prompt, chatId);
            if (commandRequest.isPresent()) {
                Optional<CommandResponse> commandResponse = commandHandlerRegistry.handle(commandRequest.get());
                if (commandResponse.isPresent() && commandResponse.get().handled()) {
                    outgoingMessagePublisherPort.publishOutgoing(chatId, commandResponse.get().responseText());
                    return;
                }

                outgoingMessagePublisherPort.publishOutgoing(
                        chatId,
                        "Comando no reconocido. Usa /help para ver los comandos disponibles."
                );
                return;
            }

            boolean statelessMode = conversationModeService.isStateless(chatId);
            Optional<AssistantSession> activeSession = resolveActiveSession(chatId);
            Optional<UUID> activeProjectId = projectSessionContextService.getActiveProjectId(chatId);
            String memoryScopeId = resolveMemoryScopeId(chatId, activeSession);

            String selectedProviderName = aiRuntimeConfigService.getProvider(chatId, defaultAiProvider);
                String configuredModel = aiRuntimeConfigService.getModel(chatId, defaultAiModel);
                String selectedModel = aiModelCatalogService.resolveEffectiveModel(
                    selectedProviderName,
                    configuredModel,
                    defaultAiModel
                );
            AiProviderPort selectedProvider = resolveProvider(selectedProviderName);

boolean fallbackApplied = false;
            if (!selectedModel.equalsIgnoreCase(configuredModel)) {
                fallbackApplied = true;
                log.warn(
                        "Modelo incompatible detectado para chat {}: provider={} model_configurado={} model_efectivo={}",
                        chatId,
                        selectedProviderName,
                        configuredModel,
                        selectedModel
                );
                String fallbackNotification = "⚠️ Notificacion: El modelo '" + configuredModel + "' no es compatible con '" + selectedProviderName
                        + "'. Usando modelo default: '" + selectedModel + "'. Usa /modelo <nombre-modelo> para cambiar.";
                outgoingMessagePublisherPort.publishOutgoing(chatId, fallbackNotification);
                }

            if (!statelessMode && activeProjectId.isPresent()) {
                List<AssistantProjectFact> heuristicFacts = projectFactService.extractProposedFacts(activeProjectId.get(), prompt);
                projectFactService.storeProposedFacts(
                        activeProjectId.get(),
                        prompt,
                        projectFactAiExtractionService.extractCandidates(selectedProvider, selectedModel, prompt, heuristicFacts.size())
                );
            }

            // 1. Obtener contexto hibrido solo en modo session
            String context = "";
            if (!statelessMode) {
                context = conversationContextPort.getFullContext(memoryScopeId);
                context = injectRoleAndFactsContext(context, activeSession, activeProjectId);
                log.debug("Contexto recuperado de longitud: {}", context.length());
            } else {
                log.debug("Modo stateless activo para chat {}: se omite carga de memoria", chatId);
            }

            // 2. Resolver proveedor/modelo y enviar solicitud
            log.info("Enviando solicitud a IA provider={} model={} para chat={}", selectedProviderName, selectedModel, chatId);

            String aiResponse;
            if (mcpEnabled) {
                aiResponse = mcpToolOrchestrationService.processWithMcp(chatId, prompt, context, selectedProvider, selectedModel);
            } else {
                aiResponse = selectedProvider.generateResponse(context, prompt, selectedModel);
            }

            // 3. Actualizar memoria solo en modo session
            if (!statelessMode) {
                conversationContextPort.updateMemory(memoryScopeId, prompt, aiResponse);
                log.debug("Memoria actualizada para scope: {}", memoryScopeId);
            }

            // 4. Enviar respuesta de vuelta a Telegram vÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­a Kafka
            outgoingMessagePublisherPort.publishOutgoing(chatId, aiResponse);
            log.info("Respuesta enviada a Kafka para {}", chatId);

        } catch (Exception e) {
            log.error("Error procesando solicitud para {}: {}", chatId, e.getMessage(), e);
            String errorResponse = "Lo siento, hubo un error procesando tu solicitud. Por favor intenta de nuevo.";
            if (isQuotaError(e)) {
                errorResponse = "He alcanzado el limite de cuota de IA en este momento. Intenta de nuevo en 1 minuto o cambia de proveedor/modelo.";
            }
            outgoingMessagePublisherPort.publishOutgoing(chatId, errorResponse);
        }
    }

    private Optional<CommandRequest> parseCommand(String prompt, String chatId) {
        if (prompt == null) {
            return Optional.empty();
        }

        String trimmedPrompt = prompt.trim();
        if (trimmedPrompt.isEmpty() || !trimmedPrompt.startsWith("/")) {
            return Optional.empty();
        }

        String withoutSlash = trimmedPrompt.substring(1).trim();
        if (withoutSlash.isEmpty()) {
            return Optional.empty();
        }

        String[] parts = withoutSlash.split("\\s+", 2);
        String commandKey = parts[0].toLowerCase(Locale.ROOT);
        String arguments = parts.length > 1 ? parts[1] : "";
        return Optional.of(new CommandRequest(chatId, commandKey, arguments, prompt));
    }

    private AiProviderPort resolveProvider(String provider) {
        return aiProviders.stream()
                .filter(service -> service.providerName().equalsIgnoreCase(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("AI_PROVIDER_NOT_FOUND: " + provider));
    }

    private Optional<AssistantSession> resolveActiveSession(String chatId) {
        Optional<UUID> activeSessionId = projectSessionContextService.getActiveSessionId(chatId);
        if (activeSessionId.isEmpty()) {
            return Optional.empty();
        }

        Optional<AssistantSession> session = assistantSessionRepository.findById(activeSessionId.get());
        if (session.isEmpty()) {
            projectSessionContextService.clearActiveSessionId(chatId);
            return Optional.empty();
        }
        return session;
    }

    private String resolveMemoryScopeId(String chatId, Optional<AssistantSession> activeSession) {
        return activeSession
                .map(session -> "session:" + session.getId())
                .orElse(chatId);
    }

    private String injectRoleAndFactsContext(
            String context,
            Optional<AssistantSession> activeSession,
            Optional<UUID> activeProjectId
    ) {
        StringBuilder builder = new StringBuilder();

        if (activeSession.isPresent()) {
            String role = activeSession.get().getAiRole();
            String roleTemplate = rolePromptTemplateService.resolveTemplate(role);
            builder.append("PLANTILLA DE ROL ACTIVA:").append("\n")
                    .append(roleTemplate)
                    .append("\n\n");
        }

        if (activeProjectId.isPresent()) {
            builder.append(projectFactsContextService.buildConfirmedFactsBlock(activeProjectId.get()));
        }

        builder.append(context);
        return builder.toString();
    }

    private boolean isQuotaError(Exception e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("ai_quota_exceeded")
                    || normalized.contains("gemini_quota_exceeded")
                        || normalized.contains("http 429")
                        || normalized.contains("resource_exhausted")
                        || normalized.contains("quota")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}



