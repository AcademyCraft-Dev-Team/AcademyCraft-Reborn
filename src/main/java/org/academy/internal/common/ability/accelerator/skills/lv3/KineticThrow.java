package org.academy.internal.common.ability.accelerator.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.accelerator.KineticStructureControl;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.structure.BlockStructureApi;
import org.academy.api.common.structure.BlockStructureCaptureOptions;
import org.academy.api.common.structure.LegacyMotionUnits;
import org.academy.api.common.structure.StructureBackendStatus;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePhysics;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsLease;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.api.common.structure.StructureQueries;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.structure.StructureTraction;
import org.academy.api.common.structure.StructureTractionSession;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.api.server.team.TeamRelations;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv2.KineticEnergyApplied;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Captures terrain as a hovering structure and throws it along the caster's view vector. */
public final class KineticThrow extends Skill {
    public static final double TARGET_RANGE = 24.0;
    public static final int TICKS_PER_CHARGE_TIER = 20;
    public static final int HOVER_TICKS = 10 * 20;
    public static final double PLAYER_CLEARANCE = 1.0;
    public static final int MAXIMUM_FLIGHT_TICKS = 30 * 20;
    public static final double BASE_THROW_SPEED = 2.5;
    public static final float DAMAGE_PER_BLOCK = 5.0f;

    private static final int[] HORIZONTAL_RADII = {5, 7, 9};
    private static final int[] VERTICAL_RADII = {2, 4, 6};
    private static final int[] CYLINDER_RADII = {5, 7, 9};
    private static final int[] CYLINDER_HEIGHTS = {5, 7, 9};
    private static final int[] LIFT_HEIGHTS = {4, 5, 7};
    private static final int[] EXPLOSION_RADII = {5, 7, 9};
    private static final int IMPACT_COOLDOWN_TICKS = 10;
    private static final double REGION_MARGIN = 6.0;
    private static final double FOLLOW_TRAILING_MARGIN = 2.0;

