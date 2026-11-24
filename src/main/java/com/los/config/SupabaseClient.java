package com.los.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Enterprise Supabase Integration Client using Java 21 java.net.http.HttpClient.
 * Interacts with Supabase Auth (GoTrue) and PostgREST endpoints.
 */
public class SupabaseClient {

    private final AppConfig config;
    private final HttpClient httpClient;

    public SupabaseClient() {
        this.config = AppConfig.getInstance();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public SupabaseClient(AppConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Checks whether the Supabase REST endpoint is responsive using the Secret API Key.
     */
    public boolean testConnection() {
        try {
            String url = config.getSupabaseUrl() + "/rest/v1/";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", config.getSupabaseSecretKey())
                    .header("Authorization", "Bearer " + config.getSupabaseSecretKey())
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            System.err.println("[SupabaseClient] Connectivity test error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Verifies JWKS (JSON Web Key Set) endpoint responsiveness.
     */
    public boolean testJwksEndpoint() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getSupabaseJwksUrl()))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            System.err.println("[SupabaseClient] JWKS check error: " + e.getMessage());
            return false;
        }
    }

    public String getSupabaseUrl() {
        return config.getSupabaseUrl();
    }

    public String getPublishableKey() {
        return config.getSupabasePublishableKey();
    }
}
