package iloveyou.ruantang.hugme.client;

import net.minecraft.client.Minecraft;
import iloveyou.ruantang.hugme.hug.HugAnimation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The hug request this client is currently expected to answer, if any.
 *
 * <p>Filled in by {@link iloveyou.ruantang.hugme.network.HugPromptPayload} and read by
 * {@link HugPromptOverlay}. It carries its own expiry as a safety net, in case the retracting
 * packet never arrives (server crash, player leaving).
 */
public final class HugPromptState {

    private static UUID initiator;
    private static HugAnimation animation;
    private static long expireAt;

    private HugPromptState() {
    }

    public static void set(UUID initiatorId, HugAnimation hugAnimation, int ticksRemaining) {
        Minecraft client = Minecraft.getInstance();
        long now = client.level == null ? 0L : client.level.getGameTime();
        initiator = initiatorId;
        animation = hugAnimation;
        expireAt = now + ticksRemaining;
    }

    public static void clear() {
        initiator = null;
        animation = null;
    }

    public static boolean isActive() {
        if (initiator == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            clear();
            return false;
        }
        if (client.level.getGameTime() > expireAt) {
            clear();
            return false;
        }
        return true;
    }

    public static @Nullable UUID initiator() {
        return initiator;
    }

    public static @Nullable HugAnimation animation() {
        return animation;
    }
}
