package org.academy.internal.common.misaka;

/**
 * Client-mirrored orbital-charge readout. Written by the charge sync packet on the client;
 * read by the designator item bar (safe on dedicated server — stays inactive).
 */
public final class MisakaOrbitalChargeDisplay {
    private static boolean active;
    private static float charge;
    private static float need = 1000.0f;
    private static float rate;

    private MisakaOrbitalChargeDisplay() {
    }

    public static void set(boolean nowActive, float nowCharge, float nowNeed, float nowRate) {
        active = nowActive;
        charge = Math.max(0.0f, nowCharge);
        need = Math.max(1.0f, nowNeed);
        rate = Math.max(0.0f, nowRate);
        if (!active) {
            charge = 0.0f;
            rate = 0.0f;
        }
    }

    public static void clear() {
        set(false, 0.0f, need, 0.0f);
    }

    public static boolean isActive() {
        return active;
    }

    public static float charge() {
        return charge;
    }

    public static float need() {
        return need;
    }

    public static float rate() {
        return rate;
    }

    public static float progress() {
        if (!active || !(need > 0.0f)) {
            return 0.0f;
        }
        return Math.min(1.0f, charge / need);
    }
}
