package org.academy.internal.client.app.props;

import org.academy.api.common.attribute.AbilityFactor;
import org.academy.internal.common.attribute.PropsMath;
import org.academy.internal.common.attribute.PropsPackets;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.annotation.SubscribePacket;

import java.util.Arrays;

public final class PropsClientState {
    private static final double[] VALUES = new double[AbilityFactor.values().length];
    private static int lockedMask;
    private static boolean started;

    private PropsClientState() {
    }

    public static void init() {
        MisakaNetworkClient.NETWORK_MANAGER.register(PropsClientState.class);
    }

    @SubscribePacket
    public static void sync(PropsPackets.SyncPacket packet) {
        var incoming = packet.values();
        System.arraycopy(incoming, 0, VALUES, 0, Math.min(incoming.length, VALUES.length));
        lockedMask = packet.lockedMask();
        started = packet.started();
    }

    public static double get(AbilityFactor factor) {
        return VALUES[factor.ordinal()];
    }

    public static double total() {
        return Arrays.stream(VALUES).sum();
    }

    public static double coefficient() {
        return PropsMath.acquisitionCoefficient(total());
    }

    public static boolean isLocked(AbilityFactor factor) {
        return (lockedMask & factor.bit()) != 0;
    }

    static void setLockedLocally(AbilityFactor factor, boolean locked) {
        if (locked) {
            lockedMask |= factor.bit();
        } else {
            lockedMask &= ~factor.bit();
        }
    }

    public static boolean isStarted() {
        return started;
    }
}
