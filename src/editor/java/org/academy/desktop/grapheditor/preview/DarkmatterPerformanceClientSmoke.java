package org.academy.desktop.grapheditor.preview;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.CameraType;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.render.vfxgraph.render.VfxGraphRenderer;
import org.academy.api.common.vfx.DirectionalArea;
import org.academy.api.server.ability.DarkmatterGraphEffects;
import org.academy.internal.client.render.vfx.DarkmatterSixWingsVfxClient;
import org.academy.internal.client.render.vfx.DarkmatterVfxClient;
import org.academy.internal.client.render.vfx.InterferenceFieldClient;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
import org.academy.internal.common.world.entity.projectile.DarkmatterFeatherProjectile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Opt-in single-client renderer pressure fixture, excluded from mod jars.
 * Eighteen neutral tracked cows model remote anchors, not eighteen network clients.
 * Client-only real feather entities exercise the production adapter's admission cap;
 * their bounded position reset is diagnostic and does not exercise server damage.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class DarkmatterPerformanceClientSmoke {
    private static final String PREFIX = "[darkmatter-performance-smoke] ";
    private static final int SAMPLE_FRAMES = 120;
    private static final int STAGE_WARMUP_TICKS = 40;
    private static final Vec3 VIEWER = new Vec3(0, 81, 174.5);
    private record Stage(String name, int wings, int fields, boolean mixed, int feathers, int distance, boolean away) { }
    private static final List<Stage> STAGES = List.of(
            new Stage("neutral_18_baseline", 0, 0, false, 0, 16, false),
            new Stage("wings_8_distance_16", 8, 0, false, 0, 16, false),
            new Stage("wings_18_distance_16", 18, 0, false, 0, 16, false),
            new Stage("wings_18_distance_48", 18, 0, false, 0, 48, false),
            new Stage("wings_18_distance_96", 18, 0, false, 0, 96, false),
            new Stage("wings_18_offscreen", 18, 0, false, 0, 16, true),
            new Stage("fields_18_distance_16", 0, 18, false, 0, 16, false),
            new Stage("fields_18_distance_48", 0, 18, false, 0, 48, false),
            new Stage("fields_18_distance_96", 0, 18, false, 0, 96, false),
            new Stage("mixed_18_distance_16", 18, 18, true, 0, 16, false),
            new Stage("feathers_191", 0, 0, false, 191, 16, false),
            new Stage("feathers_192", 0, 0, false, 192, 16, false),
            new Stage("feathers_193", 0, 0, false, 193, 16, false),
            new Stage("feathers_256", 0, 0, false, 256, 16, false));
    private static final List<Map<String, Object>> reports = new ArrayList<>();
    private static final List<ActiveEffect> attachedWings = new ArrayList<>();
    private static final Map<DarkmatterFeatherProjectile, Vec3> fixtureFeathers = new LinkedHashMap<>();
    private static volatile List<Cow> serverActors = List.of();
    private static volatile List<Integer> actorIds = List.of();
    private static volatile boolean ready, stageReady;
    private static volatile Stage stage;
    private static volatile Throwable serverFailure;
    private static boolean opened, configured, attached, finished, exiting;
    private static int total, warmup, stageIndex = -1, stageTicks, cleanupTicks, nextClientId = -700_000;
    private static Window window;
    private DarkmatterPerformanceClientSmoke() { }

    private static boolean enabled() { return Boolean.getBoolean("academy.darkmatterPerformanceSmoke"); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!enabled() || finished) return;
        var mc = Minecraft.getInstance();
        if (serverFailure != null) throw new IllegalStateException("Performance fixture server failed", serverFailure);
        if (++total > 12_000) throw new IllegalStateException("Performance fixture timed out");
        if (mc.level == null) {
            if (mc.gui.screen() instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen warning) {
                for (var widget : warning.children()) if (widget instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().equals("Proceed to main menu")) {
                    button.onPress(new net.minecraft.client.input.MouseButtonEvent(0, 0,
                            new net.minecraft.client.input.MouseButtonInfo(0, 0)));
                    break;
                }
            }
            if (!opened && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
                opened = true;
                mc.createWorldOpenFlows().openWorld("railgun", mc::stop);
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false;
        mc.player.setYRot(stage != null && stage.away ? 180 : 0);
        mc.player.yRotO = mc.player.getYRot();
        mc.player.setXRot(0); mc.player.xRotO = 0;
        if (!configured) {
            configured = true;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.fov().set(70);
            mc.options.renderDistance().set(12);
            mc.options.simulationDistance().set(10);
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(120);
            mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
            for (var old : VfxGraphManager.INSTANCE.activeEffects()) old.stop();
            server(DarkmatterPerformanceClientSmoke::configure);
        }
        if (!ready || ++warmup <= 200) return;
        if (exiting) {
            if (++cleanupTicks < 40) return;
            require(DarkmatterSixWingsVfxClient.activeCount() == 0, "All neutral wing adapters clear");
            require(InterferenceFieldClient.activeCount() == 0, "All field adapters clear");
            require(DarkmatterVfxClient.activeCount() == 0, "All maintained visual adapters clear");
            require(VfxGraphManager.INSTANCE.activeEffects().stream().noneMatch(DarkmatterPerformanceClientSmoke::darkmatter),
                    "All darkmatter graph instances clear after public stop and entity removal");
            writeReport(true);
            finished = true;
            System.out.println(PREFIX + "PASSED; simulated subjects, not eighteen network clients");
            mc.stop();
            return;
        }
        if (stageIndex < 0) { nextStage(); return; }
        if (!stageReady) return;
        if (++stageTicks > 600) throw new IllegalStateException("Slow or incomplete performance stage " + stage.name);
        if (!attached) {
            var actors = clientActors();
            if (actors.size() != 18) return;
            for (var actor : actors) actor.setOnGround(true);
            for (int i = 0; i < stage.wings; i++) attachedWings.add(DarkmatterSixWingsVfxClient.attach(actors.get(i)));
            for (int i = 0; i < stage.feathers; i++) {
                var feather = new DarkmatterFeatherProjectile(
                        org.academy.internal.common.world.entity.EntityTypes.DARKMATTER_FEATHER_PROJECTILE.get(), mc.level);
                feather.setId(nextClientId--);
                feather.setOwner(i < 16 ? mc.player : actors.get(i % actors.size()));
                var position = VIEWER.add((i % 16 - 7.5) * .30, 1 + (i / 16) * .25, 16 + (i % 4) * .1);
                feather.snapTo(position, 0, 0);
                feather.setDeltaMovement(0, .002, .008);
                mc.level.addEntity(feather);
                fixtureFeathers.put(feather, position);
            }
            attached = true;
            // Warm from the first successful attachment, not from the asynchronous server move.
            stageTicks = 0;
        }
        // These stationary neutral anchors deliberately use the accepted grounded wing pose.
        for (var actor : clientActors()) actor.setOnGround(true);
        for (var entry : fixtureFeathers.entrySet()) {
            requireAlive(entry.getKey());
            entry.getKey().snapTo(entry.getValue(), 0, 0);
            entry.getKey().setDeltaMovement(0, .002, .008);
        }
        if (window == null && stageTicks >= STAGE_WARMUP_TICKS) {
            require(DarkmatterSixWingsVfxClient.activeCount() == stage.wings, "Expected wing adapter count for " + stage.name);
            require(InterferenceFieldClient.activeCount() == stage.fields, "Expected field adapter count for " + stage.name);
            window = new Window(stage, stageTicks);
            System.out.println(PREFIX + "Measuring " + stage.name + " CPU/GPU " + SAMPLE_FRAMES + " frames without screenshots");
        }
        if (window != null && window.samples.size() == SAMPLE_FRAMES && window.complete()) {
            reports.add(window.report());
            window.queries.close(); window = null;
            writeReport(false);
            nextStage();
        }
    }

    private static void configure(ServerPlayer p) {
        p.teleportTo(p.level(), VIEWER.x, VIEWER.y, VIEWER.z, Set.of(), 0, 0, false);
        p.setGameMode(GameType.SPECTATOR);
        p.getInventory().clearContent(); p.setHealth(p.getMaxHealth());
        DarkmatterSixWings.Server.debugSetActive(p, false);
        for (int x = -28; x <= 28; x++) for (int z = 168; z <= 310; z++) {
            p.level().setBlock(new BlockPos(x, 80, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        }
        for (int x = -10; x <= 10; x++) for (int z = 169; z <= 195; z++) for (int y = 81; y <= 94; y++) {
            p.level().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        for (var old : p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(200))) old.discard();
        for (var old : p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(200))) old.discard();
        var actors = new ArrayList<Cow>();
        for (int i = 0; i < 18; i++) {
            var cow = new Cow(net.minecraft.world.entity.EntityTypes.COW, p.level());
            cow.snapTo(actorPosition(i, 16), 180, 0);
            cow.yBodyRot = 180; cow.setNoAi(true); cow.setNoGravity(true); cow.setOnGround(true);
            p.level().addFreshEntity(cow); actors.add(cow);
        }
        serverActors = List.copyOf(actors);
        actorIds = actors.stream().map(Entity::getId).toList();
        command(p, "time set noon"); command(p, "weather clear");
        ready = true;
    }

    private static Vec3 actorPosition(int index, int distance) {
        return VIEWER.add((index % 6 - 2.5) * 8, 0, distance + (index / 6) * 8);
    }

    private static List<LivingEntity> clientActors() {
        var level = Minecraft.getInstance().level;
        return actorIds.stream().map(level::getEntity).filter(Cow.class::isInstance).map(LivingEntity.class::cast).toList();
    }

    private static void stopClientFixtures() {
        for (var effect : attachedWings) effect.stop();
        attachedWings.clear();
        var level = Minecraft.getInstance().level;
        for (var feather : fixtureFeathers.keySet()) level.removeEntity(feather.getId(), Entity.RemovalReason.DISCARDED);
        fixtureFeathers.clear();
    }

    private static void nextStage() {
        stopClientFixtures();
        stageReady = false; attached = false; stageTicks = 0;
        if (++stageIndex == STAGES.size()) {
            stage = null; exiting = true;
            server(p -> { for (var actor : serverActors) { stopActor(actor); actor.discard(); } });
            return;
        }
        stage = STAGES.get(stageIndex);
        server(p -> {
            for (int i = 0; i < serverActors.size(); i++) {
                var actor = serverActors.get(i);
                stopActor(actor);
                actor.snapTo(actorPosition(i, stage.distance), 180, 0);
                actor.yBodyRot = 180; actor.setOnGround(true);
            }
            stageReady = true;
        });
    }

    private static void stopActor(LivingEntity actor) {
        DarkmatterGraphEffects.stopInterference(actor);
        DarkmatterGraphEffects.stopGeneration(actor, false);
        DarkmatterGraphEffects.stopGeneration(actor, true);
        DarkmatterGraphEffects.stopPhaseTuning(actor);
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!enabled() || !stageReady || stage == null) return;
        var current = stage;
        try {
            for (int i = 0; i < serverActors.size(); i++) {
                var actor = serverActors.get(i);
                long now = actor.level().getGameTime();
                if (i < current.fields && now % 4 == 0) {
                    var area = new DirectionalArea(actor.getEyePosition(), new Vec3(0, 0, -1),
                            new DirectionalArea.Cone(16, Math.cos(Math.toRadians(25))),
                            new DirectionalArea.Cone(12, Math.cos(Math.toRadians(52))));
                    DarkmatterGraphEffects.interference(actor, area);
                }
                if (current.mixed) {
                    if (now % 4 == 0) { DarkmatterGraphEffects.generation(actor, (i & 1) != 0); DarkmatterGraphEffects.phaseTuning(actor); }
                    if (now % 5 == 0) DarkmatterGraphEffects.repair(actor);
                    if (now % 10 == 0) {
                        switch (i % 6) {
                            case 0, 4 -> {
                                DarkmatterGraphEffects.shaping(actor, i % 10, .5f);
                                DarkmatterGraphEffects.lightContact(actor);
                            }
                            case 1, 5 -> {
                                DarkmatterGraphEffects.disassemble(actor);
                                DarkmatterGraphEffects.cut(actor, new Vec3(0, 0, -1), 1, i == 5 ? -1 : 1);
                            }
                            case 2 -> { DarkmatterGraphEffects.summon(actor); DarkmatterGraphEffects.creatureContact(actor); }
                            case 3 -> { DarkmatterGraphEffects.dismiss(actor); DarkmatterGraphEffects.wingStrike(actor); }
                        }
                    }
                }
            }
        } catch (Throwable failure) { serverFailure = failure; }
    }

    @SubscribeEvent
    public static void beforeFrame(RenderFrameEvent.Pre event) {
        if (!enabled() || window == null || window.samples.size() >= SAMPLE_FRAMES) return;
        window.observeAdmissions();
        if (window.initialInstancing == null) window.initialInstancing = VfxGraphManager.INSTANCE.instancingStatistics();
        window.recording = window.samples.size() * 2;
        long now = System.nanoTime();
        window.intervalMs = window.previousFrameNanos == 0 ? Double.NaN : (now - window.previousFrameNanos) / 1_000_000.0;
        window.previousFrameNanos = now;
        window.startedNanos = now;
        RenderSystem.getDevice().createCommandEncoder().writeTimestamp(window.queries, window.recording);
    }

    @SubscribeEvent
    public static void afterFrame(RenderFrameEvent.Post event) {
        if (!enabled() || window == null || window.recording < 0) return;
        RenderSystem.getDevice().createCommandEncoder().writeTimestamp(window.queries, window.recording + 1);
        double cpuMs = (System.nanoTime() - window.startedNanos) / 1_000_000.0;
        window.samples.add(new Sample(window.recording, cpuMs, window.intervalMs));
        window.recording = -1;
        if (window.samples.size() == SAMPLE_FRAMES) window.finalInstancing = VfxGraphManager.INSTANCE.instancingStatistics();
        window.collectStatistics();
    }

    private record Sample(int slot, double cpuMs, double intervalMs) { }
    private static final class Window {
        final Stage stage;
        final int startedTick;
        final GpuQueryPool queries = RenderSystem.getDevice().createTimestampQueryPool(SAMPLE_FRAMES * 2);
        final List<Sample> samples = new ArrayList<>(SAMPLE_FRAMES);
        final Map<String, Integer> maximumByGraph = new LinkedHashMap<>();
        Set<ActiveEffect> previous = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<ActiveEffect> current = Collections.newSetFromMap(new IdentityHashMap<>());
        VfxGraphRenderer.InstancingStatistics initialInstancing, finalInstancing;
        long startedNanos, previousFrameNanos;
        double intervalMs;
        int recording = -1, newFeatherGraphs, maxStoppedBeforeRender, maxActive, maxVisible, maxCulled,
                maxParticles, maxOutputSpecs, maxFeatherGraphs, maxSimulated;
        Window(Stage stage, int startedTick) {
            this.stage = stage; this.startedTick = startedTick;
            for (var effect : VfxGraphManager.INSTANCE.activeEffects()) if (feather(effect)) previous.add(effect);
        }
        void observeAdmissions() {
            current.clear();
            int stopped = 0;
            for (var effect : VfxGraphManager.INSTANCE.activeEffects()) if (feather(effect)) {
                current.add(effect);
                if (!previous.contains(effect)) newFeatherGraphs++;
                if (effect.isStopped()) stopped++;
            }
            maxStoppedBeforeRender = Math.max(maxStoppedBeforeRender, stopped);
            var swap = previous; previous = current; current = swap;
        }
        void collectStatistics() {
            var stats = VfxGraphManager.INSTANCE.frameStatistics();
            maxActive = Math.max(maxActive, stats.active()); maxVisible = Math.max(maxVisible, stats.visible());
            maxCulled = Math.max(maxCulled, stats.culled()); maxSimulated = Math.max(maxSimulated, stats.simulated());
            int particles = 0, specs = 0, blades = 0;
            var counts = new LinkedHashMap<String, Integer>();
            for (var effect : VfxGraphManager.INSTANCE.activeEffects()) if (darkmatter(effect) && !effect.isStopped()) {
                particles += effect.effect().buffer().count(); specs += effect.specs().size();
                counts.merge(effect.assetKey(), 1, Integer::sum);
                if (feather(effect)) blades++;
            }
            counts.forEach((key, value) -> maximumByGraph.merge(key, value, Math::max));
            maxParticles = Math.max(maxParticles, particles); maxOutputSpecs = Math.max(maxOutputSpecs, specs);
            maxFeatherGraphs = Math.max(maxFeatherGraphs, blades);
        }
        boolean complete() {
            for (var sample : samples) if (queries.getValue(sample.slot).isEmpty() || queries.getValue(sample.slot + 1).isEmpty()) return false;
            return true;
        }
        Map<String, Object> report() {
            var gpu = new ArrayList<Double>(); var cpu = new ArrayList<Double>(); var interval = new ArrayList<Double>();
            double period = RenderSystem.getDevice().getDeviceInfo().timestampPeriod();
            for (var sample : samples) {
                gpu.add((queries.getValue(sample.slot + 1).orElseThrow() - queries.getValue(sample.slot).orElseThrow()) * period / 1_000_000.0);
                cpu.add(sample.cpuMs); if (Double.isFinite(sample.intervalMs)) interval.add(sample.intervalMs);
            }
            var result = new LinkedHashMap<String, Object>();
            result.put("stage", stage.name); result.put("neutral_tracked_cows", 18); result.put("wing_actors", stage.wings);
            result.put("field_actors", stage.fields); result.put("mixed_visual_api", stage.mixed);
            result.put("client_fixture_projectiles", stage.feathers); result.put("nearest_actor_depth_blocks", stage.distance);
            result.put("offscreen", stage.away); result.put("sample_frames", samples.size());
            result.put("cpu_frame_submission_ms", distribution(cpu)); result.put("gpu_whole_frame_ms", distribution(gpu));
            result.put("frame_interval_ms_including_pacing_and_diagnostics", distribution(interval));
            result.put("max_active_graphs", maxActive); result.put("max_visible_graphs", maxVisible);
            result.put("max_culled_graphs", maxCulled); result.put("max_simulated_graphs", maxSimulated);
            result.put("max_darkmatter_particle_slots", maxParticles); result.put("max_darkmatter_output_specs", maxOutputSpecs);
            result.put("max_graph_instances_by_asset", maximumByGraph); result.put("max_live_feather_graphs", maxFeatherGraphs);
            result.put("new_feather_graph_admissions_during_window", newFeatherGraphs);
            result.put("max_stopped_feather_graphs_waiting_for_render", maxStoppedBeforeRender);
            result.put("renderer_instancing_actual_submission_totals", Map.of(
                    "draw_calls", finalInstancing.drawCalls() - initialInstancing.drawCalls(),
                    "instances", finalInstancing.instances() - initialInstancing.instances(),
                    "material_triangles", finalInstancing.materialTriangles() - initialInstancing.materialTriangles(),
                    "uploaded_instance_bytes", finalInstancing.uploadedBytes() - initialInstancing.uploadedBytes(),
                    "zero_alpha_skipped_instances", finalInstancing.zeroAlphaSkipped() - initialInstancing.zeroAlphaSkipped()));
            if (stage.feathers > 0) {
                require(maxFeatherGraphs == Math.min(stage.feathers, 192), "Feather adapter stays within its 192 graph cap and produces visible instances");
                if (System.getProperty("academy.darkmatterPerformanceLabel", "baseline").equals("optimized")) {
                    require(newFeatherGraphs == 0, "Stationary feather candidates retain their selected graphs throughout the measurement window");
                    require(maxStoppedBeforeRender == 0, "Over-budget feather candidates do not accumulate stopped graphs before rendering");
                }
                result.put("steady_feather_graph_retention", newFeatherGraphs == 0);
            }
            result.put("measured_client_ticks", stageTicks - startedTick);
            result.put("feather_admissions_per_client_tick", newFeatherGraphs / (double) Math.max(1, stageTicks - startedTick));
            result.put("all_samples_without_screenshots", true);
            System.out.println(PREFIX + stage.name + " gpu=" + distribution(gpu) + " admissions=" + newFeatherGraphs);
            return result;
        }
    }

    private static Map<String, Object> distribution(List<Double> input) {
        if (input.isEmpty()) return Map.of("samples", 0);
        var values = new ArrayList<>(input); values.sort(Double::compare);
        return Map.of("samples", values.size(), "median", percentile(values, .5), "p95", percentile(values, .95),
                "p99", percentile(values, .99), "mean", values.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
    }
    private static double percentile(List<Double> values, double quantile) {
        return values.get(Math.clamp((int) Math.ceil(values.size() * quantile) - 1, 0, values.size() - 1));
    }
    private static boolean darkmatter(ActiveEffect effect) { return effect.assetKey().contains("vfxgraph/darkmatter_"); }
    private static boolean feather(ActiveEffect effect) { return effect.assetKey().endsWith("darkmatter_feather"); }
    private static void requireAlive(Entity entity) { if (entity.isRemoved()) throw new IllegalStateException("Fixture projectile disappeared early"); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        System.out.println(PREFIX + message);
    }
    private static void writeReport(boolean cleared) {
        var label = System.getProperty("academy.darkmatterPerformanceLabel", "baseline");
        if (!label.matches("[a-zA-Z0-9_-]{1,40}")) throw new IllegalArgumentException("Invalid performance label");
        var mc = Minecraft.getInstance();
        var data = new LinkedHashMap<String, Object>();
        data.put("label", label); data.put("fixture_mode", "one local client, eighteen neutral living-entity anchors");
        data.put("diagnostic_schema_version", 2);
        data.put("renderer_counter_scope", "actual VFX instanced GPU submissions within the same 120 measured frames; snapshots outside CPU frame timing");
        data.put("archived_baseline_has_renderer_submission_counters", false);
        data.put("not_a_real_eighteen_client_network_benchmark", true);
        data.put("projectiles", "client-only real feather entity class, bounded stationary reset, production adapter; no server hit or resource test");
        data.put("mixed", "public visual API simulation, not actual skill input or resource settlement");
        data.put("resolution", List.of(mc.gameRenderer.mainRenderTarget().width, mc.gameRenderer.mainRenderTarget().height));
        data.put("gpu", RenderSystem.getDevice().getDeviceInfo().toString());
        data.put("jvm", System.getProperty("java.version")); data.put("vsync", false); data.put("frame_limit", 120);
        data.put("completed_windows", reports.size()); data.put("required_windows", STAGES.size());
        data.put("final_all_darkmatter_cleared", cleared); data.put("windows", reports);
        var output = Path.of(System.getProperty("academy.darkmatterPerformanceOutput")).resolve(label + ".json");
        try { Files.createDirectories(output.getParent()); Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(data) + "\n"); }
        catch (Exception failure) { throw new IllegalStateException("Cannot save performance report", failure); }
    }
    private static void command(ServerPlayer player, String command) {
        var server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }
    private static void server(Consumer<ServerPlayer> task) {
        var mc = Minecraft.getInstance(); var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable failure) { serverFailure = failure; }
        });
    }
}
