package org.academy.api.common.registries;

import net.neoforged.bus.api.Event;

/**
 * Read-only notification on NeoForge.EVENT_BUS after static declarations are resolved and frozen.
 * This does not imply that any world's dynamic DamageType registry is available.
 */
public final class AcademyRegistrationsReadyEvent extends Event {
}
