package fr.nayz.hydrogen.core.redis;

import io.lettuce.core.pubsub.RedisPubSubAdapter;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

final class SocialEventSubscriber extends RedisPubSubAdapter<String, String> {
    private final String channel;
    private final List<Consumer<SocialEvent>> listeners;

    SocialEventSubscriber(String channel, List<Consumer<SocialEvent>> listeners) {
        this.channel = channel;
        this.listeners = listeners;
    }

    @Override
    public void message(String receivedChannel, String message) {
        if (!channel.equals(receivedChannel) || !message.startsWith("social|")) {
            return;
        }
        String[] fields = message.split("\\|", 4);
        if (fields.length != 4) {
            return;
        }
        try {
            SocialEvent event = new SocialEvent(fields[1], UUID.fromString(fields[2]),
                    UUID.fromString(fields[3]));
            listeners.forEach(listener -> listener.accept(event));
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed external publications.
        }
    }
}
