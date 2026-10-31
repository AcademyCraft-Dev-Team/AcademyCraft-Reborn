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
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.data.AbilityData;
import org.academy.internal.client.ability.mentalout.ModularProgramScreen;
import org.academy.internal.client.ability.program.AbilityProgramEditorClient;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.program.AbilityProgramManager;
import org.academy.internal.common.ability.program.ProgramNetworkMetrics;
import org.academy.internal.common.ability.program.ProgramRunTrace;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Real client/server packet handlers in an isolated copied world; not a TCP throughput benchmark. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class PrecisionAutomaticNetworkSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.precisionAutomaticNetworkSmoke");
    private static final String STAGE = System.getProperty("academy.precisionAutomaticNetworkStage", "baseline");
    private static final Identifier CATEGORY = Identifier.fromNamespaceAndPath("academy", "accelerator");
    private static final List<String> CHECKS = new ArrayList<>();
    private static int phase, ticks, phaseTicks;
    private static boolean finished;
    private static CompletableFuture<?> pending;
    private static AbilityProgram manual, loop;
    private static long revision;
    private static Object completedTrace;
    private static volatile boolean screenshotRequested, screenshotDone;
    private static volatile Throwable screenshotFailure;

    private PrecisionAutomaticNetworkSmoke() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
        try {
            isolated(client);
            check(++ticks < 1800, "network smoke exceeded deadline at phase " + phase);
            phaseTicks++;
            if (screenshotFailure != null) throw new IllegalStateException(screenshotFailure);
            switch (phase) {
                case 0 -> {
                    client.options.pauseOnLostFocus = false;
                    client.gui.setScreen(null);
                    manual = program(false, "Network wait smoke");
                    loop = program(true, "Network automatic smoke");
                    pending = server(player -> {
                        var system = AbilitySystemServer.getSystem(player);
                        system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.ACCELERATOR.get());
                        system.setPlayerLevel(player.getUUID(), 5);
                        system.setPlayerBaseMaxCP(player.getUUID(), 100_000);
                        system.setPlayerAvailableCP(player.getUUID(), 100_000);
                        system.setPlayerStatus(player.getUUID(), AbilityData.Status.NORMAL);
                        var seed = ProgramBook.empty(10).replaceSlot(0, manual);
                        system.getPlayerData(player.getUUID()).setAbilityProgramBook(CATEGORY.toString(),
                                Base64.getEncoder().encodeToString(ProgramBookCodec.encode(seed)));
                        return seed.revision();
                    });
                    next();
                }
                case 1 -> {
                    if (!pending.isDone() || phaseTicks < 30) return;
                    revision = (long) pending.join();
                    ProgramNetworkMetrics.resetCapture(true);
                    ProgramNetworkMetrics.sendToServer(new AbilityProgramManager.RequestPacket(CATEGORY));
                    next();
                }
                case 2 -> {
                    if (saved(client) == null || saved(client).revision() != revision) return;
                    check(saved(client).slot(0).program().id().equals(manual.id()), "request did not synchronize server graph");
                    CHECKS.add("request reached server and installed synchronized client book");
                    ProgramNetworkMetrics.sendToServer(new AbilityProgramManager.SavePacket(CATEGORY, 0, revision,
                            ProgramBookCodec.encodeProgram(manual)));
                    next();
                }
                case 3 -> {
                    if (saved(client).revision() != revision + 1) return;
                    revision++;
                    CHECKS.add("save acknowledged and synchronized revision + 1");
                    ProgramNetworkMetrics.sendToServer(new AbilityProgramManager.SavePacket(CATEGORY, 0, revision - 1,
                            ProgramBookCodec.encodeProgram(loop)));
                    next();
                }
                case 4 -> {
                    if (!failures(client).containsKey(0) || phaseTicks < 10) return;
                    check(saved(client).revision() == revision && saved(client).slot(0).program().id().equals(manual.id()),
                            "stale save changed client graph/revision");
                    pending = server(AbilityProgramManager::bookForAddon);
                    next();
                }
                case 5 -> {
                    if (!pending.isDone()) return;
                    var book = (ProgramBook) pending.join();
                    check(book.revision() == revision && book.slot(0).program().id().equals(manual.id()),
                            "stale save changed authoritative graph");
                    CHECKS.add("stale-revision save rejected; original server graph and synchronized revision preserved");
                    ProgramNetworkMetrics.sendToServer(new AbilityProgramManager.ExecutePacket(CATEGORY, 0, 1));
                    next();
                }
                case 6 -> {
                    var trace = traces(client).get(0);
                    if (trace == null || trace.isEmpty()) return;
                    check(!failures(client).containsKey(0), "manual execution failed");
                    check(trace.stream().noneMatch(step -> step.nodeId() == 3), "WAIT completion arrived before deferred feedback observed");
                    CHECKS.add("manual execute delivered deferred trace before wait completion");
                    next();
                }
                case 7 -> {
                    var trace = traces(client).get(0);
                    if (trace == null || trace.stream().noneMatch(step -> step.nodeId() == 3)) return;
                    completedTrace = trace;
                    CHECKS.add("resumed manual execution delivered terminal trace to real client handler");
                    ProgramNetworkMetrics.sendToServer(new AbilityProgramManager.ExecutePacket(CATEGORY, 0, 1));
                    next();
                }
                case 8 -> {
                    if (phaseTicks < 35) return;
                    check(traces(client).get(0) == completedTrace, "duplicate execute sequence produced another trace");
                    CHECKS.add("duplicate execute sequence produced no new execution feedback");
                    ProgramNetworkMetrics.sendToServer(new AbilityProgramManager.SavePacket(CATEGORY, 1, revision,
                            ProgramBookCodec.encodeProgram(loop)));
                    next();
                }
                case 9 -> {
                    if (saved(client).revision() != revision + 1) return;
                    revision++;
                    ProgramNetworkMetrics.sendToServer(new AbilityProgramManager.ExecutePacket(CATEGORY, 1, 2));
                    next();
                }
                case 10 -> {
                    if (saved(client).revision() != revision + 1) return;
                    revision++;
                    check(loopEnabled(saved(client)), "loop enable result did not synchronize enabled graph");
                    next();
                }
                case 11 -> {
                    var trace = traces(client).get(1);
                    if (trace == null || trace.stream().noneMatch(step -> step.nodeId() == 3)) return;
                    CHECKS.add("automatic WAIT graph executed through natural player ticks and delivered terminal trace");
                    ProgramNetworkMetrics.sendToServer(new AbilityProgramManager.ExecutePacket(CATEGORY, 1, 3));
                    next();
                }
                case 12 -> {
                    if (saved(client).revision() != revision + 1) return;
                    revision++;
                    check(!loopEnabled(saved(client)), "loop disable result did not synchronize disabled graph");
                    next();
                }
                case 13 -> {
                    if (phaseTicks < 35) return;
                    pending = server(AbilityProgramManager::bookForAddon);
                    next();
                }
                case 14 -> {
                    if (!pending.isDone()) return;
                    var book = (ProgramBook) pending.join();
                    check(book.revision() == revision && !loopEnabled(book), "authoritative loop did not remain disabled");
                    CHECKS.add("loop toggle saved authoritative revisions and remained disabled");
                    client.gui.setScreen(null);
                    AbilityProgramEditorClient.openEditor();
                    next();
                }
                case 15 -> {
                    if (phaseTicks < 40) return;
                    check(client.gui.screen() instanceof ModularProgramScreen, "real editor did not open");
                    check(saved(client).revision() == revision, "editor request lost latest server revision");
                    screenshotRequested = true;
                    next();
                }
                case 16 -> {
                    if (!screenshotDone) return;
                    CHECKS.add("real editor opened with synchronized saved graph and client run trace state");
                    finish(client, null);
                }
                default -> throw new IllegalStateException("unknown phase");
            }
        } catch (Throwable failure) { finish(client, failure); }
    }

    @SubscribeEvent
    public static void render(RenderFrameEvent.Post event) {
        if (!ENABLED || !screenshotRequested) return;
        var client = Minecraft.getInstance();
        if (client.gui.overlay() != null) return;
        screenshotRequested = false;
        Screenshot.grab(client.gameDirectory, "precision-automatic-network-" + STAGE + ".png",
                client.gameRenderer.mainRenderTarget(), 1, message -> {
                    try {
                        check(Files.size(isolated(client).resolve("screenshots/precision-automatic-network-" + STAGE + ".png")) > 0,
                                message.getString());
                        screenshotDone = true;
                    } catch (Throwable failure) { screenshotFailure = failure; }
                });
    }

    private static AbilityProgram program(boolean automatic, String name) {
        var entry = new JsonObject();
        if (automatic) { entry.addProperty("enabled", false); entry.addProperty("interval", 1); }
        var ticks = new JsonObject(); ticks.addProperty("value", 20);
        return new AbilityProgram(1, UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name,
                CATEGORY, new ProgramGraph(List.of(
                        new ProgramGraph.Node(0, automatic ? CommonProgramNodeIds.TRIGGER_LOOP : BaseAbilityProgramDefinition.entryId(CATEGORY), 1, entry),
                        new ProgramGraph.Node(1, CommonProgramNodeIds.INTEGER_CONSTANT, 1, ticks),
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

    private static boolean loopEnabled(ProgramBook book) {
        return book.slot(1).program().graph().nodes().getFirst().configuration().getAsJsonObject().get("enabled").getAsBoolean();
    }

    private static <T> CompletableFuture<T> server(Function<ServerPlayer, T> action) {
        var client = Minecraft.getInstance();
        var id = client.player.getUUID();
        var server = client.getSingleplayerServer();
        var result = new CompletableFuture<T>();
        server.execute(() -> {
            try { result.complete(action.apply(Objects.requireNonNull(server.getPlayerList().getPlayer(id)))); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    private static Object state(Minecraft client) throws Exception {
        var field = AbilityProgramEditorClient.class.getDeclaredField("STATES"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).get(client.player.getUUID() + "|" + CATEGORY);
    }

    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }

    private static ProgramBook saved(Minecraft client) throws Exception {
        var state = state(client); return state == null ? null : (ProgramBook) field(state, "saved");
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, List<ProgramRunTrace.Step>> traces(Minecraft client) throws Exception {
        return (Map<Integer, List<ProgramRunTrace.Step>>) field(state(client), "runTraces");
    }

    private static Map<?, ?> failures(Minecraft client) throws Exception { return (Map<?, ?>) field(state(client), "failures"); }
    private static void next() { phase++; phaseTicks = 0; }

    private static Path isolated(Minecraft client) {
        var directory = client.gameDirectory.toPath().toAbsolutePath().normalize();
        check(directory.getParent().getFileName().toString().equals("run")
                && directory.getFileName().toString().startsWith("native-sublevel-direct-smoke"), "isolated copied world required");
        return directory;
    }

    private static void finish(Minecraft client, Throwable failure) {
        finished = true;
        try {
            var report = new LinkedHashMap<String, Object>();
            report.put("passed", failure == null); report.put("stage", STAGE); report.put("phase", phase); report.put("client_ticks", ticks);
            report.put("scope", "One real integrated client and server; real Misaka packets, production handlers and natural player ticks; local transport, not TCP/WAN throughput");
            report.put("checks", CHECKS); report.put("revision", revision); report.put("network", ProgramNetworkMetrics.snapshot());
            report.put("error", failure == null ? "" : failure.toString());
            Files.writeString(isolated(client).resolve("precision-automatic-network-" + STAGE + "-result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        } catch (Throwable writeFailure) { AcademyCraft.LOGGER.error("PRECISION_AUTOMATIC_NETWORK_REPORT_FAILED", writeFailure); }
        ProgramNetworkMetrics.resetCapture(false);
        if (failure != null) AcademyCraft.LOGGER.error("PRECISION_AUTOMATIC_NETWORK_FAILED", failure);
        else AcademyCraft.LOGGER.info("PRECISION_AUTOMATIC_NETWORK_PASSED");
        client.stop();
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
