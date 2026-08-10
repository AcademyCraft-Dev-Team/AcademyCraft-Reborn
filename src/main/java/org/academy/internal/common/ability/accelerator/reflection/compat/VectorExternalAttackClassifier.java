package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.world.damagesource.VectorRedirectedDamageSourceInfo;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class VectorExternalAttackClassifier {
    private static final Set<String> DENIED_DAMAGE_NAMES = Set.of(
            "in_fire",
            "on_fire",
            "lava",
            "hot_floor",
            "in_wall",
            "cramming",
            "drown",
            "starve",
            "cactus",
            "fall",
            "fly_into_wall",
            "fell_out_of_world",
            "generic_kill",
            "magic",
            "indirect_magic",
            "wither",
            "dragon_breath",
            "dry_out",
            "sweet_berry_bush",
            "freeze",
            "stalagmite",
            "outside_border",
            "thorns",
            "sting",
            "lightning_bolt",
            "anvil",
            "falling_block",
            "stalactite"
    );
    private static final double MELEE_DISTANCE_SQR = 16.0;

    private VectorExternalAttackClassifier() {
    }

    public static Optional<VectorAttackDescriptor> classify(
            ServerPlayer defender,
            DamageSource source,
            float damage
    ) {
        if (defender == null
                || source == null
                || !(damage > 0.0f)
                || !Float.isFinite(damage)
                || isDenied(defender, source)) {
            return Optional.empty();
        }

        var profileEntry = VectorCompatProfileRegistry.find(source).orElse(null);
        if (profileEntry != null && profileEntry.profile().deny()) return Optional.empty();
        var nativeExact = source instanceof SkillDamageSource;
        var resolvedAttribution = VectorAttackAttributionResolver.resolve(defender, source);
        if (!nativeExact
                && profileEntry == null
                && VectorCompatProfileRegistry.mode() == VectorCompatibilityMode.STRICT) {
            return Optional.empty();
        }
        var profile = profileEntry == null ? null : profileEntry.profile();
        var inference = profile == null
                ? inferDirection(defender, source)
                : inferProfileDirection(defender, source, profile.direction());
        if (inference.confidence() == VectorAttackConfidence.NONE
                && resolvedAttribution.attacker() != null) {
            var origin = resolvedAttribution.attacker().getBoundingBox().getCenter();
            var direction = normalizeFinite(defender.getBoundingBox().getCenter().subtract(origin));
            if (direction != Vec3.ZERO) {
                inference = new VectorDirectionInference(
                        origin,
                        direction,
                        VectorAttackConfidence.LOW,
                        "resolved_attribution_fallback"
                );
            }
        }
        if (inference.confidence() == VectorAttackConfidence.NONE) return Optional.empty();
        var tier = nativeExact
                ? VectorCompatibilityTier.NATIVE_EXACT
                : profile != null
                ? VectorCompatibilityTier.PROFILED_LINEAR
                : inference.confidence().atLeast(VectorAttackConfidence.HIGH)
                ? VectorCompatibilityTier.INFERRED_HITSCAN
                : VectorCompatibilityTier.DAMAGE_FALLBACK;
        var policy = profile == null
                ? VectorExecutionPolicy.safeDefault()
                : profile.executionPolicy();
        var fingerprint = VectorAttackFingerprint.compute(
                defender.level().getGameTime(),
                defender.getId(),
                source,
                inference.origin(),
                inference.direction()
        );
        return Optional.of(new VectorAttackDescriptor(
                defender,
                source,
                inference.origin(),
                inference.direction(),
                profile == null ? policy.maximumRange() : profile.range(),
                profile == null ? 0.25 : profile.radius(),
                damage,
                new VectorAttackAttribution(
                        resolvedAttribution.attacker(),
                        source.getDirectEntity(),
                        VectorCompatProfile.damageTypeId(source),
                        nativeExact
                ),
                tier,
                inference.confidence(),
                policy,
                fingerprint
        ));
    }

    public static String rejectionReason(
            ServerPlayer defender,
            DamageSource source,
            float damage
    ) {
        if (defender == null || source == null) return "invalid_context";
        if (!(damage > 0.0f) || !Float.isFinite(damage)) return "invalid_damage";
        if (VectorRedirectedDamageSourceInfo.isRedirected(source)) return "redirect_depth";
        if (source.getDirectEntity() != null
                && VectorMotionRedirects.isRedirected(source.getDirectEntity())) {
            return "direct_entity_redirect_depth";
        }
        if (source.getDirectEntity() instanceof Projectile projectile
                && VectorProjectileRedirects.isRedirected(projectile)) {
            return "projectile_redirect_depth";
        }
        if (source.getEntity() == defender || source.getDirectEntity() == defender) return "self_damage";
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return "excluded_explosion";
        if (source.is(DamageTypeTags.IS_FALL)) return "excluded_fall";
        if (source.is(DamageTypeTags.IS_FIRE)) return "excluded_fire";
        var name = source.getMsgId().toLowerCase(Locale.ROOT);
        if (isExplicitlyDeniedDamageName(name)) return "excluded_damage_type=" + name;
        var causing = source.getEntity();
        if (source.getDirectEntity() == causing
                && causing instanceof LivingEntity
                && causing.distanceToSqr(defender) <= MELEE_DISTANCE_SQR) {
            return "excluded_melee";
        }
        var profile = VectorCompatProfileRegistry.find(source).orElse(null);
        if (profile != null && profile.profile().deny()) {
            return "denied_by_profile=" + profile.id();
        }
        if (!(source instanceof SkillDamageSource)
                && profile == null
                && VectorCompatProfileRegistry.mode() == VectorCompatibilityMode.STRICT) {
            return "strict_requires_profile";
        }
        return inferDirection(defender, source).confidence() == VectorAttackConfidence.NONE
                ? "no_direction_evidence"
                : "unclassified";
    }

    static boolean isDenied(ServerPlayer defender, DamageSource source) {
        if (VectorRedirectedDamageSourceInfo.isRedirected(source)) return true;
        if (source.getDirectEntity() != null
                && VectorMotionRedirects.isRedirected(source.getDirectEntity())) {
            return true;
        }
        if (source.getDirectEntity() instanceof Projectile projectile
                && VectorProjectileRedirects.isRedirected(projectile)) {
            return true;
        }
        var causing = source.getEntity();
        var direct = source.getDirectEntity();
        if (causing == defender || direct == defender) return true;
        if (source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_FIRE)) {
            return true;
        }
        var name = source.getMsgId().toLowerCase(Locale.ROOT);
        if (isExplicitlyDeniedDamageName(name)) return true;
        return direct == causing
                && causing instanceof LivingEntity
                && causing.distanceToSqr(defender) <= MELEE_DISTANCE_SQR;
    }

    static boolean isExplicitlyDeniedDamageName(String name) {
        return name != null && DENIED_DAMAGE_NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    static VectorDirectionInference inferDirection(ServerPlayer defender, DamageSource source) {
        var target = defender.getBoundingBox().getCenter();
        var direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile) {
            var velocity = normalizeFinite(projectile.getDeltaMovement());
            if (velocity != Vec3.ZERO && aimsAt(projectile.position(), velocity, defender.getBoundingBox())) {
                return new VectorDirectionInference(
                        projectile.position(),
                        velocity,
                        VectorAttackConfidence.EXACT,
                        "projectile_velocity"
                );
            }
        }

        var sourcePosition = source.getSourcePosition();
        if (isFinite(sourcePosition)) {
            var direction = normalizeFinite(target.subtract(sourcePosition));
            if (direction != Vec3.ZERO) {
                return new VectorDirectionInference(
                        sourcePosition,
                        direction,
                        VectorAttackConfidence.HIGH,
                        "damage_source_position"
                );
            }
        }

        if (direct != null) {
            var velocity = normalizeFinite(direct.getDeltaMovement());
            if (velocity != Vec3.ZERO && aimsAt(direct.position(), velocity, defender.getBoundingBox().inflate(0.25))) {
                return new VectorDirectionInference(
                        direct.position(),
                        velocity,
                        VectorAttackConfidence.HIGH,
                        "direct_entity_motion"
                );
            }
        }

        var attacker = source.getEntity();
        if (attacker != null) {
            var origin = attacker.getEyePosition();
            var look = normalizeFinite(attacker.getLookAngle());
            if (look != Vec3.ZERO && aimsAt(origin, look, defender.getBoundingBox().inflate(0.25))) {
                return new VectorDirectionInference(
                        origin,
                        look,
                        VectorAttackConfidence.MEDIUM,
                        "attacker_look"
                );
            }
            var direction = normalizeFinite(target.subtract(attacker.getBoundingBox().getCenter()));
            if (direction != Vec3.ZERO) {
                var confidence = VectorCompatProfileRegistry.mode() == VectorCompatibilityMode.AGGRESSIVE
                        ? VectorAttackConfidence.MEDIUM
                        : VectorAttackConfidence.LOW;
                return new VectorDirectionInference(
                        attacker.getBoundingBox().getCenter(),
                        direction,
                        confidence,
                        "attacker_to_defender_fallback"
                );
            }
        }
        return VectorDirectionInference.none(target, "no_direction_evidence");
    }

    private static VectorDirectionInference inferProfileDirection(
            ServerPlayer defender,
            DamageSource source,
            VectorCompatProfile.DirectionMode mode
    ) {
        if (mode == VectorCompatProfile.DirectionMode.AUTO) {
            var inferred = inferDirection(defender, source);
            if (inferred.confidence() == VectorAttackConfidence.NONE) return inferred;
            return new VectorDirectionInference(
                    inferred.origin(),
                    inferred.direction(),
                    VectorAttackConfidence.HIGH,
                    "profile_" + inferred.reason()
            );
        }
        var target = defender.getBoundingBox().getCenter();
        if (mode == VectorCompatProfile.DirectionMode.SOURCE_POSITION) {
            var origin = source.getSourcePosition();
            var direction = isFinite(origin) ? normalizeFinite(target.subtract(origin)) : Vec3.ZERO;
            return direction == Vec3.ZERO
                    ? VectorDirectionInference.none(target, "profile_missing_source_position")
                    : new VectorDirectionInference(origin, direction, VectorAttackConfidence.HIGH, "profile_source_position");
        }
        var direct = source.getDirectEntity();
        if (mode == VectorCompatProfile.DirectionMode.DIRECT_MOTION) {
            var direction = direct == null ? Vec3.ZERO : normalizeFinite(direct.getDeltaMovement());
            return direction == Vec3.ZERO
                    ? VectorDirectionInference.none(target, "profile_missing_direct_motion")
                    : new VectorDirectionInference(direct.position(), direction, VectorAttackConfidence.HIGH, "profile_direct_motion");
        }
        var attacker = source.getEntity();
        if (attacker == null) {
            return VectorDirectionInference.none(target, "profile_missing_attacker");
        }
        var origin = mode == VectorCompatProfile.DirectionMode.ATTACKER_LOOK
                ? attacker.getEyePosition()
                : attacker.getBoundingBox().getCenter();
        var direction = mode == VectorCompatProfile.DirectionMode.ATTACKER_LOOK
                ? normalizeFinite(attacker.getLookAngle())
                : normalizeFinite(target.subtract(origin));
        return direction == Vec3.ZERO
                ? VectorDirectionInference.none(target, "profile_invalid_attacker_direction")
                : new VectorDirectionInference(origin, direction, VectorAttackConfidence.HIGH, "profile_attacker_direction");
    }

    private static boolean aimsAt(Vec3 origin, Vec3 direction, AABB target) {
        var distance = Math.max(4.0, origin.distanceTo(target.getCenter()) + 2.0);
        return target.clip(origin, origin.add(direction.scale(distance))).isPresent();
    }

    private static Vec3 normalizeFinite(Vec3 value) {
        if (!isFinite(value)) return Vec3.ZERO;
        var lengthSqr = value.lengthSqr();
        return Double.isFinite(lengthSqr) && lengthSqr > 1.0E-8
                ? value.normalize()
                : Vec3.ZERO;
    }

    private static boolean isFinite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
