package org.academy.api.server.ability;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Server-side admission hook, posted before CP reservation. Does not replace player skill events. */
public final class EntityAbilityCastEvent extends Event implements ICancellableEvent {
    private final LivingEntity actor;
    private final AbilityCastRequest request;
    public EntityAbilityCastEvent(LivingEntity actor, AbilityCastRequest request) {
        this.actor = actor;
        this.request = request;
    }
    public LivingEntity actor() { return actor; }
    public AbilityCastRequest request() { return request; }
}
