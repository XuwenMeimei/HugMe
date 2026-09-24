package iloveyou.ruantang.hugme.hug;

import net.neoforged.fml.ModList;

/**
 * Runtime compatibility switches.
 *
 * <p>Before anything else this hides the fine print of a real conflict: the two hug animations move
 * the receiver's body about a block towards the sender, which is how the two bodies end up touching
 * while their collision boxes stay apart. The shadow and the name tag are drawn at the <i>entity</i>
 * position by {@code EntityRenderDispatcher}, so they cannot follow the model - which is why the mod
 * hides them for the two players involved.
 *
 * <p><b>Yes Steve Model</b> replaces the player model and animates it with its own Bedrock
 * animations. Those animations have no such body offset, so on a YSM client the shadow is already in
 * the right place and hiding it is not only unnecessary but removes a correct shadow. YSM is
 * therefore detected at runtime and the hiding is skipped for it.
 *
 * <p>There is deliberately no deeper integration: YSM 2.6.x (the current 1.21.1 NeoForge release)
 * ships no API at all - the {@code @YsmExtension} extension mechanism only exists in its unreleased
 * 3.0 line - and even there an adapter may only project a mod's state into bounded animation inputs
 * (a Molang query, a controller predicate, a render context hint); the animation itself has to live
 * in the YSM model pack, authored by whoever made that model. So the hug animation cannot be shown
 * on YSM models from here. Everything else - menu, accept key, HUD prompt, precise placement, the
 * movement lock and the camera handling - works exactly as usual with YSM installed.
 */
public final class HugCompatibility {

    /** Yes Steve Model's mod id, which is not the shorthand "ysm". */
    private static final String YES_STEVE_MODEL_ID = "yes_steve_model";

    private static Boolean yesSteveModelLoaded;

    private HugCompatibility() {
    }

    /** @return whether Yes Steve Model is loaded; resolved once, never throws. */
    public static boolean isYesSteveModelLoaded() {
        if (yesSteveModelLoaded == null) {
            boolean loaded = false;
            try {
                loaded = ModList.get() != null && ModList.get().isLoaded(YES_STEVE_MODEL_ID);
            } catch (Throwable throwable) {
                // A compatibility probe must never be able to break the mod.
                loaded = false;
            }
            yesSteveModelLoaded = loaded;
        }
        return yesSteveModelLoaded;
    }

    /**
     * @return whether the shadow and the name tag of hugging players should be hidden, which depends
     *         on whether Yes Steve Model is the one rendering those players.
     */
    public static boolean hideShadowAndNameTag() {
        return isYesSteveModelLoaded() ? HugConfig.hideOverlaysWithYsm() : HugConfig.hideOverlays();
    }
}
