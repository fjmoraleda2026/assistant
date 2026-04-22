package com.assistant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiToolResponse {
    private String responseText;
    private List<AiToolCall> toolCalls = new ArrayList<>();

    public static AiToolResponse withoutTools(String responseText) {
        return new AiToolResponse(responseText, new ArrayList<>());
    }
}
