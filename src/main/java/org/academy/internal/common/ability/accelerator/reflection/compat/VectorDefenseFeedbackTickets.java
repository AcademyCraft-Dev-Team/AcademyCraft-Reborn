package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VectorDefenseFeedbackTickets {
    private static final long SERVER_TICKET_TICKS = 2L;
    private static final Map<UUID, Ticket> TICKETS = new HashMap<>();

    private VectorDefenseFeedbackTickets() {
    }

    public static synchronized void commitFull(ServerPlayer defender, DamageSource source) {
        if (defender == null) return;
        var now = defender.level().getGameTime();
        TICKETS.put(defender.getUUID(), new Ticket(
                now,
                now + SERVER_TICKET_TICKS,
                source == null ? "" : VectorCompatProfile.damageTypeId(source),
                source == null ? 0 : entityId(source.getEntity()),
                source == null ? 0 : entityId(source.getDirectEntity())
        ));
        VectorDefenseFeedbackPacket.broadcast(defender, now);
    }

    public static synchronized boolean shouldSuppressDamage(ServerPlayer defender, DamageSource source) {
        var ticket = validTicket(defender);
        if (ticket == null
                || ticket.committedAtTick != defender.level().getGameTime()
                || ticket.damageEventConsumed
                || source == null
                || ticket.damageTypeId.isEmpty()) return false;
        var matches = ticket.damageTypeId.equals(VectorCompatProfile.damageTypeId(source))
                && ticket.causingEntityId == entityId(source.getEntity())
                && ticket.directEntityId == entityId(source.getDirectEntity());
        if (matches) ticket.damageEventConsumed = true;
        return matches;
    }

    public static synchronized boolean shouldSuppressEntityEvent(ServerPlayer defender) {
        var ticket = validTicket(defender);
        if (ticket == null
                || ticket.committedAtTick != defender.level().getGameTime()
                || ticket.entityEventConsumed) return false;
        ticket.entityEventConsumed = true;
        return true;
    }

    public static synchronized void clear(ServerPlayer defender) {
        if (defender != null) TICKETS.remove(defender.getUUID());
    }

    private static Ticket validTicket(ServerPlayer defender) {
        if (defender == null) return null;
        var ticket = TICKETS.get(defender.getUUID());
        if (ticket == null) return null;
        if (ticket.expiresAtTick < defender.level().getGameTime()) {
            TICKETS.remove(defender.getUUID(), ticket);
            return null;
        }
        return ticket;
    }

    private static int entityId(Entity entity) {
        return entity == null ? 0 : entity.getId();
    }

    private static final class Ticket {
        private final long committedAtTick;
        private final long expiresAtTick;
        private final String damageTypeId;
        private final int causingEntityId;
        private final int directEntityId;
        private boolean damageEventConsumed;
        private boolean entityEventConsumed;

        private Ticket(
                long committedAtTick,
                long expiresAtTick,
                String damageTypeId,
                int causingEntityId,
                int directEntityId
        ) {
            this.committedAtTick = committedAtTick;
            this.expiresAtTick = expiresAtTick;
            this.damageTypeId = damageTypeId;
            this.causingEntityId = causingEntityId;
            this.directEntityId = directEntityId;
        }
    }
}
