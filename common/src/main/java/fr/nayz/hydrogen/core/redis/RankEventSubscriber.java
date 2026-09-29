package fr.nayz.hydrogen.core.redis;

import io.lettuce.core.pubsub.RedisPubSubAdapter;

import java.util.List;
import java.util.function.Consumer;

final class RankEventSubscriber extends RedisPubSubAdapter<String, String> {
    private final String channel;
    private final List<Consumer<String>> rankListeners;

    RankEventSubscriber(String channel, List<Consumer<String>> rankListeners) {
        this.channel = channel;
        this.rankListeners = rankListeners;
    }

    @Override
    public void message(String receivedChannel, String message) {
        if (channel.equals(receivedChannel) && message.startsWith("rank|")) {
            rankListeners.forEach(listener -> listener.accept(message.substring(5)));
        }
    }
}
