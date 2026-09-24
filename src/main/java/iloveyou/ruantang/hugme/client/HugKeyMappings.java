package iloveyou.ruantang.hugme.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import iloveyou.ruantang.hugme.HugMe;
import iloveyou.ruantang.hugme.hug.HugCompatibility;
import iloveyou.ruantang.hugme.network.HugAcceptPayload;
import org.lwjgl.glfw.GLFW;

/**
 * The accept key. The partner presses it while standing within
 * {@link iloveyou.ruantang.hugme.hug.HugManager#ACCEPT_RANGE} blocks of the initiator to start the hug;
 * that is what replaced the old "walk into range and it starts by itself" trigger.
 */
@EventBusSubscriber(modid = HugMe.MODID, value = Dist.CLIENT)
public final class HugKeyMappings {

    public static final KeyMapping ACCEPT = new KeyMapping(
            "key.hugme.accept", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.hugme");

    private HugKeyMappings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ACCEPT);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (HugCompatibility.isDisabled()) {
            return;
        }
        while (ACCEPT.consumeClick()) {
            PacketDistributor.sendToServer(new HugAcceptPayload());
        }
    }
}
