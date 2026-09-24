package nya.tuyw.hugme.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import nya.tuyw.hugme.HugMe;
import nya.tuyw.hugme.hug.HugAnimation;
import nya.tuyw.hugme.hug.HugManager;

/**
 * The "press [key] to accept" prompt, drawn just right of the crosshair while a hug request is
 * waiting and the requester is actually close enough for the key to work.
 *
 * <p>It sits in the middle of the right half of the screen: clear of the crosshair and of the
 * action bar, and it gets a translucent backdrop because that spot is over the world - against
 * bright terrain plain text is hard to read, even with the vanilla one pixel text shadow.
 *
 * <p>Outside the accept range nothing is drawn: the prompt would be a lie, because the server
 * rejects an accept from further away.
 */
@EventBusSubscriber(modid = HugMe.MODID, value = Dist.CLIENT)
public final class HugPromptOverlay {

    private static final int REQUESTER_COLOR = 0xFFD0D0D0;
    private static final int KEY_COLOR = 0xFFFFE27F;
    private static final int LINE_HEIGHT = 10;
    /**
     * Where the prompt sits horizontally: {@code 0.75} is the middle of the right half of the
     * screen. The text block is centred on that point.
     */
    private static final double HORIZONTAL_POSITION = 0.75D;
    private static final int PADDING_X = 4;
    private static final int PADDING_Y = 3;
    /** Translucent black backdrop. */
    private static final int BACKDROP_COLOR = 0x90000000;

    private HugPromptOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!HugPromptState.isActive()) {
            return;
        }
        HugAnimation animation = HugPromptState.animation();
        if (animation == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            return;
        }
        if (!(level.getPlayerByUUID(HugPromptState.initiator()) instanceof AbstractClientPlayer initiator)) {
            return;
        }
        if (!HugManager.withinAcceptRange(initiator, player)) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = client.font;

        Component requested = Component.translatable("hugme.hud.requested",
                initiator.getDisplayName(), animation.displayName());
        Component accept = Component.translatable("hugme.hud.accept",
                HugKeyMappings.ACCEPT.getTranslatedKeyMessage());

        int width = Math.max(font.width(requested), font.width(accept));
        int height = LINE_HEIGHT * 2;
        int x = (int) (graphics.guiWidth() * HORIZONTAL_POSITION) - width / 2;
        int y = (graphics.guiHeight() - height) / 2;

        graphics.fill(x - PADDING_X, y - PADDING_Y,
                x + width + PADDING_X, y + height + PADDING_Y, BACKDROP_COLOR);

        graphics.drawString(font, requested, x, y, REQUESTER_COLOR);
        graphics.drawString(font, accept, x, y + LINE_HEIGHT, KEY_COLOR);
    }
}
