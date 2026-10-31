package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.api.client.ability.program.ProgramNodePalette;
import org.academy.api.common.profiler.AcademyProfiler;
import org.academy.internal.client.ability.mentalout.ModularProgramEditorSession;
import org.academy.internal.client.ability.mentalout.ModularProgramScreen;
import org.academy.internal.client.ability.program.ProgramPaletteCatalog;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.ProgramNodeExtensionIndex;
import org.academy.internal.common.ability.program.editor.ProgramEditorDocument;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Opt-in real-screen elapsed/CPU/allocation probe. Never sends save/cast packets or executes graphs. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class PrecisionPerformanceSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.precisionPerformanceSmoke");
    private static final String STAGE = System.getProperty("academy.precisionPerformanceStage", "baseline");
    private static final int WARMUP = Integer.getInteger("academy.precisionPerformanceWarmup", 150);
    private static final int SAMPLES = Integer.getInteger("academy.precisionPerformanceSamples", 1000);
    private static final int FRAME_TICKS = Integer.getInteger("academy.precisionPerformanceFrameTicks", 120);
    private static final int LONG_TICKS = Integer.getInteger("academy.precisionPerformanceLongStayTicks", 1200);
    private static final Map<String, Method> METHODS = new HashMap<>();
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final List<Map<String, Object>> SCREENSHOTS = new ArrayList<>();
    private static final List<MemorySession> SESSIONS = new ArrayList<>();
    private static final List<Long> FRAME_ELAPSED = new ArrayList<>();
    private static final List<Long> FRAME_THREAD_CPU = new ArrayList<>();
    private static final List<Long> FRAME_BYTES = new ArrayList<>();
    private static final Map<String, Samples> MICRO = new LinkedHashMap<>();
    private static final com.sun.management.ThreadMXBean ALLOCATION = ENABLED ? allocationBean() : null;
    private static final java.lang.management.ThreadMXBean CPU = ENABLED ? cpuBean() : null;
    private static volatile Object blackhole;
    private static volatile Throwable screenshotFailure;
    private static volatile Map<String, Object> screenshotReceipt;
    private static volatile String screenshotRequest;
    private static boolean screenshotInFlight, initialized, finished, frameWindow, profilerWindow;
    private static int scene, totalTicks, age, phaseTicks, iterations, reloadClearTicks;
    private static int originalWidth, originalHeight;
    private static long frameStart, frameAllocated, frameCpuStarted;
    private static String initialLanguage;
    private static Phase phase;
    private static ModularProgramScreen screen;
    private static MemorySession session;
    private static AbilityProgramDefinition definition;
    private static AbilityProgram expected;
    private static byte[] expectedBytes;
    private static Map<String, Object> result;
    private static CompletableFuture<?> reload;
    private static String pendingLanguage;
    private static JsonObject compileBench;

    private PrecisionPerformanceSmoke() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
        try {
            isolated(client);
            check(++totalTicks <= Math.max(6000, LONG_TICKS + 5000), "performance fixture exceeded its tick deadline");
            if (!initialized) initialize(client);
            if (screenshotFailure != null) throw new IllegalStateException("screenshot failed", screenshotFailure);
            if (screenshotReceipt != null) {
                SCREENSHOTS.add(screenshotReceipt);
                screenshotReceipt = null;
                screenshotInFlight = false;
            }
            check(client.gui.screen() == screen, "performance editor was replaced");
            age++; phaseTicks++;
            if (phase == Phase.LEGACY) { legacy(client); return; }
            switch (phase) {
                case SETTLE -> { if (phaseTicks >= 30) transition(Phase.WARMUP); }
                case WARMUP -> {
                    for (int i = 0; i < 10 && iterations < WARMUP; i++, iterations++) invokeProbes(false);
                    if (iterations >= WARMUP) { iterations = 0; transition(Phase.MEASURE); }
                }
                case MEASURE -> {
                    for (int i = 0; i < 25 && iterations < SAMPLES; i++, iterations++) invokeProbes(true);
                    if (iterations >= SAMPLES) {
                        var measurements = new LinkedHashMap<String, Object>();
                        MICRO.forEach((key, value) -> measurements.put(key, value.result()));
                        result.put("reflection_microbenchmarks_profiler_disabled", measurements);
                        beginFrames(false); transition(Phase.FRAMES_OFF);
                    }
                }
                case FRAMES_OFF -> {
                    if (phaseTicks >= FRAME_TICKS) {
                        result.put("real_render_profiler_disabled", finishFrames());
                        beginFrames(true); transition(Phase.FRAMES_ON);
                    }
                }
                case FRAMES_ON -> {
                    if (phaseTicks >= FRAME_TICKS) {
                        result.put("real_render_profiler_enabled", finishFrames());
                        interactionChecks();
                        requestScreenshot(client, "editor-" + expected.graph().nodes().size());
                        transition(Phase.CAPTURE);
                    }
                }
                case CAPTURE -> {
                    if (!screenshotInFlight) {
                        assertUnchanged();
                        if (scene == 1) {
                            beginReload(client, initialLanguage);
                            transition(Phase.RESOURCE_WAIT);
                        } else nextScene(client);
                    }
                }
                case RESOURCE_WAIT -> {
                    if (reloadReady(client)) {
                        reload.join(); validateReload(client, "same_language_resource_reload");
                        pendingLanguage = initialLanguage.equals("zh_cn") ? "en_us" : "zh_cn";
                        beginReload(client, pendingLanguage); transition(Phase.LANGUAGE_WAIT);
                    }
                }
                case LANGUAGE_WAIT -> {
                    if (reloadReady(client)) {
                        reload.join(); validateReload(client, "alternate_language_reload");
                        beginReload(client, initialLanguage); transition(Phase.RESTORE_WAIT);
                    }
                }
                case RESTORE_WAIT -> {
                    if (reloadReady(client)) {
                        reload.join(); validateReload(client, "original_language_restored");
                        requestScreenshot(client, "editor-32-after-resource-language-reloads");
                        transition(Phase.RESTORE_CAPTURE);
                    }
                }
                case RESTORE_CAPTURE -> { if (!screenshotInFlight) nextScene(client); }
                default -> throw new IllegalStateException("Unexpected probe phase " + phase);
            }
        } catch (Throwable failure) { fail(client, failure); }
    }

    @SubscribeEvent
    public static void beforeFrame(RenderFrameEvent.Pre event) {
        if (!ENABLED || finished || !frameWindow || Minecraft.getInstance().gui.screen() != screen) return;
        frameAllocated = allocated();
        frameCpuStarted = cpuTime();
        frameStart = System.nanoTime();
    }

    @SubscribeEvent
    public static void afterFrame(RenderFrameEvent.Post event) {
        if (!ENABLED || finished) return;
        if (frameWindow && frameStart != 0) {
            FRAME_ELAPSED.add(System.nanoTime() - frameStart);
            var cpu = cpuTime();
            if (cpu >= 0 && frameCpuStarted >= 0) FRAME_THREAD_CPU.add(cpu - frameCpuStarted);
            var bytes = allocated();
            if (bytes >= 0 && frameAllocated >= 0) FRAME_BYTES.add(bytes - frameAllocated);
            frameStart = 0;
        }
        var name = screenshotRequest;
        if (name == null) return;
        var client = Minecraft.getInstance();
        if (client.gui.overlay() != null) return;
        screenshotRequest = null;
        var tick = age;
        Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1, message -> {
            try {
                var path = isolated(client).resolve("screenshots").resolve(name);
                check(Files.isRegularFile(path) && Files.size(path) > 0, message.getString());
                screenshotReceipt = Map.of("path", "screenshots/" + name, "visible_tick", tick,
                        "sha256", sha(Files.readAllBytes(path)), "bytes", Files.size(path));
            } catch (Throwable failure) { screenshotFailure = failure; }
        });
    }

    private static void initialize(Minecraft client) throws Exception {
        check(Set.of("baseline", "optimized").contains(STAGE), "expected baseline or optimized stage");
        check(WARMUP >= 10 && SAMPLES >= 50 && SAMPLES <= 10_000 && FRAME_TICKS >= 30
                && LONG_TICKS >= 1200 && LONG_TICKS <= 36_000, "invalid benchmark configuration");
        check(!Boolean.getBoolean("academy.precisionLearningPathSmoke")
                && !Boolean.getBoolean("academy.precisionPresentationSmoke"), "other editor fixtures must be disabled");
        initialLanguage = client.getLanguageManager().getSelected();
        check(Set.of("zh_cn", "en_us").contains(initialLanguage), "expected zh_cn or en_us");
        originalWidth = client.getWindow().getWidth(); originalHeight = client.getWindow().getHeight();
        client.options.pauseOnLostFocus = false;
        client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        // Compile timing has its own profiler lifecycle and runs before every UI capture window.
        AcademyProfiler.stopZoneCapture();
        compileBench = PrecisionCompileBenchmark.run(Math.min(WARMUP, 100), Math.min(SAMPLES, 1000));
        initialized = true;
        open(client, PrecisionPerformanceGraphs.editorGraph(2));
    }

    private static void open(Minecraft client, AbilityProgram initial) throws Exception {
        definition = AbilityProgramDefinitions.require(initial.category());
        session = new MemorySession(initial, PrecisionPerformanceGraphs.capabilities(definition));
        SESSIONS.add(session);
        screen = new ModularProgramScreen(session);
        client.gui.setScreen(screen);
        if (!(boolean) call("paletteVisible")) call("handleRailClick", new Class<?>[]{double.class, double.class},
                number("leftX") + 8.0, number("canvasY") + 8.0);
        check((boolean) call("paletteVisible"), "fixture could not open the real palette drawer");
        expected = program(); expectedBytes = ProgramBookCodec.encodeProgram(expected);
        check(initial.equals(expected), "opening enriched or changed the deterministic input graph");
        assertUnchanged();
        result = new LinkedHashMap<>();
        result.put("nodes", expected.graph().nodes().size());
        result.put("edges", expected.graph().edges().size());
        result.put("program_sha256", sha(expectedBytes));
        result.put("category", expected.category().toString());
        result.put("graph_compiled", true); result.put("codec_round_trip", true);
        result.put("gui_size", List.of(screen.width, screen.height));
        result.put("window_size", List.of(client.getWindow().getWidth(), client.getWindow().getHeight()));
        result.put("initial_language", initialLanguage);
        result.put("graph_execution", false);
        METHODS.clear(); MICRO.clear();
        for (var name : List.of("nodes", "paletteCards", "firstDiagnostic")) {
            method(name, new Class<?>[0]); MICRO.put(name, new Samples());
        }
        age = 0; iterations = 0;
        transition(scene == 3 ? Phase.LEGACY : Phase.SETTLE);
        AcademyCraft.LOGGER.info("PRECISION_PERFORMANCE_SCENE stage={} nodes={} edges={} sha={}",
                STAGE, expected.graph().nodes().size(), expected.graph().edges().size(), result.get("program_sha256"));
    }

    private static void invokeProbes(boolean measure) throws Exception {
        for (var item : MICRO.entrySet()) {
            var method = method(item.getKey(), new Class<?>[0]);
            if (!measure) { blackhole = method.invoke(screen); continue; }
            var bytes = allocated(); var started = System.nanoTime();
            blackhole = method.invoke(screen);
            var elapsed = System.nanoTime() - started; var after = allocated();
            item.getValue().nanos.add(elapsed);
            if (bytes >= 0 && after >= 0) item.getValue().bytes.add(after - bytes);
        }
    }

    private static void beginFrames(boolean enabled) throws Exception {
        AcademyProfiler.stopZoneCapture();
        if (enabled) AcademyProfiler.startZoneCapture();
        profilerWindow = enabled;
        FRAME_ELAPSED.clear(); FRAME_THREAD_CPU.clear(); FRAME_BYTES.clear(); frameStart = 0;
        call("resetPerformanceCapture", new Class<?>[]{boolean.class}, true);
        frameWindow = true;
    }

    private static Map<String, Object> finishFrames() throws Exception {
        frameWindow = false; frameStart = 0;
        var surface = call("performanceSnapshot");
        call("resetPerformanceCapture", new Class<?>[]{boolean.class}, false);
        var measured = new LinkedHashMap<String, Object>();
        measured.put("surface_render_elapsed_and_rebuild_counters", surface);
        measured.put("render_event_wall_elapsed_nanos", distribution(FRAME_ELAPSED));
        measured.put("render_event_thread_cpu_nanos", distribution(FRAME_THREAD_CPU));
        measured.put("render_event_thread_allocated_bytes", distribution(FRAME_BYTES));
        measured.put("render_event_cpu_zero_samples", FRAME_THREAD_CPU.stream().filter(value -> value == 0).count());
        measured.put("render_event_cpu_smallest_positive_sample_nanos", FRAME_THREAD_CPU.stream()
                .filter(value -> value > 0).mapToLong(Long::longValue).min().orElse(0));
        measured.put("thread_cpu_counter_precision_note", "Per-event CPU values can be zero or coarsely quantized on this JVM/OS; they do not establish zero work. Inspect the zero count and smallest positive sample separately from nanoTime elapsed.");
        measured.put("window_client_ticks", phaseTicks);
        measured.put("profiler_enabled", profilerWindow);
        measured.put("render_event_scope", "Local Minecraft.renderFrame fires Pre immediately before GameRenderer.render and Post immediately after it. Window texture acquisition, update/extract happen before Pre; blit, command submission, surface.present and frame limiter happen after Post. Academy ScreenDispatcher performs editor command generation at ScreenEvent.Render.Pre during GUI extraction before this interval, so the separate editor-surface measurements are not nested inside this event timer. Listener work and waits inside render may contribute. This is not a full frame interval or GPU timestamp.");
        measured.put("surface_elapsed_scope", "EditorSurface.renderInternal wall elapsed only; excludes extractRenderState configuration sync, tooltip surface and explicit present. It is not a thread CPU or GPU-completion measurement.");
        if (profilerWindow) {
            AcademyProfiler.stopZoneCapture();
            var zones = new ArrayList<Map<String, Object>>();
            for (var thread : AcademyProfiler.snapshot().getZones().values()) {
                for (var zone : thread.topSlices(4096, true)) if (zone.name().startsWith("precision.editor.")) {
                    zones.add(Map.of("thread", thread.getThreadName(), "path", zone.path(), "name", zone.name(),
                            "count", zone.count(), "total_nanos", zone.totalNs(), "self_nanos", zone.selfNs(),
                            "maximum_nanos", zone.maxNs(), "counters", zone.counters()));
                }
            }
            measured.put("fixed_editor_profiler_zones", zones);
        }
        check(FRAME_ELAPSED.size() >= 3, "insufficient real render-event samples");
        assertUnchanged();
        return measured;
    }

    private static void interactionChecks() throws Exception {
        int id = expected.graph().nodes().getFirst().id();
        var position = expected.editorLayout().nodePositions().get(id);
        var changed = new ProgramEditorDocument(expected, definition, session.capabilities).moveNode(
                id, position.x() + 11, position.y() + 7);
        check(changed.successful(), "real document move failed");
        call("setProgram", new Class<?>[]{AbilityProgram.class, boolean.class}, changed.document().program(), true);
        check(program().equals(changed.document().program()), "UI did not install the edited document");
        var movedView = call("node", new Class<?>[]{int.class}, id);
        check(((Number) property(movedView, "x")).doubleValue() == position.x() + 11
                && ((Number) property(movedView, "y")).doubleValue() == position.y() + 7,
                "NodeView position cache was stale after a layout edit");
        check(((List<?>) call("nodes")).size() == expected.graph().nodes().size(), "edit dropped a NodeView");
        call("undo"); assertUnchanged();
        var restoredView = call("node", new Class<?>[]{int.class}, id);
        check(((Number) property(restoredView, "x")).doubleValue() == position.x()
                && ((Number) property(restoredView, "y")).doubleValue() == position.y(),
                "NodeView position cache was stale after undo");
        call("redo"); check(program().equals(changed.document().program()), "redo lost edit");
        call("undo"); assertUnchanged();
        schemaAndValidationInvalidationChecks();
        var centerX = number("canvasX") + number("canvasW") / 2.0;
        var centerY = number("canvasY") + number("canvasH") / 2.0;
        var oldZoom = ((Number) field("zoom")).doubleValue();
        screen.mouseScrolled(centerX, centerY, 0, 1);
        check(((Number) field("zoom")).doubleValue() != oldZoom, "canvas scroll did not zoom");
        call("zoomAt", new Class<?>[]{double.class, double.class, double.class}, centerX, centerY, oldZoom);
        var before = number("paletteScroll");
        screen.mouseScrolled(((Number) call("paletteX")).doubleValue() + 8, number("canvasY") + 80, 0, -1);
        check(number("paletteScroll") == before + 1, "palette scroll did not advance");
        setField("paletteScroll", 0);
        check(((List<?>) call("nodes")).size() == expected.graph().nodes().size(), "zoom omitted a NodeView");
        assertUnchanged();
        result.put("edit_undo_redo_and_hash_restored", true);
        result.put("canvas_zoom_and_palette_scroll_preserve_graph", true);
        result.put("all_source_nodes_have_views", true);
        result.put("layout_and_undo_refresh_node_lookup_geometry", true);
    }

    private static void schemaAndValidationInvalidationChecks() throws Exception {
        check(call("firstDiagnostic") == null, "valid graph unexpectedly has a diagnostic");
        var edges = new ArrayList<>(expected.graph().edges());
        edges.removeFirst();
        var invalid = copy(new ProgramGraph(expected.graph().nodes(), edges));
        call("setProgram", new Class<?>[]{AbilityProgram.class, boolean.class}, invalid, true);
        check(call("firstDiagnostic") != null, "diagnostic cache hid a newly missing required input");
        call("undo"); assertUnchanged();
        check(call("firstDiagnostic") == null, "diagnostic cache retained an error after undo");
        var arithmetic = expected.graph().nodes().stream().filter(node ->
                node.type().equals(CommonProgramNodeIds.NUMERIC_ARITHMETIC)).findFirst().orElse(null);
        if (arithmetic != null) {
            var config = arithmetic.configuration().getAsJsonObject(); config.addProperty("operator", "absolute");
            var rewritten = expected.graph().nodes().stream().map(node -> node.id() == arithmetic.id()
                    ? new ProgramGraph.Node(node.id(), node.type(), node.schemaVersion(), config) : node).toList();
            var originalLeft = expected.graph().edges().stream().filter(edge ->
                    edge.to().nodeId() == arithmetic.id() && edge.to().port().equals("left")).findFirst().orElseThrow();
            edges = new ArrayList<>(expected.graph().edges().stream().filter(edge -> edge.to().nodeId() != arithmetic.id()).toList());
            edges.add(new ProgramGraph.Edge(originalLeft.from(), new ProgramGraph.Endpoint(arithmetic.id(), "value")));
            var unary = copy(new ProgramGraph(rewritten, edges));
            check(definition.compile(unary, session.capabilities).valid(), "rewired unary mode did not compile");
            call("setProgram", new Class<?>[]{AbilityProgram.class, boolean.class}, unary, true);
            var schema = (ProgramNodeSchema) property(call("node", new Class<?>[]{int.class}, arithmetic.id()), "schema");
            check(schema.input("value").isPresent() && schema.input("left").isEmpty() && schema.input("right").isEmpty(),
                    "NodeView schema cache retained the binary mode after a configuration edit");
            check(call("firstDiagnostic") == null, "rewired valid mode retained an old diagnostic");
            call("undo"); assertUnchanged();
            schema = (ProgramNodeSchema) property(call("node", new Class<?>[]{int.class}, arithmetic.id()), "schema");
            check(schema.input("left").isPresent() && schema.input("right").isPresent(),
                    "NodeView schema cache retained the unary mode after undo");
            result.put("configuration_mode_schema_and_undo_invalidation", true);
        }
        result.put("missing_input_diagnostic_and_undo_invalidation", true);
    }

    private static AbilityProgram copy(ProgramGraph graph) {
        return new AbilityProgram(expected.schemaVersion(), expected.id(), expected.name(), expected.category(),
                graph, expected.editorLayout());
    }

    private static void beginReload(Minecraft client, String language) throws Exception {
        assertUnchanged();
        reloadClearTicks = 0;
        call("resetPerformanceCapture", new Class<?>[]{boolean.class}, true);
        client.getLanguageManager().getClass().getMethod("setSelected", String.class)
                .invoke(client.getLanguageManager(), language);
        reload = (CompletableFuture<?>) client.getClass().getMethod("reloadResourcePacks").invoke(client);
        result.put("resource_reload_started_" + language + "_" + phase.name(), true);
    }

    private static boolean reloadReady(Minecraft client) {
        if (!reload.isDone()) { reloadClearTicks = 0; return false; }
        reload.join();
        if (client.gui.overlay() != null) { reloadClearTicks = 0; return false; }
        return ++reloadClearTicks >= 10;
    }

    private static void validateReload(Minecraft client, String key) throws Exception {
        call("syncLayout");
        var entry = definition.editorCatalog().entry(CommonProgramNodeIds.SCALAR_CONSTANT);
        var label = Component.translatable(entry.translationKey()).getString();
        check(!label.equals(entry.translationKey()), "unresolved localized value label after reload");
        var search = field("search");
        search.getClass().getMethod("setText", String.class).invoke(search, label);
        @SuppressWarnings("unchecked") var cards = (List<ProgramPaletteCatalog.Card>) call("paletteCards");
        check(cards.stream().flatMap(card -> card.members().stream()).anyMatch(value -> value.id().equals(entry.id())),
                "localized search index was stale after " + key);
        for (var card : cards) {
            var text = (String) call("cardLabel", new Class<?>[]{ProgramPaletteCatalog.Card.class}, card);
            check(!text.contains("screen.academy."), "palette reused unresolved text after reload");
        }
        search.getClass().getMethod("setText", String.class).invoke(search, "");
        check(((List<?>) call("nodes")).size() == expected.graph().nodes().size(), "reload dropped NodeViews");
        assertUnchanged();
        result.put(key, Map.of("passed", true, "language", client.getLanguageManager().getSelected(),
                "localized_search", label, "program_sha256", sha(expectedBytes),
                "observed_surface_and_rebuild_counters", call("performanceSnapshot"),
                "overlay_absent_for_client_ticks", reloadClearTicks));
        call("resetPerformanceCapture", new Class<?>[]{boolean.class}, false);
    }

    private static void legacy(Minecraft client) throws Exception {
        if (age == 30 || age == 140 || age == 1200 || LONG_TICKS > 1200 && age == LONG_TICKS) {
            assertUnchanged();
            requestScreenshot(client, "legacy-ten-tick-" + age);
        }
        if (age == 180) {
            interactionChecks();
            requestScreenshot(client, "legacy-ten-after-zoom-scroll-undo");
        }
        if (age == 240) {
            client.getWindow().setWindowed(Math.max(800, originalWidth - 200), Math.max(600, originalHeight - 100));
        }
        if (age == 260) {
            client.getWindow().setWindowed(originalWidth, originalHeight);
            result.put("real_window_resize_and_restore", true);
        }
        if (age == 300) org.lwjgl.glfw.GLFW.glfwIconifyWindow(client.getWindow().handle());
        if (age == 310) {
            org.lwjgl.glfw.GLFW.glfwRestoreWindow(client.getWindow().handle());
            org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().handle());
            result.put("window_iconify_restore_and_focus", true);
        }
        if (age >= LONG_TICKS && !screenshotInFlight) {
            assertUnchanged();
            dynamicPresentationChecks();
            result.put("visible_client_ticks", age);
            result.put("historical_graph_provenance", "StructureGroupSmoke.movementGraph before optional seconds source; ten-node topology reconstructed with deterministic point and UUID, not the original world coordinates");
            result.put("full_ten_minute_tick_stay", LONG_TICKS >= 12000);
            RESULTS.add(result);
            finish(client);
        }
    }

    @SuppressWarnings("unchecked")
    private static void dynamicPresentationChecks() throws Exception {
        // This runs after every latency/frame sample and historical screenshot, then exits the client.
        // Register only our exact supplier, remove only that same object, and never write registry/world data.
        var id = CommonProgramNodeIds.FLOAT_CONSTANT;
        check(!ProgramNodePalette.hasProvider(id), "fixture must not replace an existing built-in provider");
        var previousLayer = field("paletteLayer");
        var query = field("search");
        var previousQuery = (String) query.getClass().getMethod("getText").invoke(query);
        var phase = new AtomicBoolean();
        Supplier<List<ProgramNodePalette.Preset>> supplier = () -> List.of(
                new ProgramNodePalette.Preset(numberValue(0), Component.literal(phase.get() ? "cache-probe-B0" : "cache-probe-A0")),
                new ProgramNodePalette.Preset(numberValue(phase.get() ? 7 : 3), Component.literal(phase.get() ? "cache-probe-B7" : "cache-probe-A3")));
        try {
            setField("paletteLayer", ProgramPaletteCatalog.Layer.COMPATIBILITY);
            query.getClass().getMethod("setText", String.class).invoke(query, id.toString());
            call("nodes"); call("paletteCards"); call("firstDiagnostic");
            long revision = ProgramNodePalette.registrationRevision();
            ProgramNodePalette.register(id, supplier);
            check(ProgramNodePalette.registrationRevision() > revision, "provider registration revision did not advance");
            check(presetValues(id).equals(Set.of(0.0, 3.0)), "registered presets were hidden behind the warmed palette cache");
            check(nodeLabel(7).equals("cache-probe-A0"), "provider registration did not refresh an existing node label");
            phase.set(true);
            check(presetValues(id).equals(Set.of(0.0, 7.0)), "dynamic supplier changes were cached despite unchanged query");
            check(nodeLabel(7).equals("cache-probe-B0"), "dynamic supplier label changes were cached");
            result.put("provider_registration_revision_and_live_supplier_bypass", true);
        } finally {
            var providersField = ProgramNodePalette.class.getDeclaredField("PROVIDERS"); providersField.setAccessible(true);
            var providers = (Map<Identifier, Supplier<List<ProgramNodePalette.Preset>>>) providersField.get(null);
            check(providers.remove(id, supplier), "fixture could not remove only its own exact provider");
            setField("paletteLayer", previousLayer);
            query.getClass().getMethod("setText", String.class).invoke(query, previousQuery);
            // Public provider removal is deliberately unsupported. Reinstalling the unchanged document
            // invalidates this screen after fixture-only exact-supplier cleanup.
            call("setProgram", new Class<?>[]{AbilityProgram.class, boolean.class}, expected, false);
        }
        assertUnchanged();
        extensionSnapshotChecks();
    }

    @SuppressWarnings("unchecked")
    private static Set<Double> presetValues(Identifier id) throws Exception {
        var cards = (List<ProgramPaletteCatalog.Card>) call("paletteCards");
        var values = new HashSet<Double>();
        for (var card : cards) for (var entry : card.members()) if (entry.id().equals(id)) {
            values.add(entry.defaultConfiguration().getAsJsonObject().get("value").getAsDouble());
        }
        return Set.copyOf(values);
    }

    private static String nodeLabel(int id) throws Exception {
        var view = call("node", new Class<?>[]{int.class}, id);
        return ((Component) call("nodeLabel", new Class<?>[]{org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog.Entry.class},
                property(view, "entry"))).getString();
    }

    @SuppressWarnings("unchecked")
    private static void extensionSnapshotChecks() throws Exception {
        var id = Identifier.parse("academy:validation/precision_cache_probe");
        var field = ProgramNodeExtensionIndex.class.getDeclaredField("frozenSnapshots"); field.setAccessible(true);
        var original = (Map<Identifier, ProgramNodeExtensionIndex.Snapshot>) field.get(null);
        check(original != null, "extension set was not frozen normally");
        var extension = new ProgramNodeExtension<Double>() {
            @Override public Codec<Double> configurationCodec() { return Codec.DOUBLE.fieldOf("value").codec(); }
            @Override public int schemaVersion() { return 1; }
            @Override public ProgramNodeRole role() { return ProgramNodeRole.VALUE; }
            @Override public ProgramNodePurity purity() { return ProgramNodePurity.PURE; }
            @Override public ProgramNodeScope scope() { return ProgramNodeScope.category(definition.category()); }
            @Override public ProgramNodeSchema schema(Double value) { return new ProgramNodeSchema(List.of(),
                    List.of(ProgramPortDefinition.output("value", ProgramValueTypes.FLOAT))); }
            @Override public ProgramNodeExecution<Double> execution() { return (_, value, _) ->
                    ProgramNodeStep.data(Map.of("value", new ProgramValue<>(ProgramValueTypes.FLOAT, value))); }
            @Override public ProgramNodeEditorMetadata editorMetadata() { return new ProgramNodeEditorMetadata(numberValue(1),
                    ProgramNodeEditorMetadata.Group.VALUE, "screen.academy.program.node.value.scalar", "screen.academy.program.port."); }
        };
        var candidates = new LinkedHashMap<Identifier, ProgramNodeType<?>>();
        for (var registration : ProgramNodeExtensionIndex.snapshot(definition.category()).registrations()) {
            candidates.put(registration.id(), registration.extension());
        }
        check(!candidates.containsKey(id), "fixture extension ID is already registered");
        candidates.put(id, extension);
        var build = ProgramNodeExtensionIndex.class.getDeclaredMethod("build", Identifier.class, Map.class); build.setAccessible(true);
        var injected = (ProgramNodeExtensionIndex.Snapshot) build.invoke(null, definition.category(), candidates);
        var replacement = new HashMap<>(original); replacement.put(definition.category(), injected);
        try {
            check(!paletteHas(id), "fixture extension already visible before snapshot replacement");
            field.set(null, Map.copyOf(replacement));
            check(paletteHas(id), "extension snapshot identity change did not refresh the palette");
            call("nodes"); call("firstDiagnostic");
            result.put("extension_snapshot_identity_invalidation", true);
            result.put("extension_injection_scope", "Late isolated fixture reflection replaces one already-validated snapshot temporarily; not a supported post-freeze NeoForge registry operation");
        } finally { field.set(null, original); }
        check(!paletteHas(id), "restoring the original extension snapshot retained the test extension");
        assertUnchanged();
        result.put("fixture_provider_and_extension_state_restored", true);
    }

    @SuppressWarnings("unchecked")
    private static boolean paletteHas(Identifier id) throws Exception {
        return ((List<org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog.Entry>) call("paletteEntries"))
                .stream().anyMatch(entry -> entry.id().equals(id));
    }

    private static JsonObject numberValue(double number) { var value = new JsonObject(); value.addProperty("value", number); return value; }

    private static void requestScreenshot(Minecraft client, String suffix) throws Exception {
        check(!screenshotInFlight, "screenshot request overlap");
        var name = "precision-performance-" + STAGE + "-" + initialLanguage + "-" + suffix + ".png";
        check(!Files.exists(isolated(client).resolve("screenshots").resolve(name)), "fresh screenshot path required: " + name);
        screenshotInFlight = true; screenshotRequest = name;
    }

    private static void nextScene(Minecraft client) throws Exception {
        assertUnchanged(); RESULTS.add(result); scene++;
        open(client, scene == 3 ? PrecisionPerformanceGraphs.legacyTenGraph()
                : PrecisionPerformanceGraphs.editorGraph(scene == 1 ? 32 : 128));
    }

    private static void assertUnchanged() throws Exception {
        var current = program();
        check(Arrays.equals(expectedBytes, ProgramBookCodec.encodeProgram(current)) && expected.equals(session.draft),
                "graph hash or local draft changed unexpectedly");
        check(definition.compile(current, session.capabilities).valid(), "ordinary graph no longer compiles");
        var decoded = ProgramBookCodec.decodeProgram(expectedBytes);
        check(decoded.valid() && expected.equals(decoded.program()), "graph codec changed the ordinary graph");
        check(session.saveAttempts == 0, "save was attempted");
    }

    private static void finish(Minecraft client) throws Exception {
        check(RESULTS.size() == 4, "not all editor sizes completed");
        write(client, true, null); finished = true;
        AcademyProfiler.stopZoneCapture();
        client.getWindow().setWindowed(originalWidth, originalHeight);
        client.stop();
    }

    private static void fail(Minecraft client, Throwable failure) {
        AcademyCraft.LOGGER.error("PRECISION_PERFORMANCE_FAILED", failure);
        frameWindow = false; AcademyProfiler.stopZoneCapture();
        try { write(client, false, failure); } catch (Exception ignored) { }
        finished = true;
        if (originalWidth > 0) client.getWindow().setWindowed(originalWidth, originalHeight);
        client.stop();
    }

    private static void write(Minecraft client, boolean passed, Throwable failure) throws Exception {
        var report = new LinkedHashMap<String, Object>();
        report.put("passed", passed); report.put("stage", STAGE); report.put("language", initialLanguage);
        report.put("warmup_iterations_per_method", WARMUP); report.put("measured_iterations_per_method", SAMPLES);
        report.put("allocation_counter_available", ALLOCATION != null);
        report.put("render_thread_cpu_counter_available", CPU != null);
        report.put("java", System.getProperty("java.runtime.version")); report.put("vm", System.getProperty("java.vm.name"));
        report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        report.put("processors", Runtime.getRuntime().availableProcessors());
        report.put("heap_max_bytes", Runtime.getRuntime().maxMemory());
        report.put("gc", ManagementFactory.getGarbageCollectorMXBeans().stream().map(value ->
                Map.of("name", value.getName(), "collections", value.getCollectionCount(), "milliseconds", value.getCollectionTime())).toList());
        report.put("compile_benchmark_before_ui_capture", compileBench);
        report.put("editor_cases", RESULTS); report.put("screenshots", SCREENSHOTS);
        report.put("current_scene", scene); report.put("current_phase", Objects.toString(phase));
        report.put("partial_case_on_failure", passed ? null : result);
        report.put("save_attempts", SESSIONS.stream().mapToInt(value -> value.saveAttempts).sum());
        report.put("world_mutation_or_runtime_execution", false); report.put("visual_review_required", true);
        report.put("measurement_limits", List.of("Reflection microbenchmarks include cached Method.invoke overhead and do not predict whole-frame time",
                "nanoTime surface/event timings are wall elapsed and may include waits inside their scopes; ThreadMXBean render-event CPU is reported separately when supported",
                "RenderFrame Pre/Post encloses GameRenderer.render only in the local patched Minecraft; acquisition/extract and explicit submit/present/frame limiter are outside, and GPU completion is not measured",
                "Render-event elapsed/allocations exclude Academy editor command generation during GUI extraction. Compare the dedicated surface elapsed/counters for UI optimization; per-event CPU may be coarsely quantized on the host OS",
                "Synthetic editor capabilities are not learned-player or runtime admission acceptance",
                "The historical ten-node topology is reconstructed; pixel review is required before closing the old glyph/background defect",
                "1200 ticks is the default soak; 12000 ticks must be explicitly selected for the independent ten-minute run"));
        report.put("failure", failure == null ? "" : failure.toString());
        Files.writeString(isolated(client).resolve("precision-performance-" + STAGE + "-result.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        AcademyCraft.LOGGER.info("PRECISION_PERFORMANCE_RESULT stage={} passed={} scenes={} screenshots={}",
                STAGE, passed, RESULTS.size(), SCREENSHOTS.size());
    }

    private static Map<String, Object> distribution(List<Long> values) {
        if (values.isEmpty()) return Map.of("samples", 0, "available", false);
        long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        double sum = 0; for (long value : sorted) sum += value;
        return Map.of("samples", sorted.length, "available", true, "p50", percentile(sorted, .50),
                "p95", percentile(sorted, .95), "p99", percentile(sorted, .99), "min", sorted[0],
                "max", sorted[sorted.length - 1], "mean", sum / sorted.length);
    }

    private static long percentile(long[] sorted, double percentile) {
        return sorted[Math.max(0, (int) Math.ceil(sorted.length * percentile) - 1)];
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        try {
            if (!(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean value)
                    || !value.isThreadAllocatedMemorySupported()) return null;
            if (!value.isThreadAllocatedMemoryEnabled()) value.setThreadAllocatedMemoryEnabled(true);
            return value;
        } catch (RuntimeException unavailable) { return null; }
    }
    private static java.lang.management.ThreadMXBean cpuBean() {
        try {
            var value = ManagementFactory.getThreadMXBean();
            if (!value.isCurrentThreadCpuTimeSupported()) return null;
            if (!value.isThreadCpuTimeEnabled()) value.setThreadCpuTimeEnabled(true);
            return value;
        } catch (RuntimeException unavailable) { return null; }
    }
    private static long allocated() { return ALLOCATION == null ? -1 : ALLOCATION.getThreadAllocatedBytes(Thread.currentThread().threadId()); }
    private static long cpuTime() { return CPU == null ? -1 : CPU.getCurrentThreadCpuTime(); }
    private static void transition(Phase value) { phase = value; phaseTicks = 0; }
    private static Path isolated(Minecraft client) {
        var path = client.gameDirectory.toPath().toAbsolutePath().normalize();
        check(path.getParent() != null && path.getParent().getFileName().toString().equals("run")
                && path.getFileName().toString().startsWith("native-sublevel-direct-smoke"), "isolated run directory required");
        return path;
    }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static int number(String name) throws Exception { return ((Number) field(name)).intValue(); }
    private static AbilityProgram program() throws Exception { return ((ProgramEditorDocument) field("document")).program(); }
    private static Object field(String name) throws Exception { var field = ModularProgramScreen.class.getDeclaredField(name); field.setAccessible(true); return field.get(screen); }
    private static Object property(Object value, String name) throws Exception {
        var getter = value.getClass().getDeclaredMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
        getter.setAccessible(true); return getter.invoke(value);
    }
    private static void setField(String name, Object value) throws Exception { var field = ModularProgramScreen.class.getDeclaredField(name); field.setAccessible(true); field.set(screen, value); }
    private static Method method(String name, Class<?>[] types) throws Exception {
        var key = name + Arrays.toString(types);
        var method = METHODS.get(key);
        if (method == null) { method = ModularProgramScreen.class.getDeclaredMethod(name, types); method.setAccessible(true); METHODS.put(key, method); }
        return method;
    }
    private static Object call(String name) throws Exception { return call(name, new Class<?>[0]); }
    private static Object call(String name, Class<?>[] types, Object... values) throws Exception {
        try { return method(name, types).invoke(screen, values); }
        catch (InvocationTargetException wrapper) {
            if (wrapper.getCause() instanceof Exception exception) throw exception;
            if (wrapper.getCause() instanceof Error error) throw error;
            throw wrapper;
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private enum Phase { SETTLE, WARMUP, MEASURE, FRAMES_OFF, FRAMES_ON, CAPTURE, RESOURCE_WAIT, LANGUAGE_WAIT, RESTORE_WAIT, RESTORE_CAPTURE, LEGACY }
    private static final class Samples {
        private final List<Long> nanos = new ArrayList<>(), bytes = new ArrayList<>();
        private Map<String, Object> result() { return Map.of("nanoseconds", distribution(nanos), "allocated_bytes", distribution(bytes)); }
    }
    private static final class MemorySession implements ModularProgramEditorSession {
        private AbilityProgram draft;
        private final Set<Identifier> capabilities;
        private int saveAttempts;
        private MemorySession(AbilityProgram value, Set<Identifier> capabilities) { draft = value; this.capabilities = capabilities; }
        @Override public Component title() { return Component.literal("Precision C " + STAGE + " / " + draft.graph().nodes().size()); }
        @Override public int slotCount() { return 1; }
        @Override public int selectedSlot() { return 0; }
        @Override public long revision() { return 0; }
        @Override public AbilityProgram editableProgram(int slot) { return draft; }
        @Override public AbilityProgram emptyProgram(int slot) { return draft; }
        @Override public AbilityProgram restoredProgram(int slot) { return null; }
        @Override public Set<Identifier> capabilities() { return capabilities; }
        @Override public void updateLocalProgram(int slot, AbilityProgram value) { draft = value; }
        @Override public void selectSlot(int slot) { check(slot == 0, "unexpected slot selection"); }
        @Override public void saveProgram(int slot, AbilityProgram value, long revision) { saveAttempts++; throw new IllegalStateException("save forbidden in performance fixture"); }
        @Override public void closed(ModularProgramScreen value) {}
        @Override public boolean showStarter() { return false; }
    }
}
