package dev.infrai.logistics;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public record ServiceConfig(String apiKey, String baseUrl, String environment, int port) {
    public static ServiceConfig load() {
        Properties defaults = new Properties();
        try (InputStream input = ServiceConfig.class.getResourceAsStream("/application.properties")) {
            if (input != null) defaults.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load application.properties", exception);
        }
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        return new ServiceConfig(
                key,
                env("INFRAI_BASE_URL", defaults.getProperty("infrai.base-url")),
                env("APP_ENVIRONMENT", defaults.getProperty("app.environment")),
                Integer.parseInt(env("PORT", defaults.getProperty("server.port"))));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
