package iloveyou.ruantang.hugme;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import iloveyou.ruantang.hugme.hug.HugCompatibility;
import iloveyou.ruantang.hugme.hug.HugConfig;
import org.slf4j.Logger;

/**
 * Hug Me! — animation only edition.
 *
 * <p>Everything the mod does is:
 * <ul>
 *     <li>{@code /hugme hug <target> [animation]} starts a hug between two players,</li>
 *     <li>{@code /hugme stop} ends it early,</li>
 *     <li>the hug animation is replicated to every player within 64 blocks.</li>
 * </ul>
 *
 * <p>The item, the hug ticket, the request / accept / reject chat flow, the recipe and the
 * first-join gift of the original mod have been removed on purpose.
 */
@Mod(HugMe.MODID)
public final class HugMe {

    public static final String MODID = "hugme";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HugMe(IEventBus modEventBus, ModContainer modContainer) {
        // Per animation stand distance and the start alignment toggle; see HugConfig for why.
        modContainer.registerConfig(ModConfig.Type.COMMON, HugConfig.SPEC);
        if (HugCompatibility.isYesSteveModelLoaded()) {
            LOGGER.warn("Yes Steve Model detected in this pack. See disable_with_ysm in"
                    + " config/hugme-common.toml - the hug animation cannot be shown on YSM models.");
        }
        LOGGER.info("Hug Me! (animation + command edition) loaded");
    }
}
