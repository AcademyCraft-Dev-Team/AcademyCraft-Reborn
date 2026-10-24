package org.academy.internal.common.structure.upgrade.lifecycle;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureGridRestoration;
import org.academy.internal.common.structure.space.NativeStructureTickets;
import org.academy.internal.common.sublevel.*;

import java.util.*;

/** Explicit administrator maintenance, including persisted entries outside player-loaded chunks. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class LocalStructureRestorationBatch {
    private static final Map<MinecraftServer, Job> JOBS = new IdentityHashMap<>();
    private static final int MAX_CHUNKS = 64;
    private static final int WAIT_TICKS = 200;

    private LocalStructureRestorationBatch() {}

    public record Status(UUID jobId, int total, int processed, int restored, int alreadyAbsent, int recoveryPending,
                         Map<String, String> retained, boolean running) {
        public Status { retained = Map.copyOf(retained); }
    }

    public static Optional<Status> status(MinecraftServer server) {
        var job = JOBS.get(server);
        return job == null ? Optional.empty() : Optional.of(job.status());
    }

    public static int start(CommandSourceStack source) {
        var server = source.getServer();
        if (!server.isSameThread() || !Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)) return 0;
        var previous = JOBS.get(server);
        if (previous != null && previous.running) {
            source.sendFailure(Component.literal("本地结构归块任务正在执行；使用 /academy debug sublevel restore_all status 查看进度。"));
            return 0;
        }
        try {
            // Flush live poses before reading every dimension's manifest, including unavailable hosts.
            for (var runtime : SubLevelServer.runtimes(server)) runtime.persist();
            var saved = SubLevelManifest.loadHosts(server.getWorldPath(LevelResource.ROOT)
                    .resolve("data/academy/sublevels"), List.of());
            var references = new ArrayList<StructureRef>();
            for (var host : saved.entrySet()) for (var entry : host.getValue().entries()) {
                if (entry.state() != SubLevelState.REMOVED) references.add(new StructureRef(entry.id().value(),
                        host.getKey(), SubLevelStructureRefs.BACKEND, 0));
            }
            references.sort(Comparator.comparing((StructureRef ref) -> ref.dimension().toString()).thenComparing(StructureRef::id));
            if (!references.isEmpty() && !SubLevelServer.ensureForMaintenance(server)) {
                source.sendFailure(Component.literal("本地结构运行时不可用，未改动存档中的结构。"));
                return 0;
            }
            var job = new Job(source, references);
            JOBS.put(server, job);
            source.sendSuccess(() -> Component.literal("开始归块存档中的 " + job.total
                    + " 个本地结构（所有维度，含未加载位置及未标记结构）。每次处理一个，Sable 结构不参与。"), true);
            if (references.isEmpty()) finish(job);
            return 1;
        } catch (Exception failure) {
            AcademyCraft.LOGGER.error("Cannot enumerate local structures for restoration", failure);
            source.sendFailure(Component.literal("读取本地结构清单失败；详见服务端日志，未启动归块。"));
            return 0;
        }
    }

    public static int report(CommandSourceStack source) {
        if (!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)) return 0;
        var job = JOBS.get(source.getServer());
        if (job == null) { source.sendFailure(Component.literal("没有本地结构归块任务。")); return 0; }
        source.sendSuccess(() -> Component.literal(summary(job)), false);
        job.retained.entrySet().stream().limit(8).forEach(entry -> source.sendFailure(
                Component.literal("保留 " + entry.getKey() + "：" + entry.getValue())));
        if (job.retained.size() > 8) source.sendFailure(Component.literal("其余保留项已记录到服务端日志。"));
        return job.restored;
    }

    public static int cancel(CommandSourceStack source) {
        if (!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)) return 0;
        var job = JOBS.get(source.getServer());
        if (job == null || !job.running) return 0;
        release(job);
        job.running = false;
        source.sendSuccess(() -> Component.literal("已停止后续归块；已提交的归块事务仍会完成恢复。 " + summary(job)), true);
        return 1;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var job = JOBS.get(server);
        if (job == null || !job.running) return;
        try {
            if (job.current == null) {
                if (job.queue.isEmpty()) { finish(job); return; }
                job.current = job.queue.removeFirst();
                job.started = server.getTickCount();
                job.host = server.getLevel(ResourceKey.create(Registries.DIMENSION, job.current.dimension()));
                if (job.host == null) { retain(job, "host_dimension_unavailable"); return; }
                var runtime = SubLevelServer.runtime(job.host).orElse(null);
                var id = new SubLevelId(job.current.id());
                var body = runtime == null ? null : runtime.get(id).orElse(null);
                if (body == null) {
                    if (runtime != null && !runtime.hasPersisted(id)) absent(job);
                    else retain(job, "persisted_structure_unavailable");
                    return;
                }
                var image = runtime.view(id).cells();
                if (image.stream().anyMatch(cell -> cell.state().hasBlockEntity() || !cell.state().getFluidState().isEmpty())) {
                    retain(job, "unsupported_block_entity_or_fluid"); return;
                }
                if (image.size() > org.academy.internal.common.structure.transaction.StructureTransactionJournal.MAX_CELLS) {
                    retain(job, "content_budget_exceeded"); return;
                }
                // Tickets cover the actual source and proposed upright image, not a cubic radius
                // derived from all three axis spans. Vertical clearance uses these same columns.
                var area = body.worldBounds();
                if (!image.isEmpty()) {
                    var alignment = RestorationGrid.align(body.pose(), image.stream().map(cell -> cell.position()).toList());
                    for (var cell : image) area = area.minmax(new net.minecraft.world.phys.AABB(alignment.target(cell.position())));
                }
                area = area.inflate(2);
                int minX = (int) Math.floor(area.minX) >> 4, maxX = (int) Math.floor(area.maxX) >> 4;
                int minZ = (int) Math.floor(area.minZ) >> 4, maxZ = (int) Math.floor(area.maxZ) >> 4;
                if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > MAX_CHUNKS) { retain(job, "chunk_budget_exceeded"); return; }
                for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                    var chunk = new ChunkPos(x, z);
                    NativeStructureTickets.restoration(job.host, chunk, true);
                    job.chunks.add(chunk);
                }
                return;
            }
            long waited = server.getTickCount() - job.started;
            if (waited >= WAIT_TICKS) { retain(job, job.detail); return; }
            if (waited % 5 != 0) return;
            if (job.chunks.stream().anyMatch(chunk -> job.host.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null)) {
                job.detail = "host_chunks_loading_timeout"; return;
            }
            var manager = ((org.academy.mixin.common.ServerLevelEntityManagerAccessor) job.host).academy$getEntityManager();
            @SuppressWarnings("unchecked")
            var managerAccess = (org.academy.mixin.common.PersistentEntitySectionManagerAccessor<net.minecraft.world.entity.Entity>) (Object) manager;
            int queued = 0;
            for (var chunk : job.chunks) if (!manager.areEntitiesLoaded(chunk.pack())) {
                managerAccess.academy$ensureChunkQueuedForLoad(chunk.pack());
                if (++queued == 4) break;
            }
            if (queued > 0) { job.detail = "host_entities_loading_timeout"; return; }
            var runtime = SubLevelServer.runtime(job.host).orElse(null);
            var id = new SubLevelId(job.current.id());
            if (runtime != null && runtime.get(id).isEmpty() && !runtime.hasPersisted(id)) { absent(job); return; }
            var activation = SubLevelActivationRegistry.find(id).orElse(null);
            if (activation == null) { retain(job, "activation_unavailable"); return; }
            var live = new StructureRef(job.current.id(), job.current.dimension(), job.current.backend(), activation.generation());
            var result = MarkedGridRestoration.INSTANCE.restoreAdministrative(job.source, job.host, live);
            job.detail = result.detail();
            if (result.value().isPresent()) {
                var phase = result.value().orElseThrow().phase();
                if (phase == StructureGridRestoration.Phase.RESTORED) { done(job, false); return; }
                if (phase == StructureGridRestoration.Phase.RECOVERY_REQUIRED) { done(job, true); return; }
            }
            if (result.status() == org.academy.api.common.structure.upgrade.StructureOperation.Status.DENIED) retain(job, result.detail());
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.error("Local structure batch retained {}", job.current, failure);
            if (job.current != null) retain(job, "exception_see_server_log");
            else { job.running = false; release(job); }
        }
    }

    private static void retain(Job job, String detail) {
        String key = job.current.dimension() + "/" + job.current.id();
        if (job.host != null) {
            var runtime = SubLevelServer.runtime(job.host).orElse(null);
            var body = runtime == null ? null : runtime.get(new SubLevelId(job.current.id())).orElse(null);
            if (body != null) detail += " at " + net.minecraft.core.BlockPos.containing(body.pose().worldOrigin()).toShortString();
        }
        job.retained.put(key, detail);
        AcademyCraft.LOGGER.warn("Local restoration retained {}: {}", key, detail);
        job.processed++;
        release(job);
    }

    private static void done(Job job, boolean recovery) {
        job.processed++;
        if (recovery) job.recoveryPending++; else job.restored++;
        release(job);
    }

    private static void absent(Job job) {
        job.processed++; job.alreadyAbsent++; release(job);
    }

    private static void release(Job job) {
        if (job.host != null) for (var chunk : job.chunks) NativeStructureTickets.restoration(job.host, chunk, false);
        job.chunks.clear(); job.host = null; job.current = null;
        job.detail = "restoration_target_or_content_unavailable";
    }

    private static void finish(Job job) {
        release(job); job.running = false;
        job.source.sendSuccess(() -> Component.literal(summary(job)), true);
        if (!job.retained.isEmpty()) job.source.sendFailure(Component.literal("部分结构已安全保留；/academy debug sublevel restore_all status 查看原因和位置，处理后可重试。"));
        AcademyCraft.LOGGER.info("{}", summary(job));
    }

    private static String summary(Job job) {
        return "[本地归块 " + (job.running ? "执行中" : "已结束") + "] " + job.processed + "/" + job.total
                + "；已归块=" + job.restored + "，已消失=" + job.alreadyAbsent
                + "，保留=" + job.retained.size() + "，等待事务恢复=" + job.recoveryPending;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void stopping(ServerStoppingEvent event) {
        var job = JOBS.remove(event.getServer());
        if (job != null) release(job);
    }

    private static final class Job {
        final UUID id = UUID.randomUUID();
        final CommandSourceStack source;
        final Deque<StructureRef> queue;
        final List<ChunkPos> chunks = new ArrayList<>();
        final Map<String, String> retained = new LinkedHashMap<>();
        final int total;
        StructureRef current;
        ServerLevel host;
        long started;
        int processed, restored, alreadyAbsent, recoveryPending;
        boolean running = true;
        String detail = "restoration_target_or_content_unavailable";
        Job(CommandSourceStack source, List<StructureRef> references) {
            this.source = source; queue = new ArrayDeque<>(references); total = references.size();
        }
        Status status() { return new Status(id, total, processed, restored, alreadyAbsent, recoveryPending, retained, running); }
    }
}
