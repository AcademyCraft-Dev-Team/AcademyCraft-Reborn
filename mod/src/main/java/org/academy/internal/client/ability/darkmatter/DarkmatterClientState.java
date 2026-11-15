package org.academy.internal.client.ability.darkmatter;

import org.academy.internal.common.ability.darkmatter.network.SyncDarkmatterStatePacket;
import org.misaka.api.common.network.annotation.SubscribePacket;

public final class DarkmatterClientState {
    private static volatile int level;
    private static volatile int totalPoints;
    private static volatile int alphaPoints;
    private static volatile boolean gammaActive;
    private static volatile float naturalMatter;
    private static volatile float createdMatter;
    private static volatile float reservedMatter;

    private DarkmatterClientState() {
    }

    @SubscribePacket
    public static void handleSync(SyncDarkmatterStatePacket packet) {
        level = packet.abilityLevel();
        totalPoints = packet.totalPoints();
        alphaPoints = packet.alphaPoints();
        gammaActive = packet.gammaActive();
        naturalMatter = packet.naturalMatter();
        createdMatter = packet.createdMatter();
        reservedMatter = packet.reservedMatter();
    }

    public static float phase() {
        return beta() * 2.0f - 1.0f;
    }

    public static float alpha() {
        return totalPoints <= 0 ? 0.5f : alphaPoints / (float) totalPoints;
    }

    public static float beta() {
        return 1.0f - alpha();
    }

    public static float gamma() {
        if (totalPoints <= 0) return 0.0f;
        var beta = totalPoints - alphaPoints;
        return 2.0f * Math.min(alphaPoints, beta) / totalPoints;
    }

    public static int level() {
        return level;
    }

    public static int totalPoints() {
        return totalPoints;
    }

    public static int alphaPoints() {
        return alphaPoints;
    }

    public static boolean gammaActive() {
        return gammaActive;
    }

    public static float naturalMatter() {
        return naturalMatter;
    }

    public static float createdMatter() {
        return createdMatter;
    }

    public static float reservedMatter() {
        return reservedMatter;
    }
}
