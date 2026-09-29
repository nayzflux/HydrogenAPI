package fr.nayz.hydrogen.velocity.event.account;

import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import fr.nayz.hydrogen.velocity.rank.VelocityRankManager;

public final class LoginListener {
    private final VelocityRankManager ranks;

    public LoginListener(VelocityRankManager ranks) {
        this.ranks = ranks;
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        return ranks.checkLogin(event);
    }
}
