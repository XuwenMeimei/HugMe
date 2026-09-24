package iloveyou.ruantang.hugme.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import iloveyou.ruantang.hugme.hug.HugAnimation;
import iloveyou.ruantang.hugme.network.HugRequestPayload;

/**
 * The interaction menu: opened by right clicking another player with an empty hand, one button per
 * animation. Clicking one asks the server to arm that hug; the partner then confirms it with the
 * accept key.
 */
public class HugMenuScreen extends Screen {

    private static final int BUTTON_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 3;

    private final AbstractClientPlayer target;

    public HugMenuScreen(AbstractClientPlayer target) {
        super(Component.translatable("hugme.menu.title", target.getDisplayName()));
        this.target = target;
    }

    @Override
    protected void init() {
        HugAnimation[] animations = HugAnimation.values();
        int total = animations.length * (BUTTON_HEIGHT + BUTTON_GAP) - BUTTON_GAP;
        int y = this.height / 2 - total / 2 + 10;

        for (HugAnimation animation : animations) {
            this.addRenderableWidget(Button.builder(animation.displayName(), button -> {
                        PacketDistributor.sendToServer(
                                new HugRequestPayload(this.target.getUUID(), animation.ordinal()));
                        this.onClose();
                    })
                    .bounds(this.width / 2 - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            y += BUTTON_HEIGHT + BUTTON_GAP;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 50, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
