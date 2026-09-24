package iloveyou.ruantang.hugme.hug;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Every hug animation the mod knows about.
 *
 * <p>This enum is shared between both sides, so it must never reference a client only class.
 * The actual key frame data lives in
 * {@code assets/hugme/player_animations/*.json} and is looked up by the client through
 * Player Animator.
 */
public enum HugAnimation implements StringRepresentable {

    NORMAL_HUG("normal_hug", "hug_normal_sender", "hug_normal_receiver", 61, 1.0, 1.3),
    TOUCH_HEAD_HUG("touch_head_hug", "hug_touch_sender", "hug_touch_receiver", 61, 0.75, 1.15);

    /** Playback speed applied by the client layer modifier. Keep in sync with the client. */
    public static final float SPEED = 0.5F;

    /** Extra ticks kept after the animation stops, so the server never unlocks mid animation. */
    private static final int TAIL_TICKS = 6;

    private final String name;
    private final ResourceLocation initiatorAnimation;
    private final ResourceLocation partnerAnimation;
    private final int stopTick;
    /** How far the animation moves the receiver's whole body towards the sender, in blocks. */
    private final double bodyOffset;
    /** Default stand distance, in blocks. Live value comes from {@link HugConfig#frontDistance}. */
    private final double defaultFrontDistance;

    HugAnimation(String name, String initiatorAnimation, String partnerAnimation, int stopTick,
                 double bodyOffset, double defaultFrontDistance) {
        this.name = name;
        this.initiatorAnimation = ResourceLocation.fromNamespaceAndPath("hugme", initiatorAnimation);
        this.partnerAnimation = ResourceLocation.fromNamespaceAndPath("hugme", partnerAnimation);
        this.stopTick = stopTick;
        this.bodyOffset = bodyOffset;
        this.defaultFrontDistance = defaultFrontDistance;
    }

    @Override
    public @NotNull String getSerializedName() {
        return this.name;
    }

    public ResourceLocation initiatorAnimation() {
        return this.initiatorAnimation;
    }

    public ResourceLocation partnerAnimation() {
        return this.partnerAnimation;
    }

    /** How long the hug lasts on the server, in ticks. */
    public int durationTicks() {
        return (int) Math.ceil(this.stopTick / SPEED) + TAIL_TICKS;
    }

    /** How far this animation moves the receiver's whole body towards the sender, in blocks. */
    public double bodyOffset() {
        return this.bodyOffset;
    }

    /**
     * Default distance between the two entity positions for this animation, in blocks.
     *
     * <p>This has to be per animation: the receiver's body is pushed {@link #bodyOffset} blocks
     * towards the sender, and only the part of the distance that is <i>not</i> covered by that
     * offset is the visible gap between the two characters. Using one shared distance is what made
     * the head pat hug line up wrongly while the normal hug looked fine.
     *
     * <p>Both values are only defaults - {@link HugConfig} exposes them as a config file so they can
     * be dialled in without rebuilding.
     */
    public double defaultFrontDistance() {
        return this.defaultFrontDistance;
    }

    public String translationKey() {
        return "hugme.animation." + this.name;
    }

    public Component displayName() {
        return Component.translatable(translationKey());
    }

    /** @return the animation matching {@code name}, or {@code null} when unknown. */
    public static @Nullable HugAnimation byName(String name) {
        for (HugAnimation animation : values()) {
            if (animation.name.equalsIgnoreCase(name)) {
                return animation;
            }
        }
        return null;
    }

    /** Never fails; falls back to {@link #NORMAL_HUG} for out of range network values. */
    public static HugAnimation byOrdinal(int ordinal) {
        HugAnimation[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NORMAL_HUG;
    }
}
