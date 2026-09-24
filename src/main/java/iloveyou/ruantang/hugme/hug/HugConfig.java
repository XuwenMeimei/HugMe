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
}
