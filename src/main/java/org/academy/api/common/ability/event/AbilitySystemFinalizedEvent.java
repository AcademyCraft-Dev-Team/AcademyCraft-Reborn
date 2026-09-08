package org.academy.api.common.ability.event;

import net.neoforged.bus.api.Event;

/**
 * Compatibility notification during setup. For frozen registrations use AcademyRegistrationsReadyEvent.
 */
@Deprecated
public final class AbilitySystemFinalizedEvent extends Event {
}
