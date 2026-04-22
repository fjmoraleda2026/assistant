package com.assistant.application.service.command;

public record CommandRequest(
        String chatId,
        String commandKey,
        String arguments,
        String rawPrompt
) {
}
