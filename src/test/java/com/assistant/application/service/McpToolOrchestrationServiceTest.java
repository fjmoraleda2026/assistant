package com.assistant.application.service;

import com.assistant.application.port.out.AiProviderPort;
import com.assistant.application.port.out.ToolExecutorPort;
import com.assistant.domain.dto.AiToolCall;
import com.assistant.domain.dto.AiToolResponse;
import com.assistant.domain.model.AssistantProject;
import com.assistant.infrastructure.persistence.AssistantProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpToolOrchestrationServiceTest {

    private CountingToolExecutor toolExecutor;
    private McpToolOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        toolExecutor = new CountingToolExecutor();

        McpAuditService mcpAuditService = mock(McpAuditService.class);
        McpRuntimeConfigService mcpRuntimeConfigService = mock(McpRuntimeConfigService.class);
        McpRateLimitService mcpRateLimitService = mock(McpRateLimitService.class);
        ProjectSessionContextService projectSessionContextService = mock(ProjectSessionContextService.class);
        AssistantProjectRepository assistantProjectRepository = mock(AssistantProjectRepository.class);
        UUID projectId = UUID.randomUUID();

        // MCP always enabled, rate limit never exceeded in tests
        when(mcpRuntimeConfigService.isEnabled(anyString())).thenReturn(null);
        when(mcpRateLimitService.checkAndIncrement(anyString())).thenReturn(true);
        when(projectSessionContextService.getActiveProjectId(anyString())).thenReturn(Optional.of(projectId));
        when(assistantProjectRepository.findById(any(UUID.class))).thenReturn(Optional.of(
            AssistantProject.builder().id(projectId).chatId("chat-1").name("demo").basePath("C:/repo/demo").build()
        ));

        orchestrationService = new McpToolOrchestrationService(
            toolExecutor,
            mcpAuditService,
            mcpRateLimitService,
            mcpRuntimeConfigService,
            projectSessionContextService,
            assistantProjectRepository);

        ReflectionTestUtils.setField(orchestrationService, "mcpGlobalEnabled", true);
        ReflectionTestUtils.setField(orchestrationService, "maxIterations", 2);
        ReflectionTestUtils.setField(orchestrationService, "maxToolCalls", 2);
        ReflectionTestUtils.setField(orchestrationService, "toolTimeoutMs", 2000L);
        ReflectionTestUtils.setField(orchestrationService, "allowedTools", List.of("filesystem.read", "postgres.query"));
    }

    @Test
    void shouldExecuteToolAndReturnFinalAnswer() {
        AiProviderPort provider = new TwoStepProvider();

        String response = orchestrationService.processWithMcp(
                "chat-1",
                "leer archivo de configuracion",
                "contexto base",
                provider,
                "mock-model"
        );

        assertEquals("Respuesta final usando resultado MCP", response);
        assertEquals(1, toolExecutor.executionCount.get());
        assertNotNull(toolExecutor.lastArguments);
        assertEquals("C:/repo/demo", toolExecutor.lastArguments.get("basePath"));
    }

    @Test
    void shouldBlockToolOutsideWhitelist() {
        AiProviderPort provider = new ForbiddenToolProvider();

        String response = orchestrationService.processWithMcp(
                "chat-2",
                "ejecuta herramienta peligrosa",
                "contexto base",
                provider,
                "mock-model"
        );

        assertEquals("Respuesta final tras bloqueo", response);
        assertEquals(0, toolExecutor.executionCount.get());
        assertTrue(response.contains("bloqueo") || response.contains("final"));
    }

    @Test
    void shouldRecoverWhenMcpPlanningReturnsBlankWithoutTools() {
        AiProviderPort provider = new BlankPlanningProvider();

        String response = orchestrationService.processWithMcp(
                "chat-3",
                "hola",
                "contexto base",
                provider,
                "mock-model"
        );

        assertEquals("Respuesta directa recuperada", response);
        assertEquals(0, toolExecutor.executionCount.get());
    }

    private static class CountingToolExecutor implements ToolExecutorPort {
        private final AtomicInteger executionCount = new AtomicInteger();
        private Map<String, Object> lastArguments;

        @Override
        public String executeTool(String toolName, Map<String, Object> arguments) {
            executionCount.incrementAndGet();
            lastArguments = arguments;
            return "contenido leido de " + arguments.getOrDefault("path", "sin-path");
        }
    }

    private static class TwoStepProvider implements AiProviderPort {
        @Override
        public String providerName() {
            return "test";
        }

        @Override
        public String generateResponse(String systemContext, String userPrompt, String model) {
            if (userPrompt.contains("Resultados de herramientas MCP")) {
                return "Respuesta final usando resultado MCP";
            }
            return "respuesta simple";
        }

        @Override
        public AiToolResponse generateResponseWithTools(String systemContext, String userPrompt, String model) {
            if (userPrompt.contains("Resultados de herramientas MCP")) {
                return AiToolResponse.withoutTools("Respuesta final usando resultado MCP");
            }

            return new AiToolResponse(
                    "Voy a consultar tool",
                    List.of(new AiToolCall("filesystem.read", Map.of("path", "README_GEMINI.md")))
            );
        }
    }

    private static class ForbiddenToolProvider implements AiProviderPort {
        @Override
        public String providerName() {
            return "test";
        }

        @Override
        public String generateResponse(String systemContext, String userPrompt, String model) {
            if (userPrompt.contains("Resultados de herramientas MCP")) {
                return "Respuesta final tras bloqueo";
            }
            return "respuesta simple";
        }

        @Override
        public AiToolResponse generateResponseWithTools(String systemContext, String userPrompt, String model) {
            if (userPrompt.contains("Resultados de herramientas MCP")) {
                return AiToolResponse.withoutTools("Respuesta final tras bloqueo");
            }

            return new AiToolResponse(
                    "Intentare herramienta no permitida",
                    List.of(new AiToolCall("system.exec", Map.of("cmd", "whoami")))
            );
        }
    }

    private static class BlankPlanningProvider implements AiProviderPort {
        @Override
        public String providerName() {
            return "test";
        }

        @Override
        public String generateResponse(String systemContext, String userPrompt, String model) {
            return "Respuesta directa recuperada";
        }

        @Override
        public AiToolResponse generateResponseWithTools(String systemContext, String userPrompt, String model) {
            return AiToolResponse.withoutTools("");
        }
    }
}
