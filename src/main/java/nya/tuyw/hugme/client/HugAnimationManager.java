package nya.tuyw.hugme.client;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import nya.tuyw.hugme.HugMe;
import nya.tuyw.hugme.hug.HugAnimation;
import org.jetbrains.annotations.Nullable;

/**
 * Client side animation plumbing around Player Animator.
 *
 * <p>Each player gets one modifier layer (created lazily by the factory below) on which the hug
 * animation is played. The layer is run at {@link HugAnimation#SPEED} so the server side duration
 * and the key frame data line up.
 */
@EventBusSubscriber(modid = HugMe.MODID, value = Dist.CLIENT)
public final class HugAnimationManager {

    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(HugMe.MODID, "animations");
    private static final int LAYER_PRIORITY = 42;

    private HugAnimationManager() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, LAYER_PRIORITY,
                player -> player instanceof AbstractClientPlayer ? createLayer() : null);
    }

    private static ModifierLayer<IAnimation> createLayer() {
        ModifierLayer<IAnimation> layer = new ModifierLayer<>();
        layer.addModifierBefore(new SpeedModifier(HugAnimation.SPEED));
        return layer;
    }

    /**
     * Plays both halves of the hug. Safe to call with players of a foreign or unloaded model.
     */
    public static void play(AbstractClientPlayer initiator, AbstractClientPlayer partner, HugAnimation animation) {
        ModifierLayer<IAnimation> initiatorLayer = layerOf(initiator);
        ModifierLayer<IAnimation> partnerLayer = layerOf(partner);
        if (initiatorLayer == null || partnerLayer == null) {
            return;
        }

        KeyframeAnimationPlayer initiatorAnimation = createPlayer(animation.initiatorAnimation());
        KeyframeAnimationPlayer partnerAnimation = createPlayer(animation.partnerAnimation());
        if (initiatorAnimation == null || partnerAnimation == null) {
            return;
        }

        initiatorLayer.setAnimation(initiatorAnimation);
        partnerLayer.setAnimation(partnerAnimation);
        HugMe.LOGGER.debug("Playing {} for {} and {}", animation.getSerializedName(),
                initiator.getGameProfile().getName(), partner.getGameProfile().getName());
    }

    /**
     * Drops the animation from this player's layer.
     *
     * <p>Required when a hug is ended early: the key frames are not looping, so they normally stop
     * on their own once the last tick is reached, but a force stop happens while they are still
     * running - without this the pose would keep playing on every nearby client.
     */
    public static void stop(AbstractClientPlayer player) {
        ModifierLayer<IAnimation> layer = layerOf(player);
        if (layer != null) {
            layer.setAnimation(null);
        }
    }

    @Nullable
    private static KeyframeAnimationPlayer createPlayer(ResourceLocation animationId) {        if (!(PlayerAnimationRegistry.getAnimation(animationId) instanceof KeyframeAnimation keyframes)) {
            HugMe.LOGGER.warn("Hug animation {} is missing from the resources", animationId);
            return null;
        }
        return new KeyframeAnimationPlayer(keyframes)
                .setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                .setFirstPersonConfiguration(new FirstPersonConfiguration()
                        .setShowLeftArm(true).setShowLeftItem(false)
                        .setShowRightArm(true).setShowRightItem(false));
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layerOf(AbstractClientPlayer player) {
        Object layer = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER_ID);
        return layer instanceof ModifierLayer<?> ? (ModifierLayer<IAnimation>) layer : null;
    }
}
