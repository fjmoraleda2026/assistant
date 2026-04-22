package com.assistant.application.service.command;

public record CommandResponse(
        boolean handled,
        String responseText
) {

    public static CommandResponse handled(String responseText) {
        return new CommandResponse(true, responseText);
    }

    public static CommandResponse notHandled() {
        return new CommandResponse(false, null);
    }
}
