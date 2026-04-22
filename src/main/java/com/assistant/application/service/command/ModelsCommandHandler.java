package com.assistant.application.service.command;

import com.assistant.application.service.AiModelCatalogService;
import com.assistant.application.service.AiRuntimeConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ModelsCommandHandler implements CommandHandler {

    private final AiRuntimeConfigService aiRuntimeConfigService;
    private final AiModelCatalogService aiModelCatalogService;

    @Value("${app.ai.provider:mock}")
    private String defaultAiProvider;

    @Value("${app.ai.model:gemini-2.0-flash}")
    private String defaultAiModel;

    public ModelsCommandHandler(
            AiRuntimeConfigService aiRuntimeConfigService,
            AiModelCatalogService aiModelCatalogService
    ) {
        this.aiRuntimeConfigService = aiRuntimeConfigService;
        this.aiModelCatalogService = aiModelCatalogService;
    }

    @Override
    public String commandKey() {
        return "modelos";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        String currentProvider = aiRuntimeConfigService.getProvider(request.chatId(), defaultAiProvider);
        String currentModel = aiRuntimeConfigService.getModel(request.chatId(), defaultAiModel);
        String defaultModel = aiModelCatalogService.defaultModelForProvider(currentProvider, defaultAiModel);
        String allowed = aiModelCatalogService.allowedModelsHint(currentProvider);

        return CommandResponse.handled(
                "Modelos para provider '" + currentProvider + "':\n"
                        + "  Permitidos: " + allowed + "\n"
                        + "  Default: " + defaultModel + "\n"
                        + "  Activo:  " + currentModel + "\n"
                        + "Uso: /modelo <nombre-modelo> | /modelo reset"
        );
    }
}
