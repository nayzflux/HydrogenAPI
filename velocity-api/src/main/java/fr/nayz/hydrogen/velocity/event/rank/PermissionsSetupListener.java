package fr.nayz.hydrogen.velocity.event.rank;

import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.event.Subscribe;
import fr.nayz.hydrogen.velocity.rank.VelocityRankManager;

public final class PermissionsSetupListener {
    private final VelocityRankManager ranks;

    public PermissionsSetupListener(VelocityRankManager ranks) {
        this.ranks = ranks;
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void onPermissions(PermissionsSetupEvent event) {
        ranks.setupPermissions(event);
    }
}
