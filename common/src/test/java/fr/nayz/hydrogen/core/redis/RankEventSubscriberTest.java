package fr.nayz.hydrogen.core.redis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RankEventSubscriberTest {
    @Test
    void onlyDispatchesRankChangesFromHydrogenChannel() {
        List<String> received = new ArrayList<>();
        List<Consumer<String>> listeners = List.of(received::add);
        RankEventSubscriber subscriber = new RankEventSubscriber("hydrogen:events", listeners);
        String playerId = UUID.randomUUID().toString();

        subscriber.message("other:events", "rank|" + playerId);
        subscriber.message("hydrogen:events", "party|" + UUID.randomUUID());
        subscriber.message("hydrogen:events", "rank|" + playerId);

        assertEquals(List.of(playerId), received);
    }
}
