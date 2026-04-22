package com.assistant.application.service.command;

public interface CommandHandler {

    String commandKey();

    CommandResponse handle(CommandRequest request);
}
