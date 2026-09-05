package org.academy.internal.common.world.damagesource;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.damage.ReactionSlowdown;
import org.academy.api.server.time.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Multiple casters share one field. Each handle releases only its own contribution. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ReactionSlowdownRuntime {
    private static final Map<UUID, Shared> ACTIVE = new HashMap<>();
    private ReactionSlowdownRuntime() {}

    public static ReactionSlowdown acquire(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel) || !target.isAlive()) {
            throw new IllegalArgumentException("Reaction slowdown requires a living server entity");
        }
        var id = target.getUUID();
        var state = ACTIVE.computeIfAbsent(id, _ -> new Shared(
                TemporalApi.get(target).acquireField(TemporalField.scale(
                        TemporalScope.entities(Set.of(id)),
                        Set.of(TemporalChannel.ENTITY, TemporalChannel.ACADEMY_SCHEDULER),
                        2.0D / 3.0D))));
        state.references++;
        return new ReactionSlowdown() {
            private boolean closed;
            @Override public void close() {
                if (closed || state.closed) return;
                closed = true;
                if (--state.references == 0) {
                    ACTIVE.remove(id, state);
                    state.close();
                }
            }
        };
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        var state = ACTIVE.remove(event.getEntity().getUUID());
        if (state != null) state.close();
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        ACTIVE.values().forEach(Shared::close);
        ACTIVE.clear();
    }

    private static final class Shared {
        final TemporalFieldLease lease;
        int references;
        boolean closed;
        Shared(TemporalFieldLease lease) { this.lease = lease; }
        void close() {
            if (closed) return;
            closed = true;
            if (lease.isActive()) lease.close();
        }
    }
}
