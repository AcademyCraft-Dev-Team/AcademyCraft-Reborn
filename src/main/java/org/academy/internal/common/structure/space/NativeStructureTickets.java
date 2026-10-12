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

/** Non-persistent simulation tickets; the exclusive slot owner manages their lifetime. */
public final class NativeStructureTickets {
    public static final DeferredRegister<TicketType> TICKET_TYPES =
            DeferredRegister.create(Registries.TICKET_TYPE, AcademyCraft.MOD_ID);
    public static final DeferredHolder<TicketType, TicketType> ACTIVE = TICKET_TYPES.register(
            "native_structure_active",
            () -> new TicketType(TicketType.NO_TIMEOUT,
                    TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
    public static final int TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);

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

    private static TicketStorage storage(ServerLevel level) {
        if (!NativeStructureDimensions.isStorageLevel(level.dimension())) {
            throw new IllegalArgumentException("Native structure tickets require a reserved dimension");
        }
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Native structure tickets must be changed on the server thread");
        }
        return level.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
    }
}
