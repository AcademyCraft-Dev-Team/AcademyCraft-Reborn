package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureContexts;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructureMiningEvent;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.network.NativeStructureMiningProgressPacket;
import org.academy.internal.common.network.NativeStructureMiningProgressPacket.Status;
import org.academy.internal.common.structure.BlockStructureOrientedBox;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.mixin.common.MixinServerPlayerGameModeNativeStructureAccess;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** One server-owned mining lease per connection. The player never leaves the visible host world. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class NativeStructureMiningSessions {
    private static final Map<MinecraftServer, Map<ServerGamePacketListenerImpl, Client>> SERVERS = new IdentityHashMap<>();
    private static final double BROADCAST_RANGE_SQUARED = 64 * 64;
    private static final long PROGRESS_HEARTBEAT_TICKS = 20;

    private NativeStructureMiningSessions() {}

    /** Detached diagnostics for tools and validation; it conveys no mutation authority. */
    public record Snapshot(long requestId, StructureRef reference, UUID runtimeId, BlockPos localPosition,
                           float progress, int stage, boolean ready) {}

    public static Optional<Snapshot> snapshot(ServerPlayer player) {
        requireThread(player.level().getServer());
        synchronized (SERVERS) {
            var clients = SERVERS.get(player.level().getServer());
            var client = clients == null ? null : clients.get(player.connection);
            var session = client == null ? null : client.session;
            if (session == null || session.player != player) return Optional.empty();
            return Optional.of(new Snapshot(session.requestId, session.handle.reference(), session.initialHit.runtimeId(),
                    session.position, (float) session.progress.value(), session.progress.stage(), session.progress.ready()));
        }
    }

    public static void start(ServerPlayer player, long requestId, StructureHit proposed) {
        Objects.requireNonNull(proposed, "proposed");
        var server = player.level().getServer();
        requireThread(server);
        if (requestId <= 0 || player.connection == null) return;
        var client = client(player);
        if (client.dispatching || requestId <= client.highestRequest) {
            reject(player, client, requestId, proposed);
            return;
        }
        client.highestRequest = requestId;
        if (client.session != null) end(client.session, Status.ABORTED);
        var runtime = NativeStructureRuntime.backend(server);
        var handle = runtime.resolve(proposed.block().structure()).orElse(null);
        if (handle == null || !runtime.isPresented(handle.reference()) || !canMaintain(player)
                || !NativeStructureInteraction.canUseMenu(player)) {
            reject(player, client, requestId, proposed);
            return;
        }
        var storage = runtime.storageLevel(handle.reference()).orElse(null);
        if (storage == null) {
            reject(player, client, requestId, proposed);
            return;
        }
        Session[] created = {null};
        client.dispatching = true;
        boolean ran;
        try {
            ran = runtime.runMenuInteraction(handle.reference(), () -> StructureContexts.run(handle, context -> {
                var receipt = NativeStructurePlayerTarget.capture(player, proposed);
                var hit = receipt == null ? null : receipt.hit();
                if (hit == null || !canMaintain(player) || !NativeStructureInteraction.canUseMenu(player)
                        || !handle.runtimeId().equals(hit.runtimeId())) return;
                var pos = hit.block().localPosition();
                if (!localLoaded(storage, pos)) return;
                var before = handle.world().read(pos);
                var blockEntity = storage.getBlockEntity(pos);
                if (!NativeStructureMiningPolicy.canMine(before.state(), blockEntity)
                        || !hostAllows(player, player.level(), handle, pos)) return;
                var session = new Session(client, player, requestId, handle, storage, hit, before, blockEntity);
                session.observation = proposed;
                session.receipt = receipt;
                created[0] = session;
                client.session = session;
                if (session.currentHit() == null) return;
                var event = new StructureMiningEvent(player, hit, context, StructureMiningEvent.Phase.START,
                        before, session.expectedTool);
                if (NeoForge.EVENT_BUS.post(event).isCanceled() || session.currentHit() == null) return;
                if (!advance(session)) return;
                session.started = true;
            }));
        } finally {
            client.dispatching = false;
        }
        var session = created[0];
        if (session != null) session.receipt = null;
        if (!ran || session == null || !session.started || session.cancelRequested) {
            if (session != null) end(session, Status.REJECTED);
            else reject(player, client, requestId, proposed);
            return;
        }
        publish(session, session.progress.ready() ? Status.READY : Status.STARTED, session.progress.stage());
        if (session.gameType == GameType.CREATIVE) finish(session);
    }

    /** A heartbeat renews only a still-live lease and never adds mining work. */
    public static void hold(ServerPlayer player, long requestId) {
        var session = matching(player, requestId);
        if (session == null || session.client.dispatching) return;
        // Trusted in-process callers use the current actor ray. Network callers must supply an observation.
        session.observation = null;
        renew(session, false);
    }

    public static void hold(ServerPlayer player, long requestId, StructureHit observation) {
        continueObserved(player, requestId, observation, false);
    }

    public static void stop(ServerPlayer player, long requestId, StructureHit observation) {
        continueObserved(player, requestId, observation, true);
    }

    private static void continueObserved(ServerPlayer player, long requestId, StructureHit observation, boolean stop) {
        Objects.requireNonNull(observation, "observation");
        var session = matching(player, requestId);
        if (session == null || session.client.dispatching) return;
        if (!observation.block().equals(session.initialHit.block())
                || !observation.runtimeId().equals(session.initialHit.runtimeId())
                || session.observation != null && observation.geometryRevision() < session.observation.geometryRevision()) {
            end(session, Status.ABORTED);
            return;
        }
        session.observation = observation;
        if (session.currentHit() == null) end(session, Status.ABORTED);
        else renew(session, stop);
    }

    private static void renew(Session session, boolean stop) {
        var player = session.player;
        long now = tickNumber(player.level().getServer());
        if (session.progress.expired(now)) end(session, Status.ABORTED);
        else {
            if (stop) session.progress.stop();
            session.progress.hold(now);
        }
    }

    /** Early STOP may wait for genuine server work; it never grants vanilla's 0.7 shortcut. */
    public static void stop(ServerPlayer player, long requestId) {
        var session = matching(player, requestId);
        if (session == null || session.client.dispatching) return;
        session.observation = null;
        renew(session, true);
        // Commit at the normal tick point after projections refresh. A pose update earlier
        // in this packet tick must not make a valid local-cell lease look temporarily stale.
    }

    /** Cancellation remains available on a paused action tick. */
    public static void abort(ServerPlayer player, long requestId) {
        var session = matching(player, requestId);
        if (session == null) return;
        session.cancelRequested = true;
        if (!session.client.dispatching) end(session, Status.ABORTED);
    }

    /** Called once after native projections have been refreshed for the server tick. */
    public static void tick(MinecraftServer server) {
        requireThread(server);
        for (var entry : clients(server)) {
            var client = entry.getValue();
            var session = client.session;
            if (session == null) continue;
            if (client.dispatching) continue;
            if (!entry.getKey().isAcceptingMessages() || session.progress.expired(tickNumber(server))) {
                end(session, Status.ABORTED);
                continue;
            }
            var runtime = NativeStructureRuntime.backend(server);
            boolean[] valid = {false};
            client.dispatching = true;
            boolean ran;
            try {
                ran = runtime.runMenuInteraction(session.handle.reference(), () -> StructureContexts.run(session.handle,
                        ignored -> valid[0] = advance(session)));
            } finally {
                client.dispatching = false;
            }
            if (!ran || !valid[0] || session.cancelRequested) {
                end(session, Status.ABORTED);
                continue;
            }
            var status = session.progress.ready() ? Status.READY : Status.PROGRESS;
            if (session.lastStage != session.progress.stage() || session.lastStatus != status
                    || tickNumber(server) - session.lastPublished >= PROGRESS_HEARTBEAT_TICKS) {
                publish(session, status, session.progress.stage());
            }
            if (session.progress.shouldFinish() && NativeStructureInteraction.canUseMenu(session.player)) finish(session);
        }
        synchronized (SERVERS) {
            var clients = SERVERS.get(server);
            if (clients != null) {
                clients.entrySet().removeIf(entry -> !entry.getKey().isAcceptingMessages() && entry.getValue().session == null);
                if (clients.isEmpty()) SERVERS.remove(server);
            }
        }
    }

    public static void closeStructure(MinecraftServer server, StructureRef reference) {
        requireThread(server);
        for (var entry : clients(server)) {
            var session = entry.getValue().session;
            if (session == null || !session.handle.reference().equals(reference)) continue;
            session.cancelRequested = true;
            if (!session.client.dispatching) end(session, Status.ABORTED);
        }
    }

    private static boolean advance(Session session) {
        boolean pinnedHere = session.receipt == null;
        if (pinnedHere && !session.pinTarget()) return false;
        try {
            return advancePinned(session);
        } finally {
            if (pinnedHere) session.receipt = null;
        }
    }

    private static boolean advancePinned(Session session) {
        if (session.currentHit() == null) return false;
        long now = tickNumber(session.server);
        if (!NativeStructureInteraction.canUseMenu(session.player)) {
            session.progress.advance(now, false, 0);
            return true;
        }
        var state = session.before.state();
        float hardness = state.getDestroySpeed(session.storage, session.position);
        if (!Float.isFinite(hardness) || hardness < 0 && session.gameType != GameType.CREATIVE) return false;
        var tool = session.player.getMainHandItem();
        if (!tool.isEmpty() && !tool.canDestroyBlock(state, session.storage, session.position, session.player)) return false;
        boolean harvest = state.canHarvestBlock(session.storage, session.position, session.player);
        // BreakSpeed carries a player and optional position, but no level. Do not give it a
        // local storage coordinate that listeners could interpret in player.level().
        float speed = session.player.getDestroySpeed(state, null);
        double delta = session.gameType == GameType.CREATIVE ? 1
                : NativeStructureMiningProgress.increment(hardness, speed, harvest);
        if (session.currentHit() == null || !NativeStructureInteraction.canUseMenu(session.player)) return false;
        session.progress.advance(now, true, delta);
        return true;
    }

    private static void finish(Session session) {
        if (session.client.dispatching || session.client.session != session || session.cancelRequested
                || !session.progress.ready() || !NativeStructureInteraction.canUseMenu(session.player)) return;
        session.client.dispatching = true;
        boolean completed = false;
        try {
            if (!session.pinTarget()) return;
            var hit = session.currentHit();
            if (hit != null) {
                completed = NativeStructureMining.destroy(session.player, hit,
                        () -> NativeStructureInteraction.canUseMenu(session.player) ? session.currentHit() : null,
                        session.receipt::revalidate);
            }
        } finally {
            session.receipt = null;
            session.client.dispatching = false;
            end(session, completed ? Status.COMPLETED : Status.REJECTED);
        }
    }

    private static boolean canMaintain(ServerPlayer player) {
        if (!NativeStructureInteraction.canMaintainMenu(player) || player.isUsingItem()
                || player.containerMenu != player.inventoryMenu || !player.connection.isAcceptingMessages()
                || player.connection.getPlayer() != player || !NativeStructureMiningPolicy.canUseTool(player.getMainHandItem())
                || !player.getMainHandItem().isItemEnabled(player.level().enabledFeatures())
                || player.getCooldowns().isOnCooldown(player.getMainHandItem())) return false;
        var gameMode = (MixinServerPlayerGameModeNativeStructureAccess) player.gameMode;
        return !gameMode.academy$isDestroyingBlock() && !gameMode.academy$hasDelayedDestroy();
    }

    private static boolean hostAllows(ServerPlayer player, ServerLevel host, StructureHandle handle, BlockPos position) {
        var bounds = BlockStructureOrientedBox.transform(new AABB(position), handle.pose()).bounds();
        if (bounds.minX < -30_000_000 || bounds.maxX > 30_000_000
                || bounds.minZ < -30_000_000 || bounds.maxZ > 30_000_000) return false;
        int minX = Mth.floor(bounds.minX), minY = Mth.floor(bounds.minY), minZ = Mth.floor(bounds.minZ);
        int maxX = Mth.floor(Math.nextDown(bounds.maxX)), maxY = Mth.floor(Math.nextDown(bounds.maxY));
        int maxZ = Mth.floor(Math.nextDown(bounds.maxZ));
        if ((long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > 64) return false;
        for (var cell : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (host.isOutsideBuildHeight(cell) || !host.hasChunkAt(cell)
                    || !host.getWorldBorder().isWithinBounds(cell) || !host.mayInteract(player, cell)) return false;
        }
        return true;
    }

    private static boolean localLoaded(ServerLevel storage, BlockPos position) {
        return NativeStructureDimensions.contains(position) && !storage.isOutsideBuildHeight(position)
                && storage.hasChunkAt(position);
    }

    private static Client client(ServerPlayer player) {
        synchronized (SERVERS) {
            return SERVERS.computeIfAbsent(player.level().getServer(), ignored -> new IdentityHashMap<>())
                    .computeIfAbsent(player.connection, ignored -> new Client());
        }
    }

    private static @Nullable Session matching(ServerPlayer player, long requestId) {
        requireThread(player.level().getServer());
        if (requestId <= 0 || player.connection == null) return null;
        synchronized (SERVERS) {
            var clients = SERVERS.get(player.level().getServer());
            var client = clients == null ? null : clients.get(player.connection);
            var session = client == null ? null : client.session;
            return session != null && session.player == player && session.requestId == requestId ? session : null;
        }
    }

    private static List<Map.Entry<ServerGamePacketListenerImpl, Client>> clients(MinecraftServer server) {
        synchronized (SERVERS) {
            var clients = SERVERS.get(server);
            if (clients == null) return List.of();
            var copy = new ArrayList<Map.Entry<ServerGamePacketListenerImpl, Client>>();
            clients.forEach((connection, client) -> copy.add(Map.entry(connection, client)));
            return copy;
        }
    }

    private static void end(Session session, Status status) {
        if (session.client.session != session) return;
        session.client.session = null;
        session.cancelRequested = true;
        publish(session, status, -1);
    }

    private static void reject(ServerPlayer player, Client client, long requestId, StructureHit hit) {
        if (!player.connection.isAcceptingMessages()) return;
        NativeStructureMiningProgressPacket.send(player, requestId, client.nextUpdate(), player.getId(),
                hit.block().structure(), hit.runtimeId(), hit.block().localPosition(), 0, -1, Status.REJECTED);
    }

    private static void publish(Session session, Status status, int stage) {
        long sequence = session.client.nextUpdate();
        var point = session.lastWorldPoint;
        var recipients = Collections.newSetFromMap(new IdentityHashMap<ServerPlayer, Boolean>());
        recipients.add(session.player);
        for (var player : session.host.players()) {
            if (player.position().distanceToSqr(point) <= BROADCAST_RANGE_SQUARED) recipients.add(player);
        }
        session.recipients.removeIf(player -> player.connection == null || !player.connection.isAcceptingMessages()
                || player.connection.getPlayer() != player);
        // A recipient that moved away still needs the clear packet for its previous projection.
        if (stage < 0) recipients.addAll(session.recipients);
        else session.recipients.addAll(recipients);
        for (var recipient : recipients) {
            if (recipient.connection != null && recipient.connection.isAcceptingMessages()) {
                NativeStructureMiningProgressPacket.send(recipient, session.requestId, sequence, session.player.getId(),
                        session.handle.reference(), session.initialHit.runtimeId(), session.position,
                        Block.getId(session.before.state()), stage, status);
            }
        }
        session.lastStage = stage;
        session.lastStatus = status;
        session.lastPublished = tickNumber(session.server);
    }

    private static long tickNumber(MinecraftServer server) { return Integer.toUnsignedLong(server.getTickCount()); }
    private static void requireThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Structure mining requires the server thread");
    }

    @SubscribeEvent
    private static void stopped(ServerStoppedEvent event) {
        synchronized (SERVERS) { SERVERS.remove(event.getServer()); }
    }

    private static final class Client {
        private long highestRequest;
        private long updateSequence;
        private boolean dispatching;
        private @Nullable Session session;

        private long nextUpdate() { return updateSequence = Math.incrementExact(updateSequence); }
    }

    private static final class Session {
        private final Client client;
        private final ServerPlayer player;
        private final MinecraftServer server;
        private final ServerLevel host;
        private final long requestId;
        private final StructureHandle handle;
        private final ServerLevel storage;
        private final StructureHit initialHit;
        private final BlockPos position;
        private final StructureBlockData before;
        private final @Nullable BlockEntity blockEntity;
        private final long contentRevision;
        private final ItemStack tool;
        private final ItemStack expectedTool;
        private final int selectedSlot;
        private final GameType gameType;
        private final NativeStructureMiningProgress progress;
        private final Set<ServerPlayer> recipients = Collections.newSetFromMap(new IdentityHashMap<>());
        private boolean started;
        private boolean cancelRequested;
        private @Nullable StructureHit observation;
        private NativeStructurePlayerTarget.@Nullable Observation receipt;
        private Vec3 lastWorldPoint;
        private int lastStage = -1;
        private Status lastStatus = Status.STARTED;
        private long lastPublished;

        private Session(Client client, ServerPlayer player, long requestId, StructureHandle handle,
                        ServerLevel storage, StructureHit hit, StructureBlockData before, @Nullable BlockEntity entity) {
            this.client = client;
            this.player = player;
            server = player.level().getServer();
            host = player.level();
            this.requestId = requestId;
            this.handle = handle;
            this.storage = storage;
            initialHit = hit;
            position = hit.block().localPosition();
            this.before = before;
            blockEntity = entity;
            contentRevision = NativeStructureChanges.revision(storage);
            tool = player.getMainHandItem();
            expectedTool = tool.copy();
            selectedSlot = player.getInventory().getSelectedSlot();
            gameType = player.gameMode.getGameModeForPlayer();
            progress = new NativeStructureMiningProgress(tickNumber(server));
            lastWorldPoint = hit.worldPoint();
        }

        private boolean unchanged() {
            if (cancelRequested || client.session != this || player.level() != host || !canMaintain(player)
                    || player.gameMode.getGameModeForPlayer() != gameType
                    || player.getInventory().getSelectedSlot() != selectedSlot || player.getMainHandItem() != tool
                    || !ItemStack.matches(tool, expectedTool) || !localLoaded(storage, position)
                    || NativeStructureChanges.revision(storage) != contentRevision) return false;
            var runtime = NativeStructureRuntime.backend(server);
            return runtime.resolve(handle.reference()).orElse(null) == handle
                    && handle.runtimeId().equals(initialHit.runtimeId()) && runtime.isAvailableForMenu(handle.reference())
                    && runtime.storageLevel(handle.reference()).orElse(null) == storage
                    && storage.getBlockEntity(position) == blockEntity
                    && before.equals(handle.world().read(position))
                    && NativeStructureMiningPolicy.canMine(before.state(), blockEntity);
        }

        private @Nullable StructureHit currentHit() {
            try {
                if (!unchanged()) return null;
                var hit = receipt == null ? NativeStructurePlayerTarget.current(player, observation) : receipt.revalidate();
                if (hit == null || !hit.block().equals(initialHit.block()) || !hit.runtimeId().equals(initialHit.runtimeId())
                        || !hostAllows(player, host, handle, position) || !unchanged()) return null;
                lastWorldPoint = hit.worldPoint();
                return hit;
            } catch (IllegalStateException unavailable) {
                return null;
            }
        }

        private boolean pinTarget() {
            var proposed = observation == null ? NativeStructurePlayerTarget.current(player, null) : observation;
            if (proposed == null) return false;
            receipt = NativeStructurePlayerTarget.capture(player, proposed);
            return receipt != null;
        }
    }
}
