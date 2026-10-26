package org.academy.internal.common.structure.upgrade.launch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.SpaceBlockRef;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.*;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.ability.AbilityEffectPolicy;
import org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Server-owned policy and persisted launch identity. All callbacks and mutations are on its thread. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class LiveStructureLaunches {
    private static final Map<MinecraftServer, State> SERVERS = new IdentityHashMap<>();
    private static boolean checking;
    private LiveStructureLaunches() {}

    public static void registerPolicy(MinecraftServer server, Identifier id, int version, StructureLaunches.Policy policy) {
        requireThread(server);
        if (version < 1) throw new IllegalArgumentException("Invalid policy version");
        if (state(server).policies.putIfAbsent(Objects.requireNonNull(id), new Registered(version, Objects.requireNonNull(policy))) != null) {
            throw new IllegalStateException("Launch policy already registered: " + id);
        }
    }

    public static StructureOperation.Result<StructureImpactFeed.LaunchAuthority> issue(
            StructureOperation.ActorContext context, Identifier policyId, Optional<UUID> owner,
            int lifetimeTicks, Optional<StructureRef> source) {
        var host = context.host(); requireThread(host.getServer());
        if (checking || lifetimeTicks < 1 || lifetimeTicks > StructureLaunches.MAX_LIFETIME_TICKS || !present(host, context.actor())) return denied("launch_invalid_issuer");
        var state = state(host.getServer()); var policy = state.policies.get(policyId);
        if (policy == null) return denied("launch_unknown_policy");
        Optional<StructureLaunches.Source> binding = Optional.empty();
        if (source.isPresent()) {
            var ref = source.orElseThrow();
            if (!ref.dimension().equals(host.dimension().identifier()) || LiveStructureProvenance.liveReference(host.getServer(), ref).isEmpty()) return denied("launch_source_unavailable");
            var metadata = LiveStructureProvenance.metadata(host.getServer(), ref).orElse(null);
            if (metadata == null || !metadata.ownerId().equals(owner)) return denied("launch_source_creation_owner");
            binding = Optional.of(new StructureLaunches.Source(ref, metadata.creationOperationId()));
        }
        long now = host.getGameTime();
        var grant = new StructureLaunches.Grant(UUID.randomUUID(), host.dimension().identifier(), context.actor().getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(context.actor().getType()), owner, policyId, policy.version,
                now, Math.addExact(now, lifetimeTicks), binding);
        if (!check(() -> policy.policy.mayIssue(context, grant)) || !present(host, context.actor())
                || !sourceCurrent(host, grant) || !check(() -> policy.policy.mayUse(host, grant, context.actor()))
                || !present(host, context.actor()) || !sourceCurrent(host, grant)) return denied("launch_policy_denied");
        var ledger = state.ledger(host.getServer());
        if (ledger == null) return denied("launch_ledger_unavailable");
        try {
            ledger.issue(grant);
            return new StructureOperation.Result<>(StructureOperation.Status.COMPLETED, "launch_issued", Optional.of(grant.id()),
                    Optional.of(new StructureImpactFeed.LaunchAuthority(host, grant.id(), owner, policyId)));
        } catch (IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("Launch issuance was not acknowledged", failure); return denied("launch_issue_not_durable");
        }
    }

    public static Optional<StructureLaunches.Grant> resolve(StructureImpactFeed.LaunchAuthority claim) {
        var host = claim.host(); requireThread(host.getServer());
        if (checking || host.getServer().getLevel(host.dimension()) != host || !globalAllowed(host)) return Optional.empty();
        var state = state(host.getServer()); var ledger = state.ledger(host.getServer());
        var entry = ledger == null ? null : ledger.find(claim.launchId()).orElse(null);
        if (entry == null || entry.revoked()) return Optional.empty();
        var grant = entry.grant(); var policy = state.policies.get(claim.policyId());
        long now = host.getGameTime();
        if (!grant.dimension().equals(host.dimension().identifier()) || !grant.ownerId().equals(claim.ownerId())
                || !grant.policyId().equals(claim.policyId()) || policy == null || policy.version != grant.policyVersion()
                || now < grant.createdTick() || now >= grant.expiresTick() || !sourceCurrent(host, grant)) return Optional.empty();
        var actor = actor(host, grant);
        if (!check(() -> policy.policy.mayUse(host, grant, actor)) || !unchanged(ledger, entry)
                || actor(host, grant) != actor
                || !sourceCurrent(host, grant)) return Optional.empty();
        return Optional.of(grant);
    }

    public static @Nullable Entity actor(ServerLevel host, StructureLaunches.Grant grant) {
        var entity = host.getEntity(grant.actorId());
        return entity != null && present(host, entity) && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(grant.actorType()) ? entity : null;
    }

    public static boolean mayBreak(StructureImpactFeed.LaunchAuthority claim, BlockPos world, BlockState block, Optional<SpaceBlockRef> cell) {
        var grant = resolve(claim).orElse(null);
        if (grant == null || AbilityEffectPolicy.blockDestruction(claim.host()) == AbilityEffectPolicy.Decision.DENY) return false;
        var host = claim.host(); var actor = actor(host, grant);
        if (actor instanceof ServerPlayer player) {
            if (!host.mayInteract(player, world) || !LevelUtil.canAbilityBreak(host, world, block, player)) return false;
        } else if (block.getBlock() instanceof GameMasterBlock) return false;
        var policy = state(host.getServer()).policies.get(grant.policyId());
        if (!check(() -> policy.policy.mayBreak(host, grant, actor, world, block)) || resolve(claim).isEmpty()) return false;
        var event = new StructureLaunchBreakEvent(host, grant, actor, world, block, cell);
        NeoForge.EVENT_BUS.post(event);
        // Permission callbacks may revoke the launch, replace/remove its actor or alter provenance.
        return !event.isCanceled() && resolve(claim).isPresent()
                && actor(host, grant) == actor
                && check(() -> policy.policy.mayBreak(host, grant, actor, world, block))
                && resolve(claim).isPresent() && actor(host, grant) == actor
                && (!(actor instanceof ServerPlayer player) || host.mayInteract(player, world))
                && AbilityEffectPolicy.blockDestruction(host) != AbilityEffectPolicy.Decision.DENY;
    }

    public static boolean claim(StructureImpactFeed.LaunchAuthority claim, UUID effect) {
        var grant = resolve(claim).orElse(null); if (grant == null) return false;
        var ledger = state(claim.host().getServer()).ledger(claim.host().getServer());
        try { return ledger != null && ledger.claim(grant, effect, claim.host().getGameTime()); }
        catch (IOException | RuntimeException failure) { AcademyCraft.LOGGER.error("Launch effect was not durably admitted", failure); return false; }
    }

    public static boolean revoke(ServerLevel host, UUID id) {
        requireThread(host.getServer()); var ledger = state(host.getServer()).ledger(host.getServer());
        var entry = ledger == null ? null : ledger.find(id).orElse(null);
        if (entry == null || !entry.grant().dimension().equals(host.dimension().identifier())) return false;
        try { return ledger.revoke(id); }
        catch (IOException | RuntimeException failure) { AcademyCraft.LOGGER.error("Launch revocation was not durably acknowledged", failure); return false; }
    }

    private static boolean sourceCurrent(ServerLevel host, StructureLaunches.Grant grant) {
        if (grant.source().isEmpty()) return true;
        var source = grant.source().orElseThrow();
        var metadata = LiveStructureProvenance.metadata(host.getServer(), source.reference()).orElse(null);
        return metadata != null && metadata.creationOperationId().equals(source.creationId()) && metadata.ownerId().equals(grant.ownerId())
                && LiveStructureProvenance.liveReference(host.getServer(), source.reference()).isPresent();
    }
    private static boolean unchanged(StructureLaunchLedger ledger, StructureLaunchLedger.Entry entry) {
        return ledger.find(entry.grant().id()).filter(entry::equals).isPresent();
    }
    private static boolean globalAllowed(ServerLevel host) {
        var decision = AbilityEffectPolicy.blockDestruction(host);
        return decision != AbilityEffectPolicy.Decision.DENY && (decision == AbilityEffectPolicy.Decision.ALLOW
                || org.academy.internal.common.world.damagesource.DestroyBlocksSetting.isServerBlockDestructionAllowed(host));
    }
    private static boolean present(ServerLevel host, Entity actor) {
        return host.getServer().getLevel(host.dimension()) == host && actor.level() == host && actor.isAlive() && !actor.isRemoved()
                && host.getEntity(actor.getUUID()) == actor && (!(actor instanceof ServerPlayer player) || !player.hasDisconnected());
    }
    private static boolean check(BooleanSupplier callback) {
        if (checking) return false;
        checking = true;
        try { return callback.getAsBoolean(); }
        catch (RuntimeException failure) { AcademyCraft.LOGGER.error("Launch permission callback failed", failure); return false; }
        finally { checking = false; }
    }
    private static StructureOperation.Result<StructureImpactFeed.LaunchAuthority> denied(String detail) {
        return new StructureOperation.Result<>(StructureOperation.Status.DENIED, detail, Optional.empty(), Optional.empty());
    }
    private static State state(MinecraftServer server) { requireThread(server); return SERVERS.computeIfAbsent(server, ignored -> new State()); }
    private static void requireThread(MinecraftServer server) { if (!server.isSameThread()) throw new IllegalStateException("Launch access requires server thread"); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SERVERS.remove(event.getServer()); }
    private record Registered(int version, StructureLaunches.Policy policy) {}
    private static final class State {
        final Map<Identifier, Registered> policies = new HashMap<>();
        boolean loaded;
        @Nullable StructureLaunchLedger ledger;
        State() {
            policies.put(StructureLaunches.PLAYER_PRESENT, new Registered(1, new StructureLaunches.Policy() {
                @Override public boolean mayIssue(StructureOperation.ActorContext context, StructureLaunches.Grant grant) {
                    return context.actor() instanceof ServerPlayer && grant.ownerId().equals(Optional.of(context.actor().getUUID()));
                }
                @Override public boolean mayUse(ServerLevel host, StructureLaunches.Grant grant, @Nullable Entity actor) {
                    return actor instanceof ServerPlayer player && present(host, actor) && grant.ownerId().equals(Optional.of(actor.getUUID()))
                            && org.academy.internal.common.world.damagesource.DestroyBlocksSetting.canDestroyBlocks(player);
                }
                @Override public boolean mayBreak(ServerLevel host, StructureLaunches.Grant grant, @Nullable Entity actor, BlockPos world, BlockState block) {
                    return actor instanceof ServerPlayer && present(host, actor);
                }
            }));
        }
        @Nullable StructureLaunchLedger ledger(MinecraftServer server) {
            if (!loaded) {
                loaded = true;
                try { ledger = new StructureLaunchLedger(server.getWorldPath(LevelResource.ROOT).resolve("data/academy/structure_launches")); }
                catch (IOException | RuntimeException failure) { AcademyCraft.LOGGER.error("Launch ledger unavailable", failure); }
            }
            return ledger;
        }
    }
}
