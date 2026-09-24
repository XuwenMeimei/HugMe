package nya.tuyw.hugme.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import nya.tuyw.hugme.HugMe;

/**
 * Every payload of the mod, registered in one place.
 *
 * <p>Keep all registrations on a single registrar: splitting them over several subscribers works
 * but makes the channel versioning needlessly hard to reason about.
 */
@EventBusSubscriber(modid = HugMe.MODID)
public final class HugNetwork {

    private HugNetwork() {
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // server -> client: play / release the animation, and the "press to accept" prompt
        registrar.playToClient(HugPayload.TYPE, HugPayload.STREAM_CODEC, HugClientHandler::handle);
        registrar.playToClient(HugPromptPayload.TYPE, HugPromptPayload.STREAM_CODEC, HugPromptPayload::handle);

        // client -> server
        registrar.playToServer(HugRequestPayload.TYPE, HugRequestPayload.STREAM_CODEC, HugRequestPayload::handle);
        registrar.playToServer(HugAcceptPayload.TYPE, HugAcceptPayload.STREAM_CODEC, HugAcceptPayload::handle);
        registrar.playToServer(HugStopPayload.TYPE, HugStopPayload.STREAM_CODEC, HugStopPayload::handle);
    }
}
