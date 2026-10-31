package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.data.AbilityData;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.ability.mentalout.ModularProgramScreen;
import org.academy.internal.client.ability.program.AbilityProgramEditorClient;
import org.academy.internal.client.ability.program.ProgramRunStatusSelection;
import org.academy.internal.client.ability.program.ProgramTaskTemplates;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.program.AbilityProgramManager;
import org.academy.internal.common.ability.program.ProgramNetworkMetrics;
import org.academy.internal.common.ability.program.ProgramRunTrace;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.editor.ProgramEditorDocument;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Disposable integrated-world, real client/server subscription and editable-preview acceptance. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class PrecisionQueryPlaySmoke {
    private PrecisionQueryPlaySmoke() {}
    private static final boolean ENABLED = Boolean.getBoolean("academy.precisionQueryPlaySmoke");
    private static final Identifier CATEGORY = AcademyCraft.academy("accelerator");
    private static final List<String> CHECKS = new ArrayList<>(), SCREENSHOTS = new ArrayList<>();
    private static final Map<String, Object> OBSERVATIONS = new LinkedHashMap<>();
    private static int phase, ticks, phaseTicks;
    private static boolean finished;
    private static CompletableFuture<?> pending;
    private static String language;
    private static long revision;
    private static AbilityProgram manual, template;
    private static ModularProgramScreen screen;
    private static List<ProgramRunTrace.Step> completedTrace;
    private static volatile String screenshotRequested;
    private static volatile boolean screenshotDone;
    private static volatile Throwable screenshotFailure;
    private static int screenshotFrames;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
        try {
            isolated(client); check(++ticks < 2200, "query/play smoke deadline at phase " + phase);
            phaseTicks++;
            if (screenshotFailure != null) throw new IllegalStateException(screenshotFailure);
            switch (phase) {
                case 0 -> {
                    language = client.getLanguageManager().getSelected();
                    check(Set.of("zh_cn", "en_us").contains(language), "expected externally configured zh_cn/en_us");
                    client.options.pauseOnLostFocus = false; client.gui.setScreen(null);
                    client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                    client.gui.toastManager().clear();
                    manual = manualProgram();
                    pending = server(player -> {
                        var system = AbilitySystemServer.getSystem(player);
                        system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.ACCELERATOR.get());
                        system.setPlayerLevel(player.getUUID(), 5); system.setPlayerBaseMaxCP(player.getUUID(), 100_000);
                        system.setPlayerAvailableCP(player.getUUID(), 100_000); system.setPlayerStatus(player.getUUID(), AbilityData.Status.NORMAL);
                        var skill = Skills.KINETIC_THROW.get(); system.addPlayerSkill(player, skill.getKeyString());
                        if (!skill.isEnabled(player)) system.toggleSkill(player.getUUID(), skill.getKeyString());
                        var book = ProgramBook.empty(10).replaceSlot(0, manual);
                        system.getPlayerData(player.getUUID()).setAbilityProgramBook(CATEGORY.toString(),
                                Base64.getEncoder().encodeToString(ProgramBookCodec.encode(book)));
                        return book.revision();
                    });
                    next();
                }
                case 1 -> {
                    if (!pending.isDone() || phaseTicks < 35) return;
                    revision = (long) pending.join(); ProgramNetworkMetrics.resetCapture(true);
                    ProgramNetworkMetrics.sendToServer(new AbilityProgramManager.RequestPacket(CATEGORY)); next();
                }
                case 2 -> {
                    if (saved(client) == null || saved(client).revision() != revision) return;
                    check(saved(client).slot(0).program().equals(manual), "request lost the authored WAIT100 graph");
                    CHECKS.add("Real request synchronized the saved manual WAIT100 -> STOP program");
                    AbilityProgramEditorClient.openEditor(); next();
                }
                case 3 -> {
                    if (!(client.gui.screen() instanceof ModularProgramScreen current) || phaseTicks < 20) return;
                    screen = current; selectSlot(1);
                    check((boolean) call("openTaskTemplates"), "empty slot must offer editable task choices");
                    check(tasks().contains(ProgramTaskTemplates.Kind.ACCELERATOR_GUIDE_OWNED_NEARBY), "advanced learned template is inaccessible");
                    requestScreenshot("templates"); next();
                }
                case 4 -> {
                    if (!screenshotDone) return;
                    var bounds = call("starterBounds"); int row = tasks().indexOf(ProgramTaskTemplates.Kind.ACCELERATOR_GUIDE_OWNED_NEARBY);
                    check((boolean) call("handleStarterClick", new Class<?>[]{double.class, double.class},
                            rect(bounds, "x") + rect(bounds, "width") / 2.0, rect(bounds, "y") + 25 + row * 19 + 8.0),
                            "real task picker did not install the selected template");
                    template = document().program();
                    check(template.graph().nodes().stream().anyMatch(node -> node.type().equals(CommonProgramNodeIds.SAMPLE_VALUE)),
                            "advanced task did not contain the explicit sample node");
                    check(document().validation().valid(), "selected advanced task is not an editable valid graph");
                    CHECKS.add("Real task choices expanded a learned advanced template into an editable graph with a sample node");
                    inspectorTab(1); call("taskPreviewLines"); requestScreenshot("preview"); next();
                }
                case 5 -> {
                    if (!screenshotDone) return;
                    scrollPreviewToSample(); requestScreenshot("preview-sample"); next();
                }
                case 6 -> {
                    if (!screenshotDone) return;
                    check(document().program().equals(template), "reading/scanning the preview changed the draft");
                    CHECKS.add("Structured preview and real scrolling preserved the draft and exposed the sample node explanation");
                    selectSlot(0); inspectorTab(2);
                    check(!screen.isPauseScreen(), "run monitor pauses its integrated server");
                    check(document().program().equals(manual), "run monitor must show the saved manual graph"); next();
                }
                case 7 -> {
                    var status = status();
                    if (status == null || status.phase() != ProgramRunStatus.Phase.IDLE) return;
                    check(status.target().programId().equals(manual.id()) && status.target().bookRevision() == revision,
                            "subscription addressed a draft or stale book");
                    OBSERVATIONS.put("idle", status); CHECKS.add("Run tab subscribed through real packets to the saved revision and received IDLE");
                    ProgramNetworkMetrics.sendToServer(new AbilityProgramManager.ExecutePacket(CATEGORY, 0, 1)); next();
                }
                case 8 -> {
                    var status = status(); if (status == null || status.phase() != ProgramRunStatus.Phase.WAITING) return;
                    check(status.waitKind() == ProgramRunStatus.WaitKind.WAIT && status.sourceNodeId() == 2
                                    && CommonProgramNodeIds.WAIT.equals(status.nodeType()) && status.nodeId() == 3,
                            "WAIT source must be node2 while its continuation is node3: " + status);
                    check(status.wakeAtLogicalTick() > status.logicalTick() && status.wakeAtLogicalTick() - status.logicalTick() <= 100,
                            "invalid WAIT logical time remaining");
                    OBSERVATIONS.put("waiting", status); CHECKS.add("Real WAIT status identifies its waiting source node and logical wake time");
                    requestScreenshot("wait"); next();
                }
                case 9 -> {
                    if (!screenshotDone) return;
                    var status = status(); if (status == null || status.phase() != ProgramRunStatus.Phase.COMPLETED) return;
                    check(status.terminal() && status.waitKind() == ProgramRunStatus.WaitKind.NONE && !status.rollbackIncomplete(),
                            "settled completion status was inconsistent");
                    OBSERVATIONS.put("completed", status); completedTrace = traces(client).get(0);
                    check(completedTrace != null && completedTrace.stream().anyMatch(step -> step.nodeId() == 3), "terminal execution trace did not reach STOP");
                    CHECKS.add("Natural integrated server ticks completed WAIT and the real client received settled COMPLETED");
                    requestScreenshot("completed"); next();
                }
                case 10 -> {
                    if (!screenshotDone) return;
                    OBSERVATIONS.put("open_editor_network", ProgramNetworkMetrics.snapshot());
                    client.gui.setScreen(null); next();
                }
                case 11 -> {
                    if (phaseTicks < 12) return;
                    check(field(null, AbilityProgramEditorClient.class, "activeSession") == null, "closing the editor left a live client subscription");
                    ProgramNetworkMetrics.resetCapture(true);
                    ProgramNetworkMetrics.sendToServer(new AbilityProgramManager.ExecutePacket(CATEGORY, 0, 2)); next();
                }
                case 12 -> {
                    if (phaseTicks < 140) return;
                    var trace = traces(client).get(0);
                    check(trace != null && trace != completedTrace && trace.stream().anyMatch(step -> step.nodeId() == 3),
                            "unsubscribed real execution did not still complete through the old trace path");
                    var metrics = ProgramNetworkMetrics.snapshot();
                    check(calls(metrics, "run_status", "send") == 0 && calls(metrics, "run_status", "decode") == 0,
                            "closed editor still received/sent new status packets");
                    OBSERVATIONS.put("closed_editor_network", metrics);
                    CHECKS.add("Closing unsubscribed; a second WAIT execution completed with ordinary traces and zero new status sends/decodes");
                    finish(client, null);
                }
                default -> throw new IllegalStateException("unexpected phase");
            }
        } catch (Throwable failure) { finish(client, failure); }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (!ENABLED || screenshotRequested == null) return;
        var client = Minecraft.getInstance(); if (client.gui.overlay() != null) return;
        // Fixture-only: allow a complete clean frame after removing tutorial/advancement toasts.
        if (screenshotFrames++ == 0) { client.gui.toastManager().clear(); return; }
        String name = screenshotRequested; screenshotRequested = null;
        Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1, message -> {
            try {
                check(Files.size(isolated(client).resolve("screenshots").resolve(name)) > 0, message.getString());
                SCREENSHOTS.add("screenshots/" + name); screenshotDone = true;
            } catch (Throwable failure) { screenshotFailure = failure; }
        });
    }

    private static void requestScreenshot(String name) {
        Minecraft.getInstance().gui.toastManager().clear();
        screenshotFrames = 0; screenshotDone = false;
        screenshotRequested = "precision-query-play-" + language + "-" + name + ".png";
    }
    private static void inspectorTab(int index) throws Exception {
        if (!(boolean) call("inspectorVisible")) call("handleRailClick", new Class<?>[]{double.class, double.class},
                number("rightX") + 8.0, number("canvasY") + 8.0);
        int width = (numberCall("inspectorWidth") - 8) / 3;
        check((boolean) call("handleInspectorClick", new Class<?>[]{double.class, double.class},
                numberCall("inspectorX") + 4.0 + width * index + width / 2.0, number("canvasY") + 8.0), "inspector tab was unreachable");
        check((boolean) screenField(index == 1 ? "showTaskPreview" : "showRunTrace"), "wrong inspector tab selected");
    }
    private static void selectSlot(int selected) throws Exception {
        int start = number("panelX") + 4 + (number("panelW") >= 620 ? 96 : 0);
        int width = (int) call("slotTabWidth", new Class<?>[]{int.class}, start);
        check((boolean) call("handleTopBarClick", new Class<?>[]{double.class, double.class},
                start + (width + 2.0) * selected + width / 2.0, number("panelY") + 8.0), "slot tab was unreachable");
        check(number("slot") == selected, "wrong slot was selected");
    }
    private static void scrollPreviewToSample() throws Exception {
        var sample = template.graph().nodes().stream().filter(node -> node.type().equals(CommonProgramNodeIds.SAMPLE_VALUE)).findFirst().orElseThrow();
        var lines = (List<?>) call("taskPreviewLines"); int target = -1;
        for (int index = 0; index < lines.size(); index++) {
            var method = lines.get(index).getClass().getDeclaredMethod("getText"); method.setAccessible(true);
            if (((String) method.invoke(lines.get(index))).startsWith("#" + sample.id() + " ")) { target = index; break; }
        }
        check(target >= 0, "preview did not explain the sample node");
        for (int step = 0; step < 200 && number("previewScroll") < target - 2; step++) {
            int previous = number("previewScroll");
            check(screen.mouseScrolled(numberCall("inspectorX") + 15.0, number("canvasY") + 80.0, 0, -1), "preview scroll was rejected");
            if (number("previewScroll") == previous) break;
        }
        int rows = Math.max(1, (number("canvasH") - 30) / 10);
        check(target >= number("previewScroll") && target < number("previewScroll") + rows, "sample explanation did not become visible");
    }
    private static AbilityProgram manualProgram() {
        var scalar = new JsonObject(); scalar.addProperty("type", "integer"); scalar.addProperty("value", "100");
        return new AbilityProgram(1, UUID.nameUUIDFromBytes("precision-d-query-play-wait".getBytes(StandardCharsets.UTF_8)),
                "Observe WAIT 100", CATEGORY, new ProgramGraph(List.of(
                new ProgramGraph.Node(0, BaseAbilityProgramDefinition.entryId(CATEGORY), 1, new JsonObject()),
                new ProgramGraph.Node(1, CommonProgramNodeIds.SCALAR_CONSTANT, 1, scalar),
                new ProgramGraph.Node(2, CommonProgramNodeIds.WAIT, 1, new JsonObject()),
                new ProgramGraph.Node(3, CommonProgramNodeIds.STOP, 1, new JsonObject())
        ), List.of(edge(0, "flow", 2, "flow"), edge(1, "value", 2, "ticks"), edge(2, "flow", 3, "flow"))),
                new ProgramEditorLayout(Map.of(0, new ProgramEditorLayout.NodePosition(40, 80),
                        1, new ProgramEditorLayout.NodePosition(40, 250), 2, new ProgramEditorLayout.NodePosition(330, 80),
                        3, new ProgramEditorLayout.NodePosition(620, 80))));
    }
    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }
    private static ProgramRunStatus status() throws Exception {
        var session = field(null, AbilityProgramEditorClient.class, "activeSession");
        if (session == null) return null;
        var selection = (ProgramRunStatusSelection) field(session, session.getClass(), "observation");
        return selection == null ? null : selection.current();
    }
    private static Object state(Minecraft client) throws Exception {
        return ((Map<?, ?>) field(null, AbilityProgramEditorClient.class, "STATES")).get(client.player.getUUID() + "|" + CATEGORY);
    }
    private static ProgramBook saved(Minecraft client) throws Exception {
        var state = state(client); return state == null ? null : (ProgramBook) field(state, state.getClass(), "saved");
    }
    @SuppressWarnings("unchecked") private static Map<Integer, List<ProgramRunTrace.Step>> traces(Minecraft client) throws Exception {
        var state = state(client); return (Map<Integer, List<ProgramRunTrace.Step>>) field(state, state.getClass(), "runTraces");
    }
    @SuppressWarnings("unchecked") private static List<ProgramTaskTemplates.Kind> tasks() throws Exception { return (List<ProgramTaskTemplates.Kind>) call("starterTasks"); }
    private static ProgramEditorDocument document() throws Exception { return (ProgramEditorDocument) screenField("document"); }
    private static Object screenField(String name) throws Exception { return field(screen, ModularProgramScreen.class, name); }
    private static int number(String name) throws Exception { return ((Number) screenField(name)).intValue(); }
    private static int numberCall(String name) throws Exception { return ((Number) call(name)).intValue(); }
    private static Object field(Object owner, Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static int rect(Object bounds, String name) throws Exception {
        var method = bounds.getClass().getDeclaredMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
        method.setAccessible(true); return (int) method.invoke(bounds);
    }
    private static Object call(String name) throws Exception { return call(name, new Class<?>[0]); }
    private static Object call(String name, Class<?>[] types, Object... args) throws Exception {
        var method = ModularProgramScreen.class.getDeclaredMethod(name, types); method.setAccessible(true);
        try { return method.invoke(screen, args); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }
    @SuppressWarnings("unchecked") private static long calls(Map<String, Object> metrics, String kind, String phase) {
        return ((List<Map<String, Object>>) metrics.get("operations")).stream()
                .filter(row -> kind.equals(row.get("kind")) && phase.equals(row.get("phase")))
                .mapToLong(row -> ((Number) row.get("calls")).longValue()).sum();
    }
    private static <T> CompletableFuture<T> server(Function<ServerPlayer, T> action) {
        var client = Minecraft.getInstance(); var id = client.player.getUUID(); var server = client.getSingleplayerServer();
        var result = new CompletableFuture<T>();
        server.execute(() -> {
            try { result.complete(action.apply(Objects.requireNonNull(server.getPlayerList().getPlayer(id)))); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result;
    }
    private static Path isolated(Minecraft client) {
        var directory = client.gameDirectory.toPath().toAbsolutePath().normalize();
        check(directory.getParent().getFileName().toString().equals("run")
                && directory.getFileName().toString().startsWith("native-sublevel-direct-smoke-precision-query-play-"),
                "a uniquely named disposable query/play copied world is required");
        return directory;
    }
    private static void next() { phase++; phaseTicks = 0; }
    private static void finish(Minecraft client, Throwable failure) {
        finished = true;
        try {
            var report = new LinkedHashMap<String, Object>();
            report.put("passed", failure == null); report.put("language", language); report.put("phase", phase); report.put("client_ticks", ticks);
            report.put("checks", CHECKS); report.put("screenshots", SCREENSHOTS); report.put("observations", OBSERVATIONS);
            report.put("scope", List.of("One real integrated client/server, saved revision and actual Misaka request/execute/observe/status handlers",
                    "Real template picker, editable sample graph, static preview and run monitor; no gameplay action executes in preview",
                    "Integrated local transport, not TCP/WAN or multiplayer throughput; source world remains untouched",
                    "Old-token/revision/dimension rejection is covered separately by unit tests, not simulated here"));
            report.put("error", failure == null ? "" : failure.toString());
            Files.writeString(isolated(client).resolve("precision-query-play-" + language + "-result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        } catch (Throwable writeFailure) { AcademyCraft.LOGGER.error("PRECISION_QUERY_PLAY_REPORT_FAILED", writeFailure); }
        ProgramNetworkMetrics.resetCapture(false);
        if (failure != null) AcademyCraft.LOGGER.error("PRECISION_QUERY_PLAY_FAILED", failure);
        else AcademyCraft.LOGGER.info("PRECISION_QUERY_PLAY_PASSED language={}", language);
        client.stop();
    }
    private static void check(boolean value, String detail) { if (!value) throw new IllegalStateException(detail); }
}
