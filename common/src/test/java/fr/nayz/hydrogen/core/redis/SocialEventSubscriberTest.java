package fr.nayz.hydrogen.core.redis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SocialEventSubscriberTest {
    @Test
    void dispatchesOnlyValidEventsFromHydrogenChannel() {
        List<SocialEvent> received = new ArrayList<>();
        SocialEventSubscriber subscriber = new SocialEventSubscriber(
                "hydrogen:events", List.of(received::add));
        UUID target = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        subscriber.message("other:events", "social|friend-request|" + target + "|" + actor);
        subscriber.message("hydrogen:events", "social|invalid");
        subscriber.message("hydrogen:events", "social|friend-request|" + target + "|" + actor);

        assertEquals(List.of(new SocialEvent("friend-request", target, actor)), received);
    }
}
