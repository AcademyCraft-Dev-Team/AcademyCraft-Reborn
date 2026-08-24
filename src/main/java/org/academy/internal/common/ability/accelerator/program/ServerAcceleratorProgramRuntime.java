package org.academy.internal.common.ability.accelerator.program;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileStateAdapter;
import org.academy.internal.common.ability.accelerator.skills.lv2.KineticEnergyApplied;
import org.academy.internal.common.ability.program.ProgramActionTransaction;
import org.academy.internal.common.ability.program.ProgramPowerScale;
import org.academy.internal.common.ability.program.ServerProgramTargetResolver;
import org.academy.internal.common.ability.teleport.TeleportSync;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Authoritative Minecraft-server adapter for vector-manipulation programs. */
public final class ServerAcceleratorProgramRuntime implements AcceleratorProgramRuntime {
    public static final double MAX_QUERY_RANGE = 32.0;
    public static final int MAX_QUERY_RESULTS = 128;
    private static final double PROJECTILE_QUERY_RANGE = 16.0;

    private final ServerPlayer player;
    private final float costMultiplier;
    private final ImpactBatch impactBatch = new ImpactBatch();

    public ServerAcceleratorProgramRuntime(ServerPlayer player) {
        this(player, 1.0f);
    }

    public ServerAcceleratorProgramRuntime(ServerPlayer player, float costMultiplier) {
        this.player = Objects.requireNonNull(player, "player");
        this.costMultiplier = requireCostMultiplier(costMultiplier);
    }

    @Override
    public Object caster() {
        return player;
    }

    @Override
    public Optional<Object> lookTarget() {
        var origin = worldPosition(player.getEyePosition());
        var look = player.getViewVector(1.0f);
        if (!isFiniteNonZero(look)) return Optional.empty();
        return raycastEntity(
                origin,
                new ProgramDirection(look.x, look.y, look.z),
                MAX_QUERY_RANGE
        );
    }

    @Override
    public Optional<ProgramBlockPosition> lookBlockTarget() {
        var origin = worldPosition(player.getEyePosition());
        var look = player.getViewVector(1.0f);
        if (!isFiniteNonZero(look)) return Optional.empty();
        return raycastBlock(
                origin,
                new ProgramDirection(look.x, look.y, look.z),
                MAX_QUERY_RANGE
        );
    }

    @Override
    public List<?> incomingProjectiles() {
        var center = player.getBoundingBox().getCenter();
        return level().getEntitiesOfClass(
                        Projectile.class,
                        player.getBoundingBox().inflate(PROJECTILE_QUERY_RANGE),
                        projectile -> projectile.isAlive()
                                && !projectile.isRemoved()
                                && projectile.getOwner() != player
                                && isFiniteNonZero(projectile.getDeltaMovement())
                                && projectile.getDeltaMovement().dot(
                                center.subtract(projectile.getBoundingBox().getCenter())) > 0.0
                ).stream()
                .sorted(Comparator.comparing(projectile -> projectile.getUUID().toString()))
                .limit(MAX_QUERY_RESULTS)
                .toList();
    }

    @Override
    public ProgramActionTransaction.ProgramAction applyVector(
            Object entityReference,
            ProgramDirection direction,
            AcceleratorProgramStrength strength
    ) {
        var impulse = vector(direction).scale(vectorImpulse(strength));
        return new ProgramActionTransaction.ProgramAction() {
            private Entity target;

            @Override
            public void validate() {
                requireCasterReady(Skills.VECTOR_ACCEL.get());
                target = requireMovableEntity(entityReference);
                requireEntityInRange(target, MAX_QUERY_RANGE);
                requireFriendlyMovement(target);
                if (!isFiniteNonZero(impulse)) {
                    throw new IllegalArgumentException("Vector impulse is invalid");
                }
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                if (!EntityMotionGuard.canApplyMotionFrom(player, target)) {
                    throw new IllegalStateException("Target rejected forced movement");
                }
                charge(Skills.VECTOR_ACCEL.get(), vectorCost(strength));
                var previous = target.getDeltaMovement();
                setVelocity(target, previous.add(impulse));
                return () -> {
                    if (sameUsableLevel(target)) setVelocity(target, previous);
                };
            }
        };
    }

