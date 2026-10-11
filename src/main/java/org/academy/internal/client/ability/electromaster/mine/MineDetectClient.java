package org.academy.internal.client.ability.electromaster.mine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppedEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.academy.AcademyCraft;
import org.joml.Matrix4fc;

import java.util.*;
import java.util.concurrent.*;

/** Owns client-only scan state and at most eight immutable mesh jobs on two workers. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class MineDetectClient {
    private static final TagKey<Block> COMMON_ORES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));
    private static final TagKey<Block> FORGE_ORES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("forge", "ores"));
    private static MineDetectClient instance;
    private final ClientLevel level;
    private final OreScanCache scan = new OreScanCache();
    private final MineDetectRenderer renderer = new MineDetectRenderer();
    private final Map<BlockState, Boolean> oreTypes = new IdentityHashMap<>();
    private final Map<BlockState, OreGeometry> geometry = new IdentityHashMap<>();
    private final Map<OreScanCache.Cell, Job> jobs = new IdentityHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(2, runnable -> {
        var thread = new Thread(runnable, "Academy ore mesh");
        thread.setDaemon(true);
        return thread;
    });
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private long lastTickNanos;
    private record Job(long revision, Future<OreMesh> future) { }

    private final OreScanCache.Source source = new OreScanCache.Source() {
        @Override public boolean loaded(OreScanCache.CellPos pos) {
            return pos.y() >= level.getMinY() && pos.y() < level.getMaxY()
                    && level.getChunkSource().getChunk(pos.x() >> 4, pos.z() >> 4, ChunkStatus.FULL, false) != null;
        }

        @Override public boolean empty(OreScanCache.CellPos pos) {
            var chunk = level.getChunkSource().getChunk(pos.x() >> 4, pos.z() >> 4, ChunkStatus.FULL, false);
            return chunk == null || !chunk.getSection(level.getSectionIndex(pos.y())).maybeHas(MineDetectClient.this::isOre);
        }

        @Override public OreGeometry read(int x, int y, int z) {
            cursor.set(x, y, z);
            return describe(level.getBlockState(cursor), cursor);
        }
    };

    private MineDetectClient(ClientLevel level) { this.level = level; }

    public static void tick(ClientLevel level, BlockPos center, int radius) {
        long start = System.nanoTime();
        if (instance == null || instance.level != level) {
            reset();
            instance = new MineDetectClient(level);
        }
        instance.scan.moveTo(center.getX(), center.getY(), center.getZ(), radius);
        instance.collectMeshes();
        instance.scan.scan(instance.source, level.getGameTime(), start + 1_250_000, System::nanoTime);
        instance.submitMeshes();
        instance.lastTickNanos = System.nanoTime() - start;
    }

    private void collectMeshes() {
        jobs.entrySet().removeIf(entry -> {
            var cell = entry.getKey();
            var job = entry.getValue();
            if (!scan.contains(cell)) { job.future.cancel(false); return true; }
            if (!job.future.isDone()) return false;
            try {
                cell.accept(job.revision, job.future.get());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException exception) {
                // Suppress a repeated failure for this exact snapshot; a future block update can retry.
                cell.meshRevision = job.revision;
                AcademyCraft.LOGGER.warn("Mine detect mesh failed for {}", cell.pos, exception.getCause());
            }
            return true;
        });
    }

    private void submitMeshes() {
        int available = 8 - jobs.size();
        if (available <= 0) return;
        scan.cells().stream().filter(cell -> cell.revision != cell.meshRevision && !jobs.containsKey(cell))
                .sorted(Comparator.comparingLong(cell -> cell.pos.distanceSquared(scan.x(), scan.y(), scan.z())))
                .limit(available).forEach(cell -> {
                    var snapshot = cell.snapshot();
                    jobs.put(cell, new Job(cell.revision, workers.submit(() -> OreMesh.build(snapshot))));
                });
    }

    public static void render(Vec3 camera, Matrix4fc view, Matrix4fc projection) {
        if (instance == null) return;
        instance.collectMeshes();
        instance.submitMeshes();
        instance.renderer.render(instance.scan, camera, view, projection);
    }

    public static OreScanCache scan() { return instance == null ? null : instance.scan; }
    public static MineDetectRenderer renderer() { return instance == null ? null : instance.renderer; }
    public static long lastTickNanos() { return instance == null ? 0 : instance.lastTickNanos; }

    public static void blockChanged(ClientLevel level, BlockPos pos, BlockState state) {
        if (instance != null && instance.level == level
                && OreScanCache.CellPos.at(pos.getX(), pos.getY(), pos.getZ())
                .distanceSquared(instance.scan.x(), instance.scan.y(), instance.scan.z()) <= (long) instance.scan.radius() * instance.scan.radius())
            instance.scan.update(pos.getX(), pos.getY(), pos.getZ(), instance.describe(state, pos));
    }

    private boolean isOre(BlockState state) {
        return oreTypes.computeIfAbsent(state, key -> {
            if (key.is(COMMON_ORES) || key.is(FORGE_ORES)) return true;
            return isOrePath(BuiltInRegistries.BLOCK.getKey(key.getBlock()).getPath());
        });
    }

    public static boolean isOrePath(String path) {
        return path.endsWith("_ore") || path.contains("ore_") || path.contains("_ore_");
    }

    private OreGeometry describe(BlockState state, BlockPos pos) {
        if (!isOre(state)) return null;
        var cached = geometry.get(state);
        if (cached != null && !state.getBlock().hasDynamicShape()) return cached;
        var boxes = state.getShape(level, pos).toAabbs().stream().map(box -> new OreGeometry.Box(
                (float) box.minX, (float) box.minY, (float) box.minZ,
                (float) box.maxX, (float) box.maxY, (float) box.maxZ)).toList();
        var value = new OreGeometry(color(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()), boxes);
        geometry.put(state, value);
        return value;
    }

    private static int color(String path) {
        if (path.contains("redstone")) return 0xDC716A;
        if (path.contains("lapis")) return 0x728DDB;
        if (path.contains("diamond")) return 0x6FD8D2;
        if (path.contains("emerald")) return 0x75C98D;
        if (path.contains("gold")) return 0xDCC474;
        if (path.contains("iron")) return 0xC9C5BE;
        if (path.contains("copper")) return 0xCE9B7A;
        if (path.contains("coal")) return 0x939DA7;
        if (path.contains("quartz")) return 0xDCD5C5;
        if (path.contains("debris")) return 0xB79589;
        return java.awt.Color.HSBtoRGB(Math.floorMod(path.hashCode(), 360) / 360f, 0.35f, 0.8f) & 0xFFFFFF;
    }

    public static void reset() {
        if (instance == null) return;
        instance.jobs.values().forEach(job -> job.future.cancel(false));
        instance.workers.shutdownNow();
        instance.renderer.close();
        instance = null;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    @SubscribeEvent public static void stopped(ClientStoppedEvent event) { reset(); }
    @SubscribeEvent public static void resources(ClientResourceLoadFinishedEvent event) { reset(); }
    @SubscribeEvent public static void tags(TagsUpdatedEvent event) {
        Minecraft.getInstance().execute(MineDetectClient::reset);
    }
    @SubscribeEvent public static void chunkLoad(ChunkEvent.Load event) { chunkChanged(event); }
    @SubscribeEvent public static void chunkUnload(ChunkEvent.Unload event) { chunkChanged(event); }
    private static void chunkChanged(ChunkEvent event) {
        if (instance != null && event.getLevel() == instance.level) {
            var pos = event.getChunk().getPos();
            instance.scan.invalidateChunk(pos.x(), pos.z());
        }
    }
}
