package org.academy.api.common.structure.upgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.upgrade.launch.LiveStructureLaunches;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Server-only issuance. A launch claim is attribution, never authority by itself. */
public final class StructureLaunches {
    public static final Identifier PLAYER_PRESENT = Identifier.parse("academy:player_present_v1");
    public static final int MAX_LIFETIME_TICKS = 72_000;
    private StructureLaunches() {}

    /** Register once per server from trusted initialization; unknown/replaced versions fail closed. */
    public static void registerPolicy(MinecraftServer server, Identifier id, int version, Policy policy) {
        LiveStructureLaunches.registerPolicy(server, id, version, policy);
    }

    /**
     * Persist before returning a claim. One launch may admit one damaging query/path/cut;
     * an admitted effect is never automatically replayed after restart, even if interrupted.
     * A structure source must be loaded and carry matching committed skill provenance.
     */
    public static StructureOperation.Result<StructureImpactFeed.LaunchAuthority> issue(
            StructureOperation.ActorContext actor, Identifier policy, Optional<UUID> owner,
            int lifetimeTicks, Optional<StructureRef> source) {
        return LiveStructureLaunches.issue(actor, policy, owner, lifetimeTicks, source);
    }

    /** Trusted server cancellation hook. Success means the revocation is durable. */
    public static boolean revoke(ServerLevel host, UUID launchId) {
        return LiveStructureLaunches.revoke(host, launchId);
    }

    public record Source(StructureRef reference, UUID creationId) {
        public Source {
            Objects.requireNonNull(reference); Objects.requireNonNull(creationId);
            reference = new StructureRef(reference.id(), reference.dimension(), reference.backend(), 0);
        }
    }

    /** Immutable persisted facts; constructing this record cannot issue or amend a grant. */
    public record Grant(UUID id, Identifier dimension, UUID actorId, Identifier actorType,
                        Optional<UUID> ownerId, Identifier policyId, int policyVersion,
                        long createdTick, long expiresTick, Optional<Source> source) {
        public Grant {
            Objects.requireNonNull(id); Objects.requireNonNull(dimension); Objects.requireNonNull(actorId);
            Objects.requireNonNull(actorType); Objects.requireNonNull(ownerId); Objects.requireNonNull(policyId);
            Objects.requireNonNull(source);
            if (policyVersion < 1 || createdTick < 0 || expiresTick <= createdTick
                    || expiresTick - createdTick > MAX_LIFETIME_TICKS
                    || source.filter(value -> !value.reference().dimension().equals(dimension)).isPresent()) {
                throw new IllegalArgumentException("Invalid bounded launch grant");
            }
        }
    }

    /**
     * Explicit permission adapter for an integration, invoked only on the server thread.
     * Implement all three checks; cell permission must consult that integration's protection
     * system using the HOST position. Offline/absent actors require an explicit policy decision.
     * A cancellable StructureLaunchBreakEvent and global dimension denial still apply.
     */
    public interface Policy {
        boolean mayIssue(StructureOperation.ActorContext actor, Grant proposed);
        boolean mayUse(ServerLevel host, Grant grant, @Nullable Entity currentActor);
        boolean mayBreak(ServerLevel host, Grant grant, @Nullable Entity currentActor, BlockPos world, BlockState state);
    }
}
