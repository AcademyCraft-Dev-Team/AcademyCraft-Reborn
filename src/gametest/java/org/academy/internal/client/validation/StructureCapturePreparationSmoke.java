package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ScheduledTick;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureCaptureResult;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.api.common.structure.upgrade.StructureCaptureExtractions;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real soft-break events, natural scheduled ticks and staged capture in an isolated copied save. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureCapturePreparationSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.structureCapturePreparationSmoke");
    private static final List<String> CASES = List.of("scheduled_before", "scheduled_after", "cancel", "timeout", "denied", "source_changed", "cancel_in_callback", "reentrant", "many_plants");
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final List<BlockPos> PLACED = new ArrayList<>();
    private static final List<BlockPos> PLANTS = new ArrayList<>();
    private static final Map<BlockPos, BlockState> FLOOR = new LinkedHashMap<>();
    private static volatile boolean ready, complete, failed, closing;
    private static Path output;
    private static ServerPlayer player;
    private static Vec3 oldPosition, oldVelocity;
    private static float oldYaw, oldPitch;
    private static boolean oldNoGravity, oldDestroy;
    private static GameType oldMode;
    private static BlockPos anchor, seed;
    private static int scenario, age, breakEvents, dropEvents;
    private static int oldRandomTickSpeed;
    private static long startedAt;
    private static long builtAt;
    private static int loadedSourceTicks;
    private static UUID operation;
    private static StructureRef reference;
    private static boolean injected;
    private static boolean caseBuilt;

    private StructureCapturePreparationSmoke() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || complete || failed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            check(++age < 1_000, "preparation fixture deadline");
            if (player == null) {
                player = event.getServer().getPlayerList().getPlayers().getFirst();
                oldPosition = player.position(); oldVelocity = player.getDeltaMovement(); oldYaw = player.getYRot(); oldPitch = player.getXRot();
                oldNoGravity = player.isNoGravity(); oldMode = player.gameMode.getGameModeForPlayer();
                oldDestroy = DestroyBlocksSetting.isDestroyBlocksEnabled(player);
                oldRandomTickSpeed = player.level().getGameRules().get(GameRules.RANDOM_TICK_SPEED);
                player.level().getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, event.getServer());
                player.setGameMode(GameType.SURVIVAL);
                DestroyBlocksSetting.setDestroyBlocksEnabled(player, true);
                int x = player.blockPosition().getX() + 80, z = player.blockPosition().getZ();
                anchor = new BlockPos(x, Math.min(player.level().getMaxY() - 20,
                        Math.max(200, player.level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 60)), z);
            }
            player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
            if (operation == null) { startCase(); return; }
            var name = CASES.get(scenario);
            long elapsed = player.level().getGameTime() - startedAt;
            if (name.equals("cancel")) {
                check(elapsed <= 10 || FLOOR.entrySet().stream().allMatch(entry -> player.level().getBlockState(entry.getKey()).equals(entry.getValue())), "cancelled capture moved a source");
                check(PLANTS.stream().noneMatch(pos -> player.level().getBlockState(pos).isAir()) && breakEvents == 0 && dropEvents == 0,
                        "cancelled preparation broke vegetation");
                check(StructureCaptureExtractions.poll(player.level(), player, operation).isEmpty(), "cancelled preparation remained pollable");
                if (elapsed >= 10) finishCase("cancelled_without_capture");
                return;
            }
            var result = StructureCaptureExtractions.poll(player.level(), player, operation).orElseThrow();
            check(result.transactionId().equals(operation), "preparation facade changed transaction identity");
            result.structure().ifPresent(value -> reference = value);
            if (result.pending()) {
                if (reference == null) check(FLOOR.entrySet().stream().allMatch(entry -> player.level().getBlockState(entry.getKey()).equals(entry.getValue())), "preparation moved a retained source");
                return;
            }
            if (name.equals("timeout")) {
                check(result.status() == StructureCaptureResult.Status.TIMEOUT && elapsed >= 99, "scheduled tick did not consume original deadline: " + result);
                check(reference == null && breakEvents == 0 && dropEvents == 0
                        && PLANTS.stream().noneMatch(pos -> player.level().getBlockState(pos).isAir()), "timeout destroyed a plant or allocated a body");
                finishCase("timed_out_without_breaks"); return;
            }
            if (name.equals("cancel_in_callback")) {
                check(!result.succeeded() && result.structure().isEmpty() && injected, "callback cancellation created a target: " + result);
                check(dropEvents == 0 && PLANTS.stream().noneMatch(pos -> player.level().getBlockState(pos).isAir()), "callback cancellation continued breaking");
                check(FLOOR.entrySet().stream().allMatch(entry -> player.level().getBlockState(entry.getKey()).equals(entry.getValue())), "callback cancellation transferred a retained source");
                finishCase("callback_cancelled_before_write"); return;
            }
            check(result.succeeded() && reference != null, "prepared capture failed: " + result);
            var cells = StructureCaptureExtractions.sourcePositions(player, reference).orElseThrow();
            check(cells.size() == FLOOR.size() && cells.containsAll(FLOOR.keySet()), "soft plants entered source image: " + cells);
            var world = StructureSpaces.resolve(event.getServer(), reference).orElseThrow().world();
            for (var entry : FLOOR.entrySet()) {
                check(player.level().getBlockState(entry.getKey()).isAir(), "committed floor remained in host");
                check(world.read(entry.getKey().subtract(seed)).state().equals(entry.getValue()), "retained block state changed");
            }
            check(PLANTS.stream().allMatch(pos -> player.level().getBlockState(pos).isAir()), "source vegetation remained");
            check(breakEvents == PLANTS.size() && dropEvents == PLANTS.size(), "soft break/drop event duplicated or lost: " + breakEvents + "/" + dropEvents);
            finishCase("committed_after_natural_ticks");
        } catch (Throwable failure) { fail(failure); }
    }

    private static void startCase() {
        if (!caseBuilt) {
            buildCase();
            caseBuilt = true;
            builtAt = player.level().getGameTime();
            loadedSourceTicks = 0;
            return;
        }
        // setBlock can temporarily expose a chunk while teleport/player tracking is still
        // moving its FULL ticket. Observe ten consecutive loaded ticks before scheduling the
        // behavior under test; source-unloaded admission remains a production hard failure.
        boolean loaded = java.util.stream.Stream.concat(FLOOR.keySet().stream(), PLANTS.stream()).allMatch(pos ->
                player.level().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null);
        loadedSourceTicks = loaded ? loadedSourceTicks + 1 : 0;
        if (loadedSourceTicks < 10 || player.level().getGameTime() - builtAt < 10) return;
        beginCapture();
    }

    private static void buildCase() {
        seed = anchor.east(scenario * 16);
        for (var pos : BlockPos.betweenClosed(seed.offset(-2, -2, -2), seed.offset(9, 7, 9))) {
            player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3); PLACED.add(pos.immutable());
        }
        FLOOR.clear(); PLANTS.clear(); breakEvents = 0; dropEvents = 0; injected = false; reference = null;
        FLOOR.put(seed, Blocks.STONE.defaultBlockState());
        FLOOR.put(seed.east(), Blocks.GRASS_BLOCK.defaultBlockState());
        FLOOR.put(seed.east(2), Blocks.GRASS_BLOCK.defaultBlockState());
        PLANTS.add(seed.east().above()); PLANTS.add(seed.east(2).above());
        if (CASES.get(scenario).equals("many_plants")) {
            FLOOR.clear(); PLANTS.clear();
            for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
                var pos = seed.offset(x, 0, z);
                FLOOR.put(pos, Blocks.GRASS_BLOCK.defaultBlockState()); PLANTS.add(pos.above());
            }
        }
        FLOOR.forEach((pos, state) -> player.level().setBlock(pos, state, 2));
        // Crops have the default motion-protected tag and are intentionally retained with
        // their support; the H protocol fixture verifies that separate source-content case.
        for (int i = 0; i < PLANTS.size(); i++) player.level().setBlock(PLANTS.get(i),
                PLANTS.size() == 2 && i == 0 ? Blocks.POPPY.defaultBlockState()
                        : Blocks.SHORT_GRASS.defaultBlockState(), 2);
        player.connection.teleport(seed.getX() + .5, seed.getY() + 3, seed.getZ() - 5, 0, 0);
    }

    private static void beginCapture() {
        var name = CASES.get(scenario);
        startedAt = player.level().getGameTime();
        if (List.of("scheduled_before", "cancel", "timeout", "cancel_in_callback").contains(name)) schedule(name.equals("timeout") ? 200 : 6);
        var positions = new ArrayList<>(FLOOR.keySet()); positions.addAll(PLANTS);
        var request = new StructureCaptureRequest(positions, 256, 16, StructureCaptureRequest.ALLOW, true);
        var result = StructureCaptureExtractions.capture(player.level(), player, request, 1, 20);
        operation = result.transactionId();
        if (name.equals("reentrant") || name.equals("many_plants")) {
            check((!name.equals("reentrant") || injected) && (result.pending() || result.succeeded()), "owning capture failed: " + result);
            result.structure().ifPresent(value -> reference = value);
        } else if (List.of("scheduled_before", "scheduled_after", "cancel", "timeout", "cancel_in_callback").contains(name)) {
            check(result.pending() && result.structure().isEmpty(), "transient source did not enter real no-reference preparation: " + result);
            if (!name.equals("scheduled_after")) check(breakEvents == 0 && dropEvents == 0, "preflight broke plants while source tick pending");
            var repeated = StructureCaptureExtractions.poll(player.level(), player, operation).orElseThrow();
            check(repeated.pending() && repeated.structure().isEmpty(), "same-tick poll advanced preparation");
            var impostor = new ArmorStand(player.level(), seed.getX(), seed.getY() + 5, seed.getZ());
            check(!StructureCaptureExtractions.cancel(player.level(), impostor, operation)
                    && StructureCaptureExtractions.poll(player.level(), impostor, operation).isEmpty(), "foreign actor acquired/cancelled preparation");
        } else {
            check(!result.pending() && !result.succeeded() && result.structure().isEmpty(), "guarded capture unexpectedly succeeded: " + result);
            if (name.equals("denied")) {
                check(breakEvents > 0 && dropEvents == 0 && PLANTS.stream().noneMatch(pos -> player.level().getBlockState(pos).isAir()), "denied break lost plants or spawned drops");
                check(FLOOR.entrySet().stream().allMatch(entry -> player.level().getBlockState(entry.getKey()).equals(entry.getValue())), "denied capture changed retained floor");
            } else check(player.level().getBlockState(seed).is(Blocks.DIAMOND_BLOCK), "replacement source was deleted or restored without authority");
            finishCase(name.equals("denied") ? "protection_preserved" : "replacement_detected"); return;
        }
        if (name.equals("cancel")) check(StructureCaptureExtractions.cancel(player.level(), player, operation), "owning actor could not cancel preparation");
    }

    private static void schedule(int ticks) {
        player.level().getBlockTicks().schedule(new ScheduledTick<>(Blocks.STONE, seed, player.level().getGameTime() + ticks, 0));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void breakBlock(BreakBlockEvent event) {
        if (!ENABLED || player == null || complete || failed || event.getLevel() != player.level() || !PLANTS.contains(event.getPos())) return;
        breakEvents++;
        var name = CASES.get(scenario);
        if (name.equals("denied")) { event.setCanceled(true); return; }
        if (!injected && name.equals("reentrant")) {
            injected = true;
            var positions = new ArrayList<>(FLOOR.keySet()); positions.addAll(PLANTS);
            var nested = StructureCaptureExtractions.capture(player.level(), player,
                    new StructureCaptureRequest(positions, 16, 8, StructureCaptureRequest.ALLOW, true), 1, 20);
            check(!nested.pending() && !nested.succeeded() && nested.structure().isEmpty()
                    && nested.detail().equals("extraction_preparation_already_pending"), "reentrant callback bypassed capture guard: " + nested);
        }
        if (!injected && name.equals("cancel_in_callback")) {
            check(StructureCaptureExtractions.cancel(player.level(), player, operation), "event callback could not withdraw preparation");
            injected = true;
        }
        if (!injected && name.equals("scheduled_after")) { schedule(6); injected = true; }
        if (!injected && name.equals("source_changed")) {
            player.level().setBlock(seed, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2); injected = true;
        }
    }

    @SubscribeEvent
    public static void drops(BlockDropsEvent event) {
        if (ENABLED && player != null && !complete && !failed && event.getLevel() == player.level() && PLANTS.contains(event.getPos())) dropEvents++;
    }

    private static void finishCase(String outcome) {
        RESULTS.add(Map.of("case", CASES.get(scenario), "outcome", outcome, "ticks", player.level().getGameTime() - startedAt,
                "setup_ticks", startedAt - builtAt, "break_events", breakEvents, "drop_events", dropEvents,
                "backend", reference == null ? "none" : reference.backend().toString()));
        if (reference != null) {
            StructureCaptureExtractions.release(player, reference);
            check(StructureTransactions.discard(player.level().getServer(), reference), "fixture could not discard captured body");
        }
        StructureCaptureExtractions.cancel(player.level(), player, operation);
        operation = null; reference = null; caseBuilt = false;
        if (++scenario == CASES.size()) { cleanup(); complete = true; write(true, ""); }
    }

    private static void cleanup() {
        if (player == null) return;
        if (operation != null) StructureCaptureExtractions.cancel(player.level(), player, operation);
        if (reference != null) { StructureCaptureExtractions.release(player, reference); StructureTransactions.discard(player.level().getServer(), reference); }
        for (var pos : PLACED) player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        if (anchor != null) for (var item : player.level().getEntitiesOfClass(ItemEntity.class, new AABB(Vec3.atLowerCornerOf(anchor.offset(-4, -10, -6)), Vec3.atLowerCornerOf(anchor.offset(150, 10, 15))))) item.discard();
        player.setGameMode(oldMode); player.setNoGravity(oldNoGravity);
        DestroyBlocksSetting.setDestroyBlocksEnabled(player, oldDestroy);
        player.level().getGameRules().set(GameRules.RANDOM_TICK_SPEED, oldRandomTickSpeed, player.level().getServer());
        player.connection.teleport(oldPosition.x, oldPosition.y, oldPosition.z, oldYaw, oldPitch); player.setDeltaMovement(oldVelocity);
    }

    private static void write(boolean passed, String failure) {
        try { Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed", passed, "cases", RESULTS, "failure", failure))); }
        catch (Exception error) { AcademyCraft.LOGGER.error("Cannot write capture preparation smoke", error); }
    }

    private static void fail(Throwable failure) {
        if (failed || complete) return;
        failed = true; AcademyCraft.LOGGER.error("STRUCTURE_CAPTURE_PREPARATION_FAILED case={} age={}", scenario, age, failure);
        try { cleanup(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
        write(false, failure.toString());
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var client = Minecraft.getInstance();
        if (failed || complete) { closing = true; client.stop(); return; }
        if (client.player == null || client.level == null || client.getConnection() == null) return;
        var directory = client.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!directory.getFileName().toString().startsWith("native-sublevel-direct-smoke")) { fail(new IllegalStateException("Preparation fixture requires isolated native-sublevel-direct-smoke directory")); return; }
        output = directory.resolve("structure-capture-preparation-result.json"); ready = true;
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
