package com.assistant.infrastructure.adapter.out.ai.config;

public class AuthenticationResult {

    private final boolean success;
    private final String token;
    private final String errorCode;
    private final String errorMessage;

    private AuthenticationResult(boolean success, String token, String errorCode, String errorMessage) {
        this.success = success;
        this.token = token;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static AuthenticationResult success(String token) {
        return new AuthenticationResult(true, token, null, null);
    }

    public static AuthenticationResult failure(String errorCode, String errorMessage) {
        return new AuthenticationResult(false, null, errorCode, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getToken() {
        return token;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (success) {
            return "AuthenticationResult{success=true, tokenLength=" + (token != null ? token.length() : 0) + "}";
        }
        return "AuthenticationResult{success=false, errorCode='" + errorCode + "', errorMessage='" + errorMessage + "'}";
    }
}
