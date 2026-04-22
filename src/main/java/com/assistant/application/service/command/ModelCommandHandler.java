package com.assistant.application.service.command;

import com.assistant.application.service.AiModelCatalogService;
import com.assistant.application.service.AiRuntimeConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ModelCommandHandler implements CommandHandler {

    private static final String RESET_TOKEN = "reset";

    private final AiRuntimeConfigService aiRuntimeConfigService;
    private final AiModelCatalogService aiModelCatalogService;

    @Value("${app.ai.provider:mock}")
    private String defaultAiProvider;

    @Value("${app.ai.model:gemini-2.0-flash}")
    private String defaultAiModel;

    public ModelCommandHandler(
            AiRuntimeConfigService aiRuntimeConfigService,
            AiModelCatalogService aiModelCatalogService
    ) {
        this.aiRuntimeConfigService = aiRuntimeConfigService;
        this.aiModelCatalogService = aiModelCatalogService;
    }

    @Override
    public String commandKey() {
        return "modelo";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        String arguments = request.arguments() == null ? "" : request.arguments().trim();
        String currentProvider = aiRuntimeConfigService.getProvider(request.chatId(), defaultAiProvider);
        String currentModel = aiRuntimeConfigService.getModel(request.chatId(), defaultAiModel);

        if (arguments.isBlank()) {
            return CommandResponse.handled(
                    "Modelo actual: '" + currentModel + "' (provider='" + currentProvider + "').\n"
                            + "Uso: /modelo <nombre-modelo> | /modelo reset"
            );
        }

        if (RESET_TOKEN.equalsIgnoreCase(arguments)) {
            aiRuntimeConfigService.clearModel(request.chatId());
            String resetModel = aiRuntimeConfigService.getModel(request.chatId(), defaultAiModel);
            return CommandResponse.handled(
                    "Modelo IA restablecido al default: '" + resetModel + "'."
            );
        }

        if (!aiModelCatalogService.isModelAllowed(currentProvider, arguments)) {
            return CommandResponse.handled(
                    "Modelo no compatible con provider actual '" + currentProvider + "': '" + arguments + "'.\n"
                            + "Permitidos para este provider: " + aiModelCatalogService.allowedModelsHint(currentProvider) + ".\n"
                            + "Sugerencia: cambia provider con /ia <proveedor> o usa un modelo compatible."
            );
        }

        aiRuntimeConfigService.setModel(request.chatId(), arguments);
        return CommandResponse.handled(
                "Modelo IA actualizado a '" + arguments + "' para provider '" + currentProvider + "'."
        );
    }
}
