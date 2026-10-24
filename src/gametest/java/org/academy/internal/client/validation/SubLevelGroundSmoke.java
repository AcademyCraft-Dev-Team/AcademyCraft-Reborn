package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.sublevel.SubLevelClientState;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.movement.VectorStructureService;
import org.academy.internal.common.ability.accelerator.skills.lv3.KineticThrow;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.sublevel.*;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Opt-in isolated-world regression for the multi-cell, ground-level native/render path. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelGroundSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelGroundSmoke");
    private static volatile boolean clientReady;
    private static volatile boolean failed;
    private static volatile boolean complete;
    private static volatile boolean arenaActive;
    private static volatile boolean screenshotRequested;
    private static volatile boolean screenshotComplete;
    private static volatile List<Tracked> tracked = List.of();
    private static volatile int clientObserved;
    private static volatile int clientFrames;
    private static volatile PlotPos hoveringPlot;
    private static volatile boolean hoverClientObserved;
    private static boolean closing;
    private static int phase;
    private static int age;
    private static int elapsed;
    private static int terrainEdits;
    private static int serverFrames;
    private static int lastTick = -1;
    private static UUID transaction;
    private static StructureCaptureResult capture;
    private static BlockPos origin;
    private static Vec3 previousPosition;
    private static Vec3 previousVelocity;
    private static float previousYaw;
    private static float previousPitch;
    private static boolean previousNoGravity;
    private static GameType previousMode;
    private static double firstTravel;
    private static double secondTravel;
    private static StructureRef hovering;
    private static Vec3 hoverSourceCenter;
    private static Vec3 hoverLaunchCenter;
    private static Vec3 hoverLaunchDirection;
    private static int hoverLiftedAt = -1;

    private SubLevelGroundSmoke() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(ServerTickEvent.Post event) {
        if (!ENABLED || !clientReady || failed || complete) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty() || lastTick == server.getTickCount()) return;
        lastTick = server.getTickCount();
        var player = server.getPlayerList().getPlayers().getFirst();
        if (SubLevelServer.runtime(player.level()).isEmpty()) return;
        try {
            if (!SubLevelLegacyGroundSmoke.complete()) {
                SubLevelLegacyGroundSmoke.tick(server);
                return;
            }
            age++;
            require(age <= 600, "phase timeout: phase=" + phase + " observed=" + clientObserved);
            if (arenaActive) {
                player.setNoGravity(true);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
                require(player.isAlive(), "player died in isolated ground fixture");
            }
            switch (phase) {
                case 0 -> {
                    prepare(player);
                    requestCapture(player, 0, 11, 5, false);
                    advance();
                }
                case 1 -> {
                    if (!captureReady(server)) return;
                    var first = track(player, 55);
                    tracked = List.of(first);
                    requestCapture(player, 18, 9, 9, true);
                    advance();
                }
                case 2 -> {
                    if (!captureReady(server)) return;
                    tracked = List.of(tracked.getFirst(), track(player, 81));
                    advance();
                }
                case 3 -> {
                    if ((clientObserved & 3) != 3) return;
                    throwWithH(player, tracked.getFirst());
                    advance();
                }
                case 4 -> {
                    if (age < 3) return;
                    throwWithH(player, tracked.getLast());
                    advance();
                }
                case 5 -> {
                    elapsed++;
                    verifyBodies(player);
                    if (elapsed == 30) removeCell(player, new BlockPos(0, 0, 0), 80, 1);
                    if (elapsed >= 60 && (clientObserved & 4) != 0
                            && currentCellCount(player, tracked.getLast()) == 80) {
                        removeCell(player, new BlockPos(8, 0, 8), 79, 2);
                    }
                    if (elapsed % 20 == 0 && elapsed <= 140) changeGround(player);
                    if (elapsed % 20 == 0) {
                        AcademyCraft.LOGGER.info("SUBLEVEL_GROUND_PROGRESS ticks={} server_frames={} client_frames={}"
                                        + " observed={} travel55={} travel81={} ground_edits={}",
                                elapsed, serverFrames, clientFrames, clientObserved, firstTravel, secondTravel, terrainEdits);
                    }
                    if (elapsed < 140 || (clientObserved & 15) != 15 || clientFrames < 100) return;
                    require(firstTravel > 1 && secondTravel > 1,
                            "H did not move both ground structures: " + firstTravel + ", " + secondTravel);
                    require(serverFrames >= 140 && clientFrames >= 100, "insufficient live ground frames");
                    require(terrainEdits >= 7, "host terrain mutation coverage missing");
                    var large = tracked.getLast();
                    var body = SubLevelServer.runtime(player.level()).orElseThrow().get(large.id()).orElseThrow();
                    require(body.geometryRevision() == large.geometryRevision() + 2,
                            "geometry revision did not record exactly two CAS edits");
                    VectorStructureService.clear(player);
                    beginCharge(player);
                    advance();
                }
                case 6 -> {
                    if (age < 4) return;
                    var packet = KineticThrow.ReleasePacket.INSTANCE;
                    packet.setPacketListener(player.connection);
                    KineticThrow.Server.handleRelease(packet);
                    require(VectorStructureService.diagnostic(player).isPresent(), "real charge/release did not capture");
                    advance();
                }
                case 7 -> {
                    var diagnostic = VectorStructureService.diagnostic(player).orElseThrow();
                    if (diagnostic.stage() == VectorStructureService.Stage.CAPTURING) return;
                    hovering = diagnostic.reference();
                    var id = SubLevelStructureRefs.id(hovering).orElseThrow();
                    var runtime = SubLevelServer.runtime(player.level()).orElseThrow();
                    require(runtime.view(id).cells().size() == 9, "real charge must capture all nine source cells");
                    hoveringPlot = runtime.get(id).orElseThrow().plot();
                    require(diagnostic.controlActive(), "newly captured structure has no lift control");
                    AcademyCraft.LOGGER.info("SUBLEVEL_GROUND_CHARGE_CAPTURE ref={} cells=9 source_center={}", hovering, hoverSourceCenter);
                    advance();
                }
                case 8 -> {
                    verifyHover(player);
                    if (age == 40) {
                        place(player, player.position(), player.getEyePosition().add(0, 0, -20));
                    }
                    if (hoverLiftedAt < 0 || age - hoverLiftedAt < 200) return;
                    require(hoverClientObserved, "client did not receive nine-cell hovering structure");
                    var motion = StructureDynamics.motion(player.level(), hovering).orElseThrow();
                    hoverLaunchCenter = SubLevelServer.runtime(player.level()).orElseThrow()
                            .get(SubLevelStructureRefs.id(hovering).orElseThrow()).orElseThrow().worldCenter();
                    double hoverRise = hoverLaunchCenter.y - hoverSourceCenter.y;
                    require(Math.abs(hoverRise - SubLevelKineticLiftProfile.HEIGHTS[0])
                                    <= SubLevelKineticLiftProfile.ACCEPTANCE_TOLERANCE,
                            "hover did not reach original-position 3-block lift height: rise=" + hoverRise);
                    hoverLaunchDirection = player.getLookAngle().normalize();
                    require(hoverLaunchDirection.dot(hoverLaunchCenter.subtract(player.getEyePosition()).normalize()) < -0.5,
                            "player must face away from own hovering structure before H");
                    var packet = KineticThrow.PressPacket.INSTANCE;
                    packet.setPacketListener(player.connection);
                    KineticThrow.Server.handlePress(packet);
                    var diagnostic = VectorStructureService.diagnostic(player).orElseThrow();
                    require(diagnostic.flying() && diagnostic.reference().equals(hovering),
                            "back-facing H did not throw own hovering structure");
                    var launched = StructureDynamics.motion(player.level(), hovering).orElseThrow();
                    require(launched.linearVelocity().dot(hoverLaunchDirection) > 20,
                            "back-facing H did not launch along new player direction");
                    AcademyCraft.LOGGER.info("SUBLEVEL_GROUND_HOVER_PASSED held_ticks={} source={} lifted={}"
                                    + " before_velocity={} launch_direction={} launch_velocity={}",
                            age - hoverLiftedAt, hoverSourceCenter, hoverLaunchCenter, motion.linearVelocity(),
                            hoverLaunchDirection, launched.linearVelocity());
                    advance();
                }
                case 9 -> {
                    if (age < 4) return;
                    var body = SubLevelServer.runtime(player.level()).orElseThrow()
                            .get(SubLevelStructureRefs.id(hovering).orElseThrow()).orElseThrow();
                    require(body.worldCenter().subtract(hoverLaunchCenter).dot(hoverLaunchDirection) > 1,
                            "own held structure did not continue flying after back-facing H");
                    VectorStructureService.clear(player);
                    place(player, new Vec3(origin.getX() + 15.5, origin.getY() + 8, origin.getZ() - 10),
                            new Vec3(origin.getX() + 15.5, origin.getY() + 1, origin.getZ() + 15));
                    advance();
                }
                case 10 -> {
                    if (age < 10) return;
                    screenshotRequested = true;
                    advance();
                }
                case 11 -> {
                    if (!screenshotComplete) return;
                    arenaActive = false;
                    player.resetFallDistance();
                    player.connection.teleport(previousPosition.x, previousPosition.y, previousPosition.z,
                            previousYaw, previousPitch);
                    player.setDeltaMovement(previousVelocity);
                    player.setNoGravity(previousNoGravity);
                    player.setGameMode(previousMode);
                    player.resetFallDistance();
                    AcademyCraft.LOGGER.info("SUBLEVEL_GROUND_SMOKE_PASSED cells55=true cells81_80_79=true"
                                    + " h_press=true geometry_rebuilds=2 ground_edits={} server_frames={} client_frames={}"
                                    + " travel55={} travel81={} retained=true original_position_hover=200 back_facing_h=true",
                            terrainEdits, serverFrames, clientFrames, firstTravel, secondTravel);
                    complete = true;
                }
                default -> throw new IllegalStateException("Unexpected phase " + phase);
            }
        } catch (Throwable failure) {
            fail(failure);
        }
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
        try {
            // The fixture deliberately edits a sizeable patch of terrain. Never permit the normal run directory.
            require(!minecraft.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString()
                    .equalsIgnoreCase("run"), "ground smoke requires an isolated copy, not the normal run directory");
            clientReady = true;
            if (arenaActive) {
                minecraft.player.setNoGravity(true);
                minecraft.player.setDeltaMovement(Vec3.ZERO);
                minecraft.player.resetFallDistance();
            }
            var expected = tracked;
            var snapshots = SubLevelClientState.snapshots(minecraft.level);
            boolean allPresent = expected.size() == 2;
            for (var fixture : expected) {
                var snapshot = snapshots.stream().filter(value -> value.plot().equals(fixture.plot())).findFirst();
                if (snapshot.isEmpty()) { allPresent = false; continue; }
                var state = snapshot.orElseThrow();
                finitePose(state.pose());
                int count = state.view().cells().size();
                if (fixture.cells() == 55) {
                    require(count == 55 && state.boxes().size() == fixture.boxes(), "client 55-cell geometry mismatch");
                    clientObserved |= 1;
                } else {
                    require(count >= 79 && count <= 81, "client unexpected large geometry: " + count);
                    require(state.boxes().size() == fixture.boxes() - (81 - count),
                            "client collision boxes did not follow CAS geometry");
                    clientObserved |= count == 81 ? 2 : count == 80 ? 4 : 8;
                }
            }
            if (allPresent) clientFrames++;
            var currentHoverPlot = hoveringPlot;
            if (currentHoverPlot != null) {
                snapshots.stream().filter(snapshot -> snapshot.plot().equals(currentHoverPlot)).findFirst().ifPresent(snapshot -> {
                    require(snapshot.view().cells().size() == 9, "client hover geometry differs from captured nine cells");
                    finitePose(snapshot.pose());
                    hoverClientObserved = true;
                });
            }
            if (screenshotRequested && !screenshotComplete) {
                screenshotRequested = false;
                var name = "sublevel-ground-smoke-" + ProcessHandle.current().pid() + "-" + System.currentTimeMillis() + ".png";
                var path = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(name).toAbsolutePath();
                Screenshot.grab(minecraft.gameDirectory, name, minecraft.gameRenderer.mainRenderTarget(), 1, message -> {
                    try {
                        require(Files.isRegularFile(path) && Files.size(path) > 0, "ground screenshot was not saved");
                        AcademyCraft.LOGGER.info("SUBLEVEL_GROUND_SCREENSHOT path={}", path);
                        screenshotComplete = true;
                    } catch (Throwable failure) {
                        fail(failure);
                    }
                });
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void prepare(ServerPlayer player) {
        require(StructureBackendRouter.nativeEnabled(player.level().getServer()), "ground fixture requires no Sable");
        NativeStructureTerrainAdmissionSmoke.run(player.level(), new BlockPos(player.blockPosition().getX(),
                player.level().getMaxY() - 12, player.blockPosition().getZ()));
        previousPosition = player.position();
        previousVelocity = player.getDeltaMovement();
        previousYaw = player.getYRot();
        previousPitch = player.getXRot();
        previousNoGravity = player.isNoGravity();
        previousMode = player.gameMode.getGameModeForPlayer();
        var nearby = player.blockPosition().offset(64, 0, 0);
        player.level().getChunkAt(nearby);
        var groundY = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING, nearby.getX(), nearby.getZ()) - 1;
        origin = new BlockPos(nearby.getX(), groundY, nearby.getZ());
        require(origin.getY() > player.level().getMinY() + 2 && origin.getY() + 14 < player.level().getMaxY(),
                "ground fixture outside build height");
        var level = player.level();
        for (int x = -4; x <= 31; x++) for (int z = -12; z <= 28; z++) {
            var floor = origin.offset(x, 0, z);
            level.getChunkAt(floor);
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            for (int y = 1; y <= 12; y++) level.setBlock(floor.above(y), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int x = -4; x <= 31; x++) for (int y = 1; y <= 10; y++) {
            level.setBlock(origin.offset(x, y, 24), Blocks.STONE.defaultBlockState(), 3);
        }
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
        arenaActive = true;
        place(player, new Vec3(origin.getX() + 5.5, origin.getY() + 1, origin.getZ() - 8),
                new Vec3(origin.getX() + 5.5, origin.getY() + 1.5, origin.getZ() + 2.5));
        AcademyCraft.LOGGER.info("SUBLEVEL_GROUND_ARENA origin={} source_player={} floor=true wall=true", origin, previousPosition);
    }

    private static void requestCapture(ServerPlayer player, int offsetX, int width, int depth, boolean stair) {
        var cells = new ArrayList<BlockPos>();
        for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) {
            var cell = origin.offset(offsetX + x, 1, z);
            var state = stair && x == 4 && z == 4 ? Blocks.OAK_STAIRS.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState();
            player.level().setBlock(cell, state, 3);
            cells.add(cell);
        }
        capture = StructureTransactions.capture(player.level(), new StructureCaptureRequest(cells, 256, 24,
                (host, cell) -> host.mayInteract(player, cell), true));
        transaction = capture.transactionId();
    }

    private static boolean captureReady(MinecraftServer server) {
        if (capture.pending()) capture = StructureTransactions.poll(server, transaction).orElseThrow();
        if (capture.pending()) return false;
        require(capture.succeeded(), "shared capture rejected: " + capture);
        return true;
    }

    private static Tracked track(ServerPlayer player, int count) {
        var reference = capture.structure().orElseThrow();
        var id = SubLevelStructureRefs.id(reference).orElseThrow();
        var runtime = SubLevelServer.runtime(player.level()).orElseThrow();
        var body = runtime.get(id).orElseThrow();
        var handle = StructureSpaces.resolve(player.level().getServer(), reference).orElseThrow();
        require(runtime.view(id).cells().size() == count, "captured cell count mismatch");
        int boxes = runtime.localCollisionBoxes(id).size();
        require(boxes == count + (count == 81 ? 1 : 0), "fixture must cover multi-box stair geometry: boxes=" + boxes);
        AcademyCraft.LOGGER.info("SUBLEVEL_GROUND_CAPTURE cells={} boxes={} ref={} plot={} center={}",
                count, boxes, reference, body.plot(), body.worldCenter());
        return new Tracked(reference, id, body.plot(), handle.runtimeId(), count, boxes,
                body.geometryRevision(), body.worldCenter());
    }

    private static void throwWithH(ServerPlayer player, Tracked target) {
        var body = SubLevelServer.runtime(player.level()).orElseThrow().get(target.id()).orElseThrow();
        var center = body.worldCenter();
        place(player, new Vec3(center.x, origin.getY() + 1, origin.getZ() - 8), center);
        var eye = player.getEyePosition();
        var range = Skills.KINETIC_THROW.get().scaledRange(player, KineticThrow.TARGET_RANGE);
        var ray = StructureQueries.raycast(player.level(), eye, eye.add(player.getLookAngle().scale(range)));
        require(ray.filter(hit -> hit.block().structure().equals(target.reference())).isPresent(),
                "H view missed ground structure: " + ray);
        var packet = KineticThrow.PressPacket.INSTANCE;
        packet.setPacketListener(player.connection);
        KineticThrow.Server.handlePress(packet);
        var diagnostic = VectorStructureService.diagnostic(player).orElseThrow();
        require(diagnostic.flying() && diagnostic.reference().equals(target.reference()), "H did not launch target: " + diagnostic);
        var motion = StructureDynamics.motion(player.level(), target.reference()).orElseThrow();
        require(motion.linearVelocity().length() > 20, "H launch speed lost legacy response");
        AcademyCraft.LOGGER.info("SUBLEVEL_GROUND_H cells={} velocity={} center={}", target.cells(), motion.linearVelocity(), center);
    }

    private static void verifyBodies(ServerPlayer player) {
        var runtime = SubLevelServer.runtime(player.level()).orElseThrow();
        for (var target : tracked) {
            var body = runtime.get(target.id()).orElseThrow();
            var handle = StructureSpaces.resolve(player.level().getServer(), target.reference()).orElseThrow();
            require(handle.runtimeId().equals(target.runtimeId()), "CAS replaced activation identity");
            var motion = StructureDynamics.motion(player.level(), target.reference()).orElseThrow();
            finitePose(motion.pose());
            finiteVector(motion.linearVelocity());
            finiteVector(motion.angularVelocity());
            require(body.worldCenter().distanceTo(target.center()) < 180, "ground body escaped tracked arena");
            double distance = body.worldCenter().distanceTo(target.center());
            if (target.cells() == 55) firstTravel = Math.max(firstTravel, distance);
            else secondTravel = Math.max(secondTravel, distance);
        }
        serverFrames++;
    }

    private static void removeCell(ServerPlayer player, BlockPos local, int expectedCells, int edit) {
        var target = tracked.getLast();
        var handle = StructureSpaces.resolve(player.level().getServer(), target.reference()).orElseThrow();
        var before = StructureDynamics.motion(player.level(), target.reference()).orElseThrow();
        var old = handle.world().read(local);
        require(!old.state().isAir(), "CAS source was already air: " + local);
        require(handle.world().compareAndSet(local, old, new StructureBlockData(Blocks.AIR.defaultBlockState()))
                == StructureWriteResult.APPLIED, "ground CAS did not apply");
        var runtime = SubLevelServer.runtime(player.level()).orElseThrow();
        require(runtime.view(target.id()).cells().size() == expectedCells, "server geometry cell count mismatch");
        require(runtime.localCollisionBoxes(target.id()).size() == target.boxes() - edit, "server collision boxes stale");
        var after = StructureDynamics.motion(player.level(), target.reference()).orElseThrow();
        require(before.linearVelocity().distanceTo(after.linearVelocity()) < 1.0e-5
                        && before.angularVelocity().distanceTo(after.angularVelocity()) < 1.0e-5,
                "geometry rebuild lost motion");
        require(runtime.get(target.id()).orElseThrow().geometryRevision() == target.geometryRevision() + edit,
                "geometry revision did not advance");
        AcademyCraft.LOGGER.info("SUBLEVEL_GROUND_CAS edit={} local={} cells={} boxes={} velocity={}",
                edit, local, expectedCells, runtime.localCollisionBoxes(target.id()).size(), after.linearVelocity());
    }

    private static int currentCellCount(ServerPlayer player, Tracked target) {
        return SubLevelServer.runtime(player.level()).orElseThrow().view(target.id()).cells().size();
    }

    private static void changeGround(ServerPlayer player) {
        var runtime = SubLevelServer.runtime(player.level()).orElseThrow();
        var body = runtime.get(tracked.getLast().id()).orElseThrow();
        var center = body.worldCenter();
        var cell = new BlockPos((int) Math.floor(center.x), origin.getY(), (int) Math.floor(center.z));
        var old = player.level().getBlockState(cell);
        player.level().setBlock(cell, old.isAir() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        runtime.probeTerrain(body.worldBounds().inflate(2));
        terrainEdits++;
        AcademyCraft.LOGGER.info("SUBLEVEL_GROUND_TERRAIN edit={} position={} now_air={}",
                terrainEdits, cell, player.level().getBlockState(cell).isAir());
    }

    private static void beginCharge(ServerPlayer player) {
        // A low, isolated nine-cell platform keeps the real selection away from the large arena floor.
        // It still uses the ordinary H target/charge/release path, rather than invoking capture directly.
        for (int x = 13; x <= 15; x++) for (int z = -5; z <= -3; z++) {
            player.level().setBlock(origin.offset(x, 3, z), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        }
        hoverSourceCenter = new Vec3(origin.getX() + 14.5, origin.getY() + 3.5, origin.getZ() - 3.5);
        var seed = origin.offset(14, 3, -5);
        place(player, new Vec3(origin.getX() + 14.5, origin.getY() + 1, origin.getZ() - 10), Vec3.atCenterOf(seed));
        var eye = player.getEyePosition();
        require(StructureQueries.raycast(player.level(), eye, eye.add(player.getLookAngle().scale(24))).isEmpty(),
                "charge view unexpectedly intersects an existing structure");
        var packet = KineticThrow.PressPacket.INSTANCE;
        packet.setPacketListener(player.connection);
        KineticThrow.Server.handlePress(packet);
    }

    private static void verifyHover(ServerPlayer player) {
        var diagnostic = VectorStructureService.diagnostic(player).orElseThrow();
        require(diagnostic.reference().equals(hovering) && diagnostic.controlActive() && !diagnostic.flying(),
                "original-position hover lost control before its complete hover window: " + diagnostic);
        if (diagnostic.lifted() && hoverLiftedAt < 0) {
            hoverLiftedAt = age;
            AcademyCraft.LOGGER.info("SUBLEVEL_GROUND_HOVER_REACHED tick={} window_ticks=200", age);
        }
        var runtime = SubLevelServer.runtime(player.level()).orElseThrow();
        var body = runtime.get(SubLevelStructureRefs.id(hovering).orElseThrow()).orElseThrow();
        var center = body.worldCenter();
        finitePose(body.pose());
        require(Math.hypot(center.x - hoverSourceCenter.x, center.z - hoverSourceCenter.z) < 0.4,
                "newly captured structure was pulled sideways from source: source=" + hoverSourceCenter + " now=" + center);
        if (age >= 30) require(center.y > hoverSourceCenter.y + 1, "newly captured structure did not rise in place");
        if (age % 30 == 0) AcademyCraft.LOGGER.info("SUBLEVEL_GROUND_HOVER tick={} center={} diagnostic={}", age, center, diagnostic);
    }

    private static void place(ServerPlayer player, Vec3 position, Vec3 target) {
        var eye = position.add(0, player.getEyeHeight(), 0);
        var delta = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        player.resetFallDistance();
        player.connection.teleport(position.x, position.y, position.z, yaw, pitch);
        player.setYHeadRot(yaw);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    private static void finitePose(StructurePose pose) {
        finiteVector(pose.worldOrigin());
        require(Double.isFinite(pose.rotationX()) && Double.isFinite(pose.rotationY())
                        && Double.isFinite(pose.rotationZ()) && Double.isFinite(pose.rotationW()),
                "nonfinite orientation");
    }

    private static void finiteVector(Vec3 value) {
        require(Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z), "nonfinite vector: " + value);
    }

    private static void advance() { phase++; age = 0; }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("SubLevel ground smoke: " + message);
    }

    private static void fail(Throwable failure) {
        failed = true;
        AcademyCraft.LOGGER.error("SUBLEVEL_GROUND_SMOKE_FAILED phase={} ticks={} observed={}", phase, elapsed, clientObserved, failure);
    }

    private record Tracked(StructureRef reference, SubLevelId id, PlotPos plot, UUID runtimeId,
                           int cells, int boxes, long geometryRevision, Vec3 center) {}
}
