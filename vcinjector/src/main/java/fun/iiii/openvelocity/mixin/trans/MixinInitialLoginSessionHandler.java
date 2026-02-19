package fun.iiii.openvelocity.mixin.trans;

import com.google.common.primitives.Longs;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.AuthSessionHandler;
import com.velocitypowered.proxy.connection.client.InitialLoginSessionHandler;
import com.velocitypowered.proxy.connection.client.LoginInboundConnection;
import com.velocitypowered.proxy.crypto.IdentifiedKeyImpl;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.packet.EncryptionRequestPacket;
import com.velocitypowered.proxy.protocol.packet.EncryptionResponsePacket;
import com.velocitypowered.proxy.protocol.packet.ServerLoginPacket;
import fun.iiii.openvelocity.mixin.IMixinLoginInboundConnection;
import fun.iiii.openvelocity.mixin.util.ApiEventReflector;
import fun.iiii.openvelocity.mixin.util.AuthSessionHandlerFactory;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static com.velocitypowered.proxy.connection.VelocityConstants.EMPTY_BYTE_ARRAY;
import static com.velocitypowered.proxy.crypto.EncryptionUtils.decryptRsa;
import static com.velocitypowered.proxy.crypto.EncryptionUtils.generateServerId;

@Mixin(value = InitialLoginSessionHandler.class)
public class MixinInitialLoginSessionHandler {

    @Final
    @Shadow
    private MinecraftConnection mcConnection;
    @Final
    @Shadow
    private VelocityServer server;
    @Shadow
    private @MonotonicNonNull ServerLoginPacket login;
    @Final
    @Shadow
    private LoginInboundConnection inbound;
    @Shadow
    private static final Logger logger = LogManager.getLogger(InitialLoginSessionHandler.class);
    @Shadow
    private byte[] verify = EMPTY_BYTE_ARRAY;
    @Final
    @Shadow
    private boolean forceKeyAuthentication;

    @Shadow
    private EncryptionRequestPacket generateEncryptionRequest() {
        return null;
    }

    //    0-LOGIN_PACKET_EXPECTED 1-LOGIN_PACKET_RECEIVED 2-ENCRYPTION_REQUEST_SENT 3-ENCRYPTION_RESPONSE_RECEIVED
    int cState = 0;

    private void aState(int expectedState) {
        if (this.cState != expectedState) {
            if (MinecraftDecoder.DEBUG) {
                logger.error("{} Received an unexpected packet requiring state {}, but we are in {}",
                        inbound,
                        expectedState, this.cState);
            }
            mcConnection.close(true);
        }
    }

    /**
     * @author ksqeib
     * @reason easy change
     */
    @Overwrite
    public boolean handle(EncryptionResponsePacket packet) {
        aState(2);
        cState = 3;
        ServerLoginPacket login = this.login;
        if (login == null) {
            throw new IllegalStateException("No ServerLogin packet received yet.");
        }

        if (verify.length == 0) {
            throw new IllegalStateException("No EncryptionRequest packet sent yet.");
        }

        try {
            KeyPair serverKeyPair = server.getServerKeyPair();
            if (inbound.getIdentifiedKey() != null) {
                IdentifiedKey playerKey = inbound.getIdentifiedKey();
                if (!playerKey.verifyDataSignature(packet.getVerifyToken(), verify,
                        Longs.toByteArray(packet.getSalt()))) {
                    throw new IllegalStateException("Invalid client public signature.");
                }
            } else {
                byte[] decryptedVerifyToken = decryptRsa(serverKeyPair, packet.getVerifyToken());
                if (!MessageDigest.isEqual(verify, decryptedVerifyToken)) {
                    throw new IllegalStateException("Unable to successfully decrypt the verification token.");
                }
            }

            byte[] decryptedSharedSecret = decryptRsa(serverKeyPair, packet.getSharedSecret());
            String serverId = generateServerId(decryptedSharedSecret, serverKeyPair.getPublic());

            doLogin(true, serverId, decryptedSharedSecret);
        } catch (GeneralSecurityException e) {
            logger.error("Unable to enable encryption", e);
            mcConnection.close(true);
        }
        return true;
    }

    /**
     * @author ksqeib
     * @reason easy change
     */
    @Overwrite
    public boolean handle(ServerLoginPacket packet) {
        aState(0);
        cState = 1;
        IdentifiedKey playerKey = packet.getPlayerKey();
        if (playerKey != null) {
            if (playerKey.hasExpired()) {
                inbound.disconnect(
                        Component.translatable("multiplayer.disconnect.invalid_public_key_signature"));
                return true;
            }

            boolean isKeyValid;
            if (playerKey.getKeyRevision() == IdentifiedKey.Revision.LINKED_V2
                    && playerKey instanceof final IdentifiedKeyImpl keyImpl) {
                isKeyValid = keyImpl.internalAddHolder(packet.getHolderUuid());
            } else {
                isKeyValid = playerKey.isSignatureValid();
            }

            if (!isKeyValid) {
                inbound.disconnect(Component.translatable("multiplayer.disconnect.invalid_public_key"));
                return true;
            }
        } else if (mcConnection.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_19)
                && forceKeyAuthentication
                && mcConnection.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
            inbound.disconnect(Component.translatable("multiplayer.disconnect.missing_public_key"));
            return true;
        }
        inbound.setPlayerKey(playerKey);
        this.login = packet;

