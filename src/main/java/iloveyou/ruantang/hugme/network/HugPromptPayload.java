package iloveyou.ruantang.hugme.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import iloveyou.ruantang.hugme.HugMe;
import iloveyou.ruantang.hugme.client.HugPromptState;
import iloveyou.ruantang.hugme.hug.HugAnimation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Tells the partner's client that a hug request is waiting, so it can show the HUD prompt.
 *
 * <p>Only the partner receives this, and only while the request is armed; {@code clear} retracts it
 * again when the request expires, is cancelled or is accepted.
 */
public record HugPromptPayload(UUID initiator, int animation, int ticksRemaining, boolean clear)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HugPromptPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HugMe.MODID, "hug_prompt"));

    public static final StreamCodec<FriendlyByteBuf, HugPromptPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, HugPromptPayload::initiator,
            ByteBufCodecs.VAR_INT, HugPromptPayload::animation,
            ByteBufCodecs.VAR_INT, HugPromptPayload::ticksRemaining,
            ByteBufCodecs.BOOL, HugPromptPayload::clear,
            HugPromptPayload::new
    );

    static void handle(final HugPromptPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.clear()) {
                HugPromptState.clear();
            } else {
                HugPromptState.set(payload.initiator(), HugAnimation.byOrdinal(payload.animation()),
                        payload.ticksRemaining());
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
