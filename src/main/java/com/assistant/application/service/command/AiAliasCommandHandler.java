package com.assistant.application.service.command;

import org.springframework.stereotype.Component;

@Component
public class AiAliasCommandHandler implements CommandHandler {

    private final AiCommandHandler aiCommandHandler;

    public AiAliasCommandHandler(AiCommandHandler aiCommandHandler) {
        this.aiCommandHandler = aiCommandHandler;
    }

    @Override
    public String commandKey() {
        return "ai";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        CommandRequest delegated = new CommandRequest(
                request.chatId(),
                "ia",
                request.arguments(),
                request.rawPrompt()
        );
        return aiCommandHandler.handle(delegated);
    }
}
