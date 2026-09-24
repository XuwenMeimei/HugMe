package iloveyou.ruantang.hugme.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import iloveyou.ruantang.hugme.HugMe;
import iloveyou.ruantang.hugme.hug.HugCompatibility;

import java.util.List;

/**
 * The warning the client shows when the mod disabled itself because Yes Steve Model is in the pack.
 *
 * <p>It is opened once, over the title screen, as soon as the game is up - a chat line would be
 * missed and the mod-list entry only says the mod is there, not why nothing happens.
 */
@EventBusSubscriber(modid = HugMe.MODID, value = Dist.CLIENT)
public final class HugWarning {

    private static final int TEXT_WIDTH = 320;
    private static final int TITLE_COLOR = 0xFFFF5555;
    private static final int BODY_COLOR = 0xFFFFFFFF;

    private static boolean shown;

    private HugWarning() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (shown || !HugCompatibility.isDisabled()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof TitleScreen titleScreen)) {
            return;
        }
        shown = true;
        client.setScreen(new WarningScreen(titleScreen));
    }

    private static final class WarningScreen extends Screen {

        private final Screen parent;

        private WarningScreen(Screen parent) {
            super(Component.translatable("hugme.ysm.warning.title"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            this.addRenderableWidget(Button.builder(CommonComponents.GUI_OK, button -> this.onClose())
                    .bounds(this.width / 2 - 100, this.height / 2 + 50, 200, 20)
                    .build());
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);

            List<FormattedCharSequence> lines = this.font.split(
                    Component.translatable("hugme.ysm.warning.body"), TEXT_WIDTH);

            int y = this.height / 2 - 60;
            guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, y, TITLE_COLOR);
            y += 22;
            for (FormattedCharSequence line : lines) {
                guiGraphics.drawString(this.font, line, this.width / 2 - this.font.width(line) / 2, y, BODY_COLOR);
                y += 11;
            }
        }

        @Override
        public void onClose() {
            // Back to whatever was on screen before (normally the title screen), not out of the game.
            this.minecraft.setScreen(this.parent);
        }
    }
}
