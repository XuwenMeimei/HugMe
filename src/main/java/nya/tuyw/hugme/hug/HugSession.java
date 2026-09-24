package nya.tuyw.hugme.hug;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One running hug. Server side only, always touched from the server thread.
 *
 * <p>The two positions stored here are the places the players were standing at the moment the hug
 * triggered - they are used as the anchor for the movement lock, so nobody is ever moved anywhere
 * they did not walk to themselves.
 */
public final class HugSession {

    private final UUID initiator;
    private final UUID partner;
    private final HugAnimation animation;

    private final double initiatorX;
    private final double initiatorY;
    private final double initiatorZ;
    private final double partnerX;
    private final double partnerY;
    private final double partnerZ;

    /** Everyone that already received the "hug started" packet, so they all get the "hug stopped" one. */
    private final Set<UUID> audience = new LinkedHashSet<>();

    private int ticksRemaining;

    // Diagnostics: how often and how far the lock had to correct each player. Logged when the hug
    // ends, which is what makes "the server keeps moving people" answerable from the server console.
    private int initiatorCorrections;
    private int partnerCorrections;
    private double initiatorMaxDrift;
    private double partnerMaxDrift;

    public HugSession(UUID initiator, UUID partner, HugAnimation animation,
                      double initiatorX, double initiatorY, double initiatorZ,
                      double partnerX, double partnerY, double partnerZ,
                      int durationTicks) {
        this.initiator = initiator;
        this.partner = partner;
        this.animation = animation;
        this.initiatorX = initiatorX;
        this.initiatorY = initiatorY;
        this.initiatorZ = initiatorZ;
        this.partnerX = partnerX;
        this.partnerY = partnerY;
        this.partnerZ = partnerZ;
        this.ticksRemaining = durationTicks;
    }

    public UUID initiator() {
        return this.initiator;
    }

    public UUID partner() {
        return this.partner;
    }

    public HugAnimation animation() {
        return this.animation;
    }

    public double initiatorX() {
        return this.initiatorX;
    }

    public double initiatorY() {
        return this.initiatorY;
    }

    public double initiatorZ() {
        return this.initiatorZ;
    }

    public double partnerX() {
        return this.partnerX;
    }

    public double partnerY() {
        return this.partnerY;
    }

    public double partnerZ() {
        return this.partnerZ;
    }

    public Set<UUID> audience() {
        return this.audience;
    }

    public boolean involves(UUID playerId) {
        return this.initiator.equals(playerId) || this.partner.equals(playerId);
    }

    public void recordInitiatorCorrection(double drift) {
        this.initiatorCorrections++;
        this.initiatorMaxDrift = Math.max(this.initiatorMaxDrift, drift);
    }

    public void recordPartnerCorrection(double drift) {
        this.partnerCorrections++;
        this.partnerMaxDrift = Math.max(this.partnerMaxDrift, drift);
    }

    public int initiatorCorrections() {
        return this.initiatorCorrections;
    }

    public int partnerCorrections() {
        return this.partnerCorrections;
    }

    public double initiatorMaxDrift() {
        return this.initiatorMaxDrift;
    }

    public double partnerMaxDrift() {
        return this.partnerMaxDrift;
    }

    /** @return {@code true} once the hug ran out of ticks. */
    public boolean tick() {
        return --this.ticksRemaining <= 0;
    }
}
