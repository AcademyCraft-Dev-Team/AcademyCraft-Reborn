package org.academy.internal.common.structure.transaction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** Server-thread ownership barrier for unpublished cell images. Locks outlive failed operations. */
@net.neoforged.fml.common.EventBusSubscriber(modid = org.academy.AcademyCraft.MOD_ID)
public final class PayloadIsolation {
    private record Cell(ServerLevel level, BlockPos pos) {}
    private static final Map<Cell, Set<UUID>> LOCKS = new HashMap<>();
    private static final ThreadLocal<Set<Cell>> WRITES = ThreadLocal.withInitial(HashSet::new);
    private PayloadIsolation() {}

    public static void lock(ServerLevel level, Collection<BlockPos> positions, UUID owner) {
        for (var pos : positions) LOCKS.computeIfAbsent(new Cell(level, pos.immutable()), ignored -> new HashSet<>()).add(owner);
    }
    public static void release(MinecraftServer server, UUID owner) {
        LOCKS.entrySet().removeIf(entry -> entry.getKey().level().getServer() == server && entry.getValue().remove(owner) && entry.getValue().isEmpty());
    }
    public static void stop(MinecraftServer server) { LOCKS.keySet().removeIf(cell -> cell.level().getServer() == server); }
    public static boolean locked(Level level, BlockPos pos) {
        return !LOCKS.isEmpty() && level instanceof ServerLevel server && LOCKS.containsKey(new Cell(server, pos));
    }
    public static boolean denied(Level level, BlockPos pos) {
        return level instanceof ServerLevel server && locked(level, pos) && !WRITES.get().contains(new Cell(server, pos));
    }
    public static boolean denied(BlockEntity entity) {
        return entity.getLevel() instanceof ServerLevel level && denied(level, entity.getBlockPos());
    }
    public static void write(ServerLevel level, BlockPos pos, Runnable action) {
        var cell = new Cell(level, pos.immutable());
        boolean added = WRITES.get().add(cell);
        try { action.run(); } finally { if (added) WRITES.get().remove(cell); }
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void breakBlock(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && locked(level, event.getPos())) event.setCanceled(true);
    }
    @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void drops(net.neoforged.neoforge.event.level.BlockDropsEvent event) {
        if (locked(event.getLevel(), event.getPos())) event.setCanceled(true);
    }
}
