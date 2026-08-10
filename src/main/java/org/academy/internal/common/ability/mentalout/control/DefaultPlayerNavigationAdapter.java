package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.entitycontrol.*;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.skills.Flight;
import org.academy.internal.common.ability.mentalout.PlayerControlSessionManager;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;

import java.util.*;

/**
 * Built-in bounded navigation for vanilla player movement, water, flight, and ridden vehicles.
 */
public final class DefaultPlayerNavigationAdapter implements PlayerNavigationAdapter {
    private static final int[][] DIAGONAL_OFFSETS = {
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
    };

    private static PlayerMovementMode vehicleMode(Entity vehicle, ServerPlayer subject) {
        if (vehicle == null || vehicle.getControllingPassenger() != subject) return null;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).getPath();
        if (id.equals("boat") || id.endsWith("_boat")) return PlayerMovementMode.BOAT;
        if (id.contains("minecart")) return PlayerMovementMode.RAIL;
        return PlayerMovementMode.MOUNT;
    }

    private static boolean canUseFlight(ServerPlayer player) {
        if (player.mayFly()) return true;
        return flightCandidates().stream().anyMatch(skill -> skill.getRuntimeData(player).isPresent());
    }

    static boolean shouldPulseJump(boolean onGround, long gameTime, long lastJumpTick) {
        return onGround && (lastJumpTick == Long.MIN_VALUE || gameTime - lastJumpTick >= 8L);
    }

    static boolean hasReached(Vec3 current, Vec3 goal, double arrivalRadius) {
        var verticalTolerance = Math.min(0.5, arrivalRadius);
        return Math.abs(current.y - goal.y) <= verticalTolerance
                && current.distanceToSqr(goal) <= arrivalRadius * arrivalRadius;
    }

    static boolean searchNodeReaches(BlockPos current, BlockPos goal, double arrivalRadius) {
        if (current.getY() != goal.getY()) return false;
        var dx = current.getX() - goal.getX();
        var dz = current.getZ() - goal.getZ();
        return dx * dx + dz * dz <= arrivalRadius * arrivalRadius;
    }

    static boolean shouldRetainAutoFlight(
            boolean autoEnabled,
            boolean onGround,
            boolean inWater,
            boolean onClimbable,
            boolean passenger,
            boolean immediateLandingSupport
    ) {
        return autoEnabled && !onGround && !inWater && !onClimbable && !passenger
                && !immediateLandingSupport;
    }

    private static List<Skill> flightCandidates() {
        return List.of(
                Skills.FLIGHT.get(),
                Skills.STORM_WING.get(),
                Skills.BLACK_WING.get(),
                Skills.WHITE_WING.get(),
                Skills.PLATINUM_WING.get()
        );
    }

    @Override
    public boolean matches(ServerPlayer subject) {
        return subject != null;
    }

    @Override
    public Set<PlayerMovementMode> modes(ServerPlayer subject) {
        if (subject.isPassenger()) {
            var vehicle = subject.getVehicle();
            var mode = vehicleMode(vehicle, subject);
            return mode == null ? Set.of() : Set.of(mode);
        }
        var modes = EnumSet.of(
                PlayerMovementMode.WALK,
                PlayerMovementMode.JUMP,
                PlayerMovementMode.DROP,
                PlayerMovementMode.CLIMB,
                PlayerMovementMode.SWIM
        );
        if (subject.isFallFlying()) modes.add(PlayerMovementMode.GLIDE);
        if (canUseFlight(subject)) modes.add(PlayerMovementMode.FLY);
        return Set.copyOf(modes);
    }

    @Override
    public ControlBinding activate(
            ControlContext context,
            ServerPlayer subject,
            ControlDirective.MoveTo directive
    ) {
        var supportedModes = modes(subject);
        if (supportedModes.isEmpty()) {
            return new FailedBinding(ControlFailureReason.UNSUPPORTED_MOVEMENT_MODE);
        }
        return new PlayerPathBinding(context, subject, directive, supportedModes);
    }

    private enum SearchResult {
        RUNNING,
        FOUND,
        FAILED
    }

    private static final class PlayerPathBinding implements ControlBinding {
        private static final int MAX_NODES = 4096;
        private static final int MAX_EXPANSIONS_PER_TICK = 256;
        private static final int DYNAMIC_REPLAN_TICKS = 10;
        private static final int MAX_UNREACHABLE_ATTEMPTS = 3;
        private static final int MAX_SAFE_DROP = 3;
        private static final int STALL_TICKS = 20;
        private static final double MIN_PROGRESS_SQR = 0.01;

        private final ServerPlayer subject;
        private final ControlDestination destination;
        private final double arrivalRadius;
        private final Set<PlayerMovementMode> supportedModes;
        private final PlayerControlSessionManager.PathSessionToken session;
        private Search search;
        private List<NodeStep> path = List.of();
        private int pathIndex;
        private int unreachableAttempts;
        private int stalledAttempts;
        private int modeMismatchTicks;
        private int ticks;
        private int lastReplanTick = Integer.MIN_VALUE;
        private long lastJumpTick = Long.MIN_VALUE;
        private boolean clientBecameActive;
        private boolean complete;
        private boolean closed;
        private ControlFailureReason failure;
        private Skill autoEnabledFlight;
        private boolean autoEnabledVanillaFlight;
        private Vec3 plannedGoal;
        private Vec3 lastProgressPosition;
        private int lastProgressTick;
        private PlayerMovementMode lastRequestedMode;

        private PlayerPathBinding(
                ControlContext context,
                ServerPlayer subject,
                ControlDirective.MoveTo directive,
                Set<PlayerMovementMode> supportedModes
        ) {
            this.subject = subject;
            destination = directive.destination();
            arrivalRadius = directive.arrivalRadius();
            this.supportedModes = supportedModes;
            session = PlayerControlSessionManager.beginPath(context, subject);
        }

        private static boolean waypointReached(
                PlayerMovementMode mode,
                double horizontalDistanceSqr,
                double verticalDistance
        ) {
            var verticalTolerance = mode == PlayerMovementMode.FLY
                    ? 0.18
                    : mode == PlayerMovementMode.SWIM
                    || mode == PlayerMovementMode.GLIDE
                    || mode == PlayerMovementMode.JUMP
                    ? 0.35
                    : 0.8;
            return horizontalDistanceSqr <= 0.20 && Math.abs(verticalDistance) <= verticalTolerance;
        }

        private static PlayerMovementMode initialMode(ServerPlayer subject) {
            if (subject.isPassenger()) {
                var mode = vehicleMode(subject.getVehicle(), subject);
                return mode == null ? PlayerMovementMode.MOUNT : mode;
            }
            if (subject.isFallFlying()) return PlayerMovementMode.GLIDE;
            if (subject.getAbilities().flying) return PlayerMovementMode.FLY;
            if (subject.isInWater()) return PlayerMovementMode.SWIM;
            if (subject.onClimbable()) return PlayerMovementMode.CLIMB;
            return PlayerMovementMode.WALK;
        }

        private static double heuristic(BlockPos left, BlockPos right) {
            return Math.sqrt(left.distSqr(right));
        }

        private static double moveCost(NodeKey from, NodeKey to) {
            var dx = to.pos.getX() - from.pos.getX();
            var dy = to.pos.getY() - from.pos.getY();
            var dz = to.pos.getZ() - from.pos.getZ();
            return Math.sqrt(dx * dx + dy * dy + dz * dz) + switch (to.mode) {
                case JUMP -> 0.4;
                case DROP -> 0.25;
                case FLY -> 0.55;
                case GLIDE -> 0.3;
                default -> 0.0;
            };
        }

        @Override
        public void tick() {
            if (closed || complete || failure != null) return;
            ticks++;
            if (PlayerControlSessionManager.isPathHandshakePending(session)) return;
            if (!PlayerControlSessionManager.isPathActive(session)) {
                failure = ControlFailureReason.CLIENT_TIMEOUT;
                return;
            }
            if (!clientBecameActive) {
                clientBecameActive = true;
                lastProgressPosition = subject.position();
                lastProgressTick = ticks;
            }
            var goal = resolveGoal();
            if (goal == null) {
                failure = ControlFailureReason.TARGET_UNAVAILABLE;
                return;
            }
            if (hasReached(subject.position(), goal, arrivalRadius)) {
                PlayerControlSessionManager.submitPathFrame(session, PlayerControlFrame.NEUTRAL);
                complete = true;
                return;
            }
            if (hasUnsupportedAppliedMode()) {
                failure = ControlFailureReason.UNSUPPORTED_MOVEMENT_MODE;
                PlayerControlSessionManager.submitPathFrame(session, PlayerControlFrame.NEUTRAL);
                return;
            }

            if (destination instanceof ControlDestination.Entity
                    && ticks - lastReplanTick >= DYNAMIC_REPLAN_TICKS) {
                if (plannedGoal == null || plannedGoal.distanceToSqr(goal) > 0.25) {
                    resetSearch(goal);
                } else {
                    lastReplanTick = ticks;
                }
            }
            if (search == null && pathIndex >= path.size()) resetSearch(goal);
            if (search != null) {
                advanceSearch();
                if (failure != null || search != null) return;
            }
            if (detectStall(goal)) return;
            followPath(goal);
        }

        private boolean hasUnsupportedAppliedMode() {
            if (lastRequestedMode == null) return false;
            var applied = PlayerControlSessionManager.pathAppliedMode(session).orElse(null);
            if (applied == null) return false;
            var compatible = switch (lastRequestedMode) {
                case WALK, JUMP, DROP -> applied == PlayerMovementMode.WALK
                        || applied == PlayerMovementMode.JUMP
                        || applied == PlayerMovementMode.DROP;
                default -> applied == lastRequestedMode;
            };
            modeMismatchTicks = compatible ? 0 : modeMismatchTicks + 1;
            return modeMismatchTicks >= STALL_TICKS;
        }

        private boolean detectStall(Vec3 goal) {
            if (search != null || pathIndex >= path.size()) return false;
            var current = subject.position();
            if (lastProgressPosition == null
                    || current.distanceToSqr(lastProgressPosition) >= MIN_PROGRESS_SQR) {
                lastProgressPosition = current;
                lastProgressTick = ticks;
                stalledAttempts = 0;
                return false;
            }
            if (ticks - lastProgressTick < STALL_TICKS) return false;
            PlayerControlSessionManager.submitPathFrame(session, PlayerControlFrame.NEUTRAL);
            stalledAttempts++;
            lastProgressPosition = current;
            lastProgressTick = ticks;
            if (stalledAttempts >= MAX_UNREACHABLE_ATTEMPTS) {
                failure = ControlFailureReason.UNREACHABLE_DESTINATION;
            } else {
                resetSearch(goal);
            }
            return true;
        }

        private void advanceSearch() {
            var budget = PlayerNavigationRuntime.claimExpansionBudget(
                    subject.level().getGameTime(), MAX_EXPANSIONS_PER_TICK);
            if (budget <= 0) return;
            var result = search.expand(budget);
            if (result == SearchResult.RUNNING) return;
            if (result == SearchResult.FOUND) {
                path = search.buildPath();
                pathIndex = 0;
                search = null;
                unreachableAttempts = 0;
                return;
            }
            var reason = search.budgetExhausted
                    ? ControlFailureReason.PLANNING_BUDGET_EXHAUSTED
                    : ControlFailureReason.UNREACHABLE_DESTINATION;
            search = null;
            path = List.of();
            pathIndex = 0;
            unreachableAttempts++;
            if (reason == ControlFailureReason.PLANNING_BUDGET_EXHAUSTED
                    || unreachableAttempts >= MAX_UNREACHABLE_ATTEMPTS) {
                failure = reason;
            }
        }

        private void followPath(Vec3 goal) {
            if (pathIndex >= path.size()) return;
            var step = path.get(pathIndex);
            var waypoint = waypoint(step, goal);
            var delta = waypoint.subtract(subject.position());
            var horizontalSqr = delta.x * delta.x + delta.z * delta.z;
            if (waypointReached(step.mode, horizontalSqr, delta.y)) {
                pathIndex++;
                if (pathIndex >= path.size()) {
                    if (hasReached(subject.position(), goal, arrivalRadius)) {
                        PlayerControlSessionManager.submitPathFrame(session, PlayerControlFrame.NEUTRAL);
                        complete = true;
                    } else {
                        resetSearch(goal);
                    }
                    return;
                }
                step = path.get(pathIndex);
                waypoint = waypoint(step, goal);
                delta = waypoint.subtract(subject.position());
                horizontalSqr = delta.x * delta.x + delta.z * delta.z;
            }

            if (!openDoorIfNeeded(step.pos)) {
                resetSearch(goal);
                return;
            }
            var yaw = horizontalSqr <= 1.0e-6
                    ? subject.getYRot()
                    : (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
            var horizontal = Math.sqrt(horizontalSqr);
            var waterExit = step.mode == PlayerMovementMode.JUMP && subject.isInWater();
            var verticalMode = step.mode == PlayerMovementMode.FLY
                    || step.mode == PlayerMovementMode.SWIM
                    || step.mode == PlayerMovementMode.GLIDE
                    || waterExit;
            var pitch = verticalMode && delta.lengthSqr() > 1.0e-6
                    ? (float) -Math.toDegrees(Math.atan2(delta.y, Math.max(horizontal, 1.0e-6)))
                    : subject.getXRot();
            var jump = false;
            var sneak = false;
            if (step.mode == PlayerMovementMode.JUMP) {
                var now = subject.level().getGameTime();
                if (waterExit) {
                    // Leaving water for a one-block-higher bank requires a held swim-up input,
                    // not the grounded jump edge used for ordinary obstacles.
                    jump = true;
                } else {
                    jump = shouldPulseJump(subject.onGround(), now, lastJumpTick);
                    if (jump) lastJumpTick = now;
                }
            } else if (step.mode == PlayerMovementMode.CLIMB
                    || step.mode == PlayerMovementMode.SWIM
                    || step.mode == PlayerMovementMode.FLY) {
                var verticalDeadZone = step.mode == PlayerMovementMode.FLY ? 0.12 : 0.25;
                jump = delta.y > verticalDeadZone;
                sneak = delta.y < -verticalDeadZone;
            }
            if (step.mode == PlayerMovementMode.FLY && !ensureFlight()) {
                failure = ControlFailureReason.UNSUPPORTED_MOVEMENT_MODE;
                return;
            }
            var wingDriven = step.mode == PlayerMovementMode.FLY && driveWing(delta);
            var forward = wingDriven || verticalMode && horizontal <= 0.10
                    ? 0.0f
                    : 1.0f;
            PlayerControlSessionManager.submitPathFrame(session, new PlayerControlFrame(
                    forward, 0.0f, yaw, pitch, jump, sneak,
                    step.mode != PlayerMovementMode.RAIL, false, false, step.mode
            ));
            lastRequestedMode = step.mode;
        }

        private Vec3 waypoint(NodeStep step, Vec3 goal) {
            // The planner operates on integer occupancy cells. The final flight frame must use the
            // original destination Y (including its fractional component), otherwise its vertical
            // controller converges on the cell below the requested point.
            return step.mode == PlayerMovementMode.FLY && pathIndex == path.size() - 1
                    ? goal
                    : Vec3.atBottomCenterOf(step.pos);
        }

        private boolean openDoorIfNeeded(BlockPos pos) {
            var level = subject.level();
            var state = level.getBlockState(pos);
            if (!state.hasProperty(BlockStateProperties.OPEN)
                    || state.getValue(BlockStateProperties.OPEN)) return true;
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            subject.gameMode.useItemOn(
                    subject,
                    level,
                    subject.getItemInHand(InteractionHand.MAIN_HAND),
                    InteractionHand.MAIN_HAND,
                    hit
            );
            return level.getBlockState(pos).hasProperty(BlockStateProperties.OPEN)
                    && level.getBlockState(pos).getValue(BlockStateProperties.OPEN);
        }

        private boolean ensureFlight() {
            if (subject.getAbilities().flying || anyWingEnabled()) return true;
            if (subject.mayFly()) {
                subject.getAbilities().flying = true;
                subject.onUpdateAbilities();
                autoEnabledVanillaFlight = true;
                return true;
            }
            for (var skill : flightCandidates()) {
                if (skill.getRuntimeData(subject).isEmpty()) continue;
                var alreadyEnabled = skill.isEnabled(subject);
                if (!alreadyEnabled) skill.toggle(subject);
                if (skill.isEnabled(subject)) {
                    if (!alreadyEnabled) autoEnabledFlight = skill;
                    if (skill == Skills.FLIGHT.get()) {
                        Flight.Server.refreshFlightPermission(subject);
                        if (subject.mayFly() && !subject.getAbilities().flying) {
                            subject.getAbilities().flying = true;
                            subject.onUpdateAbilities();
                        }
                    }
                    return true;
                }
            }
            return false;
        }

        private boolean anyWingEnabled() {
            return Skills.STORM_WING.get().isEnabled(subject)
                    || Skills.BLACK_WING.get().isEnabled(subject)
                    || Skills.WHITE_WING.get().isEnabled(subject)
                    || Skills.PLATINUM_WING.get().isEnabled(subject);
        }

        private boolean driveWing(Vec3 delta) {
            if (!anyWingEnabled() || Skills.FLIGHT.get().isEnabled(subject)) return false;
            if (delta.lengthSqr() <= 1.0e-6) return true;
            var velocity = delta.normalize().scale(0.32);
            EntityMotionGuard.runWithMotionSource(subject, () -> subject.setDeltaMovement(velocity));
            subject.resetFallDistance();
            subject.hurtMarked = true;
            subject.connection.send(new ClientboundSetEntityMotionPacket(subject));
            return true;
        }

        private void resetSearch(Vec3 goal) {
            lastReplanTick = ticks;
            plannedGoal = goal;
            path = List.of();
            pathIndex = 0;
            search = new Search(BlockPos.containing(subject.position()), BlockPos.containing(goal));
        }

        private Vec3 resolveGoal() {
            return switch (destination) {
                case ControlDestination.Position position -> position.value();
                case ControlDestination.Entity entity -> {
                    var target = MentalControlRuntime.findLivingEntity(
                            subject.level().getServer(), entity.uuid());
                    yield target == null || target.level() != subject.level() ? null : target.position();
                }
            };
        }

        @Override
        public boolean isComplete() {
            return complete || failure != null;
        }

        @Override
        public Optional<ControlFailureReason> failureReason() {
            return Optional.ofNullable(failure);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            PlayerControlSessionManager.submitPathFrame(session, PlayerControlFrame.NEUTRAL);
            var directOverride = MentalControlApi.inspect(
                    subject, ControlCapability.DIRECT_CONTROL
            ).isPresent();
            PlayerControlSessionManager.closePath(session, clientBecameActive && !directOverride);
            var autoFlightActive = autoEnabledFlight != null || autoEnabledVanillaFlight;
            var retainAutoFlight = shouldRetainAutoFlight(
                    autoFlightActive,
                    subject.onGround(),
                    subject.isInWater(),
                    subject.onClimbable(),
                    subject.isPassenger(),
                    hasImmediateLandingSupport()
            );
            if (!retainAutoFlight) {
                if (autoEnabledFlight != null && autoEnabledFlight.isEnabled(subject)) {
                    autoEnabledFlight.toggle(subject);
                    if (autoEnabledFlight == Skills.FLIGHT.get()) {
                        Flight.Server.refreshFlightPermission(subject);
                    }
                }
                if (autoEnabledVanillaFlight && subject.getAbilities().flying) {
                    subject.getAbilities().flying = false;
                    subject.onUpdateAbilities();
                }
            }
        }

        private boolean hasImmediateLandingSupport() {
            var level = subject.level();
            return !level.noCollision(subject, subject.getBoundingBox().move(0.0, -0.5, 0.0));
        }

        private List<NodeKey> neighbors(NodeKey current) {
            var result = new ArrayList<NodeKey>(32);
            var pos = current.pos;
            if (current.mode == PlayerMovementMode.BOAT
                    || current.mode == PlayerMovementMode.MOUNT
                    || current.mode == PlayerMovementMode.RAIL) {
                for (var direction : Direction.Plane.HORIZONTAL) {
                    var next = pos.relative(direction);
                    if (vehicleCanOccupy(next, current.mode)) result.add(new NodeKey(next, current.mode));
                    var up = next.above();
                    if (current.mode != PlayerMovementMode.BOAT && vehicleCanOccupy(up, current.mode)) {
                        result.add(new NodeKey(up, current.mode));
                    }
                }
                if (current.mode != PlayerMovementMode.RAIL) {
                    addDiagonalVehicleNeighbors(result, pos, current.mode);
                }
                return result;
            }

            for (var direction : Direction.Plane.HORIZONTAL) {
                var next = pos.relative(direction);
                if (supportedModes.contains(PlayerMovementMode.SWIM)
                        && isWater(next) && canOccupy(next)) {
                    result.add(new NodeKey(next, PlayerMovementMode.SWIM));
                } else if (canStand(next)) {
                    result.add(new NodeKey(next, PlayerMovementMode.WALK));
                } else if (supportedModes.contains(PlayerMovementMode.JUMP) && canStand(next.above())) {
                    result.add(new NodeKey(next.above(), PlayerMovementMode.JUMP));
                } else if (supportedModes.contains(PlayerMovementMode.DROP)) {
                    for (var drop = 1; drop <= MAX_SAFE_DROP; drop++) {
                        var below = next.below(drop);
                        if (canStand(below)) {
                            result.add(new NodeKey(below, PlayerMovementMode.DROP));
                            break;
                        }
                    }
                }
            }
            addDiagonalSurfaceNeighbors(result, pos);

            if (supportedModes.contains(PlayerMovementMode.CLIMB) && isClimbable(pos)) {
                for (var next : List.of(pos.above(), pos.below())) {
                    if (canOccupy(next) && (isClimbable(next) || isClimbable(pos))) {
                        result.add(new NodeKey(next, PlayerMovementMode.CLIMB));
                    }
                }
            }
            if (supportedModes.contains(PlayerMovementMode.SWIM)
                    && (isWater(pos) || current.mode == PlayerMovementMode.SWIM)) {
                addSwimVolumeNeighbors(result, pos);
            }
            if (supportedModes.contains(PlayerMovementMode.FLY)) {
                addFlightVolumeNeighbors(result, pos);
            }
            if (supportedModes.contains(PlayerMovementMode.GLIDE)) {
                for (var direction : Direction.Plane.HORIZONTAL) {
                    for (var drop = 0; drop <= 1; drop++) {
                        var next = pos.relative(direction).below(drop);
                        if (canOccupy(next)) result.add(new NodeKey(next, PlayerMovementMode.GLIDE));
                    }
                }
            }
            return result;
        }

        private void addDiagonalVehicleNeighbors(
                List<NodeKey> result,
                BlockPos pos,
                PlayerMovementMode mode
        ) {
            for (var offset : DIAGONAL_OFFSETS) {
                var next = pos.offset(offset[0], 0, offset[1]);
                var sideX = pos.offset(offset[0], 0, 0);
                var sideZ = pos.offset(0, 0, offset[1]);
                if (vehicleCanOccupy(next, mode)
                        && vehicleCanOccupy(sideX, mode)
                        && vehicleCanOccupy(sideZ, mode)) {
                    result.add(new NodeKey(next, mode));
                }
            }
        }

        private void addDiagonalSurfaceNeighbors(List<NodeKey> result, BlockPos pos) {
            for (var offset : DIAGONAL_OFFSETS) {
                var next = pos.offset(offset[0], 0, offset[1]);
                var sideX = pos.offset(offset[0], 0, 0);
                var sideZ = pos.offset(0, 0, offset[1]);
                if (supportedModes.contains(PlayerMovementMode.SWIM)
                        && isWater(next) && canOccupy(next)
                        && canOccupy(sideX) && canOccupy(sideZ)) {
                    result.add(new NodeKey(next, PlayerMovementMode.SWIM));
                } else if (canStand(next) && canStand(sideX) && canStand(sideZ)) {
                    result.add(new NodeKey(next, PlayerMovementMode.WALK));
                } else if (supportedModes.contains(PlayerMovementMode.JUMP)
                        && canStand(next.above())
                        && canOccupy(sideX) && canOccupy(sideZ)
                        && canOccupy(sideX.above()) && canOccupy(sideZ.above())) {
                    // This also covers climbing diagonally from water onto an isolated surface block.
                    result.add(new NodeKey(next.above(), PlayerMovementMode.JUMP));
                }
            }
        }

        private void addSwimVolumeNeighbors(List<NodeKey> result, BlockPos pos) {
            for (var dx = -1; dx <= 1; dx++) {
                for (var dy = -1; dy <= 1; dy++) {
                    for (var dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        var next = pos.offset(dx, dy, dz);
                        if (!isWater(next) || !volumeTransitionClear(pos, dx, dy, dz)) continue;
                        result.add(new NodeKey(next, PlayerMovementMode.SWIM));
                    }
                }
            }
        }

        private void addFlightVolumeNeighbors(List<NodeKey> result, BlockPos pos) {
            for (var dx = -1; dx <= 1; dx++) {
                for (var dy = -1; dy <= 1; dy++) {
                    for (var dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        var next = pos.offset(dx, dy, dz);
                        if (canOccupy(next) && volumeTransitionClear(pos, dx, dy, dz)) {
                            result.add(new NodeKey(next, PlayerMovementMode.FLY));
                        }
                    }
                }
            }
        }

        private boolean volumeTransitionClear(BlockPos origin, int dx, int dy, int dz) {
            for (var includeX = 0; includeX <= (dx == 0 ? 0 : 1); includeX++) {
                for (var includeY = 0; includeY <= (dy == 0 ? 0 : 1); includeY++) {
                    for (var includeZ = 0; includeZ <= (dz == 0 ? 0 : 1); includeZ++) {
                        var offsetX = includeX == 0 ? 0 : dx;
                        var offsetY = includeY == 0 ? 0 : dy;
                        var offsetZ = includeZ == 0 ? 0 : dz;
                        if (offsetX == 0 && offsetY == 0 && offsetZ == 0) continue;
                        if (!canOccupy(origin.offset(offsetX, offsetY, offsetZ))) return false;
                    }
                }
            }
            return true;
        }

        private boolean canStand(BlockPos pos) {
            return canOccupy(pos) && hasGround(pos.below());
        }

        private boolean canOccupy(BlockPos pos) {
            var level = subject.level();
            var body = subject.isPassenger() ? subject.getVehicle() : subject;
            var box = body.getBoundingBox().move(
                    pos.getX() + 0.5 - body.getX(),
                    pos.getY() - body.getY(),
                    pos.getZ() + 0.5 - body.getZ()
            );
            if (level.noCollision(body, box)) return true;
            var state = level.getBlockState(pos);
            return state.hasProperty(BlockStateProperties.OPEN)
                    && !state.getValue(BlockStateProperties.OPEN)
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
        }

        private boolean vehicleCanOccupy(BlockPos pos, PlayerMovementMode mode) {
            if (!canOccupy(pos)) return false;
            var level = subject.level();
            if (mode == PlayerMovementMode.BOAT) return isWater(pos) || isWater(pos.below());
            if (mode == PlayerMovementMode.RAIL) {
                return level.getBlockState(pos).is(BlockTags.RAILS)
                        || level.getBlockState(pos.below()).is(BlockTags.RAILS);
            }
            return hasGround(pos.below());
        }

        private boolean hasGround(BlockPos pos) {
            var level = subject.level();
            return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        }

        private boolean isWater(BlockPos pos) {
            return subject.level().getFluidState(pos).is(FluidTags.WATER);
        }

        private boolean isClimbable(BlockPos pos) {
            return subject.level().getBlockState(pos).is(BlockTags.CLIMBABLE);
        }

        private final class Search {
            private final BlockPos goal;
            private final PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator
                    .comparingDouble(SearchNode::score)
                    .thenComparingDouble(SearchNode::heuristic));
            private final Map<NodeKey, SearchNode> best = new HashMap<>();
            private final Set<NodeKey> closed = new HashSet<>();
            private SearchNode found;
            private boolean budgetExhausted;

            private Search(BlockPos start, BlockPos goal) {
                this.goal = goal;
                var mode = initialMode(subject);
                var node = new SearchNode(new NodeKey(start.immutable(), mode), 0.0,
                        heuristic(start, goal), null);
                open.add(node);
                best.put(node.key, node);
            }

            private SearchResult expand(int budget) {
                for (var count = 0; count < budget; count++) {
                    var current = pollOpen();
                    if (current == null) return SearchResult.FAILED;
                    if (searchNodeReaches(current.key.pos, goal, arrivalRadius)) {
                        found = current;
                        return SearchResult.FOUND;
                    }
                    closed.add(current.key);
                    for (var neighbor : neighbors(current.key)) {
                        if (closed.contains(neighbor)) continue;
                        var cost = current.cost + moveCost(current.key, neighbor);
                        var existing = best.get(neighbor);
                        if (existing != null && existing.cost <= cost) continue;
                        if (best.size() >= MAX_NODES) {
                            budgetExhausted = true;
                            return SearchResult.FAILED;
                        }
                        var node = new SearchNode(neighbor, cost,
                                heuristic(neighbor.pos, goal), current);
                        best.put(neighbor, node);
                        open.add(node);
                    }
                }
                return SearchResult.RUNNING;
            }

            private SearchNode pollOpen() {
                while (!open.isEmpty()) {
                    var result = open.remove();
                    if (best.get(result.key) == result && !closed.contains(result.key)) return result;
                }
                return null;
            }

            private List<NodeStep> buildPath() {
                var reversed = new ArrayList<NodeStep>();
                for (var node = found; node != null && node.parent != null; node = node.parent) {
                    reversed.add(new NodeStep(node.key.pos, node.key.mode));
                }
                Collections.reverse(reversed);
                return List.copyOf(reversed);
            }
        }
    }

    private record FailedBinding(ControlFailureReason reason) implements ControlBinding {

        @Override
            public void tick() {
            }

            @Override
            public boolean isComplete() {
                return true;
            }

            @Override
            public Optional<ControlFailureReason> failureReason() {
                return Optional.of(reason);
            }

            @Override
            public void close() {
            }
        }

    private record NodeKey(BlockPos pos, PlayerMovementMode mode) {
    }

    private record NodeStep(BlockPos pos, PlayerMovementMode mode) {
    }

    private record SearchNode(NodeKey key, double cost, double heuristic, SearchNode parent) {
        private double score() {
            return cost + heuristic;
        }
    }
}
