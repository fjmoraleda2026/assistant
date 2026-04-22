package com.assistant.application.service.command;

import com.assistant.application.port.out.AiProviderPort;
import com.assistant.application.service.AiModelCatalogService;
import com.assistant.application.service.AiRuntimeConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Component
public class AiCommandHandler implements CommandHandler {

    private static final String RESET_TOKEN = "reset";

    private final AiRuntimeConfigService aiRuntimeConfigService;
    private final AiModelCatalogService aiModelCatalogService;
    private final Set<String> availableProviders;

    @Value("${app.ai.provider:mock}")
    private String defaultAiProvider;

    @Value("${app.ai.model:gemini-2.0-flash}")
    private String defaultAiModel;

    public AiCommandHandler(
            AiRuntimeConfigService aiRuntimeConfigService,
            AiModelCatalogService aiModelCatalogService,
            List<AiProviderPort> aiProviders
    ) {
        this.aiRuntimeConfigService = aiRuntimeConfigService;
        this.aiModelCatalogService = aiModelCatalogService;
        this.availableProviders = aiProviders.stream()
                .map(AiProviderPort::providerName)
                .map(name -> name == null ? "" : name.trim().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public String commandKey() {
        return "ia";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        String arguments = request.arguments() == null ? "" : request.arguments().trim();
        String currentProvider = aiRuntimeConfigService.getProvider(request.chatId(), defaultAiProvider);
        String currentModel = aiRuntimeConfigService.getModel(request.chatId(), defaultAiModel);

        if (arguments.isBlank() || isStatusRequest(arguments)) {
            return CommandResponse.handled(buildStatusMessage(currentProvider, currentModel));
        }

        if (RESET_TOKEN.equalsIgnoreCase(arguments)) {
            aiRuntimeConfigService.clearProvider(request.chatId());
            aiRuntimeConfigService.clearModel(request.chatId());
            String resetProvider = aiRuntimeConfigService.getProvider(request.chatId(), defaultAiProvider);
            String resetModel = aiModelCatalogService.defaultModelForProvider(resetProvider, defaultAiModel);
            return CommandResponse.handled(
                "Proveedor IA restablecido al default: '" + resetProvider + "'. Modelo: '" + resetModel + "'."
            );
        }

        String requestedProvider = arguments.toLowerCase(Locale.ROOT);
        if (!availableProviders.contains(requestedProvider)) {
            return CommandResponse.handled(
                    "Proveedor no valido: '" + arguments + "'. Disponibles: " + joinProviders() + "."
            );
        }

        aiRuntimeConfigService.setProvider(request.chatId(), requestedProvider);
        String newModel = aiModelCatalogService.defaultModelForProvider(requestedProvider, defaultAiModel);
        aiRuntimeConfigService.setModel(request.chatId(), newModel);
        return CommandResponse.handled(
            "Proveedor IA actualizado a '" + requestedProvider + "'. Modelo establecido: '" + newModel + "'."
        );
    }

    private boolean isStatusRequest(String arguments) {
        String normalized = arguments.trim().toLowerCase(Locale.ROOT);
        return "actual".equals(normalized) || "status".equals(normalized) || "ver".equals(normalized);
    }

    private String buildStatusMessage(String currentProvider, String currentModel) {
        return "IA actual: proveedor='" + currentProvider + "', modelo='" + currentModel + "'.\n"
                + "Proveedores disponibles: " + joinProviders() + ".\n"
                + "Uso: /ia <proveedor> | /ia reset";
    }

    private String buildCompatibilityWarning(String provider, String model) {
        if (aiModelCatalogService.isModelAllowed(provider, model)) {
            return "";
        }
        return "\nAviso: el modelo actual no es compatible con '" + provider + "'."
                + " Usa /modelo <nombre-modelo> o /modelo reset.";
    }

    private String joinProviders() {
        return String.join(", ", availableProviders);
    }
}
