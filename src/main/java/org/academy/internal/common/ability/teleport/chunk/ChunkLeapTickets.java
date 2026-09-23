package org.academy.internal.common.ability.teleport.chunk;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.TicketType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;

/**
 * Registers the ticket type used to keep 区块跃迁 map views loaded.
 *
 * <p>The ticket carries {@code FLAG_LOADING} only. It loads chunks to FULL and lets them be read,
 * but does not make them tick entities and — unlike {@code TicketType.FORCED} — is never written to
 * the level's {@code chunks.dat}, so opening the map does not permanently mark the world explored.
 */
public final class ChunkLeapTickets {
    public static final DeferredRegister<TicketType> TICKET_TYPES =
            DeferredRegister.create(Registries.TICKET_TYPE, AcademyCraft.MOD_ID);

    public static final DeferredHolder<TicketType, TicketType> MAP_VIEW = TICKET_TYPES.register(
            "chunk_leap_view",
            () -> new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));

    private ChunkLeapTickets() {
    }
}
