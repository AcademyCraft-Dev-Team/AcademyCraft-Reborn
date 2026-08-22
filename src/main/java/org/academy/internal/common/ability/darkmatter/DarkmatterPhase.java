package org.academy.internal.common.ability.darkmatter;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.darkmatter.DarkmatterPhaseSnapshot;
import org.academy.api.server.ability.AbilitySystemServer;

public final class DarkmatterPhase {
    private DarkmatterPhase() {
    }

    public static DarkmatterPhaseSnapshot snapshot(ServerPlayer player) {
        return AbilitySystemServer.getSystem(player).getDarkmatterResourceManager().getPhaseSnapshot(player);
    }

    public static float alpha(ServerPlayer player) { return snapshot(player).alphaPower(); }
    public static float beta(ServerPlayer player) { return snapshot(player).betaPower(); }
    public static float gamma(ServerPlayer player) { return snapshot(player).activeGammaPower(); }

    public static Weights weights(ServerPlayer player) {
        var snapshot = snapshot(player);
        return new Weights(snapshot.alphaPower(), snapshot.betaPower(), snapshot.activeGammaPower());
    }

    public record Weights(float alpha, float beta, float gamma) {
    }
}
