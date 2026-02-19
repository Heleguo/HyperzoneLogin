package fun.iiii.h2l.api.event.connection;

import com.velocitypowered.api.event.annotation.AwaitingEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.util.GameProfile;

import java.util.UUID;

/**
 * 触发PreLogin后触发，用于鉴别是否为离线玩家以及进行离线的验证.
 *
 * <p>
 * Velocity typically fires this event asynchronously and does not wait for a response. However,
 * it will wait for all {@link DisconnectEvent}s for every player on the proxy to fire
 * successfully before the proxy shuts down. This event is the sole exception to the
 * {@link AwaitingEvent} contract.
 * </p>
 */

@AwaitingEvent
public final class OpenPreLoginEvent {
    private final UUID uuid;
    private final String userName;
    private final String host;

    private boolean online = true;

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    public OpenPreLoginEvent(UUID uuid, String userName, String host) {
        this.uuid = uuid;
        this.userName = userName;
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getUserName() {
        return userName;
    }


}
