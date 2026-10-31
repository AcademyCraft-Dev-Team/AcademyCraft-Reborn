package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import org.academy.api.client.gui.environment.UiEnvironment;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.data.AbilityData;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.ability.mentalout.ModularProgramScreen;
import org.academy.internal.client.ability.program.AbilityProgramEditorClient;
import org.academy.internal.client.ability.program.ProgramClipboardCodec;
import org.academy.internal.client.ability.program.ProgramUpgradeBackup;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.program.AbilityProgramManager;
import org.academy.internal.common.ability.program.ProgramNetworkMetrics;
import org.academy.internal.common.ability.program.ProgramRunTrace;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.editor.ProgramEditorDocument;
import org.academy.internal.common.ability.program.migrate.ProgramNumericUpgrades;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Explicit upgrade UI and actual saved-program execution; only runs in a disposable copied world. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class PrecisionNumericUpgradeSmoke {
    private PrecisionNumericUpgradeSmoke() {}
    private static final boolean ENABLED = Boolean.getBoolean("academy.precisionNumericUpgradeSmoke");
    private static final Identifier CATEGORY = AcademyCraft.academy("accelerator");
    private static final List<String> CHECKS = new ArrayList<>(), SCREENSHOTS = new ArrayList<>();
    private static final Map<String, Object> EVIDENCE = new LinkedHashMap<>();
    private static int phase, ticks, phaseTicks, screenshotFrames;
    private static boolean finished;
    private static String language;
    private static long revision;
    private static CompletableFuture<?> pending;
    private static AbilityProgram original, candidate;
    private static ModularProgramScreen screen;
    private static ProgramUpgradeBackup.Receipt backup;
    private static List<ProgramRunTrace.Step> originalTrace;
    private static volatile String screenshotRequested;
    private static volatile boolean screenshotDone;
    private static volatile Throwable screenshotFailure;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
        try {
            isolated(client); check(++ticks < 2000, "numeric upgrade smoke deadline at phase " + phase); phaseTicks++;
            if (screenshotFailure != null) throw new IllegalStateException(screenshotFailure);
            switch (phase) {
                case 0 -> {
                    language = client.getLanguageManager().getSelected();
                    check(Set.of("zh_cn", "en_us").contains(language), "externally configure zh_cn or en_us");
                    client.options.pauseOnLostFocus = false; client.gui.setScreen(null);
                    client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); client.gui.toastManager().clear();
                    original = originalProgram();
                    pending = server(player -> {
                        var system = AbilitySystemServer.getSystem(player);
                        system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.ACCELERATOR.get()); system.setPlayerLevel(player.getUUID(), 5);
                        system.setPlayerBaseMaxCP(player.getUUID(), 100_000); system.setPlayerAvailableCP(player.getUUID(), 100_000);
                        system.setPlayerStatus(player.getUUID(), AbilityData.Status.NORMAL);
                        var book = ProgramBook.empty(10).replaceSlot(0, original);
                        system.getPlayerData(player.getUUID()).setAbilityProgramBook(CATEGORY.toString(), Base64.getEncoder().encodeToString(ProgramBookCodec.encode(book)));
                        return book.revision();
                    }); next();
                }
                case 1 -> {
                    if (!pending.isDone() || phaseTicks < 35) return;
                    revision = (long) pending.join(); ProgramNetworkMetrics.resetCapture(true);
                    ProgramNetworkMetrics.sendToServer(new AbilityProgramManager.RequestPacket(CATEGORY)); next();
                }
                case 2 -> {
                    if (saved(client) == null || saved(client).revision() != revision) return;
                    same(original, saved(client).slot(0).program(), "request automatically changed the legacy graph");
                    ProgramNetworkMetrics.sendToServer(new AbilityProgramManager.ExecutePacket(CATEGORY, 0, 1)); next();
                }
                case 3 -> {
                    var trace = traces(client).get(0); if (!answer18(trace)) return;
                    originalTrace = trace; EVIDENCE.put("legacy_trace", trace);
                    CHECKS.add("Requested saved legacy graph remained unchanged and executed (6+3)*(6/3)=18 through real handlers");
                    AbilityProgramEditorClient.openEditor(); next();
                }
                case 4 -> {
                    if (!(client.gui.screen() instanceof ModularProgramScreen opened) || phaseTicks < 20) return;
                    screen = opened; same(original, document().program(), "opening the editor changed the old graph");
                    tool(11); var plan = plan();
                    check(plan != null && plan.canApply() && plan.changes().size() == 2 && plan.retained().size() == 1,
                            "preview must distinguish two upgradeable nodes from retained integer division");
                    same(original, document().program(), "preview mutated the draft");
                    candidate = plan.candidate(); EVIDENCE.put("change_nodes", plan.changes().stream().map(ProgramNumericUpgrades.Change::nodeId).toList());
                    EVIDENCE.put("retained", plan.retained()); screenshot("preview"); next();
                }
                case 5 -> {
                    if (!screenshotDone) return;
                    clickUpgrade("numericUpgradeKeepBounds"); check(plan() == null, "keep-original action left the modal open");
                    same(original, document().program(), "keeping the old graph changed its bytes");
                    check(history("undo") == 0, "opening/keeping a preview created undo history");
                    tool(11); check(plan() != null, "second explicit preview was unavailable"); clickUpgrade("numericUpgradeApplyBounds");
                    check(plan() == null, "apply did not close its modal"); same(candidate, document().program(), "apply did not install the reviewed candidate");
                    check(history("undo") == 1 && document().validation().valid(), "upgrade was not one valid editable history operation");
                    backup = (ProgramUpgradeBackup.Receipt) screenField("numericUpgradeLastBackup");
                    check(backup != null && Files.isRegularFile(backup.path()) && backup.path().toRealPath().startsWith(isolated(client)), "local backup was not written inside the isolated game directory");
                    var json = JsonParser.parseString(Files.readString(backup.path())).getAsJsonObject();
                    same(original, ProgramClipboardCodec.decodeProgram(json.get("original_clipboard").getAsString(), CATEGORY), "backup clipboard did not preserve the old graph");
                    check(Arrays.equals(ProgramBookCodec.encodeProgram(original), Base64.getDecoder().decode(json.get("original_codec_base64").getAsString())), "backup codec bytes differ from the original");
                    same(original, saved(client).slot(0).program(), "apply silently saved the server graph");
                    var metrics = ProgramNetworkMetrics.snapshot();
                    check(calls(metrics, "save", "send") == 0 && calls(metrics, "execute", "send") == 1,
                            "preview or draft application sent an unexpected save/execute packet");
                    EVIDENCE.put("preview_apply_network", metrics); EVIDENCE.put("backup", isolated(client).relativize(backup.path()).toString());
                    CHECKS.add("Preview/keep/apply changed only the reviewed draft after creating a recoverable local backup; saved graph and execution count stayed unchanged");
                    tool(3); same(original, document().program(), "one undo did not restore the complete legacy graph");
                    tool(4); same(candidate, document().program(), "one redo did not restore the candidate");
                    CHECKS.add("One ordinary undo and redo preserved full graph, configuration, ports and layout");
                    screenshot("applied"); next();
                }
                case 6 -> {
                    if (!screenshotDone) return;
                    tool(9); client.gui.setScreen(null); next();
                }
                case 7 -> {
                    if (saved(client).revision() != revision + 1) return;
                    revision++; same(candidate, saved(client).slot(0).program(), "actual save/sync did not retain the reviewed candidate");
                    ProgramNetworkMetrics.sendToServer(new AbilityProgramManager.ExecutePacket(CATEGORY, 0, 2)); next();
                }
                case 8 -> {
                    var trace = traces(client).get(0); if (trace == originalTrace || !answer18(trace)) return;
                    EVIDENCE.put("upgraded_trace", trace); CHECKS.add("Explicit save advanced the authoritative revision and the upgraded graph still executed to 18");
                    pending = server(player -> {
                        var system = AbilitySystemServer.getSystem(player);
                        return Map.of("available_cp", system.getPlayerAvailableCP(player.getUUID()), "occupied_cp", system.getPlayerOccupiedCP(player.getUUID()));
                    }); next();
                }
                case 9 -> {
                    if (!pending.isDone()) return;
                    EVIDENCE.put("after_numeric_execution_cp", pending.join());
                    AbilityProgramEditorClient.openEditor(); next();
                }
                case 10 -> {
                    if (!(client.gui.screen() instanceof ModularProgramScreen opened) || phaseTicks < 15) return;
                    screen = opened; same(candidate, document().program(), "reopening lost the saved upgraded graph");
                    UiEnvironment.get().setClipboard(backup.originalClipboard()); tool(8);
                    same(original, document().program(), "documented backup clipboard/import recovery did not restore the old graph");
                    CHECKS.add("The documented original_clipboard + normal import restored the complete original draft");
                    screenshot("restored"); next();
                }
                case 11 -> { if (!screenshotDone) return; tool(9); client.gui.setScreen(null); next(); }
                case 12 -> {
                    if (saved(client).revision() != revision + 1) return;
                    revision++; same(original, saved(client).slot(0).program(), "restored old graph did not save/sync normally");
                    pending = server(AbilityProgramManager::bookForAddon); next();
                }
                case 13 -> {
                    if (!pending.isDone()) return;
                    var actual = (ProgramBook) pending.join(); same(original, actual.slot(0).program(), "authoritative restored book differs");
                    check(actual.revision() == revision, "authoritative restored revision changed");
                    CHECKS.add("Restored legacy graph saved through the real revisioned protocol and remained authoritative"); finish(client, null);
                }
                default -> throw new IllegalStateException("unexpected phase");
            }
        } catch (Throwable failure) { finish(client, failure); }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (!ENABLED || screenshotRequested == null) return;
        var client = Minecraft.getInstance(); if (client.gui.overlay() != null) return;
        if (screenshotFrames++ == 0) { client.gui.toastManager().clear(); return; }
        var name = screenshotRequested; screenshotRequested = null;
        Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1, message -> {
            try { check(Files.size(isolated(client).resolve("screenshots").resolve(name)) > 0, message.getString()); SCREENSHOTS.add("screenshots/" + name); screenshotDone = true; }
            catch (Throwable failure) { screenshotFailure = failure; }
        });
    }
    private static void screenshot(String suffix) {
        Minecraft.getInstance().gui.toastManager().clear(); screenshotFrames = 0; screenshotDone = false;
        screenshotRequested = "precision-numeric-upgrade-" + language + "-" + suffix + ".png";
    }
    private static AbilityProgram originalProgram() {
        var variable = new JsonObject(); variable.addProperty("name", "answer"); variable.addProperty("type", ProgramValueTypes.INTEGER.id().toString());
        var nodes = List.of(node(0, BaseAbilityProgramDefinition.entryId(CATEGORY), new JsonObject()), scalar(1, "6"), scalar(2, "3"),
                node(3, CommonProgramNodeIds.INTEGER_ADD, new JsonObject()), node(4, CommonProgramNodeIds.INTEGER_DIVIDE, new JsonObject()),
                node(5, CommonProgramNodeIds.INTEGER_MULTIPLY, new JsonObject()), node(6, CommonProgramNodeIds.VARIABLE_SET, variable), node(7, CommonProgramNodeIds.STOP, new JsonObject()));
        var edges = List.of(edge(0, "flow", 6, "flow"), edge(6, "flow", 7, "flow"), edge(1, "value", 3, "left"), edge(2, "value", 3, "right"),
                edge(1, "value", 4, "left"), edge(2, "value", 4, "right"), edge(3, "result", 5, "left"), edge(4, "result", 5, "right"), edge(5, "result", 6, "value"));
        var positions = new LinkedHashMap<Integer, ProgramEditorLayout.NodePosition>();
        positions.put(0, new ProgramEditorLayout.NodePosition(20, 20)); positions.put(1, new ProgramEditorLayout.NodePosition(20, 160));
        positions.put(2, new ProgramEditorLayout.NodePosition(20, 300)); positions.put(3, new ProgramEditorLayout.NodePosition(250, 100));
        positions.put(4, new ProgramEditorLayout.NodePosition(250, 300)); positions.put(5, new ProgramEditorLayout.NodePosition(480, 180));
        positions.put(6, new ProgramEditorLayout.NodePosition(710, 20)); positions.put(7, new ProgramEditorLayout.NodePosition(940, 20));
        return new AbilityProgram(1, UUID.nameUUIDFromBytes("precision-e-numeric-upgrade".getBytes(StandardCharsets.UTF_8)), "Editable legacy arithmetic", CATEGORY,
                new ProgramGraph(nodes, edges), new ProgramEditorLayout(positions));
    }
    private static ProgramGraph.Node scalar(int id, String value) { var config = new JsonObject(); config.addProperty("type", "integer"); config.addProperty("value", value); return node(id, CommonProgramNodeIds.SCALAR_CONSTANT, config); }
    private static ProgramGraph.Node node(int id, Identifier type, JsonObject config) { return new ProgramGraph.Node(id, type, 1, config); }
    private static ProgramGraph.Edge edge(int from, String output, int to, String input) { return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input)); }
    private static boolean answer18(List<ProgramRunTrace.Step> trace) { return trace != null && trace.stream().anyMatch(step -> step.nodeId() == 6 && step.detail().contains("value=18")) && trace.stream().anyMatch(step -> step.nodeId() == 7); }
    private static void same(AbilityProgram expected, AbilityProgram actual, String reason) { check(actual != null && ProgramUpgradeBackup.sameProgram(expected, actual), reason); }
    private static void tool(int index) throws Exception {
        int count = ((Object[]) screenField("TOOL_LABELS")).length, size = 14;
        check(index < count, "requested toolbar entry is unavailable");
        double x = number("panelX") + number("panelW") - count * (size + 2) - 2 + index * (size + 2) + size / 2.0;
        check((boolean) call("handleTopBarClick", new Class<?>[]{double.class, double.class}, x, number("panelY") + 8.0), "toolbar click was rejected");
    }
    private static void clickUpgrade(String boundsMethod) throws Exception {
        var bounds = call(boundsMethod);
        check((boolean) call("handleNumericUpgradeClick", new Class<?>[]{double.class, double.class},
                rect(bounds, "x") + rect(bounds, "width") / 2.0, rect(bounds, "y") + rect(bounds, "height") / 2.0), "upgrade modal control was rejected");
    }
    private static ProgramNumericUpgrades.Plan plan() throws Exception { return (ProgramNumericUpgrades.Plan) screenField("numericUpgradePreview"); }
    private static ProgramEditorDocument document() throws Exception { return (ProgramEditorDocument) screenField("document"); }
    private static int history(String name) throws Exception { return ((Deque<?>) screenField(name)).size(); }
    private static int number(String name) throws Exception { return ((Number) screenField(name)).intValue(); }
    private static Object screenField(String name) throws Exception { return field(screen, ModularProgramScreen.class, name); }
    private static Object field(Object owner, Class<?> type, String name) throws Exception { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
    private static Object call(String name) throws Exception { return call(name, new Class<?>[0]); }
    private static Object call(String name, Class<?>[] types, Object... args) throws Exception {
        var method = ModularProgramScreen.class.getDeclaredMethod(name, types); method.setAccessible(true);
        try { return method.invoke(screen, args); }
        catch (InvocationTargetException failure) { if (failure.getCause() instanceof Exception exception) throw exception; if (failure.getCause() instanceof Error error) throw error; throw failure; }
    }
    private static int rect(Object bounds, String name) throws Exception { var method = bounds.getClass().getDeclaredMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1)); method.setAccessible(true); return (int) method.invoke(bounds); }
    private static Object state(Minecraft client) throws Exception { return ((Map<?, ?>) field(null, AbilityProgramEditorClient.class, "STATES")).get(client.player.getUUID() + "|" + CATEGORY); }
    private static ProgramBook saved(Minecraft client) throws Exception { var state = state(client); return state == null ? null : (ProgramBook) field(state, state.getClass(), "saved"); }
    @SuppressWarnings("unchecked") private static Map<Integer, List<ProgramRunTrace.Step>> traces(Minecraft client) throws Exception { var state = state(client); return (Map<Integer, List<ProgramRunTrace.Step>>) field(state, state.getClass(), "runTraces"); }
    @SuppressWarnings("unchecked") private static long calls(Map<String, Object> metrics, String kind, String phase) { return ((List<Map<String, Object>>) metrics.get("operations")).stream().filter(row -> kind.equals(row.get("kind")) && phase.equals(row.get("phase"))).mapToLong(row -> ((Number) row.get("calls")).longValue()).sum(); }
    private static <T> CompletableFuture<T> server(Function<ServerPlayer, T> action) {
        var client = Minecraft.getInstance(); var id = client.player.getUUID(); var server = client.getSingleplayerServer(); var result = new CompletableFuture<T>();
        server.execute(() -> { try { result.complete(action.apply(Objects.requireNonNull(server.getPlayerList().getPlayer(id)))); } catch (Throwable failure) { result.completeExceptionally(failure); } }); return result;
    }
    private static Path isolated(Minecraft client) { var path = client.gameDirectory.toPath().toAbsolutePath().normalize(); check(path.getParent().getFileName().toString().equals("run") && path.getFileName().toString().startsWith("native-sublevel-direct-smoke-precision-numeric-upgrade-"), "a disposable copied numeric-upgrade world is required"); return path; }
    private static void next() { phase++; phaseTicks = 0; }
    private static void finish(Minecraft client, Throwable failure) {
        finished = true;
        try {
            var report = new LinkedHashMap<String, Object>(); report.put("passed", failure == null); report.put("language", language); report.put("phase", phase); report.put("client_ticks", ticks);
            report.put("checks", CHECKS); report.put("screenshots", SCREENSHOTS); report.put("evidence", EVIDENCE); report.put("revision", revision);
            report.put("scope", List.of("One real integrated client/server and ordinary request/execute/save/sync handlers; local transport, not network throughput",
                    "Explicit toolbar preview/keep/apply, local backup, normal undo/redo and documented clipboard/import recovery; no production test entry",
                    "Pure numeric real executions both yield 18; no paid gameplay action is included in this UI fixture"));
            report.put("error", failure == null ? "" : failure.toString());
            Files.writeString(isolated(client).resolve("precision-numeric-upgrade-" + language + "-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        } catch (Throwable writeFailure) { AcademyCraft.LOGGER.error("PRECISION_NUMERIC_UPGRADE_REPORT_FAILED", writeFailure); }
        ProgramNetworkMetrics.resetCapture(false);
        if (failure != null) AcademyCraft.LOGGER.error("PRECISION_NUMERIC_UPGRADE_FAILED", failure); else AcademyCraft.LOGGER.info("PRECISION_NUMERIC_UPGRADE_PASSED language={}", language);
        client.stop();
    }
    private static void check(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
}
