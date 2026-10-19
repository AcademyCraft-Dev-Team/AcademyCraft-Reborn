package org.academy.internal.common.world.entity.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.config.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.targeting.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.vfx.AeromanipVfx;
import org.academy.internal.common.ability.aeromanip.skills.lv4.HighSpeedJet;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileStateAdapter;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.sounds.SoundEvents;

import java.util.UUID;

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
    private StructureRef structureSupport;
    private BlockPos structureSupportOffset;
    private Direction structureSupportFace;
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
        structureSupport = null;
        structureSupportOffset = null;
        structureSupportFace = null;
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
        structureSupport = null;
        structureSupportOffset = null;
        structureSupportFace = null;
        entityData.set(OWNER_UUID, ownerUuid == null ? "" : ownerUuid.toString());
        entityData.set(SUPPORT_POS, BlockPos.ZERO);
        entityData.set(SUPPORT_ENTITY_ID, support.getId());
        setDirection(direction);
        snapToSurface();
    }

    public void attachToStructure(
            UUID ownerUuid,
            StructureRef support,
            BlockPos supportOffset,
            Direction face
    ) {
        if (support == null || supportOffset == null || face == null) {
            throw new IllegalArgumentException("Invalid block structure nozzle support");
        }
        this.ownerUuid = ownerUuid;
        supportEntityUuid = null;
        structureSupport = support;
        structureSupportOffset = supportOffset.immutable();
        structureSupportFace = face;
        entityData.set(OWNER_UUID, ownerUuid == null ? "" : ownerUuid.toString());
        entityData.set(SUPPORT_ENTITY_ID, -1);
        entityData.set(FACE, face.get3DDataValue());
        setDirection(Vec3.atLowerCornerOf(face.getUnitVec3i()));
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
        return structureSupport == null && supportEntityUuid == null && entityData.get(SUPPORT_ENTITY_ID) < 0
                && supportPos.equals(pos) && face() == face;
    }

    public boolean isAttachedTo(Entity entity) {
        return entity != null && supportEntityUuid != null
                && supportEntityUuid.equals(entity.getUUID());
    }

    public boolean isAttachedTo(StructureRef support) {
        return support != null && support.equals(structureSupport);
    }

    /** True while this nozzle rides a captured structure instead of a host block or entity. */
    public boolean isStructureMounted() {
        return structureSupport != null;
    }

    /** Stable local mount for shared whole-structure thrust; host positions are only projections. */
    public record StructureMount(StructureRef reference, BlockPos offset, Direction face) {
    }

    public java.util.Optional<StructureMount> structureMount() {
        return structureSupport == null || structureSupportOffset == null || structureSupportFace == null
                ? java.util.Optional.empty()
                : java.util.Optional.of(new StructureMount(structureSupport, structureSupportOffset, structureSupportFace));
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
        } else {
            entityData.set(ACTIVE_TICKS, 0);
            if (++missingSupportTicks > 100) discard();
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
            if (support != owner && !isStructureMounted()) {
                accelerateTarget(
                        owner,
                        support,
                        HighSpeedJet.entityThrustDirection(direction),
                        acceleration);
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
            accelerateTarget(owner, target, direction, acceleration);
            target.resetFallDistance();
        }
    }

    private static void accelerateTarget(
            ServerPlayer owner,
            Entity target,
            Vec3 direction,
            double acceleration
    ) {
        EntityMotionGuard.runWithMotionSource(owner, () -> {
            AeromanipTargeting.accelerateAlong(
                    target, direction, acceleration, MAX_JET_SPEED, MAX_JET_SPEED);
            if (target instanceof Projectile projectile) {
                VectorProjectileStateAdapter.resumeFlight(
                        projectile, projectile.getDeltaMovement());
            }
        });
    }

    public boolean jetIntersects(AABB bounds, ServerPlayer owner) {
        if (bounds == null || owner == null || owner.level() != level()) return false;
        var direction = direction();
        var rangeScale = AeromanipConfig.rangeMultiplier(owner, SkillNames.HIGH_SPEED_JET);
        var origin = position().add(direction.scale(0.18));
        var end = origin.add(direction.scale(JET_LENGTH * rangeScale));
        var expanded = bounds.inflate(JET_RADIUS * rangeScale);
        return expanded.contains(origin) || expanded.clip(origin, end).isPresent();
    }

    private void spawnVfx(ServerLevel level) {
        var direction = direction();
        var origin = position().add(direction.scale(0.25));
        AeromanipVfx.stream(level, origin, direction, JET_LENGTH * 0.72);
    }

    private boolean hasSupport() {
        if (supportEntityUuid != null || entityData.get(SUPPORT_ENTITY_ID) >= 0) {
            var support = supportEntity();
            if (support != null && support.isAlive() && !support.isRemoved()) return true;
            if (structureSupportOffset != null
                    && !level().getBlockState(supportPos).isAir()
                    && level().getBlockState(supportPos)
                    .isFaceSturdy(level(), supportPos, face())) {
                supportEntityUuid = null;
                structureSupportOffset = null;
                structureSupportFace = null;
                entityData.set(SUPPORT_ENTITY_ID, -1);
                return true;
            }
            return false;
        }
        if (structureSupport != null) {
            // A missing/unloaded local cell never changes the nozzle's content owner.
            return structureSupportOffset != null && level() instanceof ServerLevel serverLevel
                    && org.academy.internal.common.ability.aeromanip.movement.HighSpeedJetStructureService
                    .resolvesSupportCell(serverLevel, structureSupport, structureSupportOffset);
        }
        return hasBlockSupport();
    }

    private boolean hasBlockSupport() {
        return !level().getBlockState(supportPos).isAir()
                && level().getBlockState(supportPos)
                .isFaceSturdy(level(), supportPos, face());
    }

    private void snapToSurface() {
        if (structureSupport != null
                && structureSupportOffset != null
                && structureSupportFace != null) {
            var pose = structurePose();
            if (pose == null) return;
            var direction = pose.localToWorldDirection(
                    Vec3.atLowerCornerOf(structureSupportFace.getUnitVec3i()));
            if (!Double.isFinite(direction.x) || direction.lengthSqr() <= 1.0e-8) {
                direction = new Vec3(0.0, 1.0, 0.0);
            }
            direction = direction.normalize();
            setDirection(direction);
            entityData.set(FACE, HighSpeedJet.nearestFace(direction).get3DDataValue());
            var rotatedCenter = pose.localToWorldPoint(new Vec3(
                    structureSupportOffset.getX() + 0.5,
                    structureSupportOffset.getY() + 0.5,
                    structureSupportOffset.getZ() + 0.5
            ));
            supportPos = BlockPos.containing(rotatedCenter);
            entityData.set(SUPPORT_POS, supportPos);
            var center = rotatedCenter.add(direction.scale(0.535));
            setPos(center.x, center.y, center.z);
            return;
        }
        var support = supportEntity();
        var direction = direction();
        var center = support == null
                ? Vec3.atCenterOf(supportPos).add(direction.scale(0.535))
                : AeromanipTargeting.pointOutside(support.getBoundingBox(), direction, 0.08);
        setPos(center.x, center.y, center.z);
    }

    private StructurePose structurePose() {
        if (structureSupport == null || !(level() instanceof ServerLevel serverLevel)) return null;
        var handle = StructureSpaces.resolve(serverLevel.getServer(), structureSupport).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) return null;
        try {
            return handle.pose();
        } catch (IllegalStateException unavailable) {
            return null;
        }
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
                : Math.clamp(
                point.subtract(start).dot(segment) / lengthSqr, 0.0, 1.0);
        return point.distanceToSqr(start.add(segment.scale(t)))
                <= Math.max(0.0, radius) * Math.max(0.0, radius);
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean isPickable() {
        // A nozzle is a skill attachment, not a selectable aim target; its bounding box
        // otherwise steals entity rays aimed just above the support surface.
        return false;
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
        structureSupport = readStructureSupport(input);
        if (structureSupport != null) {
            structureSupportOffset = new BlockPos(
                    input.getIntOr("academy_structure_support_x", 0),
                    input.getIntOr("academy_structure_support_y", 0),
                    input.getIntOr("academy_structure_support_z", 0));
            structureSupportFace = Direction.from3DDataValue(input.getIntOr(
                    "academy_structure_support_face", Direction.UP.get3DDataValue()));
        } else {
            structureSupportOffset = null;
            structureSupportFace = null;
        }
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
        if (structureSupport != null && structureSupportOffset != null && structureSupportFace != null) {
            output.putBoolean("academy_has_structure_support", true);
            output.putString("academy_structure_support_id", structureSupport.id().toString());
            output.putString("academy_structure_support_dimension",
                    structureSupport.dimension().toString());
            output.putString("academy_structure_support_backend",
                    structureSupport.backend().toString());
            output.putLong("academy_structure_support_generation", structureSupport.generation());
            output.putInt("academy_structure_support_x", structureSupportOffset.getX());
            output.putInt("academy_structure_support_y", structureSupportOffset.getY());
            output.putInt("academy_structure_support_z", structureSupportOffset.getZ());
            output.putInt("academy_structure_support_face",
                    structureSupportFace.get3DDataValue());
        }
        output.putInt("academy_face", face().get3DDataValue());
        output.putInt("academy_active_ticks", activeTicks());
        output.putFloat("academy_direction_yaw", getYRot());
        output.putFloat("academy_direction_pitch", getXRot());
    }

    private static StructureRef readStructureSupport(ValueInput input) {
        var id = input.getString("academy_structure_support_id")
                .map(HighSpeedJetNozzle::parseUuid)
                .orElse(null);
        if (id == null) return null;
        var dimension = input.getString("academy_structure_support_dimension")
                .map(Identifier::tryParse).orElse(null);
        var backend = input.getString("academy_structure_support_backend")
                .map(Identifier::tryParse).orElse(null);
        if (dimension == null || backend == null) return null;
        var generation = input.getLongOr("academy_structure_support_generation", 0L);
        return generation < 0 ? null : new StructureRef(id, dimension, backend, generation);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
