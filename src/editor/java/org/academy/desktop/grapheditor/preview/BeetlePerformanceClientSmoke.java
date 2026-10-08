package org.academy.desktop.grapheditor.preview;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.academy.api.common.ability.darkmatter.DarkmatterCreatureRegistries;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.creature.DarkmatterCreatureBlueprint;
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterCreation;
import org.academy.internal.common.skilldata.DarkmatterCreationData;
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/** Independent multiplayer creation fixture; only included in editor runs, never production jars. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class BeetlePerformanceClientSmoke {
    private static final String PREFIX = "[beetle-performance] ";
    private static final int FRAMES = 120;
    private static final Vec3 CENTER = new Vec3(80, 81, 188);
    private record Stage(int creatures, int distance) { }
    private record FrameSample(int stage, int slot, long cpuRenderNanos, long frameIntervalNanos) { }
    private record TickSample(int stage, long nanos) { }
    private record ViewerState(boolean noGravity, boolean invulnerable, boolean flying, GameType gameMode) { }
    private static final List<Stage> STAGES = new ArrayList<>();
    private static final List<ServerPlayer> actors = new ArrayList<>();
    private static final List<FrameSample> frames = new ArrayList<>();
    private static final List<Object> stageFacts = new ArrayList<>();
    private static List<String> runtimeContracts = List.of();
    private static final ConcurrentLinkedQueue<TickSample> serverTimes = new ConcurrentLinkedQueue<>();
    private static final boolean COMBAT = Boolean.getBoolean("academy.beetlePerformanceCombat");
    private static final boolean CLEAR_CAMERA = Boolean.getBoolean("academy.beetlePerformanceClearCamera");
    private static boolean opened, configured, finished, awaiting, measuring, completing;
    private static volatile boolean ready, cleanupDone;
    private static volatile Throwable failure;
    private static volatile int serverStage = -1, samplingStage = -1;
    private static int cursor = -1, clientTicks, warmup, settled, measurementTicks, capturedFrames;
    private static int recording = -1, recordingStage, originalFov, originalLimit;
    private static boolean originalVsync;
    private static long frameStart, lastFrameStart, interval, serverStart;
    private static int serverStartedStage;
    private static GpuQueryPool queries;
    private static Cow target;
    private static ViewerState originalViewerState, restoredViewerState;

    static {
        for (var count : new int[]{0, 16, 64, 128}) for (var distance : new int[]{16, 24, 32}) {
            STAGES.add(new Stage(count, distance));
        }
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.beetlePerformanceSmoke") || finished) return;
        var mc = Minecraft.getInstance();
        if (failure != null) throw new IllegalStateException("Beetle performance server fixture failed", failure);
        if (++clientTicks > 6000) throw new IllegalStateException("Beetle performance capture timed out");
        if (mc.level == null) {
            if (mc.gui.screen() instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen warning) {
                for (var widget : warning.children()) if (widget instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().equals("Proceed to main menu")) {
                    button.onPress(new net.minecraft.client.input.MouseButtonEvent(0, 0,
                            new net.minecraft.client.input.MouseButtonInfo(0, 0))); break;
                }
            }
            if (!opened && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
                opened = true; mc.createWorldOpenFlows().openWorld("railgun", mc::stop);
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (completing) {
            if (!cleanupDone || !trackedBeetles().isEmpty()) return;
            if (mc.player.isNoGravity() != originalViewerState.noGravity
                    || mc.player.getAbilities().flying != originalViewerState.flying) return;
            require(originalViewerState.equals(restoredViewerState), "Real viewer physical state restored before world save");
            writeReport();
            mc.options.fov().set(originalFov); mc.options.framerateLimit().set(originalLimit);
            mc.options.enableVsync().set(originalVsync);
            finished = true; System.out.println(PREFIX + "PASSED " + label()); mc.stop();
            return;
        }
        mc.options.pauseOnLostFocus = false;
        mc.player.setYRot(0); mc.player.yRotO = 0; mc.player.setXRot(7); mc.player.xRotO = 7;
        if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        if (!configured) {
            configured = true;
            originalFov = mc.options.fov().get(); originalLimit = mc.options.framerateLimit().get();
            originalVsync = mc.options.enableVsync().get();
            mc.options.fov().set(70); mc.options.framerateLimit().set(260); mc.options.enableVsync().set(false);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            server(BeetlePerformanceClientSmoke::configure);
        }
        if (!ready || ++warmup < 200) return;
        if (cursor < 0) { startStage(0); return; }
        if (awaiting) {
            if (serverStage != cursor) return;
            awaiting = false;
        }
        if (!measuring) {
            if (++settled < 60) return;
            var tracked = trackedBeetles();
            require(tracked.size() == STAGES.get(cursor).creatures, "Tracked real creations=" + tracked.size() + " stage=" + cursor);
            if (CLEAR_CAMERA && STAGES.get(cursor).distance == 32) require(cameraUnobstructed(), "Real client eye corners have no view-blocking block before measurement");
            measuring = true; measurementTicks = 0; capturedFrames = 0; lastFrameStart = 0;
            samplingStage = cursor;
        }
        if (++measurementTicks < 40 || capturedFrames < FRAMES) return;
        samplingStage = -1; measuring = false;
        var creatures = trackedBeetles();
        var facts = new LinkedHashMap<String, Object>();
        facts.put("stage", cursor); facts.put("requestedCreations", STAGES.get(cursor).creatures);
        facts.put("trackedCreationsAtEnd", creatures.size()); facts.put("nominalCameraDistance", STAGES.get(cursor).distance);
        facts.put("torsoCounts", List.of(creatures.stream().filter(e -> e.torsoModel() == 0).count(),
                creatures.stream().filter(e -> e.torsoModel() == 1).count(), creatures.stream().filter(e -> e.torsoModel() == 2).count()));
        facts.put("actualDistances", summary(creatures.stream().map(e -> Math.sqrt(e.position().distanceToSqr(mc.player.getEyePosition()))).toList()));
        facts.put("cameraViewBlocking", !cameraUnobstructed());
        if (CLEAR_CAMERA && STAGES.get(cursor).distance == 32) require(cameraUnobstructed(), "Real client eye corners remain clear after measurement");
        stageFacts.add(facts);
        capture("stage_" + cursor + "_n" + STAGES.get(cursor).creatures + "_d" + STAGES.get(cursor).distance);
        if (cursor + 1 < STAGES.size()) startStage(cursor + 1);
        else {
            completing = true;
            server(p -> {
                discardAll();
                for (var actor : List.copyOf(actors)) p.level().getServer().getPlayerList().remove(actor);
                actors.clear(); target.discard();
                restoreViewer(p);
                cleanupDone = true;
            });
        }
    }

    private static void startStage(int index) {
        cursor = index; awaiting = true; settled = 0;
        var stage = STAGES.get(index);
        server(p -> {
            if (index % 3 == 0) populate(stage.creatures);
            if (CLEAR_CAMERA && stage.distance == 32) {
                // Original eye corners at (79/80,83,155/156) were inside packed ice outside the scene platform.
                // Clear only this photography point; simulation terrain and all entity positions remain unchanged.
                var cameraX = (int) CENTER.x; var cameraZ = (int) CENTER.z - stage.distance;
                for (var x = cameraX - 2; x <= cameraX + 2; x++) for (var z = cameraZ - 2; z <= cameraZ + 2; z++) {
                    for (var y = 81; y <= 85; y++) p.level().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
                for (var x = cameraX - 1; x <= cameraX + 1; x++) for (var z = cameraZ - 1; z <= cameraZ + 1; z++) {
                    p.level().setBlock(new BlockPos(x, 80, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                }
            }
            p.teleportTo(p.level(), CENTER.x, 82, CENTER.z - stage.distance, Set.of(), 0, 7, false);
            serverStage = index;
            System.out.println(PREFIX + "stage=" + index + " creations=" + stage.creatures + " nominal distance=" + stage.distance);
        });
    }

    private static void configure(ServerPlayer viewer) {
        originalViewerState = viewerState(viewer);
        var level = viewer.level();
        for (var x = 64; x <= 96; x++) for (var z = 172; z <= 202; z++) {
            level.setBlock(new BlockPos(x, 80, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            for (var y = 81; y <= 94; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        for (var x = 70; x <= 90; x++) for (var z = 190; z <= 198; z++) for (var y = 81; y <= 85; y++) {
            var wall = x == 70 || x == 90 || z == 190 || z == 198 || y == 85;
            level.setBlock(new BlockPos(x, y, z), (wall ? Blocks.GLASS : Blocks.WATER).defaultBlockState(), 3);
        }
        viewer.teleportTo(level, CENTER.x, 82, 172, Set.of(), 0, 7, false);
        viewer.setGameMode(GameType.CREATIVE); viewer.setNoGravity(true);
        viewer.getAbilities().flying = true; viewer.onUpdateAbilities(); viewer.getInventory().clearContent();
        for (var old : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, viewer.getBoundingBox().inflate(100))) old.discard();
        for (var old : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, viewer.getBoundingBox().inflate(100))) old.discard();
        var server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "weather clear");
        target = new Cow(net.minecraft.world.entity.EntityTypes.COW, level);
        target.setPos(80, 81, 187); target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000_000); target.setHealth(target.getMaxHealth());
        level.addFreshEntity(target);
        for (var i = 0; i < 8; i++) {
            var profile = new GameProfile(UUID.nameUUIDFromBytes(("academy-beetle-perf-" + i).getBytes(StandardCharsets.UTF_8)), "beetle-probe-" + i);
            var cookie = CommonListenerCookie.createInitial(profile, false);
            var actor = new ServerPlayer(server, level, profile, cookie.clientInformation()) { };
            var connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection); NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, actor, cookie); actor.connection.markClientLoaded();
            actor.teleportTo(level, 80 + (i % 4 - 1.5) * .5, 81, 185 + i / 4, Set.of(), 0, 0, false);
            actor.setGameMode(GameType.CREATIVE); actor.setNoGravity(true); actor.setInvulnerable(true);
            learn(actor); actors.add(actor);
        }
        runtimeContracts = BeetleRuntimeContractChecks.verify(actors);
        ready = true;
    }

    private static ViewerState viewerState(ServerPlayer viewer) {
        return new ViewerState(viewer.isNoGravity(), viewer.isInvulnerable(), viewer.getAbilities().flying,
                viewer.gameMode.getGameModeForPlayer());
    }

    private static void restoreViewer(ServerPlayer viewer) {
        if (originalViewerState == null) return;
        viewer.setGameMode(originalViewerState.gameMode);
        viewer.setNoGravity(originalViewerState.noGravity);
        viewer.setInvulnerable(originalViewerState.invulnerable);
        viewer.getAbilities().flying = originalViewerState.flying;
        viewer.onUpdateAbilities();
        viewer.setDeltaMovement(Vec3.ZERO);
        restoredViewerState = viewerState(viewer);
    }

    private static void learn(ServerPlayer actor) {
        var system = AbilitySystemServer.getSystem(actor); var skill = Skills.DARKMATTER_CREATION.get(); var id = actor.getUUID();
        system.setPlayerAbilityCategory(id, skill.getCategory()); system.setPlayerLevel(id, 5);
        // The shared matter ledger is available only after its category's common generation skill is learned.
        system.addPlayerSkill(actor, Skills.DARKMATTER_GENERATION.get().getKeyString());
        system.addPlayerSkill(actor, skill.getKeyString());
        if (!skill.isEnabled(actor)) skill.toggle(actor);
        system.setPlayerSkillProficiency(id, skill, 0);
        var data = system.getPlayerData(id);
        data.setAcademyMaxCp(10000); data.getCpData().setMaxCP(10000); data.getCpData().setAvailableCP(10000);
    }

    private static void populate(int count) {
        discardAll();
        for (var i = 0; i < count; i++) {
            var actor = actors.get(i / 16); var torso = i % 3; var rank = i / 3;
            var heads = List.of(DarkmatterCreatureRegistries.HEAD_JAW, DarkmatterCreatureRegistries.HEAD_CANNON, DarkmatterCreatureRegistries.HEAD_HOMING);
            var torsos = List.of(DarkmatterCreatureRegistries.TORSO_WALK, DarkmatterCreatureRegistries.TORSO_FLY, DarkmatterCreatureRegistries.TORSO_SWIM);
            var limbs = List.of(DarkmatterCreatureRegistries.LIMBS_GUARD, DarkmatterCreatureRegistries.LIMBS_MINER, DarkmatterCreatureRegistries.LIMBS_CARRIER);
            var extra = torso == 0 ? DarkmatterCreatureRegistries.ADDITIONAL_CARAPACE
                    : torso == 1 ? DarkmatterCreatureRegistries.ADDITIONAL_SENSOR : DarkmatterCreatureRegistries.ADDITIONAL_WEAPON;
            var blueprint = new DarkmatterCreatureBlueprint("Performance " + torso, COMBAT ? 10 : 5, heads.get(torso).toString(), torsos.get(torso).toString(),
                    limbs.get(torso).toString(), extra.toString(), 125, 125, 125, 125,
                    COMBAT ? List.of(DarkmatterCreatureRegistries.MODULE_FOCUS.toString()) : List.of());
            require(blueprint.validate(5).isEmpty(), "Blueprint valid at level 5");
            AbilitySystemServer.getSystem(actor).updatePlayerSkillData(actor.getUUID(), Skills.DARKMATTER_CREATION.get(), DarkmatterCreationData.class, data -> {
                data.setBlueprint(torso, blueprint, 5); data.setSelectedSlot(torso);
            });
            var resource = AbilitySystemServer.getSystem(actor).getDarkmatterResourceManager();
            resource.reconcile(actor); require(resource.debugSetPools(actor, 100, 0, 0, 0), "Real resource pool initialized");
            resource.setAlphaPoints(actor, 180);
            if (COMBAT) actor.setLastHurtMob(target);
            var at = new Vec3(80 + (rank % 8 - 3.5) * 2, torso == 0 ? 81 : 82,
                    (torso == 0 ? 176 : torso == 1 ? 183 : 192) + rank / 8);
            var id = DarkmatterCreation.Server.tryProgramCreate(actor, at, 16, 0).orElseThrow(() -> new IllegalStateException("Real performance summon rejected at " + at));
            actor.level().getEntity(id).setCustomName(null);
        }
        var actual = actors.stream().mapToInt(p -> DarkmatterCreation.Server.owned(p).size()).sum();
        require(actual == count, "Authoritative actual creations=" + actual);
    }

    private static void discardAll() {
        for (var actor : actors) for (var id : DarkmatterCreation.Server.owned(actor)) DarkmatterCreation.Server.discardProgramCreated(actor, id);
    }

    @SubscribeEvent
    public static void beforeServerTick(ServerTickEvent.Pre event) {
        if (!Boolean.getBoolean("academy.beetlePerformanceSmoke")) return;
        serverStartedStage = samplingStage; serverStart = System.nanoTime();
    }

    @SubscribeEvent
    public static void afterServerTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.beetlePerformanceSmoke")) return;
        for (var actor : actors) actor.doTick();
        if (serverStartedStage >= 0 && serverStartedStage == samplingStage) serverTimes.add(new TickSample(serverStartedStage, System.nanoTime() - serverStart));
    }

    @SubscribeEvent
    public static void beforeFrame(RenderFrameEvent.Pre event) {
        if (!Boolean.getBoolean("academy.beetlePerformanceSmoke") || !measuring || capturedFrames >= FRAMES) return;
        var device = RenderSystem.getDevice();
        if (queries == null) queries = device.createTimestampQueryPool(STAGES.size() * FRAMES * 2);
        frameStart = System.nanoTime(); interval = lastFrameStart == 0 ? 0 : frameStart - lastFrameStart; lastFrameStart = frameStart;
        recording = frames.size() * 2; recordingStage = cursor;
        device.createCommandEncoder().writeTimestamp(queries, recording);
    }

    @SubscribeEvent
    public static void afterFrame(RenderFrameEvent.Post event) {
        if (recording < 0) return;
        RenderSystem.getDevice().createCommandEncoder().writeTimestamp(queries, recording + 1);
        frames.add(new FrameSample(recordingStage, recording, System.nanoTime() - frameStart, interval));
        capturedFrames++; recording = -1;
    }

    private static List<DarkmatterBeetle> trackedBeetles() {
        var result = new ArrayList<DarkmatterBeetle>();
        for (var entity : Minecraft.getInstance().level.entitiesForRendering()) if (entity instanceof DarkmatterBeetle beetle
                && beetle.position().distanceToSqr(CENTER) < 80 * 80) result.add(beetle);
        return result;
    }

    /** Mirrors vanilla ScreenEffectRenderer's eight eye-corner block-screen-effect test. */
    private static boolean cameraUnobstructed() {
        var player = Minecraft.getInstance().player;
        for (var i = 0; i < 8; i++) {
            var pos = BlockPos.containing(player.getX() + ((i >> 0) % 2 - .5f) * player.getBbWidth() * .8f,
                    player.getEyeY() + ((i >> 1) % 2 - .5f) * .1f * player.getScale(),
                    player.getZ() + ((i >> 2) % 2 - .5f) * player.getBbWidth() * .8f);
            var block = player.level().getBlockState(pos);
            if (block.getRenderShape() != net.minecraft.world.level.block.RenderShape.INVISIBLE && block.isViewBlocking(player.level(), pos)) return false;
        }
        return true;
    }

    private static void writeReport() {
        var device = RenderSystem.getDevice(); var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var rows = new ArrayList<Object>();
        for (var i = 0; i < STAGES.size(); i++) {
            var gpu = new ArrayList<Double>(); var cpu = new ArrayList<Double>(); var wall = new ArrayList<Double>(); var ticks = new ArrayList<Double>();
            for (var frame : frames) if (frame.stage == i) {
                var start = queries.getValue(frame.slot); var end = queries.getValue(frame.slot + 1);
                if (start.isPresent() && end.isPresent()) gpu.add((end.getAsLong() - start.getAsLong()) * device.getDeviceInfo().timestampPeriod() / 1_000_000.0);
                cpu.add(frame.cpuRenderNanos / 1_000_000.0); if (frame.frameIntervalNanos > 0) wall.add(frame.frameIntervalNanos / 1_000_000.0);
            }
            for (var tick : serverTimes) if (tick.stage == i) ticks.add(tick.nanos / 1_000_000.0);
            require(gpu.size() == FRAMES && ticks.size() >= 20, "Actual GPU and server samples stage=" + i + " gpu=" + gpu.size() + " ticks=" + ticks.size());
            var row = new LinkedHashMap<String, Object>();
            row.put("scene", stageFacts.get(i)); row.put("gpuFrameMilliseconds", summary(gpu));
            row.put("cpuRenderEventMilliseconds", summary(cpu)); row.put("frameIntervalMilliseconds", summary(wall));
            row.put("serverTickMilliseconds", summary(ticks)); rows.add(row);
        }
        var report = new LinkedHashMap<String, Object>(); report.put("label", label()); report.put("status", "PASSED");
        report.put("resolution", List.of(target.width, target.height)); report.put("syntheticConnectedServerPlayers", 8);
        report.put("combat", COMBAT); report.put("framesPerStage", FRAMES); report.put("stages", rows);
        report.put("clearCameraFixture", CLEAR_CAMERA);
        report.put("viewerStateBeforeFixture", originalViewerState);
        report.put("viewerStateAfterCleanup", restoredViewerState);
        report.put("viewerPhysicalStateRestored", originalViewerState.equals(restoredViewerState));
        report.put("runtimeContracts", runtimeContracts);
        report.put("limitations", List.of("Mock-connected players exercise real server ownership, skills and entity tracking; they are not eight remote game clients.",
                "Each camera distance is nominal to scene center; actual moving entity distances are reported.",
                "Whole frame and server tick timings include the common scene and vanilla costs. They are not isolated beetle draw times.",
                "AI runs normally, including the documented existing swimming FloatGoal behavior inside real water."));
        try {
            Files.createDirectories(output()); Files.writeString(output().resolve(label() + "_client.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        } catch (Exception e) { throw new IllegalStateException(e); }
        queries.close(); queries = null;
    }

    private static Object summary(List<Double> input) {
        var sorted = new ArrayList<>(input); Collections.sort(sorted); var result = new LinkedHashMap<String, Object>();
        result.put("samples", sorted.size());
        if (!sorted.isEmpty()) { result.put("median", sorted.get(sorted.size() / 2)); result.put("p95", sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * .95)))); }
        return result;
    }

    private static String label() {
        var label = System.getProperty("academy.beetlePerformanceLabel", "baseline");
        if (!label.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid capture label");
        return label;
    }
    private static Path output() { return Path.of(System.getProperty("academy.beetlePerformanceOutput", "docs/models/darkmatter_creature/performance")); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); System.out.println(PREFIX + message); }
    private static void server(Consumer<ServerPlayer> task) {
        var mc = Minecraft.getInstance(); var id = mc.player.getUUID(); mc.getSingleplayerServer().execute(() -> {
            var viewer = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
            try { task.accept(viewer); }
            catch (Throwable e) {
                try { if (viewer != null) restoreViewer(viewer); } catch (Throwable cleanupFailure) { e.addSuppressed(cleanupFailure); }
                failure = e;
            }
        });
    }
    private static void capture(String name) {
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) { var path = output().resolve(label() + "_" + name + ".png"); Files.createDirectories(path.getParent()); image.writeToFile(path); }
            catch (Exception e) { throw new IllegalStateException(e); }
        });
    }
}
