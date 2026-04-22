package com.assistant.infrastructure.adapter.out.ai.auth;

import com.assistant.infrastructure.adapter.out.ai.config.AiProviderAuthConfig;
import com.assistant.infrastructure.adapter.out.ai.config.AuthenticationResult;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

@Slf4j
@Component
public class AiAuthenticationSupport {

    private final OAuth2ClientCredentialsTokenService oauth2TokenService;

    public AiAuthenticationSupport(OAuth2ClientCredentialsTokenService oauth2TokenService) {
        this.oauth2TokenService = oauth2TokenService;
    }

    public AuthenticationResult authenticate(String providerLabel, AiProviderAuthConfig authConfig) {
        if (authConfig == null) {
            return AuthenticationResult.failure("AI_AUTH_CONFIG_MISSING", providerLabel + ": auth config missing");
        }

        return authenticate(
                providerLabel,
                authConfig.authMode(),
                authConfig.apiKey(),
                authConfig.oauth2Token(),
                authConfig.oauth2TokenUrl(),
                authConfig.oauth2ClientId(),
                authConfig.oauth2ClientSecret(),
                authConfig.oauth2Scope(),
                authConfig.oauth2Audience()
        );
    }

    private AuthenticationResult authenticate(
            String providerLabel,
            String authMode,
            String apiKey,
            String oauth2Token,
            String oauth2TokenUrl,
            String oauth2ClientId,
            String oauth2ClientSecret,
            String oauth2Scope,
            String oauth2Audience
    ) {
        String effectiveMode = authMode == null ? "api-key" : authMode.trim().toLowerCase(Locale.ROOT);
        try {
            switch (effectiveMode) {
                case "api-key" -> {
                    if (apiKey == null || apiKey.isBlank()) {
                        return AuthenticationResult.failure(providerLabel + "_API_KEY_MISSING", "API key not configured");
                    }
                    return AuthenticationResult.success(apiKey);
                }
                case "oauth2" -> {
                    return resolveOauth2BearerToken(
                            providerLabel,
                            oauth2Token,
                            oauth2TokenUrl,
                            oauth2ClientId,
                            oauth2ClientSecret,
                            oauth2Scope,
                            oauth2Audience
                    );
                }
                default -> {
                    return AuthenticationResult.failure(
                            "AI_AUTH_MODE_UNSUPPORTED",
                            providerLabel + ": unsupported auth mode '" + authMode + "'"
                    );
                }
            }
        } catch (Exception e) {
            log.warn("{}: Authentication failed: {}", providerLabel, e.getMessage());
            return AuthenticationResult.failure(
                    "AUTH_EXCEPTION",
                    providerLabel + ": " + e.getMessage()
            );
        }
    }

    public void applyAuthToHeaders(HttpHeaders headers, AuthenticationResult result) throws IllegalStateException {
        if (!result.isSuccess()) {
            throw new IllegalStateException(result.getErrorCode() + ": " + result.getErrorMessage());
        }
        headers.setBearerAuth(result.getToken());
    }

    private AuthenticationResult resolveOauth2BearerToken(
            String providerLabel,
            String oauth2Token,
            String oauth2TokenUrl,
            String oauth2ClientId,
            String oauth2ClientSecret,
            String oauth2Scope,
            String oauth2Audience
    ) {
        if (oauth2Token != null && !oauth2Token.isBlank()) {
            log.debug("{}: Using static OAuth2 token", providerLabel);
            return AuthenticationResult.success(oauth2Token);
        }

        try {
            String token = oauth2TokenService.resolveAccessToken(
                    providerLabel,
                    oauth2TokenUrl,
                    oauth2ClientId,
                    oauth2ClientSecret,
                    oauth2Scope,
                    oauth2Audience
            );
            log.debug("{}: OAuth2 token resolved successfully", providerLabel);
            return AuthenticationResult.success(token);
        } catch (Exception e) {
            return AuthenticationResult.failure(
                    "OAUTH2_TOKEN_RESOLUTION_FAILED",
                    providerLabel + ": " + e.getMessage()
            );
        }
    }
}
