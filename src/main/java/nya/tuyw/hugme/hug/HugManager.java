package nya.tuyw.hugme.hug;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import nya.tuyw.hugme.HugMe;
import nya.tuyw.hugme.network.HugPayload;
import nya.tuyw.hugme.network.HugPromptPayload;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Server side hug bookkeeping.
 *
 * <h2>How a hug happens</h2>
 * <ol>
 *     <li>{@code /hugme hug <target>} only <b>arms</b> the hug. Nobody is moved, nothing is played.</li>
 *     <li>The target walks to the block in front of the initiator on their own.</li>
 *     <li>As soon as they stand there, the animation starts automatically.</li>
 * </ol>
 * The anchor used for step 2 is recomputed from the initiator's current position and view direction
 * on every tick, so the initiator may keep walking around while waiting.
 *
 * <h2>Movement lock</h2>
 * While the hug plays both players are held where they were standing when it started. The lock is a
 * dead zone: nothing happens until a player drifts more than {@link #LOCK_TOLERANCE} blocks away, so
 * there is no per tick teleporting and the starting position is never altered.
 *
 * <p>All state is only ever touched from the server thread; the event handlers below all fire
 * there, which is why plain collections are safe to use.
 */
@EventBusSubscriber(modid = HugMe.MODID)
public final class HugManager {

    /** Maximum distance between the two players when the request is made. */
    public static final double MAX_REQUEST_DISTANCE = 32.0;
    /** The partner has to be this close to the initiator to accept the request with the key. */
    public static final double ACCEPT_RANGE = 2.0;
    /** ... and roughly on the same level, otherwise they would be placed floating. */
    private static final double ACCEPT_Y_TOLERANCE = 1.2;
    /** How long an armed hug waits for the partner to press the accept key. */
    private static final int REQUEST_TIMEOUT_TICKS = 20 * 30;
    /**
     * How far the initiator may drift before the armed hug is cancelled. Walking away moves the
     * spot the target is supposed to walk to, which would leave them chasing a moving target.
     */
    private static final double INITIATOR_MOVE_TOLERANCE = 0.5;
    /** Movement lock dead zone: only correct once a player drifted further than this. */
    private static final double LOCK_TOLERANCE = 0.35;
    /** Safety net: if something shoves the two players apart this far, the hug ends. */
    private static final double SEPARATION_LIMIT = 3.0;
    /** Every player closer than this to the initiator sees the animation. */
    private static final double BROADCAST_RANGE = 64.0;

    private static final List<ArmedHug> ARMED = new ArrayList<>();
    private static final List<HugSession> SESSIONS = new ArrayList<>();

    private HugManager() {
    }

    /** Outcome of a request, mapped 1:1 to a translation key. */
    public enum RequestResult {
        OK(null),
        SELF("hugme.result.self"),
        INITIATOR_BUSY("hugme.result.initiator_busy"),
        PARTNER_BUSY("hugme.result.partner_busy"),
        DIFFERENT_LEVEL("hugme.result.different_level"),
        TOO_FAR("hugme.result.too_far");

        private final String translationKey;

        RequestResult(String translationKey) {
            this.translationKey = translationKey;
        }

        public boolean failed() {
            return this != OK;
        }

        public Component failureMessage(ServerPlayer partner) {
            return Component.translatable(this.translationKey, partner.getDisplayName());
        }
    }

    /** A hug waiting for the target to walk in front of the initiator. */
    private static final class ArmedHug {
        private final UUID initiator;
        private final UUID partner;
        private final HugAnimation animation;
        private final double initiatorX;
        private final double initiatorY;
        private final double initiatorZ;
        private int ticksRemaining = REQUEST_TIMEOUT_TICKS;

        private ArmedHug(UUID initiator, UUID partner, HugAnimation animation,
                         double initiatorX, double initiatorY, double initiatorZ) {
            this.initiator = initiator;
            this.partner = partner;
            this.animation = animation;
            this.initiatorX = initiatorX;
            this.initiatorY = initiatorY;
            this.initiatorZ = initiatorZ;
        }
    }

    /**
     * Arms a hug. Nobody is teleported, nothing is played yet - the target has to walk over.
     */
    public static RequestResult request(ServerPlayer initiator, ServerPlayer partner, HugAnimation animation) {
        if (initiator == partner) {
            return RequestResult.SELF;
        }
        if (isBusy(initiator.getUUID())) {
            return RequestResult.INITIATOR_BUSY;
        }
        if (isBusy(partner.getUUID())) {
            return RequestResult.PARTNER_BUSY;
        }
        if (initiator.serverLevel() != partner.serverLevel()) {
            return RequestResult.DIFFERENT_LEVEL;
        }
        if (initiator.distanceTo(partner) > MAX_REQUEST_DISTANCE) {
            return RequestResult.TOO_FAR;
        }

        ARMED.add(new ArmedHug(initiator.getUUID(), partner.getUUID(), animation,
                initiator.getX(), initiator.getY(), initiator.getZ()));
        PacketDistributor.sendToPlayer(partner, new HugPromptPayload(initiator.getUUID(), animation.ordinal(),
                REQUEST_TIMEOUT_TICKS, false));
        HugMe.LOGGER.debug("Hug armed: {} -> {} ({})",
                initiator.getGameProfile().getName(), partner.getGameProfile().getName(), animation.getSerializedName());
        return RequestResult.OK;
    }

    /**
     * Whether the partner is close enough for the accept key to work.
     *
     * <p>Shared by the server side check in {@link #accept} and the client HUD prompt, so the prompt
     * is only ever shown when pressing the key would actually do something.
     */
    public static boolean withinAcceptRange(Player initiator, Player partner) {
        return initiator.level().dimension().equals(partner.level().dimension())
                && initiator.distanceTo(partner) <= ACCEPT_RANGE
                && Math.abs(partner.getY() - initiator.getY()) <= ACCEPT_Y_TOLERANCE;
    }

    /** Retracts the HUD prompt on the partner's client. */
    private static void clearPrompt(ArmedHug armed) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerPlayer partner = resolve(server, armed.partner);
        if (partner != null) {
            PacketDistributor.sendToPlayer(partner,
                    new HugPromptPayload(armed.initiator, armed.animation.ordinal(), 0, true));
        }
    }

    /**
     * Request entry point shared by the command and the interaction menu: validates, arms, and tells
     * both players what happens next.
     *
     * @return {@code true} when the request was armed
     */
    public static boolean requestAndReport(ServerPlayer initiator, ServerPlayer partner, HugAnimation animation) {
        RequestResult result = request(initiator, partner, animation);
        if (result.failed()) {
            initiator.sendSystemMessage(result.failureMessage(partner).copy().withStyle(ChatFormatting.RED));
            return false;
        }
        // The "press [key] within N blocks" part is deliberately not in the chat message: the actual
        // key is only known on the client, and KeyMapping#getTranslatedKeyMessage is client only.
        // The HUD prompt (HugPromptOverlay) shows the real bound key instead.
        initiator.sendSystemMessage(Component.translatable("hugme.command.hug.success",
                partner.getDisplayName(), animation.displayName()).withStyle(ChatFormatting.GREEN));
        partner.sendSystemMessage(Component.translatable("hugme.command.hug.received",
                initiator.getDisplayName(), animation.displayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }

    /** Cancels the armed hug or ends the running hug of the given player. */
    public static boolean stop(UUID playerId) {
        ArmedHug armed = findArmed(playerId);
        if (armed != null) {
            ARMED.remove(armed);
            clearPrompt(armed);
            return true;
        }
        HugSession session = findSession(playerId);
        if (session != null) {
            finish(session);
            return true;
        }
        return false;
    }

    private static boolean isBusy(UUID playerId) {
        return findArmed(playerId) != null || findSession(playerId) != null;
    }

    private static ArmedHug findArmed(UUID playerId) {
        for (ArmedHug armed : ARMED) {
            if (armed.initiator.equals(playerId) || armed.partner.equals(playerId)) {
                return armed;
            }
        }
        return null;
    }

    private static HugSession findSession(UUID playerId) {
        for (HugSession session : SESSIONS) {
            if (session.involves(playerId)) {
                return session;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ arming / triggering

    private static void tickArmed(MinecraftServer server) {
        if (ARMED.isEmpty()) {
            return;
        }
        List<ArmedHug> resolved = null;
        for (ArmedHug armed : ARMED) {
            ServerPlayer initiator = resolve(server, armed.initiator);
            ServerPlayer partner = resolve(server, armed.partner);

            if (initiator == null || partner == null || initiator.serverLevel() != partner.serverLevel()) {
                // Someone left or changed dimension: drop it silently.
                clearPrompt(armed);
                resolved = add(resolved, armed);
                continue;
            }
            if (hasMoved(initiator, armed)) {
                initiator.sendSystemMessage(Component.translatable("hugme.message.cancelled_moved_initiator"));
                partner.sendSystemMessage(Component.translatable("hugme.message.cancelled_moved_partner", initiator.getDisplayName()));
                clearPrompt(armed);
                resolved = add(resolved, armed);
                continue;
            }
            if (--armed.ticksRemaining <= 0) {
                initiator.sendSystemMessage(Component.translatable("hugme.message.expired_initiator", partner.getDisplayName()));
                partner.sendSystemMessage(Component.translatable("hugme.message.expired_partner", initiator.getDisplayName()));
                clearPrompt(armed);
                resolved = add(resolved, armed);
            }
            // No walking-up trigger any more: the partner starts the hug with the accept key, see
            // accept().
        }
        if (resolved != null) {
            ARMED.removeAll(resolved);
        }
    }

    private static List<ArmedHug> add(List<ArmedHug> list, ArmedHug armed) {
        if (list == null) {
            list = new ArrayList<>();
        }
        list.add(armed);
        return list;
    }

    /**
     * @return {@code true} when the initiator walked away from where they armed the hug.
     */
    private static boolean hasMoved(ServerPlayer initiator, ArmedHug armed) {
        double dx = initiator.getX() - armed.initiatorX;
        double dy = initiator.getY() - armed.initiatorY;
        double dz = initiator.getZ() - armed.initiatorZ;
        return dx * dx + dy * dy + dz * dz > INITIATOR_MOVE_TOLERANCE * INITIATOR_MOVE_TOLERANCE;
    }

    /**
     * Called when the partner presses the accept key, or when the command is used on their behalf.
     *
     * <p>They have to be within {@link #ACCEPT_RANGE} blocks; the initiator then has the partner
     * placed exactly on the animation's stand distance in front of them, which is what keeps the
     * hug from looking loose or clipped.
     */
    public static void accept(ServerPlayer partner) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ArmedHug armed = findArmed(partner.getUUID());
        if (server == null || armed == null || !armed.partner.equals(partner.getUUID())) {
            partner.sendSystemMessage(Component.translatable("hugme.message.nothing_to_accept")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        ServerPlayer initiator = resolve(server, armed.initiator);
        if (initiator == null) {
            ARMED.remove(armed);
            return;
        }
        if (!withinAcceptRange(initiator, partner)) {
            partner.sendSystemMessage(Component.translatable("hugme.message.accept_too_far",
                    initiator.getDisplayName(), f2(ACCEPT_RANGE)).withStyle(ChatFormatting.RED));
            return;
        }
        ARMED.remove(armed);
        clearPrompt(armed);
        begin(initiator, partner, armed.animation);
    }

    /** Horizontal, normalised view direction of the initiator. */
    private static double[] lookDirection(ServerPlayer initiator) {
        Vec3 look = initiator.getLookAngle();
        double dirX = look.x;
        double dirZ = look.z;
        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 1.0E-4) {
            // Looking straight up or down: fall back to the body yaw.
            float yawRad = initiator.getYRot() * ((float) Math.PI / 180.0F);
            return new double[]{-Math.sin(yawRad), Math.cos(yawRad)};
        }
        return new double[]{dirX / length, dirZ / length};
    }

    /** Starts the animation at the positions both players are standing at right now. */
    private static void begin(ServerPlayer initiator, ServerPlayer partner, HugAnimation animation) {
        double target = HugConfig.frontDistance(animation);
        align(initiator, partner, target);

        HugSession session = new HugSession(
                initiator.getUUID(), partner.getUUID(), animation,
                initiator.getX(), initiator.getY(), initiator.getZ(),
                partner.getX(), partner.getY(), partner.getZ(),
                animation.durationTicks());
        SESSIONS.add(session);
        broadcast(session, initiator, false);
        // INFO on purpose: one line per hug is what makes "did the auto trigger fire?" answerable
        // from a server log at all, the debug level never reaches the log file.
        HugMe.LOGGER.info("Hug started: {} <-> {} ({}) | gap {} blocks (target {}), body gap {}",
                initiator.getGameProfile().getName(), partner.getGameProfile().getName(),
                animation.getSerializedName(),
                f2(horizontalDistance(initiator.getX(), initiator.getZ(), partner.getX(), partner.getZ())),
                f2(target),
                f2(horizontalDistance(initiator.getX(), initiator.getZ(), partner.getX(), partner.getZ()) - animation.bodyOffset()));
    }

    /**
     * Puts the partner exactly on the animation's stand distance, in front of the initiator.
     *
     * <p>People walk in one tick at a time - roughly a quarter of a block - so the distance at the
     * moment the hug triggers varies by that much from hug to hug. A quarter of a block is the
     * difference between a hug that looks tight and two players clipping into each other, and it is
     * why a single fixed distance could not be right for every hug. The correction is at most that
     * quarter block, only ever moves the partner, and can be switched off in the config.
     */
    private static void align(ServerPlayer initiator, ServerPlayer partner, double target) {
        if (!HugConfig.alignOnStart()) {
            return;
        }
        double[] direction = lookDirection(initiator);
        double x = initiator.getX() + direction[0] * target;
        double z = initiator.getZ() + direction[1] * target;
        if (partner.distanceToSqr(x, partner.getY(), z) > 1.0E-4) {
            partner.teleportTo(x, partner.getY(), z);
        }
    }

    private static double horizontalDistance(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String f2(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    // ------------------------------------------------------------------ running hugs

    private static void tickSessions(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        List<HugSession> finished = null;
        for (HugSession session : SESSIONS) {
            ServerPlayer initiator = resolve(server, session.initiator());
            ServerPlayer partner = resolve(server, session.partner());
            if (initiator == null || partner == null) {
                finished = add(finished, session);
                continue;
            }
            lock(initiator, session.initiatorX(), session.initiatorY(), session.initiatorZ(), session, true);
            lock(partner, session.partnerX(), session.partnerY(), session.partnerZ(), session, false);
            if (session.tick() || initiator.distanceTo(partner) > SEPARATION_LIMIT) {
                finished = add(finished, session);
            }
        }
        if (finished != null) {
            finished.forEach(HugManager::finish);
        }
    }

    private static List<HugSession> add(List<HugSession> list, HugSession session) {
        if (list == null) {
            list = new ArrayList<>();
        }
        list.add(session);
        return list;
    }

    /**
     * Holds a player around their anchor like a leash.
     *
     * <p>Inside the dead zone nothing happens at all, so small movements stay fully client
     * authoritative and there is no teleport on every tick. Once a player pushes past the dead zone
     * they are put back <b>onto its boundary</b> rather than snapped to the centre: the correction
     * only cancels the excess, so the anchor never yanks anyone around and can even be slid along.
     *
     * @return how far the player had drifted, {@code 0} when no correction was needed
     */
    private static double lock(ServerPlayer player, double x, double y, double z, HugSession session, boolean isInitiator) {
        double dx = player.getX() - x;
        double dy = player.getY() - y;
        double dz = player.getZ() - z;
        double drift = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (drift <= LOCK_TOLERANCE) {
            return 0.0;
        }
        double scale = LOCK_TOLERANCE / drift;
        player.teleportTo(x + dx * scale, y + dy * scale, z + dz * scale);
        if (isInitiator) {
            session.recordInitiatorCorrection(drift);
        } else {
            session.recordPartnerCorrection(drift);
        }
        return drift;
    }

    private static void finish(HugSession session) {
        SESSIONS.remove(session);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            broadcast(session, resolve(server, session.initiator()), true);
            HugMe.LOGGER.info("Hug ended: {} <-> {} | lock corrections initiator {} (max drift {}), partner {} (max drift {})",
                    nameOf(server, session.initiator()), nameOf(server, session.partner()),
                    session.initiatorCorrections(), f2(session.initiatorMaxDrift()),
                    session.partnerCorrections(), f2(session.partnerMaxDrift()));
        }
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer player = resolve(server, id);
        return player != null ? player.getGameProfile().getName() : id.toString().substring(0, 8);
    }

    /**
     * Sends the start / stop packet to every player that must see it.
     *
     * <p>On start the audience is everyone within 64 blocks. On stop the audience is that same set
     * plus whoever is nearby now plus both participants, so a player that walked away (or logged
     * out and back in) can never keep a frozen pose.
     */
    private static void broadcast(HugSession session, ServerPlayer initiator, boolean stop) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        Set<UUID> recipients = new LinkedHashSet<>();
        if (stop) {
            recipients.addAll(session.audience());
            recipients.add(session.initiator());
            recipients.add(session.partner());
            if (initiator != null) {
                nearbyPlayers(initiator).forEach(player -> recipients.add(player.getUUID()));
            }
        } else {
            session.audience().add(session.initiator());
            session.audience().add(session.partner());
            nearbyPlayers(initiator).forEach(player -> session.audience().add(player.getUUID()));
            recipients.addAll(session.audience());
        }

        HugPayload payload = new HugPayload(session.initiator(), session.partner(), session.animation().ordinal(), stop);
        for (UUID id : recipients) {
            ServerPlayer player = resolve(server, id);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static ServerPlayer resolve(MinecraftServer server, UUID id) {
        return server.getPlayerList().getPlayer(id);
    }

    private static List<ServerPlayer> nearbyPlayers(ServerPlayer center) {
        List<ServerPlayer> players = new ArrayList<>();
        MinecraftServer server = center.getServer();
        if (server == null) {
            return players;
        }
        ServerLevel level = center.serverLevel();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(level.dimension()) && player.distanceTo(center) < BROADCAST_RANGE) {
                players.add(player);
            }
        }
        return players;
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ARMED.isEmpty() && SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            ARMED.clear();
            SESSIONS.clear();
            return;
        }
        tickArmed(server);
        tickSessions(server);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Without this the remaining partner would keep the hug pose on every nearby client.
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ARMED.clear();
        SESSIONS.clear();
    }
}
