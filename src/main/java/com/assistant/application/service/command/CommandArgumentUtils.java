package com.assistant.application.service.command;

import java.util.Optional;
import java.util.UUID;

final class CommandArgumentUtils {

    private CommandArgumentUtils() {
    }

    static Optional<UUID> asUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
