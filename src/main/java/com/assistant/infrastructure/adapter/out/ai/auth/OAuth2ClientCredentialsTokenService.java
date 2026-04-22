package com.assistant.infrastructure.adapter.out.ai.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OAuth2ClientCredentialsTokenService {

    private static final long DEFAULT_EXPIRES_IN_SECONDS = 3600L;
    private static final long REFRESH_SKEW_SECONDS = 30L;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public OAuth2ClientCredentialsTokenService(ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().build();
        this.objectMapper = objectMapper;
    }

    public String resolveAccessToken(
            String providerLabel,
            String tokenUrl,
            String clientId,
            String clientSecret,
            String scope,
            String audience
    ) {
        String nonBlankTokenUrl = requireNonBlank(tokenUrl, providerLabel + "_OAUTH2_TOKEN_URL_MISSING");
        String nonBlankClientId = requireNonBlank(clientId, providerLabel + "_OAUTH2_CLIENT_ID_MISSING");
        String nonBlankClientSecret = requireNonBlank(clientSecret, providerLabel + "_OAUTH2_CLIENT_SECRET_MISSING");

        String cacheKey = providerLabel + "|" + nonBlankTokenUrl + "|" + nonBlankClientId + "|"
                + normalize(scope) + "|" + normalize(audience);

        CachedToken cached = tokenCache.get(cacheKey);
        long now = Instant.now().getEpochSecond();
        if (cached != null && cached.expiresAtEpochSecond - REFRESH_SKEW_SECONDS > now) {
            return cached.accessToken;
        }

        return refreshToken(cacheKey, nonBlankTokenUrl, nonBlankClientId, nonBlankClientSecret, scope, audience);
    }

    private synchronized String refreshToken(
            String cacheKey,
            String tokenUrl,
            String clientId,
            String clientSecret,
            String scope,
            String audience
    ) {
        CachedToken cached = tokenCache.get(cacheKey);
        long now = Instant.now().getEpochSecond();
        if (cached != null && cached.expiresAtEpochSecond - REFRESH_SKEW_SECONDS > now) {
            return cached.accessToken;
        }

        String formBody = buildFormBody(clientId, clientSecret, scope, audience);
        try {
            String rawResponse = restClient.post()
                    .uri(tokenUrl)
                    .headers(headers -> headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .body(formBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(rawResponse);
            String accessToken = root.path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw new IllegalStateException("OAUTH2_ACCESS_TOKEN_EMPTY");
            }

            long expiresIn = root.path("expires_in").asLong(DEFAULT_EXPIRES_IN_SECONDS);
            long expiresAt = Instant.now().getEpochSecond() + Math.max(expiresIn, 60L);
            tokenCache.put(cacheKey, new CachedToken(accessToken, expiresAt));
            return accessToken;
        } catch (Exception e) {
            throw new IllegalStateException("OAUTH2_TOKEN_REQUEST_FAILED", e);
        }
    }

    private String buildFormBody(String clientId, String clientSecret, String scope, String audience) {
        StringBuilder builder = new StringBuilder();
        appendFormParam(builder, "grant_type", "client_credentials");
        appendFormParam(builder, "client_id", clientId);
        appendFormParam(builder, "client_secret", clientSecret);
        if (scope != null && !scope.isBlank()) {
            appendFormParam(builder, "scope", scope);
        }
        if (audience != null && !audience.isBlank()) {
            appendFormParam(builder, "audience", audience);
        }
        return builder.toString();
    }

    private void appendFormParam(StringBuilder builder, String key, String value) {
        if (builder.length() > 0) {
            builder.append('&');
        }
        builder.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
        builder.append('=');
        builder.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private String requireNonBlank(String value, String errorCode) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(errorCode);
        }
        return value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class CachedToken {
        private final String accessToken;
        private final long expiresAtEpochSecond;

        private CachedToken(String accessToken, long expiresAtEpochSecond) {
            this.accessToken = accessToken;
            this.expiresAtEpochSecond = expiresAtEpochSecond;
        }
    }
}