        final PreLoginEvent event = new PreLoginEvent(inbound, login.getUsername(), login.getHolderUuid());
        server.getEventManager().fire(event).thenRunAsync(() -> {
            if (mcConnection.isClosed()) {
                // The player was disconnected
                return;
            }

            PreLoginEvent.PreLoginComponentResult result = event.getResult();
            Optional<Component> disconnectReason = result.getReasonComponent();
            if (disconnectReason.isPresent()) {
                // The component is guaranteed to be provided if the connection was denied.
                inbound.disconnect(disconnectReason.get());
                return;
            }

            IMixinLoginInboundConnection myInbound = (IMixinLoginInboundConnection) inbound;
            myInbound.fireLogin(() -> {
                if (mcConnection.isClosed()) {
                    // The player was disconnected
                    return;
                }

                mcConnection.eventLoop().execute(() -> {
                    if (!ApiEventReflector.tryInit()) {
                        logger.error("Unable to initialize ApiEventReflector: plugin classloaders are not ready");
                        inbound.disconnect(Component.text("内部错误[ApiEventReflector未就绪]"));
                        return;
                    }

                    UUID holderUuid = login.getHolderUuid();
                    String userName = login.getUsername();
                    String host = inbound.getRawVirtualHost().isPresent() ? inbound.getRawVirtualHost().get() : "";
                    Object openPreLoginEvent = ApiEventReflector.createOpenPreLoginEvent(holderUuid, userName, host);
                    server.getEventManager().fire(openPreLoginEvent).thenRun(() -> {
                        if (ApiEventReflector.isOpenPreLoginOnline(openPreLoginEvent)) {
                            EncryptionRequestPacket request = generateEncryptionRequest();
                            this.verify = Arrays.copyOf(request.getVerifyToken(), 4);
                            mcConnection.write(request);
                            cState = 2;
//                            this.currentState = InitialLoginSessionHandler.LoginState.ENCRYPTION_REQUEST_SENT;
                        } else {
                            doLogin(false, null, null);
                        }

                    }).exceptionally((ex) -> {
                        logger.error("Exception in pre-login stage", ex);
                        return null;
                    });


                });
            });
        }, mcConnection.eventLoop()).exceptionally((ex) -> {
            logger.error("Exception in pre-login stage", ex);
            return null;
        });

        return true;
    }

    private void doLogin(boolean online, String serverId, byte[] decryptedSharedSecret) {
        if (!ApiEventReflector.tryInit()) {
            logger.error("Unable to initialize ApiEventReflector: plugin classloaders are not ready");
            inbound.disconnect(Component.text("内部错误[ApiEventReflector未就绪]"));
            return;
        }

        String playerIp = ((InetSocketAddress) mcConnection.getRemoteAddress()).getHostString();
        Object onlineAuthEvent = ApiEventReflector.createOnlineAuthEvent(
            login.getUsername(),
            login.getHolderUuid(),
            serverId,
            playerIp,
            online
        );
        server.getEventManager().fire(onlineAuthEvent).thenRunAsync(
                () -> {
                    if (mcConnection.isClosed()) {
                        // The player disconnected after we authenticated them.
                        return;
                    }

                    // Go ahead and enable encryption. Once the client sends EncryptionResponse, encryption
                    // is enabled.
                    try {
                        if (online) {
//                            logger.info(
//                                    "已开启加密为 {} ({})",
//                                    login.getUsername(), playerIp);
                            mcConnection.enableEncryption(decryptedSharedSecret);
                        }
                    } catch (GeneralSecurityException e) {
                        logger.error("Unable to enable encryption for connection", e);
                        // At this point, the connection is encrypted, but something's wrong on our side and
                        // we can't do anything about it.
                        mcConnection.close(true);
                        return;
                    }

                    final GameProfile profile = ApiEventReflector.getOnlineAuthGameProfile(onlineAuthEvent);

                    AuthSessionHandler authSessionHandler = createHandler(server, inbound, profile, online);

                    if (authSessionHandler == null) {
                        inbound.disconnect(Component.text("内部错误[AuthSessionHandler对象创建失败]"));
                    }
                    mcConnection.setActiveSessionHandler(StateRegistry.LOGIN, authSessionHandler);
                }, mcConnection.eventLoop());
    }

    private static AuthSessionHandler createHandler(VelocityServer server, LoginInboundConnection inbound, GameProfile profile, boolean onlineMode) {
        try {
            return AuthSessionHandlerFactory.create(server, inbound, profile, onlineMode);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }
}
