package nya.tuyw.hugme.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import nya.tuyw.hugme.HugMe;
import nya.tuyw.hugme.hug.HugAnimation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which players are currently held in a hug pose, client side.
 *
 * <p>Besides the "hug stopped" packet there is a safety net: every lock carries an expiry tick, so
 * a hug can never survive a server crash or a dropped stop packet.
 *
 * <p>While at least one lock exists the mod
 * <ul>
 *     <li>switches a first person camera to third person, so the player actually sees the hug,</li>
 *     <li>locks movement ({@link #isInputLocked()}). Only walking and jumping are disabled - looking
 *         around, other keys, screens and the mouse stay usable.</li>
 * </ul>
 *
 * <p>Both are undone when the hug ends: the camera goes back to the perspective the player had
 * before the hug and the players stay where the animation left them.
 */
@EventBusSubscriber(modid = HugMe.MODID, value = Dist.CLIENT)
public final class HugClientState {

    /** Extra ticks on top of the animation length before the client force releases the pose. */
    private static final long SAFETY_MARGIN_TICKS = 40L;

    /**
     * @param target   the player this one must face while hugging
     * @param expireAt client game time at which the lock is dropped no matter what
     */
    public record Lock(UUID target, long expireAt) {
    }

    private static final Map<UUID, Lock> LOCKS = new HashMap<>();

    /** Perspective to go back to when the hug ends; non-null only while the mod switched it. */
    private static CameraType savedCameraType;

    private HugClientState() {
    }

    public static @Nullable Lock lockOf(UUID playerId) {
        return LOCKS.get(playerId);
    }

    /** Read only view of every locked player and the partner they have to face. */
    public static Map<UUID, Lock> locks() {
        return Collections.unmodifiableMap(LOCKS);
    }

    /** @return {@code true} while a hug is playing, i.e. while input must be swallowed. */
    public static boolean isInputLocked() {
        return !LOCKS.isEmpty();
    }

    public static void lockAndPlay(AbstractClientPlayer initiator, AbstractClientPlayer partner, HugAnimation animation) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long expireAt = level.getGameTime() + animation.durationTicks() + SAFETY_MARGIN_TICKS;
        LOCKS.put(initiator.getUUID(), new Lock(partner.getUUID(), expireAt));
        LOCKS.put(partner.getUUID(), new Lock(initiator.getUUID(), expireAt));

        // Drop whatever the player is holding, otherwise a key that was already down would keep
        // feeding movement input for the whole hug.
        KeyMapping.releaseAll();

        // A hug is unwatchable from inside your own head, so take over the camera - and remember
        // what to go back to, see restoreCamera().
        if (Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON) {
            savedCameraType = CameraType.FIRST_PERSON;
            Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }

        HugAnimationManager.play(initiator, partner, animation);
    }

    public static void unlock(AbstractClientPlayer initiator, AbstractClientPlayer partner) {
        LOCKS.remove(initiator.getUUID());
        LOCKS.remove(partner.getUUID());
        HugAnimationManager.stop(initiator);
        HugAnimationManager.stop(partner);
        if (LOCKS.isEmpty()) {
            restoreCamera();
        }
    }

    /** Puts the perspective back to whatever the player was using before the hug. */
    private static void restoreCamera() {
        if (savedCameraType != null) {
            Minecraft.getInstance().options.setCameraType(savedCameraType);
            savedCameraType = null;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (LOCKS.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            LOCKS.clear();
            restoreCamera();
            return;
        }

        long now = level.getGameTime();
        List<UUID> expired = null;
        for (Iterator<Map.Entry<UUID, Lock>> iterator = LOCKS.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<UUID, Lock> entry = iterator.next();
            if (entry.getValue().expireAt() <= now) {
                iterator.remove();
                if (expired == null) {
                    expired = new ArrayList<>();
                }
                expired.add(entry.getKey());
            }
        }

        if (expired != null) {
            for (UUID playerId : expired) {
                if (level.getPlayerByUUID(playerId) instanceof AbstractClientPlayer player) {
                    HugAnimationManager.stop(player);
                }
            }
        }
        if (LOCKS.isEmpty()) {
            restoreCamera();
        }
    }
}
