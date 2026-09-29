package fr.nayz.hydrogen.velocity;

import fr.nayz.hydrogen.core.CoreHydrogenApi;
import fr.nayz.hydrogen.velocity.presence.VelocityPresenceManager;
import fr.nayz.hydrogen.velocity.matchmaking.VelocityQueueMessenger;
import fr.nayz.hydrogen.velocity.rank.VelocityRankManager;

public final class VelocityRuntime implements AutoCloseable {
    private final CoreHydrogenApi core;
    private final VelocityRankManager ranks;
    private final VelocityPresenceManager presence;
    private final VelocityQueueMessenger queues;

    public VelocityRuntime(
            CoreHydrogenApi core,
            VelocityRankManager ranks,
            VelocityPresenceManager presence,
            VelocityQueueMessenger queues
    ) {
        this.core = core;
        this.ranks = ranks;
        this.presence = presence;
        this.queues = queues;
    }

    public CoreHydrogenApi core() {
        return core;
    }

    public VelocityRankManager ranks() {
        return ranks;
    }

    public VelocityPresenceManager presence() {
        return presence;
    }

    @Override
    public void close() {
        queues.close();
        ranks.close();
        presence.close();
        core.close();
    }
}