    @Override
    public ProgramActionTransaction.ProgramAction kineticImpact(
            Object entityReference,
            ProgramDirection direction,
            AcceleratorProgramStrength strength
    ) {
        return impactAction(() -> requireMovableEntity(entityReference)
                .getBoundingBox().getCenter(), direction, impactLevel(strength), null);
    }

    @Override
    public ProgramActionTransaction.ProgramAction kineticShockwave(
            ProgramWorldPosition position,
            ProgramDirection direction,
            float power,
            boolean destroyBlocks,
            int radius
    ) {
        return impactAction(
                () -> requireLocalPosition(position),
                direction,
                KineticEnergyApplied.PROGRAM_IMPACT_LEVEL,
                new ConfiguredShockwave(power, destroyBlocks, radius)
        );
    }

    @Override
    public ProgramActionTransaction.ProgramAction redirectProjectile(
            Object projectileReference,
            ProgramDirection direction
    ) {
        var normalized = normalized(direction);
        return new ProgramActionTransaction.ProgramAction() {
            private Projectile projectile;

            @Override
            public void validate() {
                requireCasterReady(Skills.VECTOR_REFLECTION.get());
                if (!(projectileReference instanceof Projectile value)
                        || !sameUsableLevel(value)) {
                    throw new IllegalArgumentException("Projectile target is invalid");
                }
                projectile = value;
                requireEntityInRange(projectile, MAX_QUERY_RANGE);
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                var previousVelocity = projectile.getDeltaMovement();
                var speed = Math.max(0.5, Math.min(4.0, previousVelocity.length()));
                var previousOwner = projectile.getOwner();
                charge(Skills.VECTOR_REFLECTION.get(), 8.0f + (float) speed * 2.0f);
                projectile.setOwner(player);
                VectorProjectileStateAdapter.applyRedirect(
                        projectile, normalized.scale(speed), previousOwner);
                return () -> {
                    if (!sameUsableLevel(projectile)) return;
                    projectile.setOwner(previousOwner);
                    VectorProjectileStateAdapter.applyRedirect(
                            projectile, previousVelocity, player);
                };
            }
        };
    }

