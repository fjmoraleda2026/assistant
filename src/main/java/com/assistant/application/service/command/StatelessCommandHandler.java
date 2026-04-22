package com.assistant.application.service.command;

import com.assistant.application.service.ConversationModeService;
import org.springframework.stereotype.Component;

@Component
public class StatelessCommandHandler implements CommandHandler {

    private final ConversationModeService conversationModeService;

    public StatelessCommandHandler(ConversationModeService conversationModeService) {
        this.conversationModeService = conversationModeService;
    }

    @Override
    public String commandKey() {
        return "stateless";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        conversationModeService.setStateless(request.chatId());
        return CommandResponse.handled(
                "Modo stateless activado. A partir de ahora no usare memoria conversacional hasta que ejecutes /stateful."
        );
    }
}
