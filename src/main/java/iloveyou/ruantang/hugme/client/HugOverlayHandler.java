package iloveyou.ruantang.hugme.client;

import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import iloveyou.ruantang.hugme.HugMe;
import iloveyou.ruantang.hugme.hug.HugCompatibility;

/**
 * Hides the name tag of the players taking part in a hug.
 *
 * <p>Same reason as the shadow being hidden in
 * {@link iloveyou.ruantang.hugme.mixin.LivingEntityRendererMixin}: the animation moves the model about a
 * block away from the entity, and the name tag is drawn at the entity position, so it would hang in
 * the air next to the hugging player.
 */
@EventBusSubscriber(modid = HugMe.MODID, value = Dist.CLIENT)
public final class HugOverlayHandler {

    private HugOverlayHandler() {
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof Player player
                && HugClientState.lockOf(player.getUUID()) != null
                && HugCompatibility.hideShadowAndNameTag()) {
            event.setCanRender(TriState.FALSE);
        }
    }
}
