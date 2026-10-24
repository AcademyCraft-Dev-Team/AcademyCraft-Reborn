package org.academy.internal.common.structure.space;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;

/** Non-persistent tickets; each exclusive storage owner or host terrain cache manages its lifetime. */
public final class NativeStructureTickets {
    public static final DeferredRegister<TicketType> TICKET_TYPES =
            DeferredRegister.create(Registries.TICKET_TYPE, AcademyCraft.MOD_ID);
    public static final DeferredHolder<TicketType, TicketType> ACTIVE = TICKET_TYPES.register(
            "native_structure_active",
            () -> new TicketType(TicketType.NO_TIMEOUT,
                    TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
    public static final DeferredHolder<TicketType, TicketType> SUBLEVEL_COLLISION = TICKET_TYPES.register(
            "sublevel_collision",
            () -> new TicketType(TicketType.NO_TIMEOUT,
                    TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
    public static final int TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    public static final DeferredHolder<TicketType, TicketType> RESTORATION = TICKET_TYPES.register(
            "sublevel_restoration", () -> new TicketType(TicketType.NO_TIMEOUT,
                    TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
    public static final int COLLISION_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.FULL);

    private NativeStructureTickets() {
    }

    /** Adds exactly one (type, chunk, level) ticket; callers separately await actual chunk residency. */
    public static boolean acquire(ServerLevel level, ChunkPos position) {
        return storage(level).addTicket(position.pack(), new Ticket(ACTIVE.get(), TICKET_LEVEL));
    }

    /** Symmetric removal; no radius conversion, persistent forced-chunk entry, or reference count. */
    public static boolean release(ServerLevel level, ChunkPos position) {
        return storage(level).removeTicket(position.pack(), new Ticket(ACTIVE.get(), TICKET_LEVEL));
    }

    /** Holds host terrain at FULL without ticking its blocks/entities or persisting a forced chunk. */
    public static boolean acquireCollision(ServerLevel level, ChunkPos position) {
        return hostStorage(level).addTicket(position.pack(),
                new Ticket(SUBLEVEL_COLLISION.get(), COLLISION_TICKET_LEVEL));
    }

    public static boolean releaseCollision(ServerLevel level, ChunkPos position) {
        return hostStorage(level).removeTicket(position.pack(),
                new Ticket(SUBLEVEL_COLLISION.get(), COLLISION_TICKET_LEVEL));
    }

    private static TicketStorage storage(ServerLevel level) {
        if (!NativeStructureDimensions.isStorageLevel(level.dimension())) {
            throw new IllegalArgumentException("Native structure tickets require a reserved dimension");
        }
        return hostStorage(level);
    }

    /** Exclusive batch-maintenance ticket; it cannot remove the physics terrain cache's ticket. */
    public static void restoration(ServerLevel level, ChunkPos position, boolean acquire) {
        var ticket = new Ticket(RESTORATION.get(), COLLISION_TICKET_LEVEL);
        if (acquire) hostStorage(level).addTicket(position.pack(), ticket);
        else hostStorage(level).removeTicket(position.pack(), ticket);
    }

    private static TicketStorage hostStorage(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Native structure tickets must be changed on the server thread");
        }
        return level.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
    }
}
