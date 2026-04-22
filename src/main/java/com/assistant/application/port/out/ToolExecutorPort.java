package com.assistant.application.port.out;

import java.util.Map;

public interface ToolExecutorPort {
    String executeTool(String toolName, Map<String, Object> arguments);
}




