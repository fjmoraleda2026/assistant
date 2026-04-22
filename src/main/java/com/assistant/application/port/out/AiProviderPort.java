package com.assistant.application.port.out;

import com.assistant.domain.dto.AiToolResponse;

public interface AiProviderPort {
    String providerName();

    String generateResponse(String systemContext, String userPrompt, String model);

    default AiToolResponse generateResponseWithTools(String systemContext, String userPrompt, String model) {
        return AiToolResponse.withoutTools(generateResponse(systemContext, userPrompt, model));
    }
}