    public KineticThrow() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .damage()
                .level(AbilityLevel.LEVEL3)
                .energyCost(20_000)
                .cpCost(30)
                .iterationTicks(5)
                .maxStacks(3)
                .dependsOn(Skills.KINETIC_ENERGY_APPLIED)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
                .devCondition(new DevCondition.DependencyCondition(
                        "Kinetic Energy Applied", "academy:kinetic_energy_applied"))
        );
    }

    public static int chargeTier(long chargeTicks) {
        return Mth.clamp((int) (Math.max(0L, chargeTicks) / TICKS_PER_CHARGE_TIER), 0, 2);
    }

    public static int horizontalRadius(int tier) {
        return HORIZONTAL_RADII[Mth.clamp(tier, 0, 2)];
    }

    public static int verticalRadius(int tier) {
        return VERTICAL_RADII[Mth.clamp(tier, 0, 2)];
    }

    public static int cylinderRadius(int tier) {
        return CYLINDER_RADII[Mth.clamp(tier, 0, 2)];
    }

    public static int cylinderHeight(int tier) {
        return CYLINDER_HEIGHTS[Mth.clamp(tier, 0, 2)];
    }

    public static int liftHeight(int tier) {
        return LIFT_HEIGHTS[Mth.clamp(tier, 0, 2)];
    }

    public static int explosionRadius(int tier) {
        return EXPLOSION_RADII[Mth.clamp(tier, 0, 2)];
    }

    public static float structureDamage(int blockCount) {
        return Math.max(0, blockCount) * DAMAGE_PER_BLOCK;
    }

    public static Vec3 throwDirection(Vec3 viewDirection) {
        return viewDirection != null && viewDirection.lengthSqr() > 1.0e-8
                ? viewDirection.normalize()
                : Vec3.ZERO;
    }

    /**
     * Deterministic pre-trim before a capture transaction: the seed cell is kept, remaining
     * cells are ranked by squared distance to the seed with coordinate tie-breaks, and at most
     * {@code budget} cells are returned. The backend enforces its own cell budget after this.
     */
    static List<BlockPos> cropToBudget(List<BlockPos> positions, BlockPos seed, int budget) {
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(seed, "seed");
        if (budget < 1) return List.of();
        var unique = new ArrayList<BlockPos>();
        var seen = new HashSet<BlockPos>();
        for (var position : positions) {
            var immutable = Objects.requireNonNull(position, "position").immutable();
            if (seen.add(immutable)) unique.add(immutable);
        }
        unique.sort(Comparator
                .comparingDouble((BlockPos position) -> position.distSqr(seed))
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ));
        var kept = new ArrayList<BlockPos>(Math.min(budget, unique.size()));
        var immutableSeed = seed.immutable();
        if (seen.contains(immutableSeed)) kept.add(immutableSeed);
        for (var position : unique) {
            if (kept.size() >= budget) break;
            if (position.equals(immutableSeed)) continue;
            kept.add(position);
        }
        return List.copyOf(kept);
    }

    /** Smallest usable physics region around the captured cells and their vertical lift. */
    static AABB regionBounds(List<BlockPos> positions, int lift, double minimumY, double maximumY, AABB limits) {
        Objects.requireNonNull(positions, "positions");
        if (positions.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var position : positions) {
            minX = Math.min(minX, position.getX()); maxX = Math.max(maxX, position.getX());
            minY = Math.min(minY, position.getY()); maxY = Math.max(maxY, position.getY());
            minZ = Math.min(minZ, position.getZ()); maxZ = Math.max(maxZ, position.getZ());
        }
        var required = new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0 + lift, maxZ + 1.0);
        double marginX = Math.min(REGION_MARGIN, (StructurePhysics.MAX_REGION_AXIS - required.getXsize()) / 2.0);
        double marginY = Math.min(REGION_MARGIN, (StructurePhysics.MAX_REGION_AXIS - required.getYsize()) / 2.0);
        double marginZ = Math.min(REGION_MARGIN, (StructurePhysics.MAX_REGION_AXIS - required.getZsize()) / 2.0);
        if (marginX < 0 || marginY < 0 || marginZ < 0) return null;
        var bounded = new AABB(required.minX - marginX, required.minY - marginY, required.minZ - marginZ,
                required.maxX + marginX, required.maxY + marginY, required.maxZ + marginZ);
        bounded = clampInside(bounded, minimumY, maximumY, limits);
        return bounded != null && contains(bounded, required) ? bounded : null;
    }

    /** Signed distance from a box to the nearest region edge; negative when the box sticks out. */
    static double edgeDistance(AABB bounds, AABB box) {
        return Math.min(Math.min(box.minX - bounds.minX, bounds.maxX - box.maxX),
                Math.min(Math.min(box.minY - bounds.minY, bounds.maxY - box.maxY),
                        Math.min(box.minZ - bounds.minZ, bounds.maxZ - box.maxZ)));
    }

    /**
     * Same-size follow candidate that advances the occupied box toward the travel direction
     * while retaining it with a trailing margin, clamped to the host height and world border.
     * The candidate must still contain the current occupancy; null means the caller cannot keep
     * the body inside any rebaseable region and must end its action safely instead of letting
     * the region fail terminally on the next physics step.
     */
    static AABB followBounds(AABB region, AABB occupied, Vec3 velocity, double lookaheadSeconds,
                             double trailingMargin, double minimumY, double maximumY, AABB limits) {
        double sizeX = region.getXsize(), sizeY = region.getYsize(), sizeZ = region.getZsize();
        if (limits.getXsize() < sizeX || limits.getZsize() < sizeZ || maximumY - minimumY < sizeY) return null;
        var center = occupied.getCenter().add(
                limitedShift(velocity.x * lookaheadSeconds, sizeX, occupied.getXsize(), trailingMargin),
                limitedShift(velocity.y * lookaheadSeconds, sizeY, occupied.getYsize(), trailingMargin),
                limitedShift(velocity.z * lookaheadSeconds, sizeZ, occupied.getZsize(), trailingMargin));
        double x = Mth.clamp(center.x, limits.minX + sizeX / 2.0, limits.maxX - sizeX / 2.0);
        double y = Mth.clamp(center.y, minimumY + sizeY / 2.0, maximumY - sizeY / 2.0);
        double z = Mth.clamp(center.z, limits.minZ + sizeZ / 2.0, limits.maxZ - sizeZ / 2.0);
        var candidate = new AABB(x - sizeX / 2.0, y - sizeY / 2.0, z - sizeZ / 2.0,
                x + sizeX / 2.0, y + sizeY / 2.0, z + sizeZ / 2.0);
        return contains(candidate, occupied) ? candidate : null;
    }

    private static double limitedShift(double desired, double regionSize, double boxSize, double trailing) {
        double maximum = Math.max(0, (regionSize - boxSize) / 2.0 - trailing);
        return Mth.clamp(desired, -maximum, maximum);
    }

    private static AABB clampInside(AABB bounds, double minimumY, double maximumY, AABB limits) {
        double sizeX = bounds.getXsize(), sizeY = bounds.getYsize(), sizeZ = bounds.getZsize();
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) return null;
        if (limits.getXsize() < sizeX || limits.getZsize() < sizeZ || maximumY - minimumY < sizeY) return null;
        var center = bounds.getCenter();
        double x = Mth.clamp(center.x, limits.minX + sizeX / 2.0, limits.maxX - sizeX / 2.0);
        double y = Mth.clamp(center.y, minimumY + sizeY / 2.0, maximumY - sizeY / 2.0);
        double z = Mth.clamp(center.z, limits.minZ + sizeZ / 2.0, limits.maxZ - sizeZ / 2.0);
        return new AABB(x - sizeX / 2.0, y - sizeY / 2.0, z - sizeZ / 2.0,
                x + sizeX / 2.0, y + sizeY / 2.0, z + sizeZ / 2.0);
    }

    private static boolean contains(AABB outer, AABB inner) {
        return outer.minX <= inner.minX && outer.minY <= inner.minY && outer.minZ <= inner.minZ
                && outer.maxX >= inner.maxX && outer.maxY >= inner.maxY && outer.maxZ >= inner.maxZ;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(
                Client.KEY_NAME_CHARGE,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_CHARGE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD,
                                InputConstants.KEY_H, InputConstants.PRESS, 0)),
                _ -> Client.startCharge()
        );
        InputSystem.addKeyBinding(
                Client.KEY_NAME_RELEASE,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_RELEASE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD,
                                InputConstants.KEY_H, InputConstants.RELEASE, 0)),
                _ -> Client.releaseCharge()
        );
        InputSystem.addKeyBinding(
                Client.KEY_NAME_THROW,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_THROW,
                        InputSystem.combo(InputSystem.InputType.MOUSE,
                                InputConstants.MOUSE_BUTTON_LEFT, InputConstants.PRESS, 0)),
                _ -> Client.throwLookedAtStructure()
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.KINETIC_THROW.get(),
                        List.of(KineticEnergyApplied.Client.SKILL_INFO),
                        R.textures.kinetic_throw_icon,
                        150,
                        100
                )
        );
        public static final String KEY_NAME_CHARGE = SkillNames.KINETIC_THROW + "_charge";
        public static final String KEY_NAME_RELEASE = SkillNames.KINETIC_THROW + "_release";
        public static final String KEY_NAME_THROW = SkillNames.KINETIC_THROW + "_throw";
        public static Config CONFIG = new Config();
        private static boolean charging;

        private Client() {
        }

        private static void startCharge() {
            var minecraft = Minecraft.getInstance();
            if (charging || minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.KINETIC_THROW.get())) return;
            charging = true;
            MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        private static void releaseCharge() {
            if (!charging) return;
            charging = false;
            MisakaNetworkClient.send(ReleasePacket.INSTANCE);
        }

        private static void throwLookedAtStructure() {
            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            if (player == null || minecraft.gui.screen() != null) return;
            MisakaNetworkClient.send(ThrowPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        private static final int NATIVE_CELL_BUDGET = 128;
        private static final int SABLE_CELL_BUDGET = 256;
        private static final int TRACTION_LEASE_TICKS = 20;
        private static final long CAPTURE_TIMEOUT_TICKS = 600;
        private static final double FOLLOW_TRIGGER = 2.0;
        private static final double FOLLOW_LOOKAHEAD_SECONDS = 0.5;
        private static final double ANGULAR_MARGIN_SECONDS = 0.05;
        private static final double SUPPORT_TOLERANCE = 0.5;
        private static final int REGION_UNAVAILABLE_GRACE_TICKS = 40;

        private static final Map<UUID, ChargeState> CHARGES = new HashMap<>();
        private static final Map<UUID, CaptureState> CAPTURES = new HashMap<>();
        private static final Map<UUID, HeldStructure> HELD_STRUCTURES = new HashMap<>();

        private Server() {
        }

        /** Public stage observation for integrations and validation; it cannot drive the skill. */
        public enum Stage {
            CAPTURING, AWAITING_PHYSICS, LIFTING, FLYING
        }

        public record Diagnostic(Stage stage, StructureRef reference, int blockCount, boolean lifted,
                                 boolean controlActive, boolean flying, boolean sable, int impacts) {}

        public static Optional<Diagnostic> diagnostic(ServerPlayer player) {
            requireThread(player);
            var capture = CAPTURES.get(player.getUUID());
            if (capture != null) {
                return Optional.of(new Diagnostic(Stage.CAPTURING, capture.reference, capture.blockCount(),
                        false, false, false, capture.sable, 0));
            }
            var held = HELD_STRUCTURES.get(player.getUUID());
            if (held == null) return Optional.empty();
            return Optional.of(new Diagnostic(held.stage, held.reference, held.blockCount, held.lifted,
                    held.controlActive(), held.stage == Stage.FLYING, held.traction != null, held.impactCount));
        }

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            startCharge(packet.getPacketListener().getPlayer());
        }

        /** Same server-owned entry point used by the skill packet; it grants no extra capability. */
        public static boolean startCharge(ServerPlayer player) {
            requireThread(player);
            if (!Skills.KINETIC_THROW.get().isEnabled(player)
                    || !(player.level() instanceof ServerLevel level)) return false;
            var hit = lookedAtBlock(player);
            if (hit == null) {
                player.sendOverlayMessage(Component.translatable(
                        "message.academy.kinetic_throw.block_target_required"));
                return false;
            }
            CHARGES.put(player.getUUID(), new ChargeState(
                    level.dimension(), hit.getBlockPos().immutable(), level.getGameTime()));
            return true;
        }

        @SubscribePacket
        public static void handleRelease(ReleasePacket packet) {
            releaseCharge(packet.getPacketListener().getPlayer());
        }

        /** Same server-owned entry point used by the skill packet. */
        public static boolean releaseCharge(ServerPlayer player) {
            requireThread(player);
            var charge = CHARGES.remove(player.getUUID());
            if (charge == null || !charge.dimension.equals(player.level().dimension())
                    || !(player.level() instanceof ServerLevel level)
                    || !Skills.KINETIC_THROW.get().isEnabled(player)) return false;
            var chargeTicks = Math.max(0L, level.getGameTime() - charge.startTick);
            if (Skills.KINETIC_THROW.get().hasProficiencyMilestone(player, 2)) {
                chargeTicks = Math.round(chargeTicks / 0.8);
            }
            captureAndLift(player, charge.seed, chargeTier(chargeTicks));
            return true;
        }

        @SubscribePacket
        public static void handleThrow(ThrowPacket packet) {
            throwHeld(packet.getPacketListener().getPlayer());
        }

        /** Same server-owned entry point used by the skill packet. */
        public static boolean throwHeld(ServerPlayer player) {
            requireThread(player);
            var held = HELD_STRUCTURES.get(player.getUUID());
            if (held == null || held.stage != Stage.LIFTING || !held.usableBy(player)
                    || player.level().getGameTime() > held.expiresAt) {
                return false;
            }
            if (held.traction != null) {
                // Sable launch and free flight are explicitly not implemented yet. The captured
                // structure is retained instead of being mutated through an unsupported path.
                AcademyCraft.LOGGER.info("Kinetic Throw sable launch is not implemented; retaining {}",
                        held.reference);
                releaseHeld(player.getUUID());
                return false;
            }
            if (!(player.level() instanceof ServerLevel level)) return false;
            var direction = throwDirection(player.getLookAngle());
            if (direction.lengthSqr() <= 1.0e-8) return false;
            if (!aimedAtHeld(player, held, level) && !viewerInsideStructure(player, held)) return false;
            return launchNative(player, held);
        }

        /** Test/program entry: releases the lift or flight session and keeps the structure active. */
        public static void stopHeld(ServerPlayer player) {
            requireThread(player);
            releaseHeld(player.getUUID());
        }

        private static void captureAndLift(ServerPlayer player, BlockPos seed, int tier) {
            var level = player.level();
            var policy = capturePolicy(player);
            var selection = BlockStructureApi.selectLowerEllipsoidWithUpperCylinder(
                    level,
                    seed,
                    horizontalRadius(tier),
                    verticalRadius(tier),
                    cylinderRadius(tier),
                    cylinderHeight(tier),
                    policy
            );
            if (!selection.succeeded()) {
                rejectCapture(player, "selection_empty");
                return;
            }
            var forward = Vec3.directionFromRotation(0.0f, player.getYRot());
            var playerSafeSelection = BlockStructureApi.cropToForwardHalfSpace(
                    selection.positions(), player.position(), forward, PLAYER_CLEARANCE);
            var positions = BlockStructureApi.cropImmediatelyBlocked(
                    level, playerSafeSelection, Direction.UP);
            if (positions.isEmpty() || !positions.contains(seed)) {
                rejectCapture(player, "selection_blocked_or_seed_missing");
                return;
            }
            var budget = cellBudget(level);
            var cropped = cropToBudget(positions, seed, budget);
            if (cropped.isEmpty() || !cropped.contains(seed)) {
                rejectCapture(player, "budget_crop_lost_seed");
                return;
            }
            var skill = Skills.KINETIC_THROW.get();
            skill.executeActive(player, (_, _) -> beginCapture(player, level, tier, cropped));
        }

        private static void beginCapture(ServerPlayer player, ServerLevel level, int tier, List<BlockPos> positions) {
            var budget = cellBudget(level);
            var result = StructureTransactions.capture(level, new StructureCaptureRequest(
                    positions, budget, StructureCaptureRequest.MAXIMUM_SPAN, capturePermission(player)));
            var reference = result.structure().orElse(null);
            if (result.pending()) {
                if (reference == null) {
                    rejectCapture(player, "pending_without_reference");
                    return;
                }
                CAPTURES.put(player.getUUID(), new CaptureState(reference, level.dimension(), tier,
                        List.copyOf(positions), result.transactionId(), level.getGameTime(), sableMode(level)));
                return;
            }
            if (!result.succeeded()) {
                rejectCapture(player, result.status() + ":" + result.detail());
                return;
            }
            if (reference == null) {
                rejectCapture(player, "committed_without_reference");
                return;
            }
            beginLift(player, reference, tier, positions);
        }

        private static void beginLift(ServerPlayer player, StructureRef reference, int tier, List<BlockPos> positions) {
            releaseHeld(player.getUUID());
            if (sableMode(player.level())) {
                beginSableLift(player, reference, tier, positions.size());
                return;
            }
            if (!(player.level() instanceof ServerLevel level)) return;
            var limits = new AABB(level.getWorldBorder().getMinX(), level.getMinY(), level.getWorldBorder().getMinZ(),
                    level.getWorldBorder().getMaxX(), level.getMaxY(), level.getWorldBorder().getMaxZ());
            var bounds = regionBounds(positions, liftHeight(tier),
                    level.getMinY(), level.getMaxY(), limits);
            if (bounds == null) {
                rejectCapture(player, "region_cannot_fit_structure");
                return;
            }
            StructurePhysicsRegion region;
            try {
                region = StructurePhysics.openRegion(level, bounds);
            } catch (RuntimeException | AssertionError failure) {
                AcademyCraft.LOGGER.error("Kinetic Throw could not open a physics region for {}", reference, failure);
                rejectCapture(player, "region_open_failed");
                return;
            }
            var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
            if (handle == null) {
                region.close();
                rejectCapture(player, "captured_handle_unavailable");
                return;
            }
            try {
                region.attach(handle);
            } catch (RuntimeException | AssertionError failure) {
                AcademyCraft.LOGGER.error("Kinetic Throw could not attach {}", reference, failure);
                try {
                    region.close();
                } catch (RuntimeException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                rejectCapture(player, "region_attach_failed");
                return;
            }
            var held = new HeldStructure(reference, level.dimension(), tier, positions.size(), region, null);
            held.stage = Stage.AWAITING_PHYSICS;
            held.expiresAt = level.getGameTime() + HOVER_TICKS;
            HELD_STRUCTURES.put(player.getUUID(), held);
            var motion = region.motion(reference).orElse(null);
            playKineticCue(level, motion == null ? bounds.getCenter()
                    : motion.pose().localToWorldPoint(motion.localCenterOfMass()));
        }

        private static void beginSableLift(ServerPlayer player, StructureRef reference, int tier, int blockCount) {
            if (!(player.level() instanceof ServerLevel level)) return;
            var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
            var motion = StructureDynamics.motion(level, reference).orElse(null);
            if (handle == null || motion == null) {
                rejectCapture(player, "sable_structure_unavailable");
                return;
            }
            var range = Skills.KINETIC_THROW.get().scaledRange(player, TARGET_RANGE + liftHeight(tier));
            var hit = lookedAtStructure(player, reference, range);
            if (hit == null) {
                rejectCapture(player, "sable_target_not_visible");
                return;
            }
            var settings = KineticStructureControl.settings(motion.mass(), tier,
                    Skills.KINETIC_THROW.get().getEffectiveProficiencyMilestone(player), range);
            var acquired = StructureTraction.tryAcquire(player, UUID.randomUUID(), hit, settings,
                    (actor, candidate) -> actor == player && !player.isSpectator() && player.getAbilities().mayBuild);
            if (acquired.status() != StructureTraction.Status.ACTIVE) {
                rejectCapture(player, "sable_traction_" + acquired.status().name().toLowerCase(java.util.Locale.ROOT));
                return;
            }
            var session = acquired.session().orElseThrow();
            var point = session.worldPoint();
            var target = point.add(0, liftHeight(tier), 0);
            if (session.update(target, Vec3.ZERO) != StructureTraction.Status.ACTIVE) {
                session.close();
                rejectCapture(player, "sable_lift_target_rejected");
                return;
            }
            var held = new HeldStructure(reference, level.dimension(), tier, blockCount, null, session);
            held.stage = Stage.LIFTING;
            held.liftStartY = point.y;
            held.liftTarget = target;
            HELD_STRUCTURES.put(player.getUUID(), held);
            playKineticCue(level, point);
        }

        private static boolean launchNative(ServerPlayer player, HeldStructure held) {
            if (!(player.level() instanceof ServerLevel level) || held.region == null) return false;
            var direction = throwDirection(player.getLookAngle());
            if (direction.lengthSqr() <= 1.0e-8) return false;
            var motion = held.region.motion(held.reference).orElse(null);
            if (motion == null) return false;
            var skill = Skills.KINETIC_THROW.get();
            var speed = LegacyMotionUnits.velocityPerSecond(BASE_THROW_SPEED)
                    * (skill.hasProficiencyMilestone(player, 3) ? 1.2 : 1.0);
            var mass = motion.mass();
            var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
            held.closeControl();
            held.stage = Stage.FLYING;
            held.lifted = true;
            held.expiresAt = level.getGameTime() + MAXIMUM_FLIGHT_TICKS;
            held.velocity = direction.scale(speed);
            try {
                // Clear lift momentum first so this single impulse defines the launch velocity
                // exactly; from here the body is pure inertia with an empty control slot.
                held.region.setVelocity(held.reference, Vec3.ZERO, Vec3.ZERO);
                held.region.applyImpulse(held.reference, direction.scale(speed * mass), center);
            } catch (RuntimeException | AssertionError failure) {
                AcademyCraft.LOGGER.error("Kinetic Throw launch was rejected for {}", held.reference, failure);
                releaseHeld(player.getUUID());
                return false;
            }
            return true;
        }

        private static void playKineticCue(ServerLevel level, Vec3 center) {
            level.playSound(
                    null,
                    center.x, center.y, center.z,
                    org.academy.internal.common.sounds.SoundEvents.VECTOR_REFLECTION.get(),
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f
            );
        }

        private static void playImpactCue(ServerLevel level, Vec3 center, int tier) {
            var radius = explosionRadius(tier);
            level.sendParticles(
                    ParticleTypes.EXPLOSION_EMITTER,
                    center.x, center.y, center.z,
                    1,
                    0.0, 0.0, 0.0,
                    0.0
            );
            level.sendParticles(
                    ParticleTypes.EXPLOSION,
                    center.x, center.y, center.z,
                    radius * 8,
                    radius * 0.45, radius * 0.45, radius * 0.45,
                    0.08
            );
            level.playSound(
                    null,
                    center.x, center.y, center.z,
                    SoundEvents.GENERIC_EXPLODE,
                    SoundSource.BLOCKS,
                    4.0f,
                    0.8f + level.getRandom().nextFloat() * 0.2f
            );
        }

        private static void tick(ServerPlayer player) {
            var playerId = player.getUUID();
            tickCharge(player);
            tickCapture(player);
            var held = HELD_STRUCTURES.get(playerId);
            if (held == null) return;
            if (!held.usableBy(player) || player.level().getGameTime() > held.expiresAt) {
                releaseHeld(playerId);
                return;
            }
            switch (held.stage) {
                case AWAITING_PHYSICS -> tickAwaitingPhysics(player, held);
                case LIFTING -> tickLifting(player, held);
                case FLYING -> tickFlight(player, held);
                case CAPTURING -> throw new IllegalStateException("Held structure cannot be capturing");
            }
        }

        private static void tickCharge(ServerPlayer player) {
            var charge = CHARGES.get(player.getUUID());
            if (charge == null) return;
            if (!charge.dimension.equals(player.level().dimension())
                    || !player.isAlive()
                    || player.hasDisconnected()
                    || !Skills.KINETIC_THROW.get().isEnabled(player)) {
                CHARGES.remove(player.getUUID());
            } else {
                Skills.KINETIC_THROW.get().reportActivity(player, true);
            }
        }

        private static void tickCapture(ServerPlayer player) {
            var playerId = player.getUUID();
            var capture = CAPTURES.get(playerId);
            if (capture == null) return;
            if (!(player.level() instanceof ServerLevel level)
                    || !capture.dimension.equals(player.level().dimension())
                    || !player.isAlive() || player.hasDisconnected()) {
                CAPTURES.remove(playerId);
                return;
            }
            var result = StructureTransactions.poll(level.getServer(), capture.transactionId);
            if (result.isEmpty()) {
                CAPTURES.remove(playerId);
                rejectCapture(player, "transaction_evicted");
                return;
            }
            var current = result.get();
            if (current.pending()) {
                if (level.getGameTime() - capture.startedAt > CAPTURE_TIMEOUT_TICKS) {
                    CAPTURES.remove(playerId);
                    AcademyCraft.LOGGER.warn("Kinetic Throw capture {} exceeded its tracking window; structure retained",
                            capture.transactionId);
                }
                return;
            }
            CAPTURES.remove(playerId);
            if (current.succeeded()) {
                beginLift(player, current.structure().orElse(capture.reference), capture.tier, capture.positions);
            } else {
                rejectCapture(player, current.status() + ":" + current.detail());
            }
        }

        private static void tickAwaitingPhysics(ServerPlayer player, HeldStructure held) {
            var region = held.region;
            if (region == null) {
                releaseHeld(player.getUUID());
                return;
            }
            if (region.status() == StructurePhysicsRegion.Status.FAILED) {
                AcademyCraft.LOGGER.error("Kinetic Throw region failed before lift: {}", region.diagnostic());
                releaseHeld(player.getUUID());
                return;
            }
            if (region.status() != StructurePhysicsRegion.Status.READY) return;
            var motion = region.motion(held.reference).orElse(null);
            if (motion == null) return;
            var skill = Skills.KINETIC_THROW.get();
            var range = skill.scaledRange(player, TARGET_RANGE + liftHeight(held.tier));
            var settings = KineticStructureControl.settings(motion.mass(), held.tier,
                    skill.getEffectiveProficiencyMilestone(player), range);
            var local = motion.localCenterOfMass();
            var point = motion.pose().localToWorldPoint(local);
            held.liftStartY = point.y;
            held.liftTarget = point.add(0, liftHeight(held.tier), 0);
            held.stiffness = settings.stiffness();
            held.damping = settings.damping();
            held.maxForce = settings.maxForce();
            var control = new StructurePhysicsControl.Anchor(local, held.liftTarget, Vec3.ZERO,
                    settings.stiffness(), settings.damping(), settings.maxForce());
            Optional<StructurePhysicsLease> acquired;
            try {
                acquired = region.acquireEntityControl(held.reference, player, held.controllerId,
                        control, TRACTION_LEASE_TICKS);
            } catch (IllegalStateException | IllegalArgumentException rejection) {
                AcademyCraft.LOGGER.warn("Kinetic Throw lift control was rejected for {}", held.reference, rejection);
                releaseHeld(player.getUUID());
                return;
            }
            if (acquired.isEmpty()) {
                AcademyCraft.LOGGER.warn("Kinetic Throw lift control slot is occupied for {}", held.reference);
                releaseHeld(player.getUUID());
                return;
            }
            held.lease = acquired.orElseThrow();
            held.stage = Stage.LIFTING;
        }

        private static void tickLifting(ServerPlayer player, HeldStructure held) {
            if (held.traction != null) {
                tickSableLift(player, held);
                return;
            }
            var region = held.region;
            if (region == null) {
                releaseHeld(player.getUUID());
                return;
            }
            if (region.status() == StructurePhysicsRegion.Status.FAILED) {
                AcademyCraft.LOGGER.error("Kinetic Throw region failed while lifting: {}", region.diagnostic());
                releaseHeld(player.getUUID());
                return;
            }
            var motion = region.motion(held.reference).orElse(null);
            if (motion == null) {
                if (++held.unavailableTicks > REGION_UNAVAILABLE_GRACE_TICKS) releaseHeld(player.getUUID());
                return;
            }
            held.unavailableTicks = 0;
            var lease = held.lease;
            if (lease == null || lease.state() != StructurePhysicsLease.State.ACTIVE) {
                releaseHeld(player.getUUID());
                return;
            }
            try {
                lease.setControl(new StructurePhysicsControl.Anchor(motion.localCenterOfMass(),
                        held.liftTarget, Vec3.ZERO, held.stiffness, held.damping, held.maxForce));
                lease.renew(TRACTION_LEASE_TICKS);
            } catch (IllegalStateException | IllegalArgumentException rejection) {
                AcademyCraft.LOGGER.warn("Kinetic Throw lift lease ended for {}", held.reference, rejection);
                releaseHeld(player.getUUID());
                return;
            }
            held.velocity = motion.linearVelocity();
            var com = motion.pose().localToWorldPoint(motion.localCenterOfMass());
            if (!held.lifted && com.y >= held.liftStartY + liftHeight(held.tier) - 0.6) held.lifted = true;
            applyImpacts(player, held);
        }

        private static void tickSableLift(ServerPlayer player, HeldStructure held) {
            var session = held.traction;
            if (session == null || session.status() != StructureTraction.Status.ACTIVE) {
                releaseHeld(player.getUUID());
                return;
            }
            var status = session.update(held.liftTarget, Vec3.ZERO);
            if (status != StructureTraction.Status.ACTIVE) {
                AcademyCraft.LOGGER.info("Kinetic Throw sable lift ended: {} for {}", status, held.reference);
                releaseHeld(player.getUUID());
                return;
            }
            var point = session.worldPoint();
            held.velocity = Vec3.ZERO;
            if (!held.lifted && point.y >= held.liftStartY + liftHeight(held.tier) - 0.6) held.lifted = true;
        }

        private static void tickFlight(ServerPlayer player, HeldStructure held) {
            var region = held.region;
            if (region == null) {
                releaseHeld(player.getUUID());
                return;
            }
            if (region.status() == StructurePhysicsRegion.Status.FAILED) {
                AcademyCraft.LOGGER.error("Kinetic Throw region failed during flight: {}", region.diagnostic());
                releaseHeld(player.getUUID());
                return;
            }
            var motion = region.motion(held.reference).orElse(null);
            if (motion == null) {
                if (++held.unavailableTicks > REGION_UNAVAILABLE_GRACE_TICKS * 2) releaseHeld(player.getUUID());
                return;
            }
            held.unavailableTicks = 0;
            held.velocity = motion.linearVelocity();
            if (region.status() == StructurePhysicsRegion.Status.READY) followRegion(player, held, motion);
            applyImpacts(player, held);
        }

        private static void followRegion(ServerPlayer player, HeldStructure held, StructureMotionState motion) {
            if (!(player.level() instanceof ServerLevel level) || held.region == null) return;
            var projection = projection(level, held.reference, held.region.bounds().inflate(16.0));
            if (projection == null) return;
            var occupied = union(projection.worldCollisionBoxes());
            if (occupied == null) return;
            var halfDiagonal = Math.sqrt(occupied.getXsize() * occupied.getXsize()
                    + occupied.getYsize() * occupied.getYsize()
                    + occupied.getZsize() * occupied.getZsize()) / 2.0;
            var predicted = occupied.move(motion.linearVelocity().scale(0.05))
                    .inflate(motion.angularVelocity().length() * halfDiagonal * ANGULAR_MARGIN_SECONDS);
            var bounds = held.region.bounds();
            if (edgeDistance(bounds, predicted) >= FOLLOW_TRIGGER) return;
            var limits = new AABB(level.getWorldBorder().getMinX(), level.getMinY(), level.getWorldBorder().getMinZ(),
                    level.getWorldBorder().getMaxX(), level.getMaxY(), level.getWorldBorder().getMaxZ());
            var candidate = followBounds(bounds, predicted, motion.linearVelocity(), FOLLOW_LOOKAHEAD_SECONDS,
                    FOLLOW_TRAILING_MARGIN, level.getMinY(), level.getMaxY(), limits);
            if (candidate == null || candidate.equals(bounds) || !tryRebind(held.region, candidate)) {
                // A body that can no longer be kept inside a rebaseable region would fail the
                // region terminally on the next step. Ending the action keeps the structure.
                var conservative = followBounds(bounds, occupied, motion.linearVelocity(), FOLLOW_LOOKAHEAD_SECONDS,
                        FOLLOW_TRAILING_MARGIN, level.getMinY(), level.getMaxY(), limits);
                if (conservative == null || conservative.equals(bounds) || !tryRebind(held.region, conservative)) {
                    AcademyCraft.LOGGER.warn("Kinetic Throw region follow could not rebase {}; ending flight safely",
                            held.reference);
                    releaseHeld(player.getUUID());
                }
            }
        }

        private static boolean tryRebind(StructurePhysicsRegion region, AABB candidate) {
            try {
                return region.rebind(candidate);
            } catch (RuntimeException | AssertionError failure) {
                AcademyCraft.LOGGER.error("Kinetic Throw region rebase failed", failure);
                return false;
            }
        }

        private static void applyImpacts(ServerPlayer player, HeldStructure held) {
            if (!(player.level() instanceof ServerLevel level) || held.region == null) return;
            var projection = projection(level, held.reference, held.region.bounds().inflate(16.0));
            if (projection == null) return;
            var boxes = projection.worldCollisionBoxes();
            if (boxes.isEmpty()) return;
            var union = union(boxes);
            if (union == null) return;
            var now = level.getGameTime();
            var targets = new ArrayList<Entity>();
            for (var target : level.getEntities(player, union.inflate(1.5))) {
                if (target == projection || target.noPhysics || !canAffect(player, target)) continue;
                var last = held.impactTicks.get(target.getUUID());
                if (last != null && now - last < IMPACT_COOLDOWN_TICKS) continue;
                var targetBox = target.getBoundingBox();
                if (projection.supports(targetBox, SUPPORT_TOLERANCE)) continue;
                if (boxes.stream().noneMatch(box -> box.intersects(targetBox))) continue;
                targets.add(target);
            }
            for (var target : targets) settleImpact(player, held, projection, target, union.getCenter());
            held.impactTicks.entrySet().removeIf(entry -> now - entry.getValue() > IMPACT_COOLDOWN_TICKS * 4L);
        }

        private static void settleImpact(ServerPlayer player, HeldStructure held,
                                         NativeStructureProjectionEntity direct, Entity target, Vec3 center) {
            var level = (ServerLevel) player.level();
            var skill = Skills.KINETIC_THROW.get();
            var damage = structureDamage(held.blockCount);
            if (target instanceof LivingEntity living && damage > 0.0f) {
                var before = living.getHealth();
                var accepted = living.hurtServer(level, SkillDamageSource.ofDirect(player, skill, direct), damage);
                AcademyCraft.LOGGER.info(
                        "NATIVE_STRUCTURE_KINETIC_IMPACT tick={} target={} damage={} before={} after={} accepted={}",
                        level.getGameTime(), target.getStringUUID(), damage, before, living.getHealth(), accepted);
                if (accepted) {
                    skill.onHurt(player, living, damage);
                }
            }
            var movement = held.velocity;
            if (movement.lengthSqr() > 1.0e-8 && EntityMotionGuard.canApplyMotionFrom(player, target)) {
                var milestoneScale = skill.hasProficiencyMilestone(player, 3) ? 1.25 : 1.0;
                var knockback = Math.min(4.0, 0.6 + Math.sqrt(held.blockCount) * 0.08) * milestoneScale;
                var impulse = movement.normalize().scale(knockback).add(0.0, 0.15, 0.0);
                EntityMotionGuard.runWithMotionSource(player, () -> {
                    target.setDeltaMovement(target.getDeltaMovement().add(impulse));
                    target.hurtMarked = true;
                });
            }
            held.impactTicks.put(target.getUUID(), level.getGameTime());
            held.impactCount = Math.incrementExact(held.impactCount);
            if (!held.impactCuePlayed) {
                held.impactCuePlayed = true;
                playImpactCue(level, center, held.tier);
            }
        }

        private static boolean capturePolicyAllowed(ServerLevel level, ServerPlayer player, BlockPos position) {
            return level.mayInteract(player, position);
        }

        private static boolean canAffect(ServerPlayer player, Entity target) {
            return target != player
                    && target.isAlive()
                    && !target.isSpectator()
                    && !TeamRelations.areAllied(player, target)
                    && !PvpSetting.shouldPrevent(player, target);
        }

        private static BlockStructureCaptureOptions.CapturePolicy capturePolicy(ServerPlayer player) {
            return (level, position, state, blockEntity) ->
                    capturePolicyAllowed(level, player, position)
                            && blockEntity == null
                            && BlockStructureCaptureOptions.CapturePolicy.MOVABLE.canCapture(
                            level, position, state, null);
        }

        private static BiPredicate<ServerLevel, BlockPos> capturePermission(ServerPlayer player) {
            return (level, position) -> capturePolicyAllowed(level, player, position);
        }

        private static int cellBudget(ServerLevel level) {
            return sableMode(level) ? SABLE_CELL_BUDGET : NATIVE_CELL_BUDGET;
        }

        private static boolean sableMode(Level level) {
            return StructureBackendRouter.status(level.getServer()).mode() == StructureBackendStatus.Mode.SABLE;
        }

        private static StructureHit lookedAtStructure(ServerPlayer player, StructureRef reference, double range) {
            if (!(player.level() instanceof ServerLevel level)) return null;
            var look = player.getLookAngle();
            if (look.lengthSqr() <= 1.0e-8) return null;
            var eye = player.getEyePosition();
            var end = eye.add(look.normalize().scale(range));
            var hit = StructureQueries.raycast(level, eye, end)
                    .filter(candidate -> candidate.block().structure().equals(reference)).orElse(null);
            if (hit == null) return null;
            return StructureQueries.validate(level, eye, end, hit).currentHit().orElse(null);
        }

        private static boolean aimedAtHeld(ServerPlayer player, HeldStructure held, ServerLevel level) {
            var look = player.getLookAngle();
            if (look.lengthSqr() <= 1.0e-8) return false;
            var range = Skills.KINETIC_THROW.get().scaledRange(player, TARGET_RANGE);
            var eye = player.getEyePosition();
            var end = eye.add(look.scale(range));
            return StructureQueries.raycast(level, eye, end)
                    .filter(hit -> hit.block().structure().equals(held.reference)).isPresent();
        }

        private static boolean viewerInsideStructure(ServerPlayer player, HeldStructure held) {
            if (!(player.level() instanceof ServerLevel level) || held.region == null) return false;
            var projection = projection(level, held.reference, held.region.bounds().inflate(16.0));
            if (projection == null) return false;
            for (var box : projection.worldCollisionBoxes()) {
                if (box.intersects(player.getBoundingBox())) return true;
            }
            return false;
        }

        private static NativeStructureProjectionEntity projection(ServerLevel level, StructureRef reference, AABB area) {
            for (var entity : level.getEntitiesOfClass(NativeStructureProjectionEntity.class, area)) {
                if (!entity.isRemoved() && entity.viewState().reference().equals(reference)) return entity;
            }
            return null;
        }

        private static AABB union(List<AABB> boxes) {
            AABB result = null;
            for (var box : boxes) result = result == null ? box : result.minmax(box);
            return result;
        }

        private static BlockHitResult lookedAtBlock(ServerPlayer player) {
            var range = Skills.KINETIC_THROW.get().scaledRange(player, TARGET_RANGE);
            var start = player.getEyePosition();
            var end = start.add(player.getLookAngle().scale(range));
            var hit = player.level().clip(new ClipContext(
                    start,
                    end,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player
            ));
            return hit.getType() == HitResult.Type.BLOCK
                    && start.distanceToSqr(hit.getLocation()) <= range * range
                    ? hit
                    : null;
        }

        private static void rejectCapture(ServerPlayer player, String detail) {
            AcademyCraft.LOGGER.info("Kinetic Throw capture rejected for {}: {}", player.getUUID(), detail);
            player.sendOverlayMessage(Component.translatable(
                    "message.academy.kinetic_throw.capture_rejected"));
        }

        private static void clear(ServerPlayer player) {
            CHARGES.remove(player.getUUID());
            CAPTURES.remove(player.getUUID());
            releaseHeld(player.getUUID());
        }

        private static void releaseHeld(UUID playerId) {
            var held = HELD_STRUCTURES.remove(playerId);
            if (held == null) return;
            held.closeControl();
            // The structure, its physics region and any inertia are deliberately retained.
        }

        private static void requireThread(ServerPlayer player) {
            Objects.requireNonNull(player, "player");
            if (!player.level().getServer().isSameThread()) {
                throw new IllegalStateException("Kinetic Throw requires the owning server thread");
            }
        }

        private record ChargeState(
                ResourceKey<Level> dimension,
                BlockPos seed,
                long startTick
        ) {
        }

        private record CaptureState(
                StructureRef reference,
                ResourceKey<Level> dimension,
                int tier,
                List<BlockPos> positions,
                UUID transactionId,
                long startedAt,
                boolean sable
        ) {
            private int blockCount() {
                return positions.size();
            }
        }

        private static final class HeldStructure {
            private final StructureRef reference;
            private final ResourceKey<Level> dimension;
            private final int tier;
            private final int blockCount;
            private final UUID controllerId = UUID.randomUUID();
            private final boolean sable;
            private final Map<UUID, Long> impactTicks = new HashMap<>();
            private final StructurePhysicsRegion region;
            private final StructureTractionSession traction;
            private Stage stage = Stage.AWAITING_PHYSICS;
            private StructurePhysicsLease lease;
            private long expiresAt;
            private Vec3 liftTarget = Vec3.ZERO;
            private double liftStartY;
            private double stiffness;
            private double damping;
            private double maxForce;
            private boolean lifted;
            private boolean impactCuePlayed;
            private int impactCount;
            private int unavailableTicks;
            private Vec3 velocity = Vec3.ZERO;

            private HeldStructure(StructureRef reference, ResourceKey<Level> dimension, int tier, int blockCount,
                                  StructurePhysicsRegion region, StructureTractionSession traction) {
                this.reference = reference;
                this.dimension = dimension;
                this.tier = tier;
                this.blockCount = blockCount;
                this.region = region;
                this.traction = traction;
                this.sable = traction != null;
            }

            private boolean controlActive() {
                if (traction != null) return traction.status() == StructureTraction.Status.ACTIVE;
                return lease != null && lease.state() == StructurePhysicsLease.State.ACTIVE;
            }

            private boolean usableBy(ServerPlayer player) {
                return dimension.equals(player.level().dimension())
                        && StructureSpaces.resolve(player.level().getServer(), reference)
                        .map(handle -> handle.lifecycle() == StructureLifecycle.ACTIVE)
                        .orElse(false);
            }

            private void closeControl() {
                try {
                    if (lease != null) lease.close();
                } catch (RuntimeException failure) {
                    AcademyCraft.LOGGER.warn("Kinetic Throw lease cleanup failed for {}", reference, failure);
                } finally {
                    lease = null;
                }
                try {
                    if (traction != null) traction.close();
                } catch (RuntimeException failure) {
                    AcademyCraft.LOGGER.warn("Kinetic Throw traction cleanup failed for {}", reference, failure);
                }
            }
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clear(player);
        }

        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clear(player);
        }

        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clear(player);
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            for (var playerId : List.copyOf(Server.HELD_STRUCTURES.keySet())) {
                try {
                    Server.releaseHeld(playerId);
                } catch (RuntimeException failure) {
                    AcademyCraft.LOGGER.warn("Kinetic Throw shutdown cleanup failed for {}", playerId, failure);
                }
            }
            Server.CHARGES.clear();
            Server.CAPTURES.clear();
            Server.HELD_STRUCTURES.clear();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> {
        public static final StartPacket INSTANCE = new StartPacket();
        public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StartPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() {
            return PacketTypes.KINETIC_THROW_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ReleasePacket extends Packet<ServerGamePacketListenerImpl, ReleasePacket> {
        public static final ReleasePacket INSTANCE = new ReleasePacket();
        public static final StreamCodec<ByteBuf, ReleasePacket> CODEC = StreamCodec.unit(INSTANCE);

        private ReleasePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ReleasePacket> getPacketType() {
            return PacketTypes.KINETIC_THROW_RELEASE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ThrowPacket extends Packet<ServerGamePacketListenerImpl, ThrowPacket> {
        public static final ThrowPacket INSTANCE = new ThrowPacket();
        public static final StreamCodec<ByteBuf, ThrowPacket> CODEC = StreamCodec.unit(INSTANCE);

        private ThrowPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ThrowPacket> getPacketType() {
            return PacketTypes.KINETIC_THROW_THROW.get();
        }
    }
}
