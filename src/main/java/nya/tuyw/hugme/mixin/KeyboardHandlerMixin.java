package nya.tuyw.hugme.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import nya.tuyw.hugme.client.HugClientState;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swallows the movement keys while a hug is playing: forward, back, left, right and jump.
 *
 * <p>Everything else stays usable on purpose - inventory, chat, hotbar, drop, perspective, mouse,
 * ... Only moving is locked, and even the movement keys are merely the second layer:
 * {@link InputMixin} already clears the movement impulses, which is what actually keeps the player
 * standing still. Swallowing the key presses as well just keeps the bindings from going into a
 * "held" state in the first place.
 *
 * <p>Release events are always forwarded, otherwise a key that was already held when the hug
 * started could never be released.
 *
 * <p>NeoForge's {@code InputEvent.Key} is not cancelable, so a mixin is the only way to stop
 * vanilla from acting on a key at all.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void hugme$swallowMovementKeys(long windowPointer, int key, int scanCode, int action, int modifiers,
                                           CallbackInfo callback) {
        if (!HugClientState.isInputLocked()) {
            return;
        }
        if (action == GLFW.GLFW_RELEASE) {
            return;
        }
        if (!isMovementKey(key, scanCode)) {
            return;
        }
        callback.cancel();
    }

    private static boolean isMovementKey(int key, int scanCode) {
        Options options = Minecraft.getInstance().options;
        return options.keyUp.matches(key, scanCode)
                || options.keyDown.matches(key, scanCode)
                || options.keyLeft.matches(key, scanCode)
                || options.keyRight.matches(key, scanCode)
                || options.keyJump.matches(key, scanCode);
    }
}
