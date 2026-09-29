package fr.nayz.hydrogen.velocity.event.lifecycle;

import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.Subscribe;
import fr.nayz.hydrogen.velocity.VelocityRuntime;

public final class ProxyShutdownListener {
    private final VelocityRuntime runtime;

    public ProxyShutdownListener(VelocityRuntime runtime) {
        this.runtime = runtime;
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        runtime.close();
    }
}
