package com.assistant.application.port.in;

import java.util.List;

public interface CompressConversationSummaryUseCase {
    void compress(String chatId, List<String> messages);
}




