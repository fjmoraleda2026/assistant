package com.assistant.application.port.out;

import java.util.List;

public interface ChatMessageStorePort {
    void appendMessage(String chatId, String message);

    Long size(String chatId);

    String popOldest(String chatId);

    List<String> range(String chatId, long start, long end);
}




