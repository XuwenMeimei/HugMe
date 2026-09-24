package nya.tuyw.hugme.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import nya.tuyw.hugme.HugMe;
import nya.tuyw.hugme.hug.HugAnimation;
import nya.tuyw.hugme.hug.HugManager;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * "Play animation X with that player" - sent by the interaction menu when an entry is clicked.
 */
public record HugRequestPayload(UUID target, int animation) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HugRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HugMe.MODID, "hug_request"));

    public static final StreamCodec<FriendlyByteBuf, HugRequestPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, HugRequestPayload::target,
            ByteBufCodecs.VAR_INT, HugRequestPayload::animation,
            HugRequestPayload::new
    );

    static void handle(final HugRequestPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(payload.target());
            if (target == null) {
                return;
            }
            HugManager.requestAndReport(player, target, HugAnimation.byOrdinal(payload.animation()));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
