package com.assistant.application.service.command;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CommandHandlerRegistry {

    private final Map<String, CommandHandler> handlersByKey;

    public CommandHandlerRegistry(List<CommandHandler> handlers) {
        this.handlersByKey = handlers.stream()
                .collect(Collectors.toMap(
                        h -> normalize(h.commandKey()),
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    public Optional<CommandResponse> handle(CommandRequest request) {
        CommandHandler handler = handlersByKey.get(normalize(request.commandKey()));
        if (handler == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(handler.handle(request));
    }

    private String normalize(String commandKey) {
        return commandKey == null ? "" : commandKey.trim().toLowerCase(Locale.ROOT);
    }
}
