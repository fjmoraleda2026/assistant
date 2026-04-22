package com.assistant.application.service.command;

import com.assistant.application.service.HelpCommandService;
import org.springframework.stereotype.Component;

@Component
public class HelpCommandHandler implements CommandHandler {

    private final HelpCommandService helpCommandService;

    public HelpCommandHandler(HelpCommandService helpCommandService) {
        this.helpCommandService = helpCommandService;
    }

    @Override
    public String commandKey() {
        return "help";
    }

    @Override
    public CommandResponse handle(CommandRequest request) {
        return CommandResponse.handled(helpCommandService.buildHelpMessage(request.chatId()));
    }
}
