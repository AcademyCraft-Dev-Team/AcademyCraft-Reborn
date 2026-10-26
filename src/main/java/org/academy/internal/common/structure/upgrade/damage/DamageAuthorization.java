package org.academy.internal.common.structure.upgrade.damage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.api.common.structure.SpaceBlockRef;
import org.academy.api.common.structure.upgrade.*;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.ability.AbilityEffectPolicy;
import org.academy.internal.common.structure.upgrade.launch.LiveStructureLaunches;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** Runtime view of an active player or a server-issued launch; never creates player entities. */
final class DamageAuthorization {
    private final StructureDamage.Authority authority;
    private final ServerLevel host;
    private final UUID budgetPrincipal;
    private DamageAuthorization(StructureDamage.Authority authority, ServerLevel host, UUID budgetPrincipal) {
        this.authority = authority; this.host = host; this.budgetPrincipal = budgetPrincipal;
    }
    static @Nullable DamageAuthorization resolve(StructureDamage.Authority authority) {
        if (authority instanceof StructureDamage.ActiveActor active && active.context().actor() instanceof ServerPlayer) {
            return new DamageAuthorization(authority, active.context().host(), active.context().actor().getUUID());
        }
        if (authority instanceof StructureDamage.Launched launched) {
            var grant = LiveStructureLaunches.resolve(launched.authority()).orElse(null);
            if (grant != null) return new DamageAuthorization(authority, launched.authority().host(), grant.ownerId().orElse(grant.actorId()));
        }
        return null;
    }
    ServerLevel host() { return host; }
    @Nullable Entity actor() {
        if (authority instanceof StructureDamage.ActiveActor active) return active.context().actor();
        var grant = LiveStructureLaunches.resolve(((StructureDamage.Launched)authority).authority()).orElse(null);
        return grant == null ? null : LiveStructureLaunches.actor(host, grant);
    }
    @Nullable ServerPlayer player() { return actor() instanceof ServerPlayer player ? player : null; }
    boolean available() {
        if (authority instanceof StructureDamage.Launched launched) return LiveStructureLaunches.resolve(launched.authority()).isPresent();
        var player = player();
        return player != null && player.level() == host && player.isAlive() && !player.isRemoved() && !player.hasDisconnected()
                && host.getEntity(player.getUUID()) == player && host.getServer().getLevel(host.dimension()) == host;
    }
    boolean allowed() { return available() && AbilityEffectPolicy.blockDestruction(host) != AbilityEffectPolicy.Decision.DENY; }
    boolean mayBreak(BlockPos world, BlockState block, Optional<SpaceBlockRef> cell) {
        if (!allowed()) return false;
        if (authority instanceof StructureDamage.Launched launched) return LiveStructureLaunches.mayBreak(launched.authority(), world, block, cell);
        var player = player();
        return player != null && host.mayInteract(player, world) && LevelUtil.canAbilityBreak(host, world, block, player)
                && allowed() && host.mayInteract(player, world);
    }
    boolean claim(UUID effect) {
        return !(authority instanceof StructureDamage.Launched launched) || LiveStructureLaunches.claim(launched.authority(), effect);
    }
    boolean same(DamageAuthorization other) {
        if (host != other.host) return false;
        if (authority instanceof StructureDamage.ActiveActor active) {
            return other.authority instanceof StructureDamage.ActiveActor candidate && active.context().actor() == candidate.context().actor();
        }
        return authority.equals(other.authority);
    }
    BudgetOwner budgetOwner() {
        // Attribution is immutable; reauthorization happens at admission, never an unchecked lookup here.
        return new BudgetOwner(host.getServer(), budgetPrincipal);
    }
    record BudgetOwner(MinecraftServer server, UUID principal) {}
}
