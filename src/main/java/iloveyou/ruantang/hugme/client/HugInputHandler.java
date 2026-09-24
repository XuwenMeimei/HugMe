package iloveyou.ruantang.hugme.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import iloveyou.ruantang.hugme.HugMe;
import iloveyou.ruantang.hugme.hug.HugCompatibility;
import iloveyou.ruantang.hugme.network.HugStopPayload;
import org.lwjgl.glfw.GLFW;

/**
 * Client input handling:
 *
 * <ul>
 *     <li>right clicking another player with an empty hand opens {@link HugMenuScreen},</li>
 *     <li>double tapping Shift force ends a running hug.</li>
 * </ul>
 *
 * <p>Movement is locked elsewhere: {@link iloveyou.ruantang.hugme.mixin.InputMixin} clears the movement
 * impulses and {@link iloveyou.ruantang.hugme.mixin.KeyboardHandlerMixin} swallows the movement keys.
 */
@EventBusSubscriber(modid = HugMe.MODID, value = Dist.CLIENT)
public final class HugInputHandler {

    /** Two Shift presses closer together than this count as a double tap. */
    private static final long DOUBLE_TAP_MILLIS = 400L;

    private static long lastShiftTap;

    private HugInputHandler() {
    }

    /** Right click on another player -> interaction menu. */
    @SubscribeEvent
    public static void onInteractionKeyMapping(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() || event.isPickBlock() || HugCompatibility.isDisabled()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.screen != null) {
            return;
        }
        // Only with an empty main hand, so using an item on a player still works as usual.
        if (!client.player.getMainHandItem().isEmpty()) {
            return;
        }
        if (!(client.hitResult instanceof EntityHitResult hit)) {
            return;
        }
        if (!(hit.getEntity() instanceof AbstractClientPlayer target) || target == client.player) {
            return;
        }
        event.setCanceled(true);
        client.setScreen(new HugMenuScreen(target));
    }

    /** Double tap Shift force ends the hug. */
    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (HugCompatibility.isDisabled()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        if (!client.options.keyShift.matches(event.getKey(), event.getScanCode())) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        if (!HugClientState.isInputLocked()) {
            lastShiftTap = 0L;
            return;
        }

        long now = Util.getMillis();
        if (now - lastShiftTap <= DOUBLE_TAP_MILLIS) {
            lastShiftTap = 0L;
            PacketDistributor.sendToServer(new HugStopPayload());
        } else {
            lastShiftTap = now;
        }
    }
}
