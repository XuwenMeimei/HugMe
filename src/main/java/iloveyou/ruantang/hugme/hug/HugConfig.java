package iloveyou.ruantang.hugme.hug;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

/**
 * Tuning knobs for the hug geometry, written to {@code config/hugme-common.toml}.
 *
 * <p>Both values exist because the animations carry their own body displacement: the receiver half
 * of each animation shifts its whole body towards the sender, so the distance the two <i>entity</i>
 * positions need is per animation and has to be found by eye.
 */
public final class HugConfig {

    public static final ModConfigSpec SPEC;

    private static final Map<HugAnimation, ModConfigSpec.DoubleValue> FRONT_DISTANCE =
            new EnumMap<>(HugAnimation.class);

    private static final ModConfigSpec.BooleanValue ALIGN_ON_START;
    private static final ModConfigSpec.BooleanValue HIDE_OVERLAYS;
    private static final ModConfigSpec.BooleanValue HIDE_OVERLAYS_WITH_YSM;
    private static final ModConfigSpec.BooleanValue DISABLE_WITH_YSM;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("How far the partner stands from the initiator, in blocks, per animation.",
                        "Each animation moves the receiver's body towards the sender by its own body offset",
                        "(the torso position key frame in the matching *_r.json), so the two entity positions",
                        "have to be that much further apart for the bodies to end up where the animation",
                        "expects them. Too large looks loose, too small makes the players clip into",
                        "each other.")
                .push("front_distance");
        for (HugAnimation animation : HugAnimation.values()) {
            FRONT_DISTANCE.put(animation, builder
                    .comment("this animation's body offset is " + animation.bodyOffset() + " blocks")
                    .defineInRange(animation.getSerializedName(), animation.defaultFrontDistance(), 0.5D, 4.0D));
        }
        builder.pop();

        ALIGN_ON_START = builder
                .comment("Nudge the partner onto exactly the distance above when the hug starts.",
                        "Players walk in at their own pace, so without this the real distance - and with it how",
                        "tight the hug looks - varies by up to a quarter of a block from one hug to the next.",
                        "The correction is never larger than that and the initiator is never moved.")
                .define("align_on_start", true);

        HIDE_OVERLAYS = builder
                .comment("Hide the shadow and the name tag of the two players while a hug plays.",
                        "Each hug animation moves the receiver's body about a block towards the sender (the torso",
                        "position key frame), which is how the two bodies end up touching while their collision",
                        "boxes stay apart. The shadow and the name tag are always drawn at the entity position, so",
                        "they would lag behind the moved model - hiding them is what keeps that invisible.")
                .define("hide_shadow_and_nametag", true);

        HIDE_OVERLAYS_WITH_YSM = builder
                .comment("The shadow / name tag switch for clients that run Yes Steve Model.",
                        "Only relevant when disable_with_ysm is off. YSM replaces the player model and",
                        "animates it with its own Bedrock animations, so the body offset above does not exist",
                        "there and the shadow is already in the right place.")
                .define("hide_shadow_and_nametag_with_ysm", false);

        DISABLE_WITH_YSM = builder
                .comment("Disable the whole mod when Yes Steve Model is in the modpack.",
                        "YSM replaces the player model and animates it with its own Bedrock animations, and it",
                        "has no API this mod could drive - so the hug animation can never be shown on YSM",
                        "models, and the mod would only lock the two players in place for six seconds while",
                        "appearing to do nothing. With this on, the mod registers no command, opens no menu and",
                        "rejects every request, and a client shows a warning screen after start up.",
                        "Set it to false if you still want the menu, the placement and the movement lock.")
                .define("disable_with_ysm", true);

        SPEC = builder.build();
    }

    private HugConfig() {
    }

    public static double frontDistance(HugAnimation animation) {
        ModConfigSpec.DoubleValue value = FRONT_DISTANCE.get(animation);
        return value == null ? animation.defaultFrontDistance() : value.get();
    }

    public static boolean alignOnStart() {
        return ALIGN_ON_START.get();
    }

    public static boolean hideOverlays() {
        return HIDE_OVERLAYS.get();
    }

    public static boolean hideOverlaysWithYsm() {
        return HIDE_OVERLAYS_WITH_YSM.get();
    }

    public static boolean disableWithYsm() {
        return DISABLE_WITH_YSM.get();
    }
}
