package fr.nayz.hydrogen.core;

import io.lettuce.core.RedisURI;
import java.util.Map;

public record HydrogenConfig(
        String databaseHost,
        int databasePort,
        String databaseName,
        String databaseUser,
        String databasePassword,
        RedisURI redisUri
) {
    public HydrogenConfig {
        if (databaseHost == null || databaseHost.isBlank()) {
            throw new IllegalArgumentException("Database host is required");
        }
        if (databasePort < 1 || databasePort > 65535) {
            throw new IllegalArgumentException("Database port must be between 1 and 65535");
        }
        if (databaseName == null || databaseName.isBlank()) {
            throw new IllegalArgumentException("Database name is required");
        }
        if (databaseUser == null || databaseUser.isBlank()) {
            throw new IllegalArgumentException("Database user is required");
        }
        if (databasePassword == null) {
            throw new IllegalArgumentException("Database password is required");
        }
        if (redisUri == null) {
            throw new IllegalArgumentException("Redis configuration is required");
        }
    }

    public static HydrogenConfig fromEnvironment() {
        return from(System.getenv());
    }

    public static HydrogenConfig from(Map<String, String> environment) {
        return from(environment, "HYDROGEN_");
    }

    public static HydrogenConfig from(Map<String, String> environment, String prefix) {
        String redisHost = required(environment, prefix + "REDIS_HOST");
        int redisPort = port(environment, prefix + "REDIS_PORT");
        int redisDatabase = nonNegative(environment, prefix + "REDIS_DB", 0);
        String redisUser = optional(environment, prefix + "REDIS_USER");
        String redisPassword = optionalSecret(environment, prefix + "REDIS_PASSWORD");

        RedisURI.Builder redis = RedisURI.Builder.redis(redisHost, redisPort)
                .withDatabase(redisDatabase);
        if (redisPassword != null) {
            if (redisUser == null) {
                redis.withPassword(redisPassword.toCharArray());
            } else {
                redis.withAuthentication(redisUser, redisPassword);
            }
        } else if (redisUser != null) {
            throw new IllegalArgumentException(prefix + "REDIS_PASSWORD is required with Redis user");
        }

        return new HydrogenConfig(
                required(environment, prefix + "DB_HOST"),
                port(environment, prefix + "DB_PORT"),
                required(environment, prefix + "DB_NAME"),
                required(environment, prefix + "DB_USER"),
                requiredSecret(environment, prefix + "DB_PASSWORD"),
                redis.build()
        );
    }

    private static String required(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }

    private static String optional(Map<String, String> environment, String key) {
        String value = environment.get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requiredSecret(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static String optionalSecret(Map<String, String> environment, String key) {
        String value = environment.get(key);
        return value == null || value.isEmpty() ? null : value;
    }

    private static int port(Map<String, String> environment, String key) {
        int value = integer(required(environment, key), key);
        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException(key + " must be between 1 and 65535");
        }
        return value;
    }

    private static int nonNegative(Map<String, String> environment, String key, int fallback) {
        String raw = optional(environment, key);
        if (raw == null) {
            return fallback;
        }
        int value = integer(raw, key);
        if (value < 0) {
            throw new IllegalArgumentException(key + " must be non-negative");
        }
        return value;
    }

    private static int integer(String value, String key) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }
}
