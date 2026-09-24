package iloveyou.ruantang.hugme.hug;

import net.neoforged.fml.ModList;

/**
 * Yes Steve Model detection.
 *
 * <p><b>Yes Steve Model</b> replaces the player model and animates it with its own Bedrock
 * animations. Those animations live in the YSM model pack and cannot be driven from outside: YSM
 * 2.6.x, the current 1.21.1 NeoForge release, ships no API at all - the {@code @YsmExtension}
 * extension mechanism only exists in its unreleased 3.0 line - and even there an adapter may only
 * project a mod's state into bounded animation inputs (a Molang query, a controller predicate, a
 * render context hint), never play a foreign animation itself.
 *
 * <p>So on a client running YSM the hug animation can never be shown. The mod would only lock the
 * two players in place for six seconds, hide their shadow and name tag for nothing, and look like it
 * did nothing at all - and mixing the two is a reliable source of bugs. There is therefore no
 * coexistence mode: as soon as YSM is in the pack the whole mod switches itself off, before it
 * registers a command, an interaction menu, a key bind or a packet handler. The client says so on
 * screen after start up, this being the only thing that still runs.
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
     * @return whether the whole mod is switched off because Yes Steve Model is in the pack.
     *
     *         <p>This is not configurable on purpose; see the class comment.
     */
    public static boolean isDisabled() {
        return isYesSteveModelLoaded();
    }
}
