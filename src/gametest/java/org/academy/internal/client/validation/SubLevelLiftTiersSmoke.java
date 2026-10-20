package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.movement.VectorStructureService;
import org.academy.internal.common.ability.accelerator.skills.lv3.KineticThrow;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Opt-in client validation of the live kinetic-throw lift for the three design gradients.
 * A 9x9 platform, or a solid 21x21x9 soil volume with close-fitting pit walls, is captured
 * with a 4/24/44 tick charge (tiers 0/1/2), then the
 * client keeps the actual skill session for 200 consecutive ticks within +/-0.1 blocks.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelLiftTiersSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelLiftTiersSmoke");
    private static final boolean VEGETATION = Boolean.getBoolean("academy.sublevelLiftVegetation");
    private static final boolean EMBEDDED = Boolean.getBoolean("academy.sublevelLiftEmbedded");
    private static final int[] HEIGHTS = {3, 5, 7};
    private static final int[] CHARGE_TICKS = {4, 24, 44};
    private static final double TOLERANCE = 0.1;
    private static final int HOVER_TICKS = 200;
    private static final int PLATFORM_RADIUS = 4;
    private static final int TIER_SPACING = 40;
    private static final int MAX_AGE = 2_000;

    private static volatile boolean clientReady;
    private static volatile boolean failed;
    private static volatile boolean complete;
    private static volatile boolean closing;
    private static boolean started;
    private static int lastTick = -1;
    private static int age;
    private static int tier;
    private static int phase;
    private static long chargeStart;
    private static int releaseAt;
    private static int hoverTicks;
    private static int hoverBest;
    private static int platformX, platformY, platformZ;
    private static Vec3 sourceCenter;
    private static StructureRef reference;
    private static ServerPlayer player;
    private static Vec3 previousPosition;
    private static Vec3 previousVelocity;
    private static float previousYaw;
    private static float previousPitch;
    private static boolean previousNoGravity;
    private static GameType previousMode;
    private static final List<Map<String, Object>> results = new ArrayList<>();
    private static final List<BlockPos> canopy = new ArrayList<>();
    private static int capturedPlants;
    private static int leafBreakEvents;
    private static int leafDropEvents;
    private static Vec3 launchOrigin;

    private SubLevelLiftTiersSmoke() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(ServerTickEvent.Post event) {
        if (!ENABLED || !clientReady || failed || complete) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty() || lastTick == server.getTickCount()) return;
        lastTick = server.getTickCount();
        try {
            if (!started) {
                begin(server);
                return;
            }
            age++;
            if (age > MAX_AGE) throw new IllegalStateException("lift tiers smoke deadline");
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            switch (phase) {
                case 1 -> tickCharging(server);
                case 2 -> tickCapturing(server);
                case 3 -> tickHovering(server);
                case 4 -> tickLaunching(server);
                default -> throw new IllegalStateException("unexpected phase " + phase);
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void begin(MinecraftServer server) {
        player = server.getPlayerList().getPlayers().getFirst();
        previousPosition = player.position();
        previousVelocity = player.getDeltaMovement();
        previousYaw = player.getYRot();
        previousPitch = player.getXRot();
        previousNoGravity = player.isNoGravity();
        previousMode = player.gameMode.getGameModeForPlayer();
        enable(player);
        tier = 0;
        buildPlatform();
        phase = 1;
        started = true;
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_START origin={} tier=0", player.blockPosition());
    }

    private static void buildPlatform() {
        var level = player.level();
        int x = player.blockPosition().getX() + 40 + tier * TIER_SPACING;
        int z = player.blockPosition().getZ();
        int y = Math.min(level.getMaxY() - 24, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 30);
        platformX = x;
        platformY = y;
        platformZ = z;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int dy = EMBEDDED ? -8 : -2; dy <= 10; dy++) {
                    level.setBlock(new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        if (EMBEDDED) {
            // A full soil volume leaves close-fitting side walls after capture. A one-layer
            // floating platform cannot reveal rotation or budget-created overhang traps.
            for (int dx = -10; dx <= 10; dx++) {
                for (int dz = -10; dz <= 10; dz++) {
                    for (int dy = -8; dy <= 0; dy++) {
                        level.setBlock(new BlockPos(x + dx, y + dy, z + dz),
                                dy == 0 ? Blocks.GRASS_BLOCK.defaultBlockState()
                                        : Blocks.DIRT.defaultBlockState(), 2);
                    }
                }
            }
        }
        if (VEGETATION && tier == 0) {
            NativeStructureTerrainAdmissionSmoke.run(level, new BlockPos(x + 7, y + 3, z + 7));
        }
        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                var ground = new BlockPos(x + dx, y, z + dz);
                boolean crop = (dx + dz) % 3 == 0;
                level.setBlock(ground, !VEGETATION ? (EMBEDDED ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState()
                        : crop ? Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7)
                        : Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                if (VEGETATION) {
                    level.setBlock(ground.above(), crop
                            ? Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)
                            : Blocks.SHORT_GRASS.defaultBlockState(), 2);
                }
            }
        }
        sourceCenter = new Vec3(x + 0.5, y + 0.5, z + 0.5);
        var feet = new Vec3(x + 0.5, y + 1, z - 8.5);
        place(feet, sourceCenter);
        var press = KineticThrow.PressPacket.INSTANCE;
        press.setPacketListener(player.connection);
        KineticThrow.Server.handlePress(press);
        chargeStart = player.level().getGameTime();
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_PLATFORM tier={} center={} platform={}", tier, sourceCenter,
                new BlockPos(x, y, z));
    }

    private static void tickCharging(MinecraftServer server) {
        if (player.level().getGameTime() - chargeStart < CHARGE_TICKS[tier]) return;
        var release = KineticThrow.ReleasePacket.INSTANCE;
        release.setPacketListener(player.connection);
        KineticThrow.Server.handleRelease(release);
        releaseAt = server.getTickCount();
        phase = 2;
    }

    private static void tickCapturing(MinecraftServer server) {
        var diagnostic = VectorStructureService.diagnostic(player).orElse(null);
        if (diagnostic == null) {
            if (server.getTickCount() - releaseAt > 80) throw new IllegalStateException("capture did not start");
            return;
        }
        if (diagnostic.stage() == VectorStructureService.Stage.CAPTURING) {
            if (server.getTickCount() - releaseAt > 200) throw new IllegalStateException("capture did not finish");
            return;
        }
        if (diagnostic.reference() == null) throw new IllegalStateException("captured structure has no reference");
        reference = diagnostic.reference();
        var capturedMotion = StructureDynamics.motion(player.level(), reference).orElseThrow();
        var sourceCells = VectorStructureService.session(player).orElseThrow().capturedCells();
        if (sourceCells.isEmpty()) throw new IllegalStateException("lift session lost source cells");
        var sourceOrigin = new Vec3(sourceCells.stream().mapToInt(BlockPos::getX).min().orElseThrow(),
                sourceCells.stream().mapToInt(BlockPos::getY).min().orElseThrow(),
                sourceCells.stream().mapToInt(BlockPos::getZ).min().orElseThrow());
        sourceCenter = sourceOrigin.add(capturedMotion.localCenterOfMass());
        if (EMBEDDED) {
            var selected = new java.util.HashSet<>(sourceCells);
            long buried = sourceCells.stream().filter(position -> !selected.contains(position.above())
                    && !player.level().getBlockState(position.above())
                    .getCollisionShape(player.level(), position.above()).isEmpty()).count();
            AcademyCraft.LOGGER.info("SUBLEVEL_EMBEDDED_SELECTION tier={} cells={} trapped_cells={} source={}",
                    tier, sourceCells.size(), buried, sourceCenter);
            var sections = new java.util.HashSet<net.minecraft.core.SectionPos>();
            for (var cell : sourceCells) sections.add(net.minecraft.core.SectionPos.of(cell));
            var terrainBoxes = new ArrayList<net.minecraft.world.phys.AABB>();
            for (var section : sections) {
                var geometry = org.academy.internal.common.sublevel.TerrainSectionGeometry.capture(
                        player.level(), section.x(), section.y(), section.z());
                for (var box : geometry.boxes()) {
                    var center = box.center();
                    var half = box.halfExtents();
                    terrainBoxes.add(new net.minecraft.world.phys.AABB(
                            section.minBlockX() + center.x() - half.x(),
                            section.minBlockY() + center.y() - half.y(),
                            section.minBlockZ() + center.z() - half.z(),
                            section.minBlockX() + center.x() + half.x(),
                            section.minBlockY() + center.y() + half.y(),
                            section.minBlockZ() + center.z() + half.z()));
                }
            }
            long overlaps = sourceCells.stream().filter(cell -> terrainBoxes.stream()
                    .anyMatch(box -> box.intersects(new net.minecraft.world.phys.AABB(cell).deflate(1.0E-6)))).count();
            long liftOverlaps = sourceCells.stream().filter(cell -> terrainBoxes.stream()
                    .anyMatch(box -> box.intersects(new net.minecraft.world.phys.AABB(cell)
                            .move(0, 0.1, 0).deflate(1.0E-6)))).count();
            AcademyCraft.LOGGER.info("SUBLEVEL_EMBEDDED_TERRAIN tier={} boxes={} source_overlaps={} lift_overlaps={}",
                    tier, terrainBoxes.size(), overlaps, liftOverlaps);
            if (buried != 0 || overlaps != 0 || liftOverlaps != 0) {
                throw new IllegalStateException("capture left blocking terrain: caps=" + buried
                        + ", source=" + overlaps + ", lift=" + liftOverlaps);
            }
        }
        if (VEGETATION) {
            var runtime = SubLevelServer.runtime(player.level()).orElseThrow();
            var id = SubLevelStructureRefs.id(reference).orElseThrow();
            capturedPlants = (int) runtime.view(id).cells().stream()
                    .filter(cell -> cell.state().is(Blocks.SHORT_GRASS) || cell.state().is(Blocks.WHEAT)).count();
            if (capturedPlants == 0) throw new IllegalStateException("capture discarded all grass/crops");
            canopy.clear();
            leafBreakEvents = 0;
            leafDropEvents = 0;
            for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
                for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                    var position = new BlockPos(platformX + dx, platformY + 2, platformZ + dz);
                    player.level().setBlock(position,
                            Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1), 2);
                    canopy.add(position);
                }
            }
            AcademyCraft.LOGGER.info("SUBLEVEL_VEGETATION_CAPTURED tier={} plants={} canopy={}",
                    tier, capturedPlants, canopy.size());
        }
        hoverTicks = 0;
        hoverBest = 0;
        phase = 3;
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_CAPTURED tier={} reference={} source={}", tier, reference, sourceCenter);
    }

    private static void tickHovering(MinecraftServer server) {
        var motion = StructureDynamics.motion(player.level(), reference).orElse(null);
        if (motion == null) {
            if (server.getTickCount() - releaseAt > 400) throw new IllegalStateException("lift body unavailable");
            return;
        }
        var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
        double rise = center.y - sourceCenter.y;
        if ((server.getTickCount() - releaseAt) % 20 == 0) {
            var diagnostic = VectorStructureService.diagnostic(player).orElse(null);
            long cleared = canopy.stream().filter(position -> player.level().getBlockState(position).isAir()).count();
            AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_PROGRESS tier={} ticks={} center={} rise={} "
                            + "linear={} angular={} mass={} source={} session={} cleared={} breakEvents={} dropEvents={} pose={}",
                    tier, server.getTickCount() - releaseAt, center, rise, motion.linearVelocity(),
                    motion.angularVelocity(), motion.mass(), sourceCenter, diagnostic, cleared,
                    leafBreakEvents, leafDropEvents, motion.pose());
        }
        if (Math.abs(rise - HEIGHTS[tier]) <= TOLERANCE) {
            hoverTicks++;
            hoverBest = Math.max(hoverBest, hoverTicks);
        } else {
            hoverTicks = 0;
        }
        if (hoverTicks >= HOVER_TICKS) {
            long clearedLeaves = canopy.stream().filter(position -> player.level().getBlockState(position).isAir()).count();
            if (VEGETATION && (clearedLeaves == 0 || leafBreakEvents == 0 || leafDropEvents == 0)) {
                throw new IllegalStateException("leaf motion destruction missed blocks or global events: "
                        + clearedLeaves + "/" + leafBreakEvents + "/" + leafDropEvents);
            }
            var result = new LinkedHashMap<String, Object>();
            result.put("tier", tier);
            result.put("height", HEIGHTS[tier]);
            result.put("rise", rise);
            result.put("hover_ticks", hoverTicks);
            result.put("hover_best", hoverBest);
            result.put("captured_plants", capturedPlants);
            result.put("cleared_leaves", clearedLeaves);
            result.put("leaf_break_events", leafBreakEvents);
            result.put("leaf_drop_events", leafDropEvents);
            result.put("pass", true);
            results.add(result);
            AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_TIER_PASSED tier={} height={} rise={} hover={}",
                    tier, HEIGHTS[tier], rise, hoverTicks);
            player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                    player.getYRot() + 180.0F, player.getXRot());
            player.setYHeadRot(player.getYRot());
            if (!VectorStructureService.launch(player)) throw new IllegalStateException("tier launch refused");
            launchOrigin = center;
            phase = 4;
            releaseAt = server.getTickCount();
        } else if (server.getTickCount() - releaseAt > (EMBEDDED && rise < HEIGHTS[tier] - TOLERANCE ? 220 : 700)) {
            throw new IllegalStateException("tier " + tier + " did not hold 200 ticks; best=" + hoverBest);
        }
    }

    private static void tickLaunching(MinecraftServer server) {
        if (server.getTickCount() - releaseAt < 20) return;
        if (VEGETATION) {
            var motion = StructureDynamics.motion(player.level(), reference).orElseThrow();
            double distance = motion.pose().localToWorldPoint(motion.localCenterOfMass()).distanceTo(launchOrigin);
            if (distance < 3.0) throw new IllegalStateException("vegetation launch remained stuck: " + distance);
            AcademyCraft.LOGGER.info("SUBLEVEL_VEGETATION_LAUNCH_PASSED tier={} distance={}", tier, distance);
        }
        tier++;
        if (tier > 2) {
            complete = true;
            finish();
            return;
        }
        buildPlatform();
        phase = 1;
    }

    private static void enable(ServerPlayer player) {
        var system = AbilitySystemServer.getSystem(player);
        var actor = player.getUUID();
        var skill = Skills.KINETIC_THROW.get();
        system.setPlayerAbilityCategory(actor, AbilityCategories.ACCELERATOR.get());
        system.setPlayerLevel(actor, 5);
        system.setPlayerBaseMaxCP(actor, 100_000);
        system.setPlayerAvailableCP(actor, 100_000);
        system.setPlayerStatus(actor, org.academy.api.common.data.AbilityData.Status.NORMAL);
        system.addPlayerSkill(player, skill.getKeyString());
        if (!skill.isEnabled(player)) system.toggleSkill(actor, skill.getKeyString());
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
    }

    @SubscribeEvent
    public static void leafBreak(BreakBlockEvent event) {
        if (ENABLED && VEGETATION && player != null && event.getLevel() == player.level()
                && canopy.contains(event.getPos())) leafBreakEvents++;
    }

    @SubscribeEvent
    public static void leafDrops(BlockDropsEvent event) {
        if (ENABLED && VEGETATION && player != null && event.getLevel() == player.level()
                && canopy.contains(event.getPos())) leafDropEvents++;
    }

    private static void place(Vec3 feet, Vec3 lookAt) {
        var eye = feet.add(0, player.getEyeHeight(), 0);
        var delta = lookAt.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        player.connection.teleport(feet.x, feet.y, feet.z, yaw, pitch);
        player.setYHeadRot(yaw);
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(true);
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var minecraft = Minecraft.getInstance();
        if (failed || complete) {
            closing = true;
            if (minecraft.player != null) minecraft.player.resetFallDistance();
            minecraft.stop();
            return;
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) return;
        if (!minecraft.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString()
                .startsWith("native-sublevel-direct-smoke")) {
            fail(new IllegalStateException("lift tiers smoke requires an isolated run directory"));
            return;
        }
        clientReady = true;
    }

    private static void finish() {
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_SMOKE_PASSED tiers={} results={}", results.size(), results);
        restore();
    }

    private static void fail(Throwable failure) {
        if (failed || complete) return;
        failed = true;
        AcademyCraft.LOGGER.error("SUBLEVEL_LIFT357_SMOKE_FAILED tier={} phase={} age={} best={} results={}",
                tier, phase, age, hoverBest, results, failure);
        restore();
    }

    private static void restore() {
        if (player == null) return;
        try {
            player.resetFallDistance();
            player.connection.teleport(previousPosition.x, previousPosition.y, previousPosition.z,
                    previousYaw, previousPitch);
            player.setDeltaMovement(previousVelocity);
            player.setNoGravity(previousNoGravity);
            player.setGameMode(previousMode);
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.warn("Could not restore lift tiers smoke player", failure);
        }
    }

    public static boolean complete() {
        return complete;
    }
}
