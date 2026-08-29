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
import net.minecraft.world.entity.player.Player;
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
import org.academy.internal.common.ability.aeromanip.skills.lv4.HighSpeedJet;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.sounds.SoundEvents;

import java.util.UUID;

/** Block-face or entity-mounted nozzle shared by High-Speed Jet and ability programs. */
public final class HighSpeedJetNozzle extends Entity {
    private static final EntityDataAccessor<Integer> FACE =
            SynchedEntityData.defineId(HighSpeedJetNozzle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTIVE_TICKS =
            SynchedEntityData.defineId(HighSpeedJetNozzle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SUPPORT_ENTITY_ID =
            SynchedEntityData.defineId(HighSpeedJetNozzle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> OWNER_UUID =
            SynchedEntityData.defineId(HighSpeedJetNozzle.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<BlockPos> SUPPORT_POS =
            SynchedEntityData.defineId(HighSpeedJetNozzle.class, EntityDataSerializers.BLOCK_POS);
    private static final double JET_LENGTH = 14.0;
    private static final double JET_RADIUS = 1.35;
    private static final double MAX_JET_SPEED = 6.0;

    private UUID ownerUuid;
    private BlockPos supportPos = BlockPos.ZERO;
    private UUID supportEntityUuid;
    private int missingSupportTicks;
    private boolean temporary;

    public HighSpeedJetNozzle(EntityType<? extends HighSpeedJetNozzle> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FACE, Direction.UP.get3DDataValue());
        builder.define(ACTIVE_TICKS, 0);
        builder.define(SUPPORT_ENTITY_ID, -1);
        builder.define(OWNER_UUID, "");
        builder.define(SUPPORT_POS, BlockPos.ZERO);
    }

    public void attach(UUID ownerUuid, BlockPos supportPos, Direction face) {
        this.ownerUuid = ownerUuid;
        this.supportPos = supportPos.immutable();
        supportEntityUuid = null;
        entityData.set(OWNER_UUID, ownerUuid == null ? "" : ownerUuid.toString());
        entityData.set(SUPPORT_POS, this.supportPos);
        entityData.set(SUPPORT_ENTITY_ID, -1);
        entityData.set(FACE, face.get3DDataValue());
        setYRot(face.toYRot());
        setXRot(face == Direction.UP ? -90.0f
                : face == Direction.DOWN ? 90.0f : 0.0f);
        snapToSurface();
    }

    public void attach(UUID ownerUuid, Entity support, Vec3 direction) {
        if (support == null) throw new IllegalArgumentException("support entity cannot be null");
        this.ownerUuid = ownerUuid;
        supportEntityUuid = support.getUUID();
        entityData.set(OWNER_UUID, ownerUuid == null ? "" : ownerUuid.toString());
        entityData.set(SUPPORT_POS, BlockPos.ZERO);
        entityData.set(SUPPORT_ENTITY_ID, support.getId());
        setDirection(direction);
        snapToSurface();
    }

    public UUID ownerUuid() {
        var syncedOwner = parseUuid(entityData.get(OWNER_UUID));
        return syncedOwner == null ? ownerUuid : syncedOwner;
    }

    public boolean isOwnedBy(Player player) {
        return player != null && player.getUUID().equals(ownerUuid());
    }

    public Direction face() {
        return Direction.from3DDataValue(entityData.get(FACE));
    }

    public BlockPos supportPos() {
        return entityData.get(SUPPORT_POS);
    }

    public int supportEntityId() {
        return entityData.get(SUPPORT_ENTITY_ID);
    }

    public boolean isEntityMounted() {
        return supportEntityUuid != null || supportEntityId() >= 0;
    }

    public Vec3 direction() {
        var direction = Vec3.directionFromRotation(getXRot(), getYRot());
        return direction.lengthSqr() <= 1.0e-8 ? new Vec3(0.0, 1.0, 0.0) : direction.normalize();
    }

    public boolean isAttachedTo(BlockPos pos, Direction face) {
        return supportEntityUuid == null && entityData.get(SUPPORT_ENTITY_ID) < 0
                && supportPos.equals(pos) && face() == face;
    }

    public boolean isAttachedTo(Entity entity) {
        return entity != null && supportEntityUuid != null
                && supportEntityUuid.equals(entity.getUUID());
    }

    public int activeTicks() {
        return entityData.get(ACTIVE_TICKS);
    }

    public void activate(int durationTicks) {
        entityData.set(ACTIVE_TICKS,
                Math.max(activeTicks(), Math.max(1, durationTicks)));
    }

    public void restoreActiveTicks(int durationTicks) {
        entityData.set(ACTIVE_TICKS, Math.max(0, durationTicks));
    }

    public void markTemporary() {
        temporary = true;
    }

    public boolean isTemporary() {
        return temporary;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!(level() instanceof ServerLevel serverLevel)) return;
        if (hasSupport()) {
            missingSupportTicks = 0;
            snapToSurface();
        } else if (++missingSupportTicks > 100) {
            discard();
            return;
        }
        var owner = ownerUuid == null ? null
                : serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
        if (owner != null && (owner.level() != serverLevel
                || HighSpeedJet.isOutsideNozzleRetentionRange(owner.position(), position()))) {
            discard();
            return;
        }
        var remaining = activeTicks();
        if (remaining <= 0) return;
        entityData.set(ACTIVE_TICKS, remaining - 1);
        if (owner == null
                || !owner.isAlive() || !Skills.HIGH_SPEED_JET.get().isEnabled(owner)) {
            entityData.set(ACTIVE_TICKS, 0);
            if (temporary) discard();
            return;
        }
        applyJet(serverLevel, owner);
        if (remaining % 6 == 0) spawnVfx(serverLevel);
        if (remaining % 12 == 0) {
            serverLevel.playSound(null, blockPosition(), SoundEvents.AIRFLOW_FIELD.get(),
                    SoundSource.PLAYERS, 0.28f, 1.45f);
        }
        if (temporary && remaining == 1) discard();
    }

    private void applyJet(ServerLevel level, ServerPlayer owner) {
        var direction = direction();
        var milestone = Skills.HIGH_SPEED_JET.get().getEffectiveProficiencyMilestone(owner);
        var acceleration = 0.12 * (milestone >= 3 ? 1.25 : 1.0);
        Entity support = null;
        if (isEntityMounted()) {
            support = supportEntity();
            if (support == null || !support.isAlive()) return;
            if (support != owner) {
                var mountedTarget = support;
                EntityMotionGuard.runWithMotionSource(owner, () ->
                        AeromanipTargeting.accelerateAlong(
                                mountedTarget,
                                HighSpeedJet.entityThrustDirection(direction),
                                acceleration,
                                MAX_JET_SPEED,
                                MAX_JET_SPEED));
                support.resetFallDistance();
            }
        }

        var origin = position().add(direction.scale(0.18));
        var rangeScale = AeromanipConfig.rangeMultiplier(owner, SkillNames.HIGH_SPEED_JET);
        var resolvedEnd = origin.add(direction.scale(JET_LENGTH * rangeScale));
        var bounds = new AABB(origin, resolvedEnd).inflate(JET_RADIUS * rangeScale);
        var mountedTarget = support;
        for (var target : level.getEntities(this, bounds, target ->
                target != owner
                        && target != mountedTarget
                        && target.isAlive()
                        && !(target instanceof HighSpeedJetNozzle))) {
            if (!insideCapsule(target.getBoundingBox().getCenter(), origin, resolvedEnd,
                    JET_RADIUS * rangeScale + target.getBbWidth() * 0.5)) continue;
            EntityMotionGuard.runWithMotionSource(owner, () ->
                    AeromanipTargeting.accelerateAlong(
                            target, direction, acceleration, MAX_JET_SPEED, MAX_JET_SPEED));
            target.resetFallDistance();
        }
    }

    private void spawnVfx(ServerLevel level) {
        var direction = direction();
        var origin = position().add(direction.scale(0.25));
        AeromanipVfx.stream(level, origin, direction, JET_LENGTH * 0.72);
    }

    private boolean hasSupport() {
        if (supportEntityUuid != null || entityData.get(SUPPORT_ENTITY_ID) >= 0) {
            var support = supportEntity();
            return support != null && support.isAlive() && !support.isRemoved();
        }
        return !level().getBlockState(supportPos).isAir()
                && level().getBlockState(supportPos)
                .isFaceSturdy(level(), supportPos, face());
    }

    private void snapToSurface() {
        var direction = direction();
        var support = supportEntity();
        var center = support == null
                ? Vec3.atCenterOf(supportPos).add(direction.scale(0.535))
                : AeromanipTargeting.pointOutside(support.getBoundingBox(), direction, 0.08);
        setPos(center.x, center.y, center.z);
    }

    private Entity supportEntity() {
        Entity support = null;
        var supportId = entityData.get(SUPPORT_ENTITY_ID);
        if (supportId >= 0) support = level().getEntity(supportId);
        if (support == null && supportEntityUuid != null && level() instanceof ServerLevel serverLevel) {
            support = serverLevel.getEntity(supportEntityUuid);
            if (support != null) entityData.set(SUPPORT_ENTITY_ID, support.getId());
        }
        return support;
    }

    private void setDirection(Vec3 direction) {
        var normalized = direction == null || direction.lengthSqr() <= 1.0e-8
                ? new Vec3(0.0, 1.0, 0.0)
                : direction.normalize();
        setYRot((float) (Math.toDegrees(Math.atan2(normalized.z, normalized.x)) - 90.0));
        setXRot((float) -Math.toDegrees(Math.asin(Math.clamp(normalized.y, -1.0, 1.0))));
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
    public boolean shouldBeSaved() {
        return !temporary;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        input.getString("academy_owner").ifPresent(value -> ownerUuid = parseUuid(value));
        supportPos = new BlockPos(
                input.getIntOr("academy_support_x", 0),
                input.getIntOr("academy_support_y", 0),
                input.getIntOr("academy_support_z", 0));
        supportEntityUuid = input.getString("academy_support_entity")
                .map(HighSpeedJetNozzle::parseUuid)
                .orElse(null);
        entityData.set(SUPPORT_ENTITY_ID, -1);
        entityData.set(OWNER_UUID, ownerUuid == null ? "" : ownerUuid.toString());
        entityData.set(SUPPORT_POS, supportPos);
        entityData.set(FACE, Direction.from3DDataValue(
                input.getIntOr("academy_face", Direction.UP.get3DDataValue())).get3DDataValue());
        entityData.set(ACTIVE_TICKS, Math.max(0,
                input.getIntOr("academy_active_ticks", 0)));
        setYRot(input.getFloatOr("academy_direction_yaw", face().toYRot()));
        setXRot(input.getFloatOr("academy_direction_pitch",
                face() == Direction.UP ? -90.0f : face() == Direction.DOWN ? 90.0f : 0.0f));
        if (supportEntityUuid == null || supportEntity() != null) {
            snapToSurface();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (ownerUuid != null) output.putString("academy_owner", ownerUuid.toString());
        output.putInt("academy_support_x", supportPos.getX());
        output.putInt("academy_support_y", supportPos.getY());
        output.putInt("academy_support_z", supportPos.getZ());
        if (supportEntityUuid != null) {
            output.putString("academy_support_entity", supportEntityUuid.toString());
        }
        output.putInt("academy_face", face().get3DDataValue());
        output.putInt("academy_active_ticks", activeTicks());
        output.putFloat("academy_direction_yaw", getYRot());
        output.putFloat("academy_direction_pitch", getXRot());
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
