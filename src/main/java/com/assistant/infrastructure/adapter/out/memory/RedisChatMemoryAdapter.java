package com.assistant.infrastructure.adapter.out.memory;

import com.assistant.application.port.out.ChatMessageStorePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisChatMemoryAdapter implements ChatMessageStorePort {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void appendMessage(String chatId, String message) {
        stringRedisTemplate.opsForList().rightPush(buildKey(chatId), message);
    }

    @Override
    public Long size(String chatId) {
        return stringRedisTemplate.opsForList().size(buildKey(chatId));
    }

    @Override
    public String popOldest(String chatId) {
        return stringRedisTemplate.opsForList().leftPop(buildKey(chatId));
    }

    @Override
    public List<String> range(String chatId, long start, long end) {
        return stringRedisTemplate.opsForList().range(buildKey(chatId), start, end);
    }

    private String buildKey(String chatId) {
        return "chat:" + chatId;
    }
}




