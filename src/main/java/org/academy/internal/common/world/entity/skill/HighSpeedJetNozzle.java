package org.academy.internal.common.world.entity.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.sounds.SoundEvents;

import java.util.UUID;

/** Persistent block-face nozzle placed by High-Speed Jet. */
public final class HighSpeedJetNozzle extends Entity {
    private static final EntityDataAccessor<Integer> FACE =
            SynchedEntityData.defineId(HighSpeedJetNozzle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTIVE_TICKS =
            SynchedEntityData.defineId(HighSpeedJetNozzle.class, EntityDataSerializers.INT);
    private static final double JET_LENGTH = 14.0;
    private static final double JET_RADIUS = 1.35;
    private static final double MAX_JET_SPEED = 6.0;

    private UUID ownerUuid;
    private BlockPos supportPos = BlockPos.ZERO;

    public HighSpeedJetNozzle(EntityType<? extends HighSpeedJetNozzle> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FACE, Direction.UP.get3DDataValue());
        builder.define(ACTIVE_TICKS, 0);
    }

    public void attach(UUID ownerUuid, BlockPos supportPos, Direction face) {
        this.ownerUuid = ownerUuid;
        this.supportPos = supportPos.immutable();
        entityData.set(FACE, face.get3DDataValue());
        snapToSurface();
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public boolean isOwnedBy(ServerPlayer player) {
        return player != null && player.getUUID().equals(ownerUuid);
    }

    public Direction face() {
        return Direction.from3DDataValue(entityData.get(FACE));
    }

    public int activeTicks() {
        return entityData.get(ACTIVE_TICKS);
    }

    public void activate(int durationTicks) {
        entityData.set(ACTIVE_TICKS,
                Math.max(activeTicks(), Math.max(1, durationTicks)));
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        snapToSurface();
        if (level().isClientSide()) return;
        if ((tickCount % 20) == 0 && !hasSupport()) {
            discard();
            return;
        }
        var remaining = activeTicks();
        if (remaining <= 0 || !(level() instanceof ServerLevel serverLevel)) return;
        entityData.set(ACTIVE_TICKS, remaining - 1);
        var owner = ownerUuid == null ? null
                : serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
        if (owner == null || owner.level() != serverLevel
                || !owner.isAlive() || !Skills.HIGH_SPEED_JET.get().isEnabled(owner)) {
            entityData.set(ACTIVE_TICKS, 0);
            return;
        }
        applyJet(serverLevel, owner);
        if (remaining % 6 == 0) spawnVfx(serverLevel);
        if (remaining % 12 == 0) {
            serverLevel.playSound(null, blockPosition(), SoundEvents.AIRFLOW_FIELD.get(),
                    SoundSource.PLAYERS, 0.28f, 1.45f);
        }
    }

    private void applyJet(ServerLevel level, ServerPlayer owner) {
        var direction = Vec3.atLowerCornerOf(face().getUnitVec3i());
        var origin = position().add(direction.scale(0.18));
        var milestone = Skills.HIGH_SPEED_JET.get().getEffectiveProficiencyMilestone(owner);
        var acceleration = 0.12 * (milestone >= 3 ? 1.25 : 1.0);
        var rangeScale = AeromanipConfig.rangeMultiplier(owner, SkillNames.HIGH_SPEED_JET);
        var resolvedEnd = origin.add(direction.scale(JET_LENGTH * rangeScale));
        var bounds = new AABB(origin, resolvedEnd).inflate(JET_RADIUS * rangeScale);
        for (var target : level.getEntities(this, bounds, target ->
                target.isAlive() && !(target instanceof HighSpeedJetNozzle))) {
            if (!insideCapsule(target.getBoundingBox().getCenter(), origin, resolvedEnd,
                    JET_RADIUS * rangeScale + target.getBbWidth() * 0.5)) continue;
            EntityMotionGuard.runWithMotionSource(owner, () ->
                    AeromanipTargeting.accelerateAlong(
                            target, direction, acceleration, MAX_JET_SPEED, MAX_JET_SPEED));
            target.resetFallDistance();
        }
    }

    private void spawnVfx(ServerLevel level) {
        var direction = Vec3.atLowerCornerOf(face().getUnitVec3i());
        var origin = position().add(direction.scale(0.25));
        AeromanipVfx.stream(level, origin, direction, JET_LENGTH * 0.72);
    }

    private boolean hasSupport() {
        return !level().getBlockState(supportPos).isAir()
                && level().getBlockState(supportPos)
                .isFaceSturdy(level(), supportPos, face());
    }

    private void snapToSurface() {
        var direction = Vec3.atLowerCornerOf(face().getUnitVec3i());
        var center = Vec3.atCenterOf(supportPos).add(direction.scale(0.535));
        setPos(center.x, center.y, center.z);
        setYRot(face().toYRot());
        setXRot(face() == Direction.UP ? -90.0f
                : face() == Direction.DOWN ? 90.0f : 0.0f);
    }

    static boolean insideCapsule(Vec3 point, Vec3 start, Vec3 end, double radius) {
        if (point == null || start == null || end == null || !Double.isFinite(radius)) return false;
        var segment = end.subtract(start);
        var lengthSqr = segment.lengthSqr();
        var t = lengthSqr <= 1.0e-8 ? 0.0
                : Math.max(0.0, Math.min(1.0,
                point.subtract(start).dot(segment) / lengthSqr));
        return point.distanceToSqr(start.add(segment.scale(t)))
                <= Math.max(0.0, radius) * Math.max(0.0, radius);
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof ServerPlayer player && isOwnedBy(player)) {
            discard();
            return true;
        }
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        input.getString("academy_owner").ifPresent(value -> ownerUuid = parseUuid(value));
        supportPos = new BlockPos(
                input.getIntOr("academy_support_x", 0),
                input.getIntOr("academy_support_y", 0),
                input.getIntOr("academy_support_z", 0));
        entityData.set(FACE, Direction.from3DDataValue(
                input.getIntOr("academy_face", Direction.UP.get3DDataValue())).get3DDataValue());
        entityData.set(ACTIVE_TICKS, Math.max(0,
                input.getIntOr("academy_active_ticks", 0)));
        snapToSurface();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (ownerUuid != null) output.putString("academy_owner", ownerUuid.toString());
        output.putInt("academy_support_x", supportPos.getX());
        output.putInt("academy_support_y", supportPos.getY());
        output.putInt("academy_support_z", supportPos.getZ());
        output.putInt("academy_face", face().get3DDataValue());
        output.putInt("academy_active_ticks", activeTicks());
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
