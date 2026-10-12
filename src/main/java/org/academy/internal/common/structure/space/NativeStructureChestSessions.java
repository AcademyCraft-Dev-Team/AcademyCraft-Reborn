package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureContexts;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Server-owned menu lifetimes. The only inventory is the original block entity's inventory. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class NativeStructureChestSessions {
    private static final Map<MinecraftServer, Set<Session>> SESSIONS = new IdentityHashMap<>();
    private static final double DISTANCE_BUFFER = 4.0;

    private NativeStructureChestSessions() {
    }

    /** Called inside the caller's validated structure context and runtime write guard. */
    public static boolean open(ServerPlayer player, StructureHit hit) {
        var server = player.level().getServer();
        requireThread(server);
        var runtime = NativeStructureRuntime.backend(server);
        var reference = hit.block().structure();
        var context = StructureContexts.current().orElse(null);
        if (context == null || !context.reference().equals(reference)
                || !context.runtimeId().equals(hit.runtimeId())
                || !NativeStructureInteraction.canUseMenu(player)
                || !runtime.isAvailableForMenu(reference)) return false;
        var handle = runtime.resolve(reference).orElse(null);
        if (handle == null || !handle.runtimeId().equals(hit.runtimeId())) return false;
        var storage = runtime.storageLevel(reference).orElse(null);
        var position = hit.block().localPosition();
        if (storage == null || !storage.hasChunkAt(position)) return false;
        var state = storage.getBlockState(position);
        if (!state.is(Blocks.CHEST) || state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                || state.getValue(ChestBlock.WATERLOGGED)
                || !(storage.getBlockEntity(position) instanceof ChestBlockEntity chest)
                || chest.getClass() != ChestBlockEntity.class || chest.getLootTable() != null
                || ChestBlock.isChestBlockedAt(storage, position)) return false;
        if (!chest.canOpen(player)) {
            BaseContainerBlockEntity.sendChestLockedNotifications(
                    handle.pose().localToWorldPoint(Vec3.atCenterOf(position)), player, chest.getDisplayName());
            return false;
        }

        var session = new Session(player, reference, hit.runtimeId(), position, chest, handle.pose());
        synchronized (SESSIONS) {
            SESSIONS.computeIfAbsent(server, ignored -> new HashSet<>()).add(session);
        }
        boolean success = false;
        try {
            var opened = player.openMenu(new SimpleMenuProvider((id, inventory, actor) -> {
                if (actor != player || !session.canRead()) return null;
                var menu = new NativeStructureChestMenu(id, inventory, session);
                session.menu = menu;
                return menu;
            }, chest.getDisplayName()));
            if (opened.isEmpty() || session.closed || session.menu == null
                    || player.containerMenu != session.menu || !session.canRead()) return false;
            session.opening = false;
            // Constructor startOpen precedes installation of player.containerMenu. Reconcile
            // after open callbacks, including ones that closed/replaced this menu synchronously.
            chest.recheckOpen();
            if (viewerCount(session) == 1) session.playSound(true);
            success = true;
            return true;
        } finally {
            if (!success) session.close();
        }
    }

    /** The runtime already blocks new access while calling this before its unload save. */
    public static void closeStructure(MinecraftServer server, StructureRef reference) {
        requireThread(server);
        for (var session : snapshot(server)) {
            if (session.reference.equals(reference)) session.close();
        }
    }

    /** Return carried items before destroying this exact container, without closing other cells. */
    public static void closeBlock(MinecraftServer server, StructureRef reference, UUID runtimeId, BlockPos position) {
        requireThread(server);
        for (var session : snapshot(server)) {
            if (session.reference.equals(reference) && session.runtimeId.equals(runtimeId)
                    && session.position.equals(position)) session.close();
        }
    }

    /** Closes invalid/deferred sessions without relying on the player's simulation tick rate. */
    public static void tick(MinecraftServer server) {
        requireThread(server);
        for (var session : snapshot(server)) {
            if (!session.opening && !session.canRead()) session.close();
        }
    }

    /** Only positions and open/closed state are made available to the projection. */
    public static Set<BlockPos> openPositions(MinecraftServer server, StructureRef reference, UUID runtimeId) {
        requireThread(server);
        var positions = new HashSet<BlockPos>();
        for (var session : snapshot(server)) {
            if (session.reference.equals(reference) && session.runtimeId.equals(runtimeId)
                    && session.ownsMenu() && session.canRead()) positions.add(session.position);
        }
        return Set.copyOf(positions);
    }

    /** Additional real users for the storage chest's original ContainerOpenersCounter. */
    public static List<ContainerUser> viewers(Level level, BlockPos position) {
        var server = level.getServer();
        if (server == null || !NativeStructureDimensions.isStorageLevel(level.dimension())) return List.of();
        requireThread(server);
        var viewers = new ArrayList<ContainerUser>();
        // Count a still-installed menu until removal actually runs. Eagerly excluding an
        // out-of-range user here would make the later vanilla stopOpen decrement twice.
        for (var session : snapshot(server)) {
            if (session.ownsMenu() && session.chest.getLevel() == level
                    && session.position.equals(position) && level.hasChunkAt(position)
                    && level.getBlockEntity(position) == session.chest
                    && !viewers.contains(session.player)) viewers.add(session.player);
        }
        return List.copyOf(viewers);
    }

    static boolean withinReach(StructurePose pose, BlockPos position, Vec3 eye, double range) {
        if (!Double.isFinite(range) || range < 0 || range > 256
                || !Double.isFinite(eye.x) || !Double.isFinite(eye.y) || !Double.isFinite(eye.z)) return false;
        var maximum = range + DISTANCE_BUFFER;
        return new AABB(position).distanceToSqr(pose.worldToLocalPoint(eye)) < maximum * maximum;
    }

    private static List<Session> snapshot(MinecraftServer server) {
        synchronized (SESSIONS) {
            var sessions = SESSIONS.get(server);
            return sessions == null ? List.of() : List.copyOf(sessions);
        }
    }

    private static void remove(Session session) {
        synchronized (SESSIONS) {
            var sessions = SESSIONS.get(session.server);
            if (sessions == null) return;
            sessions.remove(session);
            if (sessions.isEmpty()) SESSIONS.remove(session.server);
        }
    }

    private static int viewerCount(Session target) {
        int count = 0;
        for (var session : snapshot(target.server)) {
            if (session.chest == target.chest && session.ownsMenu()) count++;
        }
        return count;
    }

    private static void requireThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Structure menus require the server thread");
    }

    @SubscribeEvent
    private static void onServerStopped(ServerStoppedEvent event) {
        Set<Session> sessions;
        synchronized (SESSIONS) { sessions = SESSIONS.remove(event.getServer()); }
        if (sessions != null) for (var session : sessions) session.closed = true;
    }

    static final class Session {
        final ServerPlayer player;
        final MinecraftServer server;
        final StructureRef reference;
        final UUID runtimeId;
        final BlockPos position;
        final ChestBlockEntity chest;
        @Nullable NativeStructureChestMenu menu;
        private StructurePose lastPose;
        private boolean opening = true;
        private boolean closed;
        private boolean deferredClose;
        private boolean mutating;
        private boolean soundOnClose;

        private Session(ServerPlayer player, StructureRef reference, UUID runtimeId, BlockPos position,
                        ChestBlockEntity chest, StructurePose pose) {
            this.player = player;
            this.server = player.level().getServer();
            this.reference = reference;
            this.runtimeId = runtimeId;
            this.position = position.immutable();
            this.chest = chest;
            this.lastPose = pose;
        }

        private boolean ownsMenu() {
            return !opening && !closed && menu != null && player.containerMenu == menu;
        }

        boolean canRead() {
            requireThread(server);
            if (closed || deferredClose || !opening && !ownsMenu()
                    || !NativeStructureInteraction.canMaintainMenu(player)
                    || player.level().getServer() != server
                    || !player.level().dimension().identifier().equals(reference.dimension())) return false;
            try {
                var runtime = NativeStructureRuntime.backend(server);
                if (!runtime.isAvailableForMenu(reference)) return false;
                var handle = runtime.resolve(reference).orElse(null);
                if (handle == null || !handle.runtimeId().equals(runtimeId)) return false;
                var level = runtime.storageLevel(reference).orElse(null);
                if (level == null || level != chest.getLevel() || !level.hasChunkAt(position)
                        || chest.isRemoved() || level.getBlockEntity(position) != chest) return false;
                var state = level.getBlockState(position);
                if (!state.is(Blocks.CHEST) || state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                        || state.getValue(ChestBlock.WATERLOGGED) || chest.getLootTable() != null) return false;
                lastPose = handle.pose();
                if (!withinReach(lastPose, position, player.getEyePosition(), player.blockInteractionRange())) return false;
                var worldPosition = BlockPos.containing(lastPose.localToWorldPoint(Vec3.atCenterOf(position)));
                return player.level().hasChunkAt(worldPosition)
                        && player.level().mayInteract(player, worldPosition)
                        && player.level().getWorldBorder().isWithinBounds(worldPosition);
            } catch (IllegalStateException unavailable) {
                return false;
            }
        }

        <T> T mutate(Player actor, Supplier<T> action, T refused) {
            requireThread(server);
            if (actor != player || mutating) return refused;
            if (opening || !canRead()) {
                deferredClose = true;
                return refused;
            }
            if (!NativeStructureInteraction.canUseMenu(player)) {
                if (menu != null) menu.requestResync();
                return refused;
            }
            var runtime = NativeStructureRuntime.backend(server);
            var handle = runtime.resolve(reference).orElse(null);
            if (handle == null) {
                deferredClose = true;
                return refused;
            }
            var result = new MutationResult<>(refused);
            boolean applied = runtime.runMenuInteraction(reference, () -> {
                mutating = true;
                try {
                    result.value = StructureContexts.call(handle, ignored -> action.get());
                } finally {
                    mutating = false;
                }
            });
            if (!applied) deferredClose = true;
            return applied ? result.value : refused;
        }

        void read(Runnable action) {
            requireThread(server);
            if (!canRead()) return;
            // Opening is already guarded by the interaction dispatcher. Vanilla full-state
            // synchronization also nests sendAllDataToRemote inside an existing guarded read.
            if (opening || mutating) {
                action.run();
                return;
            }
            var runtime = NativeStructureRuntime.backend(server);
            var handle = runtime.resolve(reference).orElse(null);
            if (handle == null) {
                deferredClose = true;
                return;
            }
            if (!runtime.runMenuInteraction(reference, () -> {
                mutating = true;
                try {
                    StructureContexts.run(handle, ignored -> action.run());
                } finally {
                    mutating = false;
                }
            })) deferredClose = true;
        }

        boolean deferCloseIfMutating() {
            requireThread(server);
            if (!mutating) return false;
            deferredClose = true;
            return true;
        }

        void beginRemoval() {
            if (closed) return;
            soundOnClose = !opening && viewerCount(this) <= 1;
            closed = true;
            remove(this);
        }

        void finishRemoval() {
            // A replaced/removed chest no longer owns a counter. Otherwise this also repairs
            // a recheck that occurred between invalidation and vanilla stopOpen.
            if (!chest.isRemoved()) chest.recheckOpen();
            if (soundOnClose) {
                soundOnClose = false;
                playSound(false);
            }
        }

        private void close() {
            if (closed) return;
            if (mutating) {
                deferredClose = true;
                return;
            }
            if (menu != null) {
                if (player.containerMenu == menu) player.closeContainer();
                else menu.removed(player);
            } else {
                // Construction can fail after startOpen. Recheck against installed sessions
                // instead of guessing whether its opener increment completed.
                beginRemoval();
                finishRemoval();
            }
        }

        private void playSound(boolean open) {
            var host = server.getLevel(ResourceKey.create(Registries.DIMENSION, reference.dimension()));
            if (host == null) return;
            var point = lastPose.localToWorldPoint(Vec3.atCenterOf(position));
            var block = (ChestBlock) Blocks.CHEST;
            host.playSound(null, point.x, point.y, point.z,
                    open ? block.getOpenChestSound() : block.getCloseChestSound(), SoundSource.BLOCKS,
                    0.5F, 0.9F + host.getRandom().nextFloat() * 0.1F);
        }
    }

    private static final class MutationResult<T> {
        private T value;

        private MutationResult(T value) {
            this.value = value;
        }
    }
}
