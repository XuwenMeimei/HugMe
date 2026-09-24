package iloveyou.ruantang.hugme.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import iloveyou.ruantang.hugme.HugMe;

import java.util.Map;
import java.util.UUID;

/**
 * Makes the two locked players face each other.
 *
 * <p>This replaces the original mod's approach of cancelling {@code RenderPlayerEvent.Pre} and
 * redrawing the model with a hand built transform. That transform cancelled the vanilla renderer
 * (which is why armour, cape, held items, the name tag and Player Animator's own first person bone
 * hiding were all lost), and any small mistake in it moves the model away from the entity position -
 * which is exactly what makes the entity shadow look misplaced, because the shadow is drawn by
 * {@code EntityRenderDispatcher} <i>after</i> the renderer returns, straight at the entity position.
 *
 * <p>The maths work out identical: the original chain {@code Rx(180) * T(0,-1.5,0) * Ry(yaw)} equals
 * vanilla's own {@code Ry(180 - bodyYaw) * scale(-1,-1,1) * T(0,-1.501,0)} for {@code bodyYaw = yaw},
 * i.e. the hard coded 180 degree flip existed only to cancel out vanilla's own 180 degree offset.
 * So writing the yaw into the entity is both simpler and exactly as correct - and the game then
 * renders the model, its layers, its name tag and its shadow the normal way.
 *
 * <p>Only the body / head yaw is touched, never {@code yRot}: that one drives the camera, so the
 * player keeps full control of where they look.
 */
@EventBusSubscriber(modid = HugMe.MODID, value = Dist.CLIENT)
public final class HugFacingHandler {

    private HugFacingHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!HugClientState.isInputLocked()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        for (Map.Entry<UUID, HugClientState.Lock> entry : HugClientState.locks().entrySet()) {
            if (!(level.getPlayerByUUID(entry.getKey()) instanceof AbstractClientPlayer player)) {
                continue;
            }
            if (!(level.getPlayerByUUID(entry.getValue().target()) instanceof AbstractClientPlayer target)) {
                continue;
            }
            float yaw = yawTowards(player, target);
            // Both the current and the previous value, otherwise the renderer interpolates between
            // the old facing and the new one for a tick.
            player.yBodyRot = yaw;
            player.yBodyRotO = yaw;
            player.yHeadRot = yaw;
            player.yHeadRotO = yaw;
        }
    }

    /** Minecraft yaw that makes {@code from} look at {@code to}, in degrees. */
    private static float yawTowards(AbstractClientPlayer from, AbstractClientPlayer to) {
        double deltaX = to.getX() - from.getX();
        double deltaZ = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
    }
}
