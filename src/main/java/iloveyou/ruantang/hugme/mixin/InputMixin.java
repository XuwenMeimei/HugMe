package iloveyou.ruantang.hugme.mixin;

import net.minecraft.client.player.Input;
import iloveyou.ruantang.hugme.client.HugClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The real fix for "the player drifts while hugging".
 *
 * <p>The client owns its own position: as long as it keeps producing movement it will keep sending
 * position updates, and the server has no way of holding it in place other than teleporting it back
 * - which is exactly what caused the visible offset and the entity interpolation desync behind the
 * broken shadow.
 *
 * <p>So instead of correcting the position afterwards, movement is never produced in the first
 * place. {@code Input.tick} is where the impulses are computed from the key bindings;
 * {@code LocalPlayer.aiStep} reads them into {@code xxa}/{@code zza} at the start of the next tick,
 * so clearing them right here means the next tick moves the player by exactly nothing.
 *
 * <p>Looking around (mouse) and sneaking (Shift) stay untouched on purpose.
 */
@Mixin(Input.class)
public abstract class InputMixin {

    @Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void hugme$freezeMovement(boolean isSneaking, float sneakingSpeedMultiplier, CallbackInfo callback) {
        if (!HugClientState.isInputLocked()) {
            return;
        }
        Input input = (Input) (Object) this;
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        // input.shiftKeyDown is left alone: sneak stays usable.
    }
}
