package org.academy.api.common.structure.upgrade;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureProvenance.Metadata;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Server-thread protection hook before automatic or administrative maintenance writes. Offline owners are UUID
 * attribution, never fabricated players. Cancelling retains the complete physical structure.
 */
public final class StructureRestorationEvent extends Event implements ICancellableEvent {
    private final ServerLevel host;
    private final StructureRef reference;
    private final @Nullable Metadata metadata;
    private final List<StructureGridRestoration.Placement> placements;
    private final boolean offlineOwner;
    private final StructureGridRestoration.Cause cause;
    private final Optional<UUID> actor;

    public StructureRestorationEvent(ServerLevel host, StructureRef reference, StructureProvenance.Metadata metadata,
            List<StructureGridRestoration.Placement> placements, boolean offlineOwner) {
        this(host, reference, metadata, placements, offlineOwner, StructureGridRestoration.Cause.MARKED_REST,
                metadata.ownerId());
    }

    /** Administrative requests may target an unmarked structure; no skill provenance is fabricated. */
    public StructureRestorationEvent(ServerLevel host, StructureRef reference, @Nullable Metadata metadata,
            List<StructureGridRestoration.Placement> placements, boolean offlineOwner,
            StructureGridRestoration.Cause cause, Optional<UUID> actor) {
        this.host = host;
        this.reference = reference;
        this.metadata = metadata;
        this.placements = List.copyOf(placements);
        this.offlineOwner = offlineOwner;
        this.cause = cause;
        this.actor = actor;
    }

    public ServerLevel host() { return host; }
    public StructureRef reference() { return reference; }
    public @Nullable Metadata metadata() { return metadata; }
    public Optional<StructureProvenance.Metadata> provenance() { return Optional.ofNullable(metadata); }
    public StructureGridRestoration.Cause cause() { return cause; }
    /** Initiating player/owner, empty for server-console maintenance. It need not be the creation owner. */
    public Optional<UUID> actor() { return actor; }
    public List<StructureGridRestoration.Placement> placements() { return placements; }
    public boolean offlineOwner() { return offlineOwner; }
}
