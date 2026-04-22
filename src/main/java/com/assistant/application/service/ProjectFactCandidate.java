package com.assistant.application.service;

import com.assistant.domain.model.FactType;

public record ProjectFactCandidate(
        String fact,
        FactType factType,
        Integer confidence,
        String source
) {
}