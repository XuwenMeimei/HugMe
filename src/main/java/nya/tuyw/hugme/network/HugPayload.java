package nya.tuyw.hugme.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import nya.tuyw.hugme.HugMe;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * "Start / stop the hug between these two players" - the only packet the server sends.
 *
 * <p>It is broadcast to everyone that can see the hug, not just the two participants.
 *
 * @param initiator   the player that started the hug
 * @param partner     the player that got hugged
 * @param animation   ordinal of {@link nya.tuyw.hugme.hug.HugAnimation}
 * @param stop        {@code true} releases the pose, {@code false} plays the animation
 */
public record HugPayload(UUID initiator, UUID partner, int animation, boolean stop) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HugPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HugMe.MODID, "hug"));

    public static final StreamCodec<FriendlyByteBuf, HugPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, HugPayload::initiator,
            UUIDUtil.STREAM_CODEC, HugPayload::partner,
            ByteBufCodecs.VAR_INT, HugPayload::animation,
            ByteBufCodecs.BOOL, HugPayload::stop,
            HugPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
