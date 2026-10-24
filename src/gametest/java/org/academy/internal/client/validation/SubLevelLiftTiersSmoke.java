package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
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
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.api.common.structure.upgrade.StructureCaptureExtractions;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.movement.VectorStructureService;
import org.academy.internal.common.ability.accelerator.skills.lv3.KineticThrow;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

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
    private static final String TERRAIN = System.getProperty("academy.sublevelLiftTerrain", "flat");
    private static final String MATERIAL = System.getProperty("academy.sublevelLiftMaterial", "soil");
    private static final int ONLY_TIER = Integer.getInteger("academy.sublevelLiftTier", -1);
    private static final String FIXED_ANCHOR = System.getProperty("academy.sublevelLiftAnchor", "").trim();
    /** Diagnostic isolation only: a pass in this mode does not validate sequential retained bodies. */
    private static final boolean DISCARD_BETWEEN_TIERS = Boolean.getBoolean("academy.sublevelLiftDiscardBetweenTiers");
    private static final boolean HARD_ROOF = TERRAIN.equals("roof");
    private static final boolean DISTURBANCE = Boolean.getBoolean("academy.sublevelLiftDisturbance");
    private static final String DISTURBANCE_MODE = System.getProperty("academy.sublevelLiftDisturbanceMode", "both");
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
    private static BlockPos fixedAnchor;
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
    private static double maximumHorizontalDrift;
    private static double maximumAngularSpeed;
    private static double maximumOrientationErrorDegrees;
    private static int angularReversals;
    private static Vec3 previousAngular = Vec3.ZERO;
    private static double maximumRise;
    private static final List<BlockPos> hardRoof = new ArrayList<>();
    private static StructureRef pendingDiscardObservation;
    private static Map<BlockPos, BlockState> roofSourceSnapshot = Map.of();
    private static Set<String> roofStructureSnapshot = Set.of();
    private static CaptureRejectionObserver rejectionObserver;
    private static LoggerConfig rejectionLoggerConfig;
    private static Path output;

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
            if (pendingDiscardObservation != null) {
                AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_DISCARD_NEXT_TICK reference={} terrain={} active={}",
                        pendingDiscardObservation, terrainCoverage(), activeBodies());
                pendingDiscardObservation = null;
            }
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            switch (phase) {
                case 1 -> tickCharging(server);
                case 2 -> tickCapturing(server);
                case 3 -> tickHovering(server);
                case 4 -> tickLaunching(server);
                case 5 -> tickRoofRejection(server);
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
        if (!List.of("flat", "slope", "step", "corner", "roof").contains(TERRAIN)) {
            throw new IllegalArgumentException("Unknown lift terrain fixture: " + TERRAIN);
        }
        if (!List.of("soil", "stone", "iron").contains(MATERIAL)) throw new IllegalArgumentException("Unknown lift material");
        if (ONLY_TIER < -1 || ONLY_TIER > 2) throw new IllegalArgumentException("Lift tier must be -1 or 0..2");
        if (!EMBEDDED && !TERRAIN.equals("flat")) throw new IllegalArgumentException("Terrain fixtures require embedded=true");
        if (!FIXED_ANCHOR.isEmpty()) {
            var coordinates = FIXED_ANCHOR.split(",", -1);
            if (coordinates.length != 3) throw new IllegalArgumentException("Lift anchor must be tier-0 platform x,y,z");
            fixedAnchor = new BlockPos(Integer.parseInt(coordinates[0].trim()),
                    Integer.parseInt(coordinates[1].trim()), Integer.parseInt(coordinates[2].trim()));
            if (Math.abs((long) fixedAnchor.getX()) > 29_999_000
                    || Math.abs((long) fixedAnchor.getZ()) > 29_999_000
                    || fixedAnchor.getY() < player.level().getMinY() + 8
                    || fixedAnchor.getY() > player.level().getMaxY() - 24) {
                throw new IllegalArgumentException("Lift anchor leaves insufficient room for the fixture");
            }
        }
        tier = ONLY_TIER >= 0 ? ONLY_TIER : 0;
        buildPlatform();
        phase = 1;
        started = true;
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_START origin={} tier={} terrain={} fixed_anchor={} discard_between_tiers={}",
                player.blockPosition(), tier, TERRAIN, fixedAnchor, DISCARD_BETWEEN_TIERS);
    }

    private static void buildPlatform() {
        var level = player.level();
        int x = player.blockPosition().getX() + 40 + tier * TIER_SPACING;
        int z = player.blockPosition().getZ();
        // Keep every sequential tier well above natural terrain. At +30 the second platform can
        // sit in a surface hillside; its uncaptured terrain then blocks the ascent even though the
        // captured columns are clear.
        int y = Math.min(level.getMaxY() - 24, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 60);
        if (fixedAnchor != null) {
            // Preserve sequential tier spacing (+80/+120 X, -9 Z) independently of player
            // startup motion. The anchor always denotes tier 0, including single-tier runs.
            x = fixedAnchor.getX() + TIER_SPACING * tier * (tier + 3) / 2;
            y = fixedAnchor.getY();
            z = fixedAnchor.getZ() - 9 * tier;
        }
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
                    int top = terrainHeight(dx, dz);
                    for (int dy = -8; dy <= top; dy++) {
                        level.setBlock(new BlockPos(x + dx, y + dy, z + dz),
                                MATERIAL.equals("iron") ? Blocks.IRON_BLOCK.defaultBlockState()
                                        : MATERIAL.equals("stone") ? Blocks.STONE.defaultBlockState()
                                        : dy == top ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2);
                    }
                }
            }
        }
        if (VEGETATION && tier == 0) {
            NativeStructureTerrainAdmissionSmoke.run(level, new BlockPos(x + 7, y + 9, z + 7));
        }
        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                var ground = new BlockPos(x + dx, y + (EMBEDDED ? terrainHeight(dx, dz) : 0), z + dz);
                boolean crop = (dx + dz) % 3 == 0;
                level.setBlock(ground, !VEGETATION ? (MATERIAL.equals("iron") ? Blocks.IRON_BLOCK
                        : MATERIAL.equals("stone") ? Blocks.STONE : EMBEDDED ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState()
                        : crop ? Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7)
                        : Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                if (VEGETATION) {
                    level.setBlock(ground.above(), crop
                            ? Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)
                            : Blocks.SHORT_GRASS.defaultBlockState(), 2);
                }
            }
        }
        hardRoof.clear();
        if (HARD_ROOF) {
            // An ordinary roof inside the upper cylinder is captured with its column. Bedrock
            // instead remains outside admission, with one air cell before the first collision.
            for (int dx = 1; dx <= 3; dx++) {
                for (int dz = -3; dz <= -1; dz++) {
                    var position = new BlockPos(x + dx, y + 2, z + dz);
                    level.setBlock(position, Blocks.BEDROCK.defaultBlockState(), 2);
                    hardRoof.add(position);
                }
            }
        }
        maximumHorizontalDrift = 0;
        maximumAngularSpeed = 0;
        maximumOrientationErrorDegrees = 0;
        angularReversals = 0;
        previousAngular = Vec3.ZERO;
        maximumRise = 0;
        sourceCenter = new Vec3(x + 0.5, y + 0.5, z + 0.5);
        var feet = new Vec3(x + 0.5, y + 1, z - 8.5);
        place(feet, sourceCenter);
        var press = KineticThrow.PressPacket.INSTANCE;
        press.setPacketListener(player.connection);
        KineticThrow.Server.handlePress(press);
        chargeStart = player.level().getGameTime();
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_PLATFORM tier={} center={} platform={} terrain={}", tier, sourceCenter,
                new BlockPos(x, y, z), TERRAIN);
    }

    private static int terrainHeight(int dx, int dz) {
        return switch (TERRAIN) {
            case "slope" -> Math.floorDiv(dx + 4, 2) - 2;
            case "step" -> dx > 0 ? 2 : 0;
            case "corner" -> dx > 0 && dz > 0 ? 2 : 0;
            default -> 0;
        };
    }

    private static void tickCharging(MinecraftServer server) {
        if (player.level().getGameTime() - chargeStart < CHARGE_TICKS[tier]) return;
        boolean expectRoofRejection = HARD_ROOF && StructureCaptureExtractions.enabled();
        if (expectRoofRejection) beginRoofRejection(server);
        var release = KineticThrow.ReleasePacket.INSTANCE;
        release.setPacketListener(player.connection);
        KineticThrow.Server.handleRelease(release);
        releaseAt = server.getTickCount();
        phase = expectRoofRejection ? 5 : 2;
    }

    private static void beginRoofRejection(MinecraftServer server) {
        var snapshot = new LinkedHashMap<BlockPos, BlockState>();
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int dy = -8; dy <= 10; dy++) {
                    var position = new BlockPos(platformX + dx, platformY + dy, platformZ + dz);
                    snapshot.put(position, player.level().getBlockState(position));
                }
            }
        }
        roofSourceSnapshot = Map.copyOf(snapshot);
        if (hardRoof.isEmpty() || hardRoof.stream().anyMatch(position -> !snapshot.get(position).is(Blocks.BEDROCK))) {
            throw new IllegalStateException("Hard roof fixture is incomplete before release");
        }
        roofStructureSnapshot = structureIdentities(server);
        // Observe the rejection emitted by the real H release. An absent session alone cannot
        // distinguish BLOCKED from a missed ray, unavailable backend or another failed check.
        var logger = LogManager.getLogger(AcademyCraft.LOGGER.getName());
        if (!(logger instanceof org.apache.logging.log4j.core.Logger coreLogger)) {
            throw new IllegalStateException("Hard roof fixture cannot observe actual capture rejection");
        }
        // Attach to the existing effective config rather than Logger.addAppender(), which
        // can create a new non-additive child config and hide the normal academy appenders.
        rejectionLoggerConfig = coreLogger.getContext().getConfiguration()
                .getLoggerConfig(AcademyCraft.LOGGER.getName());
        rejectionObserver = new CaptureRejectionObserver(player.getUUID().toString());
        rejectionObserver.start();
        rejectionLoggerConfig.addAppender(rejectionObserver, null, null);
    }

    private static void tickRoofRejection(MinecraftServer server) {
        for (var entry : roofSourceSnapshot.entrySet()) {
            if (!player.level().getBlockState(entry.getKey()).equals(entry.getValue())) {
                throw new IllegalStateException("Rejected lift modified source/roof block at " + entry.getKey());
            }
        }
        var currentStructures = structureIdentities(server);
        var added = new HashSet<>(currentStructures);
        added.removeAll(roofStructureSnapshot);
        if (!added.isEmpty()) throw new IllegalStateException("Rejected lift created structures: " + added);
        if (VectorStructureService.diagnostic(player).isPresent() || VectorStructureService.session(player).isPresent()) {
            throw new IllegalStateException("Rejected lift retained a vector session");
        }
        var reasons = List.copyOf(rejectionObserver.reasons);
        int elapsed = server.getTickCount() - releaseAt;
        if (reasons.isEmpty()) {
            if (elapsed > 10) throw new IllegalStateException("Real H release emitted no observable rejection");
            return;
        }
        String blockedPrefix = "REJECTED:extraction_preview:BLOCKED:";
        if (reasons.size() != 1 || !reasons.getFirst().startsWith(blockedPrefix)
                || reasons.getFirst().substring(blockedPrefix.length()).isBlank()) {
            throw new IllegalStateException("Roof rejection was not the actual BLOCKED preview: " + reasons);
        }
        if (elapsed < 5) return;
        var result = new LinkedHashMap<String, Object>();
        result.put("tier", tier);
        result.put("height", HEIGHTS[tier]);
        result.put("terrain", TERRAIN);
        result.put("extraction_gameplay_enabled", true);
        result.put("rejection", reasons.getFirst());
        result.put("unchanged_source_cells", roofSourceSnapshot.size());
        result.put("unchanged_bedrock_cells", hardRoof.size());
        result.put("structures_before", roofStructureSnapshot.size());
        result.put("structures_after", currentStructures.size());
        result.put("new_structures", 0);
        result.put("observation_ticks", elapsed);
        result.put("pass", true);
        results.add(result);
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT_ROOF_NEGATIVE_PASSED preview_rejection={} "
                        + "unchanged_source_cells={} unchanged_bedrock_cells={} new_structures=0 observation_ticks={}",
                reasons.getFirst(), roofSourceSnapshot.size(), hardRoof.size(), elapsed);
        finish();
    }

    private static Set<String> structureIdentities(MinecraftServer server) {
        var identities = new HashSet<String>();
        SubLevelServer.runtime(player.level()).ifPresent(runtime -> runtime.subLevels().forEach(body ->
                identities.add("academy:sublevel:" + body.id().value())));
        if (StructureSpaces.status(server).sableQueriesAvailable()) SableEvidence.addIdentities(identities);
        return Set.copyOf(identities);
    }

    /** Fixture-local evidence only; no production diagnostic or capture behavior is changed. */
    private static final class CaptureRejectionObserver extends AbstractAppender {
        private final String prefix;
        private final ConcurrentLinkedQueue<String> reasons = new ConcurrentLinkedQueue<>();

        private CaptureRejectionObserver(String actorId) {
            super("AcademyLiftRoofRejection", null, null, true, Property.EMPTY_ARRAY);
            prefix = "Kinetic Throw capture rejected for " + actorId + ": ";
        }

        @Override
        public void append(LogEvent event) {
            if (!AcademyCraft.LOGGER.getName().equals(event.getLoggerName())) return;
            String message = event.getMessage().getFormattedMessage();
            if (message.startsWith(prefix)) reasons.add(message.substring(prefix.length()));
        }
    }

    private static void stopRejectionObserver() {
        if (rejectionObserver == null) return;
        if (rejectionLoggerConfig != null) rejectionLoggerConfig.removeAppender(rejectionObserver.getName());
        rejectionObserver.stop();
        rejectionObserver = null;
        rejectionLoggerConfig = null;
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
        if (DISTURBANCE) {
            disturb(capturedMotion);
            capturedMotion = StructureDynamics.motion(player.level(), reference).orElseThrow();
        }
        var sourceCells = VectorStructureService.session(player).orElseThrow().capturedCells();
        if (sourceCells.isEmpty()) throw new IllegalStateException("lift session lost source cells");
        var sourceOrigin = new Vec3(sourceCells.stream().mapToInt(BlockPos::getX).min().orElseThrow(),
                sourceCells.stream().mapToInt(BlockPos::getY).min().orElseThrow(),
                sourceCells.stream().mapToInt(BlockPos::getZ).min().orElseThrow());
        sourceCenter = sourceOrigin.add(capturedMotion.localCenterOfMass());
        var initialCenter = capturedMotion.pose().localToWorldPoint(capturedMotion.localCenterOfMass());
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT_INITIAL terrain={} tier={} cells={} source_origin={} local_com={} "
                        + "source_com={} actual_com={} delta={} pose={} linear={} angular={} terrain_covered={}",
                TERRAIN, tier, sourceCells.size(), sourceOrigin, capturedMotion.localCenterOfMass(), sourceCenter,
                initialCenter, initialCenter.subtract(sourceCenter), capturedMotion.pose(), capturedMotion.linearVelocity(),
                capturedMotion.angularVelocity(), terrainCoverage());
        if (EMBEDDED) {
            var selected = new java.util.HashSet<>(sourceCells);
            long buried = sourceCells.stream().filter(position -> !selected.contains(position.above())
                    && !player.level().getBlockState(position.above())
                    .getCollisionShape(player.level(), position.above()).isEmpty()).count();
            AcademyCraft.LOGGER.info("SUBLEVEL_EMBEDDED_SELECTION tier={} cells={} trapped_cells={} source={}",
                    tier, sourceCells.size(), buried, sourceCenter);
            var sections = new java.util.HashSet<net.minecraft.core.SectionPos>();
            for (var cell : sourceCells) {
                for (int dy = 0; dy <= HEIGHTS[tier]; dy++) {
                    sections.add(net.minecraft.core.SectionPos.of(cell.above(dy)));
                }
            }
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
            long oneBlockOverlaps = sourceCells.stream().filter(cell -> terrainBoxes.stream()
                    .anyMatch(box -> box.intersects(new net.minecraft.world.phys.AABB(cell)
                            .move(0, 1.01, 0).deflate(1.0E-6)))).count();
            long targetOverlaps = sourceCells.stream().filter(cell -> terrainBoxes.stream()
                    .anyMatch(box -> box.intersects(new net.minecraft.world.phys.AABB(cell)
                            .move(0, HEIGHTS[tier], 0).deflate(1.0E-6)))).count();
            AcademyCraft.LOGGER.info("SUBLEVEL_EMBEDDED_TERRAIN tier={} terrain={} boxes={} source_overlaps={} "
                            + "lift_overlaps={} one_block_overlaps={} target_overlaps={}",
                    tier, TERRAIN, terrainBoxes.size(), overlaps, liftOverlaps, oneBlockOverlaps, targetOverlaps);
            if (buried != 0 || overlaps != 0 || liftOverlaps != 0) {
                throw new IllegalStateException("capture left blocking terrain: caps=" + buried
                        + ", source=" + overlaps + ", lift=" + liftOverlaps);
            }
            if (HARD_ROOF && oneBlockOverlaps == 0) {
                throw new IllegalStateException("Hard roof fixture did not intersect the captured body's ascent");
            }
            if (!HARD_ROOF && (oneBlockOverlaps != 0 || targetOverlaps != 0)) {
                throw new IllegalStateException("Positive terrain fixture has a real ascent obstruction");
            }
        }
        if (VEGETATION) {
            var world = StructureSpaces.resolve(server, reference).orElseThrow().world();
            var sourceMinimum = BlockPos.containing(sourceOrigin);
            capturedPlants = (int) sourceCells.stream().map(cell -> world.read(cell.subtract(sourceMinimum)).state())
                    .filter(state -> state.is(Blocks.SHORT_GRASS) || state.is(Blocks.WHEAT)).count();
            if (StructureCaptureExtractions.enabled() && capturedPlants != 0) {
                throw new IllegalStateException("soft source plants were copied instead of explicitly cleared");
            }
            canopy.clear();
            leafBreakEvents = 0;
            leafDropEvents = 0;
            for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
                for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                    var position = new BlockPos(platformX + dx,
                            platformY + (EMBEDDED ? terrainHeight(dx, dz) : 0) + 2, platformZ + dz);
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

    private static void disturb(StructureDynamics.State motion) {
        var linear = DISTURBANCE_MODE.equals("angular") ? Vec3.ZERO : new Vec3(0.2, 0, 0);
        var angular = DISTURBANCE_MODE.equals("linear") ? Vec3.ZERO : new Vec3(0, 0, 0.05);
        if (SubLevelStructureRefs.isSubLevel(reference)) {
            var universe = SubLevelServer.runtime(player.level()).orElseThrow().universe();
            var velocity = motion.linearVelocity().add(linear);
            var spin = motion.angularVelocity().add(angular);
            if (!universe.setVelocity(SubLevelStructureRefs.id(reference).orElseThrow(),
                    new org.academy.internal.common.structure.physics.PhysicsVector(velocity.x, velocity.y, velocity.z),
                    new org.academy.internal.common.structure.physics.PhysicsVector(spin.x, spin.y, spin.z))) {
                throw new IllegalStateException("Lift disturbance was rejected");
            }
        } else {
            SableDisturbance.apply(linear, angular);
        }
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT_DISTURBANCE tier={} terrain={} reference={} linear_delta={} angular_delta={}",
                tier, TERRAIN, reference, linear, angular);
    }

    /** Optional backend linkage is resolved only when the fixture actually runs with Sable. */
    private static final class SableDisturbance {
        static void apply(Vec3 linear, Vec3 angular) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(player.level());
            var body = container == null ? null : container.getSubLevel(reference.id());
            if (!(body instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel serverBody)) {
                throw new IllegalStateException("Sable disturbance body is unavailable");
            }
            container.physicsSystem().getPhysicsHandle(serverBody).addLinearAndAngularVelocity(
                    new org.joml.Vector3d(linear.x, linear.y, linear.z),
                    new org.joml.Vector3d(angular.x, angular.y, angular.z));
        }
    }

    private static final class SableEvidence {
        static void addIdentities(Set<String> identities) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(player.level());
            if (container == null) throw new IllegalStateException("Sable structure inventory is unavailable");
            for (var body : container.getAllSubLevels()) identities.add("academy:sable:" + body.getUniqueId());
        }
    }

    private static void tickHovering(MinecraftServer server) {
        var motion = StructureDynamics.motion(player.level(), reference).orElse(null);
        if (motion == null) {
            if (server.getTickCount() - releaseAt > 400) throw new IllegalStateException("lift body unavailable");
            return;
        }
        var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
        double rise = center.y - sourceCenter.y;
        double horizontalDrift = Math.hypot(center.x - sourceCenter.x, center.z - sourceCenter.z);
        maximumHorizontalDrift = Math.max(maximumHorizontalDrift, horizontalDrift);
        maximumAngularSpeed = Math.max(maximumAngularSpeed, motion.angularVelocity().length());
        var rotation = motion.pose().rotation();
        maximumOrientationErrorDegrees = Math.max(maximumOrientationErrorDegrees,
                2.0 * Math.acos(Math.min(1.0, Math.abs(rotation.w()))) * 180.0 / Math.PI);
        var angular = motion.angularVelocity();
        if (previousAngular.length() > 0.02 && angular.length() > 0.02
                && previousAngular.dot(angular) < 0) angularReversals++;
        previousAngular = angular;
        maximumRise = Math.max(maximumRise, rise);
        if ((server.getTickCount() - releaseAt) % 20 == 0) {
            var diagnostic = VectorStructureService.diagnostic(player).orElse(null);
            long cleared = canopy.stream().filter(position -> player.level().getBlockState(position).isAir()).count();
            AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_PROGRESS tier={} ticks={} center={} rise={} "
                            + "linear={} angular={} mass={} source={} session={} cleared={} breakEvents={} dropEvents={} "
                            + "pose={} terrain={} terrain_covered={} xz_drift={} max_xz_drift={} max_angular={}",
                    tier, server.getTickCount() - releaseAt, center, rise, motion.linearVelocity(),
                    motion.angularVelocity(), motion.mass(), sourceCenter, diagnostic, cleared,
                    leafBreakEvents, leafDropEvents, motion.pose(), TERRAIN, terrainCoverage(), horizontalDrift,
                    maximumHorizontalDrift, maximumAngularSpeed);
        }
        if (HARD_ROOF) {
            if (hardRoof.stream().anyMatch(position -> !player.level().getBlockState(position).is(Blocks.BEDROCK))) {
                throw new IllegalStateException("Lift modified the hard roof");
            }
            if (rise > 1.5) throw new IllegalStateException("Lift passed through or escaped the hard roof: " + rise);
            if (server.getTickCount() - releaseAt >= 160) {
                if (maximumRise < 0.5) throw new IllegalStateException("Hard roof fixture never reached its air gap");
                AcademyCraft.LOGGER.info("SUBLEVEL_LIFT_ROOF_NEGATIVE_PASSED rise={} max_rise={} max_xz_drift={} "
                                + "max_angular={} terrain_covered={}", rise, maximumRise, maximumHorizontalDrift,
                        maximumAngularSpeed, terrainCoverage());
                finish();
            }
            return;
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
            result.put("terrain", TERRAIN);
            result.put("discard_between_tiers", DISCARD_BETWEEN_TIERS);
            result.put("max_xz_drift", maximumHorizontalDrift);
            result.put("max_angular_speed", maximumAngularSpeed);
            result.put("max_orientation_error_deg", maximumOrientationErrorDegrees);
            result.put("angular_reversals", angularReversals);
            result.put("hover_ticks", hoverTicks);
            result.put("hover_best", hoverBest);
            result.put("captured_plants", capturedPlants);
            result.put("cleared_leaves", clearedLeaves);
            result.put("leaf_break_events", leafBreakEvents);
            result.put("leaf_drop_events", leafDropEvents);
            result.put("pass", true);
            results.add(result);
            AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_TIER_PASSED tier={} height={} rise={} hover={} "
                            + "max_orientation_error_deg={} angular_reversals={}",
                    tier, HEIGHTS[tier], rise, hoverTicks, maximumOrientationErrorDegrees, angularReversals);
            player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                    player.getYRot() + 180.0F, VEGETATION ? -15.0F : player.getXRot());
            // The ground-selection pitch points down into the solid step (confirmed at 50 m/s
            // in sable vegetation r8). Test release into open space on BOTH backends; do not
            // require a body fired at a hard floor to travel through it. Terrain stays intact.
            player.setYHeadRot(player.getYRot());
            if (!VectorStructureService.launch(player)) throw new IllegalStateException("tier launch refused");
            traceFlight(0);
            launchOrigin = center;
            phase = 4;
            releaseAt = server.getTickCount();
        } else if (server.getTickCount() - releaseAt > (EMBEDDED && rise < HEIGHTS[tier] - TOLERANCE ? 220 : 700)) {
            throw new IllegalStateException("tier " + tier + " did not hold 200 ticks; best=" + hoverBest
                    + ", max_orientation_error_deg=" + maximumOrientationErrorDegrees
                    + ", angular_reversals=" + angularReversals);
        }
    }

    private static void tickLaunching(MinecraftServer server) {
        int flightTicks = server.getTickCount() - releaseAt;
        if (flightTicks == 1 || flightTicks == 5 || flightTicks == 20) traceFlight(flightTicks);
        if (server.getTickCount() - releaseAt < 20) return;
        if (VEGETATION) {
            var motion = StructureDynamics.motion(player.level(), reference).orElseThrow();
            double distance = motion.pose().localToWorldPoint(motion.localCenterOfMass()).distanceTo(launchOrigin);
            if (distance < 3.0) throw new IllegalStateException("vegetation launch remained stuck: " + distance);
            results.getLast().put("launch_distance", distance);
            results.getLast().put("launch_pitch", -15.0F);
            results.getLast().put("flight_pass", true);
            AcademyCraft.LOGGER.info("SUBLEVEL_VEGETATION_LAUNCH_PASSED tier={} distance={}", tier, distance);
        }
        if (DISCARD_BETWEEN_TIERS) {
            // Remove only the reference captured and launched by this fixture. Keep the same
            // 20-tick flight window, terrain edits and next-tier coordinates as the retained
            // path. The ordinary runtime tick, not a forced cache refresh here, evicts terrain.
            var discarded = reference;
            AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_DISCARD_BEGIN tier={} reference={} terrain={} active={}",
                    tier, discarded, terrainCoverage(), activeBodies());
            if (discarded == null || !StructureTransactions.discard(server, discarded)) {
                throw new IllegalStateException("Prior-tier diagnostic discard failed: " + discarded);
            }
            if (StructureDynamics.motion(player.level(), discarded).isPresent()) {
                throw new IllegalStateException("Prior-tier diagnostic discard retained a body: " + discarded);
            }
            AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_DISCARD_COMPLETE tier={} reference={} terrain={} active={} "
                            + "terrain_cleanup=deferred_to_normal_runtime_tick", tier, discarded,
                    terrainCoverage(), activeBodies());
            pendingDiscardObservation = discarded;
        }
        tier++;
        if (tier > 2 || ONLY_TIER >= 0) {
            finish();
            return;
        }
        buildPlatform();
        phase = 1;
    }

    private static void traceFlight(int ticks) {
        if (!VEGETATION) return;
        var motion = StructureDynamics.motion(player.level(), reference).orElseThrow();
        var access = org.academy.internal.common.structure.StructureBackendRouter.orientationAccess(player.level(), reference).orElseThrow();
        var body = access.observe(player.level(), reference).orElseThrow();
        var boxes = body.localBoxes().stream().map(box ->
                org.academy.internal.common.structure.NativeStructureCollisionBoxes.transformBox(box, motion.pose())).toList();
        var bounds = boxes.stream().reduce(net.minecraft.world.phys.AABB::minmax).orElseThrow();
        var contacts = new ArrayList<String>();
        int visited = 0;
        for (var position : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (++visited > 4096 || contacts.size() >= 24) break;
            var state = player.level().getBlockState(position);
            for (var shape : state.getCollisionShape(player.level(), position).toAabbs()) {
                if (boxes.stream().anyMatch(box -> box.intersects(shape.move(position)))) {
                    contacts.add(position + ":" + state);
                    break;
                }
            }
        }
        AcademyCraft.LOGGER.info("SUBLEVEL_VEGETATION_FLIGHT tier={} ticks={} center={} linear={} angular={} look={}"
                        + " bounds={} host_aabb_contacts={} player_bounds={}", tier, ticks,
                motion.pose().localToWorldPoint(motion.localCenterOfMass()), motion.linearVelocity(), motion.angularVelocity(),
                player.getLookAngle(), bounds, contacts, player.getBoundingBox());
    }

    private static String terrainCoverage() {
        return SubLevelServer.runtime(player.level()).map(runtime ->
                runtime.terrainCoverageComplete() + ":" + runtime.terrainDiagnostics()).orElse("foreign_solver");
    }

    private static int activeBodies() {
        return SubLevelServer.runtime(player.level()).map(runtime -> runtime.universe().activeCount()).orElse(-1);
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
        var directory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!directory.getFileName().toString().startsWith("native-sublevel-direct-smoke")) {
            fail(new IllegalStateException("lift tiers smoke requires an isolated run directory"));
            return;
        }
        if (output == null) {
            output = directory.resolve("sublevel-lift-tiers-result.json");
            try {
                // Overwrite any old result before the server starts. Abrupt exit must not
                // leave a previous run's passed=true report as apparent current evidence.
                writeResult(false, "running", null);
            } catch (IOException | RuntimeException error) {
                fail(error);
                return;
            }
        }
        if (!clientReady) org.lwjgl.glfw.GLFW.glfwIconifyWindow(minecraft.getWindow().handle());
        clientReady = true;
    }

    private static void finish() {
        try {
            writeResult(true, "passed", null);
        } catch (IOException | RuntimeException error) {
            fail(error);
            return;
        }
        stopRejectionObserver();
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_SMOKE_PASSED tiers={} results={}", results.size(), results);
        restore();
        // Publish termination only after the independent evidence has reached the file.
        complete = true;
    }

    private static void fail(Throwable failure) {
        if (failed || complete) return;
        try {
            if (output != null) writeResult(false, "failed", failure);
        } catch (IOException | RuntimeException reportError) {
            failure.addSuppressed(reportError);
        }
        stopRejectionObserver();
        AcademyCraft.LOGGER.error("SUBLEVEL_LIFT357_SMOKE_FAILED tier={} phase={} age={} best={} results={}",
                tier, phase, age, hoverBest, results, failure);
        restore();
        failed = true;
    }

    private static void writeResult(boolean passed, String status, Throwable failure) throws IOException {
        if (output == null) throw new IllegalStateException("Lift tiers report path is unavailable");
        var report = new LinkedHashMap<String, Object>();
        report.put("passed", passed);
        report.put("status", status);
        report.put("extraction_gameplay_enabled", StructureCaptureExtractions.enabled());
        report.put("terrain", TERRAIN);
        report.put("material", MATERIAL);
        report.put("embedded", EMBEDDED);
        report.put("vegetation", VEGETATION);
        report.put("disturbance", DISTURBANCE);
        report.put("disturbance_mode", DISTURBANCE_MODE);
        report.put("discard_between_tiers", DISCARD_BETWEEN_TIERS);
        report.put("only_tier", ONLY_TIER);
        report.put("tier", tier);
        report.put("phase", phase);
        report.put("age_ticks", age);
        report.put("hover_best", hoverBest);
        report.put("cases", List.copyOf(results));
        report.put("roof_observed_rejections", rejectionObserver == null ? List.of()
                : List.copyOf(rejectionObserver.reasons));
        report.put("roof_source_snapshot_cells", roofSourceSnapshot.size());
        report.put("roof_structure_count_before", roofStructureSnapshot.size());
        report.put("failure", failure == null ? "" : failure.toString());
        report.put("failure_stack", failure == null ? List.of()
                : Arrays.stream(failure.getStackTrace()).map(StackTraceElement::toString).toList());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static void restore() {
        stopRejectionObserver();
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
