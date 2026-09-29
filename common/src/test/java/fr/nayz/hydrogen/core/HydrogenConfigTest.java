package fr.nayz.hydrogen.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class HydrogenConfigTest {
    @Test
    void readsPostgresAndRedisFromSeparateVariables() {
        HydrogenConfig config = HydrogenConfig.from(environment());

        assertEquals("postgres", config.databaseHost());
        assertEquals(5432, config.databasePort());
        assertEquals("mc", config.databaseName());
        assertEquals("hydrogen", config.databaseUser());
        assertEquals("secret", config.databasePassword());
        assertEquals("redis", config.redisUri().getHost());
        assertEquals(6379, config.redisUri().getPort());
        assertEquals(0, config.redisUri().getDatabase());
    }

    @Test
    void rejectsInvalidPortBeforeConnecting() {
        Map<String, String> environment = environment();
        environment.put("HYDROGEN_DB_PORT", "invalid");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> HydrogenConfig.from(environment));

        assertEquals("HYDROGEN_DB_PORT must be an integer", error.getMessage());
    }

    @Test
    @SuppressWarnings("deprecation")
    void preservesPasswordWhitespace() {
        Map<String, String> environment = environment();
        environment.put("HYDROGEN_DB_PASSWORD", " secret ");
        environment.put("HYDROGEN_REDIS_PASSWORD", " redis secret ");

        HydrogenConfig config = HydrogenConfig.from(environment);

        assertEquals(" secret ", config.databasePassword());
        assertEquals(" redis secret ", new String(config.redisUri().getPassword()));
    }

    @Test
    void requiresSeparateHostInsteadOfOldUrlVariable() {
        Map<String, String> environment = environment();
        environment.remove("HYDROGEN_DB_HOST");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> HydrogenConfig.from(environment));

        assertEquals("HYDROGEN_DB_HOST is required", error.getMessage());
    }

    private Map<String, String> environment() {
        Map<String, String> values = new HashMap<>();
        values.put("HYDROGEN_DB_HOST", "postgres");
        values.put("HYDROGEN_DB_PORT", "5432");
        values.put("HYDROGEN_DB_NAME", "mc");
        values.put("HYDROGEN_DB_USER", "hydrogen");
        values.put("HYDROGEN_DB_PASSWORD", "secret");
        values.put("HYDROGEN_REDIS_HOST", "redis");
        values.put("HYDROGEN_REDIS_PORT", "6379");
        return values;
    }
}
