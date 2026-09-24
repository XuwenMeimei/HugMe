package nya.tuyw.hugme.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import nya.tuyw.hugme.client.HugClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides the entity shadow of the players taking part in a hug.
 *
 * <p>{@code EntityRenderDispatcher} only draws a shadow when this returns a positive radius:
 * <pre>
 *     float f = entityrenderer.getShadowRadius(entity);
 *     if (f &gt; 0.0F) { renderShadow(...); }
 * </pre>
 *
 * <p>Why hide it at all: the hug animations translate the receiver's whole body about a block
 * forward (the {@code torso} z offset in {@code normal_r}/{@code touch_r}), which is how the two
 * characters' bodies end up touching while their collision boxes stay a safe 1.3 blocks apart. The
 * shadow and the name tag are always drawn at the <i>entity</i> position by
 * {@code EntityRenderDispatcher}, so they can never follow the model. Rather than flattening that
 * deliberately tight hug, the two things that would give the offset away are simply not drawn while
 * the animation plays.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "getShadowRadius(Lnet/minecraft/world/entity/LivingEntity;)F",
            at = @At("HEAD"), cancellable = true)
    private void hugme$hideShadowWhileHugging(LivingEntity entity, CallbackInfoReturnable<Float> callback) {
        if (entity instanceof Player player && HugClientState.lockOf(player.getUUID()) != null) {
            callback.setReturnValue(0.0F);
        }
    }
}
