package fr.nayz.hydrogen.core.redis;

import io.lettuce.core.pubsub.RedisPubSubAdapter;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

final class QueueEventSubscriber extends RedisPubSubAdapter<String, String> {
    private final String channel;
    private final List<Consumer<UUID>> listeners;

    QueueEventSubscriber(String channel, List<Consumer<UUID>> listeners) {
        this.channel = channel;
        this.listeners = listeners;
    }

    @Override
    public void message(String receivedChannel, String message) {
        if (!channel.equals(receivedChannel) || !message.startsWith("queue|")) {
            return;
        }
        try {
            UUID playerId = UUID.fromString(message.substring(6));
            listeners.forEach(listener -> listener.accept(playerId));
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed messages from other publishers on the shared channel.
        }
    }
}