    @Override
    public ProgramActionTransaction.ProgramAction displaceEntity(
            Object entityReference,
            ProgramWorldPosition destination,
            AcceleratorProgramStrength strength
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private Entity target;
            private Vec3 targetPosition;

            @Override
            public void validate() {
                requireCasterReady(Skills.VECTOR_ACCEL.get());
                target = requireMovableEntity(entityReference);
                requireEntityInRange(target, MAX_QUERY_RANGE);
                requireFriendlyMovement(target);
                if (target.isPassenger() || target.isVehicle()) {
                    throw new IllegalArgumentException("Mounted entity displacement is not supported");
                }
                targetPosition = requireLocalPosition(destination);
                requirePositionInRange(targetPosition, MAX_QUERY_RANGE);
                if (target.position().distanceTo(targetPosition) > displacementRange(strength)) {
                    throw new IllegalArgumentException("Entity displacement exceeds its strength limit");
                }
                requireEntityDestination(target, targetPosition);
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                if (!EntityMotionGuard.canApplyMotionFrom(player, target)) {
                    throw new IllegalStateException("Target rejected forced movement");
                }
                charge(Skills.VECTOR_ACCEL.get(), displacementCost(strength));
                var previousPosition = target.position();
                var previousVelocity = target.getDeltaMovement();
                var moved = Boolean.TRUE.equals(EntityMotionGuard.callWithMotionSource(
                        player, () -> TeleportSync.teleportInstantly(target, targetPosition)));
                if (!moved) throw new IllegalStateException("Entity displacement was rejected");
                target.resetFallDistance();
                return () -> {
                    if (!sameUsableLevel(target)) return;
                    EntityMotionGuard.runInternalCorrection(target, () ->
                            TeleportSync.teleportInstantly(target, previousPosition));
                    setVelocity(target, previousVelocity);
                };
            }
        };
    }

    @Override
    public ProgramActionTransaction.ProgramAction displaceBlock(
            ProgramBlockPosition block,
            ProgramBlockPosition destination,
            AcceleratorProgramStrength strength
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            private BlockPos sourcePos;
            private BlockPos destinationPos;
            private net.minecraft.world.level.block.state.BlockState sourceState;

            @Override
            public void validate() {
                requireCasterReady(Skills.KINETIC_ENERGY_APPLIED.get());
                if (!DestroyBlocksSetting.canDestroyBlocks(
                        player, Skills.KINETIC_ENERGY_APPLIED.get())) {
                    throw new IllegalStateException("Kinetic block operations are disabled");
                }
                sourcePos = requireLocalBlock(block);
                destinationPos = requireLocalBlock(destination);
                if (sourcePos.equals(destinationPos)) {
                    throw new IllegalArgumentException("Block source and destination are equal");
                }
                requireBlockInRange(sourcePos, MAX_QUERY_RANGE);
                requireBlockInRange(destinationPos, MAX_QUERY_RANGE);
                if (Math.sqrt(sourcePos.distSqr(destinationPos)) > blockDisplacementRange(strength)) {
                    throw new IllegalArgumentException("Block displacement exceeds its strength limit");
                }
                requireEditableBlock(sourcePos);
                requireEditableBlock(destinationPos);
                sourceState = level().getBlockState(sourcePos);
                if (sourceState.isAir()
                        || sourceState.hasBlockEntity()
                        || sourceState.getDestroySpeed(level(), sourcePos) < 0.0f
                        || sourceState.is(Blocks.MOVING_PISTON)) {
                    throw new IllegalArgumentException("Source block cannot be displaced");
                }
                if (!level().getBlockState(destinationPos).isAir()) {
                    throw new IllegalArgumentException("Block destination is occupied");
                }
                if (!sourceState.canSurvive(level(), destinationPos)) {
                    throw new IllegalArgumentException("Source block cannot survive at destination");
                }
                if (!level().getEntities(null, new AABB(destinationPos)).isEmpty()) {
                    throw new IllegalArgumentException("An entity occupies the block destination");
                }
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                if (!level().getBlockState(sourcePos).equals(sourceState)
                        || !level().getBlockState(destinationPos).isAir()) {
                    throw new IllegalStateException("Block displacement target changed before execution");
                }
                charge(Skills.KINETIC_ENERGY_APPLIED.get(), blockDisplacementCost(strength));
                var movement = AcceleratorBlockDisplacementRuntime.start(
                        player,
                        sourcePos,
                        destinationPos,
                        sourceState,
                        blockMovementSpeed(strength)
                );
                return movement::rollback;
            }
        };
    }

    @Override
    public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
        if (!(entityReference instanceof Entity entity) || !sameUsableLevel(entity)) {
            return Optional.empty();
        }
        return Optional.of(worldPosition(entity.position()));
    }

    @Override
    public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
        if (!(entityReference instanceof Entity entity) || !sameUsableLevel(entity)) {
            return Optional.empty();
        }
        var look = entity.getLookAngle();
        if (!isFiniteNonZero(look)) return Optional.empty();
        return Optional.of(new ProgramDirection(look.x, look.y, look.z));
    }

    @Override
    public List<?> entitiesAround(ProgramWorldPosition center, double radius) {
        var origin = requireLocalPosition(center);
        var boundedRadius = requireQueryRange(radius);
        return level().getEntities(
                        player,
                        new AABB(origin, origin).inflate(boundedRadius),
                        entity -> entity.isAlive()
                                && !entity.isRemoved()
                                && !entity.isSpectator()
                                && ServerProgramTargetResolver.isWithinRadius(
                                origin, entity.position(), boundedRadius)
                ).stream()
                .sorted(Comparator.comparing(entity -> entity.getUUID().toString()))
                .limit(MAX_QUERY_RESULTS)
                .toList();
    }

    @Override
    public Optional<ProgramBlockPosition> raycastBlock(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    ) {
        var start = requireLocalPosition(origin);
        var distance = requireQueryRange(maximumDistance);
        var end = start.add(normalized(direction).scale(distance));
        var hit = level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return Optional.empty();
        var block = hit.getBlockPos();
        return Optional.of(new ProgramBlockPosition(
                level().dimension().identifier(), block.getX(), block.getY(), block.getZ()));
    }

    @Override
    public Optional<Object> raycastEntity(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    ) {
        var start = requireLocalPosition(origin);
        var distance = requireQueryRange(maximumDistance);
        var fullEnd = start.add(normalized(direction).scale(distance));
        var blockHit = level().clip(new ClipContext(
                start, fullEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        var end = blockHit.getType() == HitResult.Type.MISS
                ? fullEnd : blockHit.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(
                level(),
                player,
                start,
                end,
                new AABB(start, end).inflate(1.0),
                entity -> entity != player
                        && entity.isAlive()
                        && !entity.isRemoved()
                        && !entity.isSpectator()
                        && entity.isPickable(),
                0.3f
        );
        return hit == null ? Optional.empty() : Optional.of(hit.getEntity());
    }

    private ProgramActionTransaction.ProgramAction impactAction(
            PositionSupplier position,
            ProgramDirection direction,
            int level,
            ConfiguredShockwave configuredShockwave
    ) {
        var normalized = normalized(direction);
        var cost = level * 10.0f;
        if (configuredShockwave != null) {
            cost = ProgramPowerScale.cost(cost, configuredShockwave.power());
        }
        impactBatch.add(cost * costMultiplier);
        return new ProgramActionTransaction.ProgramAction() {
            private Vec3 center;

            @Override
            public void validate() {
                if (configuredShockwave == null) {
                    requireCasterReady(Skills.KINETIC_ENERGY_APPLIED.get());
                } else {
                    requireCasterLearned(Skills.KINETIC_ENERGY_APPLIED.get());
                }
                center = Objects.requireNonNull(position.get(), "Impact position");
                requirePositionInRange(center, MAX_QUERY_RANGE);
                var block = BlockPos.containing(center);
                if (!level().hasChunkAt(block)
                        || !level().getWorldBorder().isWithinBounds(block)) {
                    throw new IllegalArgumentException("Impact position is not loaded or in bounds");
                }
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                if (!impactBatch.reserve(player)) {
                    throw new IllegalStateException("Insufficient CP for kinetic shockwave batch");
                }
                var executed = configuredShockwave == null
                        ? KineticEnergyApplied.Server.executeReservedProgramImpact(
                        player, center, normalized, level)
                        : KineticEnergyApplied.Server.executeConfiguredProgramImpact(
                        player,
                        center,
                        normalized,
                        configuredShockwave.power(),
                        configuredShockwave.destroyBlocks(),
                        configuredShockwave.radius()
                );
                if (!executed) {
                    throw new IllegalStateException("Kinetic shockwave was rejected");
                }
                return ProgramActionTransaction.Undo.NONE;
            }
        };
    }

    private void requireCasterReady(Skill skill) {
        if (!player.isAlive()
                || player.hasDisconnected()
                || player.isSpectator()
                || !skill.isEnabled(player)) {
            throw new IllegalStateException("Required accelerator skill is unavailable");
        }
    }

    private void requireCasterLearned(Skill skill) {
        if (!player.isAlive()
                || player.hasDisconnected()
                || player.isSpectator()
                || skill.getRuntimeData(player).isEmpty()) {
            throw new IllegalStateException("Required accelerator skill is unavailable");
        }
    }

    private Entity requireMovableEntity(Object value) {
        if (!(value instanceof Entity entity) || !sameUsableLevel(entity)) {
            throw new IllegalArgumentException("Entity target is invalid");
        }
        return entity;
    }

    private void requireFriendlyMovement(Entity target) {
        if (target != player
                && target instanceof LivingEntity living
                && CtaFriendlyFireWhitelist.shouldProtect(player, living)) {
            throw new IllegalArgumentException("Friendly-fire policy protects the target");
        }
    }

    private void requireEntityInRange(Entity entity, double range) {
        if (entity.distanceToSqr(player) > range * range) {
            throw new IllegalArgumentException("Entity target is outside program range");
        }
    }

    private void requirePositionInRange(Vec3 position, double range) {
        if (position.distanceToSqr(player.position()) > range * range) {
            throw new IllegalArgumentException("Position is outside program range");
        }
    }

    private void requireBlockInRange(BlockPos position, double range) {
        if (Vec3.atCenterOf(position).distanceToSqr(player.position()) > range * range) {
            throw new IllegalArgumentException("Block is outside program range");
        }
    }

    private void requireEntityDestination(Entity entity, Vec3 destination) {
        var block = BlockPos.containing(destination);
        var moved = entity.getBoundingBox().move(destination.subtract(entity.position()));
        if (!level().hasChunkAt(block)
                || !level().getWorldBorder().isWithinBounds(moved)
                || !level().noCollision(entity, moved)) {
            throw new IllegalArgumentException("Entity destination is unsafe");
        }
    }

    private void requireEditableBlock(BlockPos position) {
        if (position.getY() < level().getMinY()
                || position.getY() >= level().getMaxY()
                || !level().hasChunkAt(position)
                || !level().getWorldBorder().isWithinBounds(position)
                || !level().mayInteract(player, position)) {
            throw new IllegalArgumentException("Block position is not editable");
        }
    }

    private void charge(Skill skill, float cost) {
        if (!AbilitySystemServer.getSystem(player).tryTimedOccupation(
                player, cost * costMultiplier, skill)) {
            throw new IllegalStateException("Insufficient CP for accelerator program action");
        }
    }

    private static float requireCostMultiplier(float multiplier) {
        if (!Float.isFinite(multiplier) || multiplier <= 0.0f) {
            throw new IllegalArgumentException("Program cost multiplier must be positive");
        }
        return multiplier;
    }

    private void setVelocity(Entity entity, Vec3 velocity) {
        EntityMotionGuard.runWithMotionSource(player, () -> entity.setDeltaMovement(velocity));
        entity.hurtMarked = true;
        entity.resetFallDistance();
        if (entity instanceof ServerPlayer targetPlayer) {
            targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
        }
    }

    private boolean sameUsableLevel(Entity entity) {
        return entity.level() == level() && entity.isAlive() && !entity.isRemoved();
    }

    private Vec3 requireLocalPosition(ProgramWorldPosition position) {
        Objects.requireNonNull(position, "position");
        if (!position.dimension().equals(level().dimension().identifier())) {
            throw new IllegalArgumentException("Position is in another dimension");
        }
        return new Vec3(position.x(), position.y(), position.z());
    }

    private BlockPos requireLocalBlock(ProgramBlockPosition position) {
        Objects.requireNonNull(position, "position");
        if (!position.dimension().equals(level().dimension().identifier())) {
            throw new IllegalArgumentException("Block is in another dimension");
        }
        return new BlockPos(position.x(), position.y(), position.z());
    }

    private ProgramWorldPosition worldPosition(Vec3 position) {
        return new ProgramWorldPosition(
                level().dimension().identifier(), position.x, position.y, position.z);
    }

    private ServerLevel level() {
        return (ServerLevel) player.level();
    }

    private static double requireQueryRange(double range) {
        if (!Double.isFinite(range) || range < 0.0 || range > MAX_QUERY_RANGE) {
            throw new IllegalArgumentException("Query range is outside the allowed limit");
        }
        return range;
    }

    private static Vec3 vector(ProgramDirection direction) {
        Objects.requireNonNull(direction, "direction");
        return new Vec3(direction.x(), direction.y(), direction.z());
    }

    private static Vec3 normalized(ProgramDirection direction) {
        var result = vector(direction);
        if (!isFiniteNonZero(result)) {
            throw new IllegalArgumentException("Direction is invalid");
        }
        return result.normalize();
    }

    private static boolean isFiniteNonZero(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z)
                && value.lengthSqr() > 1.0E-12;
    }

    private static double vectorImpulse(AcceleratorProgramStrength strength) {
        return switch (Objects.requireNonNull(strength, "strength")) {
            case CONTROLLED -> 0.4;
            case STANDARD -> 0.8;
            case MAXIMUM -> 1.2;
        };
    }

    private static float vectorCost(AcceleratorProgramStrength strength) {
        return switch (Objects.requireNonNull(strength, "strength")) {
            case CONTROLLED -> 5.0f;
            case STANDARD -> 10.0f;
            case MAXIMUM -> 20.0f;
        };
    }

    private static double displacementRange(AcceleratorProgramStrength strength) {
        return switch (Objects.requireNonNull(strength, "strength")) {
            case CONTROLLED -> 4.0;
            case STANDARD -> 8.0;
            case MAXIMUM -> 16.0;
        };
    }

    private static float displacementCost(AcceleratorProgramStrength strength) {
        return switch (Objects.requireNonNull(strength, "strength")) {
            case CONTROLLED -> 8.0f;
            case STANDARD -> 16.0f;
            case MAXIMUM -> 30.0f;
        };
    }

    private static double blockDisplacementRange(AcceleratorProgramStrength strength) {
        return switch (Objects.requireNonNull(strength, "strength")) {
            case CONTROLLED -> 2.0;
            case STANDARD -> 4.0;
            case MAXIMUM -> 8.0;
        };
    }

    private static float blockDisplacementCost(AcceleratorProgramStrength strength) {
        return switch (Objects.requireNonNull(strength, "strength")) {
            case CONTROLLED -> 20.0f;
            case STANDARD -> 40.0f;
            case MAXIMUM -> 80.0f;
        };
    }

    private static double blockMovementSpeed(AcceleratorProgramStrength strength) {
        return switch (Objects.requireNonNull(strength, "strength")) {
            case CONTROLLED -> 0.65;
            case STANDARD -> 0.9;
            case MAXIMUM -> 1.15;
        };
    }

    static int impactLevel(AcceleratorProgramStrength strength) {
        return switch (Objects.requireNonNull(strength, "strength")) {
            case CONTROLLED -> 1;
            case STANDARD -> 2;
            case MAXIMUM -> 3;
        };
    }

    /** All impacts staged by one VM run reserve their charges before the first world effect. */
    private static final class ImpactBatch {
        private final java.util.ArrayList<Float> costs = new java.util.ArrayList<>();
        private boolean reserved;

        private void add(float cost) {
            if (reserved) throw new IllegalStateException("Cannot extend a reserved impact batch");
            costs.add(cost);
        }

        private boolean reserve(ServerPlayer player) {
            if (reserved) return true;
            if (!KineticEnergyApplied.Server.tryReserveProgramImpactCosts(player, costs)) return false;
            reserved = true;
            return true;
        }
    }

    private record ConfiguredShockwave(float power, boolean destroyBlocks, int radius) {
    }

    @FunctionalInterface
    private interface PositionSupplier {
        Vec3 get();
    }
}
