package iloveyou.ruantang.hugme.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import iloveyou.ruantang.hugme.HugMe;
import iloveyou.ruantang.hugme.hug.HugManager;
import org.jetbrains.annotations.NotNull;

/**
 * "I accept the hug request" - sent when the partner presses the accept key.
 */
public record HugAcceptPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HugAcceptPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HugMe.MODID, "hug_accept"));

    public static final StreamCodec<FriendlyByteBuf, HugAcceptPayload> STREAM_CODEC =
            StreamCodec.unit(new HugAcceptPayload());

    static void handle(final HugAcceptPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                HugManager.accept(player);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
