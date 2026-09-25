package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VectorAttackAttributionResolver {
    static final long RECENT_EVIDENCE_TICKS = 10L;
    private static final String ORIGINAL_OWNER_TAG = "academy_vector_original_owner";
    private static final Map<UUID, RecentEvidence> RECENT = new HashMap<>();

    private VectorAttackAttributionResolver() {
    }

    public static ResolvedAttribution resolve(ServerPlayer defender, DamageSource source) {
        if (defender == null || source == null) return ResolvedAttribution.EMPTY;
        var attacker = resolveImmediate(defender, source);
        var damageTypeId = VectorCompatProfile.damageTypeId(source);
        if (attacker != null) {
            remember(defender, attacker, damageTypeId);
        } else {
            attacker = resolveRecent(defender, damageTypeId);
        }
        var direction = resolveEffectDirection(defender, source, attacker);
        return new ResolvedAttribution(attacker, source.getDirectEntity(), direction);
    }

    public static void rememberFromSource(ServerPlayer defender, DamageSource source) {
        if (defender == null || source == null) return;
        var attacker = resolveImmediate(defender, source);
        if (attacker != null) remember(defender, attacker, VectorCompatProfile.damageTypeId(source));
    }

    public static void captureProjectileOwner(Projectile projectile) {
        if (projectile == null || projectile.level().isClientSide()) return;
        var owner = projectile.getOwner();
        if (owner == null || projectile.getPersistentData().contains(ORIGINAL_OWNER_TAG)) return;
        projectile.getPersistentData().putString(ORIGINAL_OWNER_TAG, owner.getUUID().toString());
    }

    public static @Nullable UUID originalProjectileOwnerId(Projectile projectile) {
        if (projectile == null) return null;
        var value = projectile.getPersistentData().getString(ORIGINAL_OWNER_TAG).orElse("");
        if (!value.isBlank()) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                // Fall back to the current owner when another mod wrote malformed data.
            }
        }
        var owner = projectile.getOwner();
        return owner == null ? null : owner.getUUID();
    }

    public static void clear(ServerPlayer defender) {
        if (defender != null) RECENT.remove(defender.getUUID());
    }

    private static @Nullable LivingEntity resolveImmediate(ServerPlayer defender, DamageSource source) {
        var causing = unwrapLiving(source.getEntity());
        if (validAttacker(defender, causing)) return causing;

        var direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile) {
            var owner = unwrapLiving(projectile.getOwner());
            if (validAttacker(defender, owner)) return owner;
            var ownerId = originalProjectileOwnerId(projectile);
            if (ownerId != null && defender.level() instanceof ServerLevel level) {
                var persistedOwner = unwrapLiving(level.getEntity(ownerId));
                if (validAttacker(defender, persistedOwner)) return persistedOwner;
            }
        }

        var directLiving = unwrapLiving(direct);
        return validAttacker(defender, directLiving) ? directLiving : null;
    }

    private static @Nullable LivingEntity unwrapLiving(@Nullable Entity entity) {
        if (entity instanceof LivingEntity living) return living;
        if (entity instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity living) return living;
        return null;
    }

    private static boolean validAttacker(ServerPlayer defender, @Nullable LivingEntity attacker) {
        return attacker != null
                && attacker != defender
                && attacker.isAlive()
                && !attacker.isRemoved()
                && attacker.level() == defender.level();
    }

    private static void remember(ServerPlayer defender, LivingEntity attacker, String damageTypeId) {
        RECENT.put(defender.getUUID(), new RecentEvidence(
                attacker.getUUID(),
                defender.level().dimension(),
                damageTypeId,
                defender.level().getGameTime()
        ));
    }

    private static @Nullable LivingEntity resolveRecent(ServerPlayer defender, String damageTypeId) {
        var evidence = RECENT.get(defender.getUUID());
        if (evidence == null) return null;
        var now = defender.level().getGameTime();
        if (!isRecentEvidence(
                now,
                evidence.gameTime,
                evidence.dimension.equals(defender.level().dimension()),
                evidence.damageTypeId.equals(damageTypeId))) {
            RECENT.remove(defender.getUUID(), evidence);
            return null;
        }
        if (!(defender.level() instanceof ServerLevel level)) return null;
        var attacker = unwrapLiving(level.getEntity(evidence.attackerId));
        return validAttacker(defender, attacker) ? attacker : null;
    }

    static boolean isRecentEvidence(
            long now,
            long recordedAt,
            boolean sameDimension,
            boolean sameDamageType
    ) {
        var age = now - recordedAt;
        return age >= 0L
                && age <= RECENT_EVIDENCE_TICKS
                && sameDimension
                && sameDamageType;
    }

    private static Vec3 resolveEffectDirection(
            ServerPlayer defender,
            DamageSource source,
            @Nullable LivingEntity attacker
    ) {
        var center = defender.getBoundingBox().getCenter();
        var direct = source.getDirectEntity();
        if (direct != null) {
            var direction = normalize(direct.getBoundingBox().getCenter().subtract(center));
            if (direction != Vec3.ZERO) return direction;
        }
        var sourcePosition = source.getSourcePosition();
        if (sourcePosition != null) {
            var direction = normalize(sourcePosition.subtract(center));
            if (direction != Vec3.ZERO) return direction;
        }
        if (attacker != null) {
            var direction = normalize(attacker.getBoundingBox().getCenter().subtract(center));
            if (direction != Vec3.ZERO) return direction;
        }
        return normalize(defender.getLookAngle());
    }

    private static Vec3 normalize(Vec3 value) {
        if (value == null || !Double.isFinite(value.lengthSqr()) || value.lengthSqr() < 1.0E-8) {
            return Vec3.ZERO;
        }
        return value.normalize();
    }

    public record ResolvedAttribution(
            @Nullable LivingEntity attacker,
            @Nullable Entity directEntity,
            Vec3 effectDirection
    ) {
        private static final ResolvedAttribution EMPTY = new ResolvedAttribution(null, null, Vec3.ZERO);
    }

    private record RecentEvidence(
            UUID attackerId,
            ResourceKey<Level> dimension,
            String damageTypeId,
            long gameTime
    ) {
    }
}
