package org.academy.api.common.entitycontrol;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.WeakHashMap;

/** Shared across dimensions and all controlled-navigation entry points. Server thread only. */
public final class WorkScheduling {
    private static final WeakHashMap<MinecraftServer, WorkBudget> BUDGETS = new WeakHashMap<>();
    private WorkScheduling() {}
    public static WorkBudget budget(MinecraftServer server) {
        var budget = BUDGETS.computeIfAbsent(server, ignored -> new WorkBudget());
        // World time advances inside the tick, between the task and entity-AI phases.
        budget.beginTick(Integer.toUnsignedLong(server.getTickCount()));
        return budget;
    }
    public static WorkBudget budget(ServerLevel level) { return budget(level.getServer()); }
    public static void clear(MinecraftServer server) { BUDGETS.remove(server); }
}
