package fr.nayz.hydrogen.api.account;

import java.util.Map;
import java.util.UUID;

public record Account(UUID playerId, Rank rank, int level, long coins, Map<String, Integer> gameLevels) {
}
