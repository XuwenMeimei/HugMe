package nya.tuyw.hugme.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import nya.tuyw.hugme.HugMe;
import nya.tuyw.hugme.hug.HugManager;
import org.jetbrains.annotations.NotNull;

/**
 * "Let me out of this hug" - sent when the player double taps Shift.
 */
public record HugStopPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HugStopPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HugMe.MODID, "hug_stop"));

    public static final StreamCodec<FriendlyByteBuf, HugStopPayload> STREAM_CODEC =
            StreamCodec.unit(new HugStopPayload());

    static void handle(final HugStopPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && HugManager.stop(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("hugme.message.force_stopped"));
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
