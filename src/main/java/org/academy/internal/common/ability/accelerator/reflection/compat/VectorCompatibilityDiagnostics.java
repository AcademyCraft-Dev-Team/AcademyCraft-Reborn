package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.List;

public final class VectorCompatibilityDiagnostics {
    private static final int MAX_ENTRIES = 32;
    private static final ArrayDeque<Entry> RECENT = new ArrayDeque<>();

    private VectorCompatibilityDiagnostics() {
    }

    public static synchronized void record(VectorAttackDescriptor attack, String outcome) {
        while (RECENT.size() >= MAX_ENTRIES) RECENT.removeFirst();
        RECENT.addLast(new Entry(
                attack.source().getMsgId(),
                attack.attribution().directEntity() == null
                        ? "none"
                        : BuiltInRegistries.ENTITY_TYPE.getKey(
                                attack.attribution().directEntity().getType()
                        ).toString(),
                attack.direction(),
                attack.confidence(),
                attack.tier(),
                outcome,
                attack.fingerprint()
        ));
    }

    public static synchronized void recordPassThrough(
            ServerPlayer defender,
            DamageSource source,
            String reason
    ) {
        while (RECENT.size() >= MAX_ENTRIES) RECENT.removeFirst();
        var direct = source.getDirectEntity();
        RECENT.addLast(new Entry(
                VectorCompatProfile.damageTypeId(source),
                direct == null
                        ? "none"
                        : BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).toString(),
                Vec3.ZERO,
                VectorAttackConfidence.NONE,
                VectorCompatibilityTier.PASS_THROUGH,
                reason,
                VectorAttackFingerprint.compute(
                        defender.level().getGameTime(),
                        defender.getId(),
                        source,
                        defender.getBoundingBox().getCenter(),
                        Vec3.ZERO
                )
        ));
    }

    public static synchronized List<Entry> recent() {
        return List.copyOf(RECENT);
    }

    public record Entry(
            String damageType,
            String directEntityType,
            net.minecraft.world.phys.Vec3 direction,
            VectorAttackConfidence confidence,
            VectorCompatibilityTier tier,
            String outcome,
            long fingerprint
    ) {
    }
}
