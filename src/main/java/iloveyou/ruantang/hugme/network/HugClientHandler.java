package iloveyou.ruantang.hugme.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import iloveyou.ruantang.hugme.client.HugClientState;
import iloveyou.ruantang.hugme.hug.HugAnimation;

import java.util.UUID;

/**
 * Client half of {@link HugPayload}.
 *
 * <p>The class deliberately has no static state: the method reference in
 * {@link HugPayload#register} makes the dedicated server load this class, and touching the
 * client only state at class initialisation would blow up there.
 */
public final class HugClientHandler {

    private HugClientHandler() {
    }

    public static void handle(final HugPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft client = Minecraft.getInstance();
            ClientLevel level = client.level;
            if (level == null) {
                return;
            }
            AbstractClientPlayer initiator = player(level, payload.initiator());
            AbstractClientPlayer partner = player(level, payload.partner());
            if (initiator == null || partner == null) {
                // Someone is out of tracking range: nothing to animate, and nothing to release.
                return;
            }

            if (payload.stop()) {
                HugClientState.unlock(initiator, partner);
            } else {
                HugClientState.lockAndPlay(initiator, partner, HugAnimation.byOrdinal(payload.animation()));
            }
        }).exceptionally(throwable -> {
            context.disconnect(Component.translatable("hugme.network.failed", throwable.getMessage()));
            return null;
        });
    }

    private static AbstractClientPlayer player(ClientLevel level, UUID id) {
        return level.getPlayerByUUID(id) instanceof AbstractClientPlayer player ? player : null;
    }
}
