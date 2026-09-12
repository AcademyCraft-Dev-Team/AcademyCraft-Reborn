package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.misaka.OrbitalStrikeProxyEntity;
import org.academy.internal.server.world.level.storage.MisakaRelayEntry;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Server-side orbital laser strike state machine for Misaka relay satellites.
 */
public final class MisakaOrbitalStrikeSupport {
    public static final int APPROACH_TICKS = 70;
    public static final int FIRE_TICKS = 100;
    public static final int RETURN_TICKS = 40;
    public static final int COOLDOWN_TICKS = 600;
    public static final int DESIGNATOR_RANGE = 192;
    public static final int FIRE_PULSE_INTERVAL = 5;
    public static final int MAX_LAVA_REPLACEMENTS = 8;
    public static final float BASE_DAMAGE = 4.0f;
    public static final double BASE_COLUMN_RADIUS = 1.5;
    public static final double HYPER_COLUMN_RADIUS = 2.5;
    public static final int MELT_RADIUS = 2;
    public static final int HYPER_MELT_RADIUS = 3;
    /**
     * Visual strike apex above impact (blocks). Full orbit altitude (~1000) is fog/far-plane invisible
     * and forces a huge {@code clientTrackingRange}; this height stays on-screen and tracks at ~256.
     * Worst-case player distance ≈ {@code hypot(DESIGNATOR_RANGE, STRIKE_APEX_HEIGHT)} ≈ 250.
     */
    public static final double STRIKE_APEX_HEIGHT = 160.0;

    public enum BeginResult {
        OK,
        NOT_FOUND,
        BAD_PHASE,
        NOT_BOUND,
        FORCE_CRASH_ARMED,
        BUSY,
        COOLDOWN,
        NO_POWER,
        WRONG_DIMENSION,
        CHUNK_UNLOADED
    }

    private MisakaOrbitalStrikeSupport() {
    }

    public static BeginResult begin(
            MinecraftServer server,
            UUID satelliteId,
            BlockPos target,
            @Nullable ServerPlayer designator
    ) {
        var registry = MisakaRelayRegistry.get(server);
        var entry = registry.get(satelliteId);
        if (entry == null) {
            return BeginResult.NOT_FOUND;
        }
        if (entry.phase != MisakaRelayEntry.Phase.ORBIT) {
            return BeginResult.BAD_PHASE;
        }
        if (!entry.laserBound) {
            return BeginResult.NOT_BOUND;
        }
        if (entry.forceCrashCountdownTicks > 0) {
            return BeginResult.FORCE_CRASH_ARMED;
        }
        if (entry.strikeMode != MisakaRelayEntry.StrikeMode.IDLE) {
            return BeginResult.BUSY;
        }
        if (entry.strikeCooldownTicks > 0) {
            return BeginResult.COOLDOWN;
        }
        if (!registry.isReceivingPower(satelliteId)) {
            return BeginResult.NO_POWER;
        }
        if (designator != null && !designator.level().dimension().equals(entry.dimension)) {
            return BeginResult.WRONG_DIMENSION;
        }
        var level = server.getLevel(entry.dimension);
        if (level == null || !level.hasChunkAt(target)) {
            return BeginResult.CHUNK_UNLOADED;
        }

        long time = level.getGameTime();
        int seed = satelliteId.hashCode();
        double orbitY = MisakaRelayOrbits.visualOrbitY(level, server);
        Vec3 from = MisakaRelayOrbits.orbitSlotWorld(entry.laserPos, orbitY, time, seed);

        var proxy = new OrbitalStrikeProxyEntity(EntityTypes.ORBITAL_STRIKE_PROXY.get(), level);
        proxy.configure(satelliteId, entry.hyper, target.immutable());
        proxy.moveToSlot(from);
        level.addFreshEntity(proxy);

        entry.strikeMode = MisakaRelayEntry.StrikeMode.APPROACHING;
        entry.strikeTarget = target.immutable();
        entry.strikeTicks = 0;
        entry.strikeProxyUuid = proxy.getUUID();
        entry.strikeDesignatorPlayer = designator != null ? designator.getUUID() : null;
        entry.strikeLavaReplacements = 0;
        entry.strikeApproachFrom = from;
        return BeginResult.OK;
    }

    public static void tickAll(MinecraftServer server) {
        var registry = MisakaRelayRegistry.get(server);
        var ids = new ArrayList<>(registry.all());
        for (var entry : ids) {
            if (entry.strikeCooldownTicks > 0 && entry.strikeMode == MisakaRelayEntry.StrikeMode.IDLE) {
                entry.strikeCooldownTicks--;
            }
            if (entry.strikeMode == MisakaRelayEntry.StrikeMode.IDLE) {
                continue;
            }
            tickEntry(server, registry, entry);
        }
    }

    public static void cancel(MinecraftServer server, UUID satelliteId) {
        if (server == null || satelliteId == null) {
            return;
        }
        var entry = MisakaRelayRegistry.get(server).get(satelliteId);
        if (entry == null) {
            return;
        }
        discardProxy(server, entry);
        clearStrikeRuntime(entry);
    }

    public static boolean shouldDiscardOrphan(MinecraftServer server, OrbitalStrikeProxyEntity proxy) {
        UUID satId = proxy.getSatelliteId();
        if (satId == null) {
            return true;
        }
        var entry = MisakaRelayRegistry.get(server).get(satId);
        if (entry == null || entry.strikeMode == MisakaRelayEntry.StrikeMode.IDLE) {
            return true;
        }
        return entry.strikeProxyUuid == null || !entry.strikeProxyUuid.equals(proxy.getUUID());
    }

    private static void tickEntry(MinecraftServer server, MisakaRelayRegistry registry, MisakaRelayEntry entry) {
        var level = server.getLevel(entry.dimension);
        if (level == null || entry.strikeTarget == null || !level.hasChunkAt(entry.strikeTarget)) {
            cancel(server, entry.satelliteId);
            return;
        }
        var proxy = findProxy(level, entry);
        if (proxy == null) {
            clearStrikeRuntime(entry);
            entry.strikeCooldownTicks = COOLDOWN_TICKS;
            return;
        }

        double orbitY = MisakaRelayOrbits.visualOrbitY(level, server);
        Vec3 zenith = strikeApex(level, entry.strikeTarget, orbitY);

        entry.strikeTicks++;
        switch (entry.strikeMode) {
            case APPROACHING -> {
                float t = Mth.clamp(entry.strikeTicks / (float) APPROACH_TICKS, 0.0f, 1.0f);
                float eased = t * t * (3.0f - 2.0f * t);
                Vec3 from = entry.strikeApproachFrom != null ? entry.strikeApproachFrom : proxy.position();
                proxy.moveToSlot(from.lerp(zenith, eased));
                proxy.setFiring(false);
                if (entry.strikeTicks >= APPROACH_TICKS) {
                    entry.strikeMode = MisakaRelayEntry.StrikeMode.FIRING;
                    entry.strikeTicks = 0;
                    proxy.moveToSlot(zenith);
                    proxy.setFiring(true);
                }
            }
            case FIRING -> {
                proxy.moveToSlot(zenith);
                proxy.setFiring(true);
                if (entry.strikeTicks % FIRE_PULSE_INTERVAL == 0) {
                    applyFirePulse(level, entry, proxy);
                }
                if (entry.strikeTicks >= FIRE_TICKS) {
                    entry.strikeMode = MisakaRelayEntry.StrikeMode.RETURNING;
                    entry.strikeTicks = 0;
                    proxy.setFiring(false);
                    entry.strikeApproachFrom = zenith;
                }
            }
            case RETURNING -> {
                long time = level.getGameTime();
                Vec3 home = MisakaRelayOrbits.orbitSlotWorld(
                        entry.laserPos, orbitY, time, entry.satelliteId.hashCode()
                );
                float t = Mth.clamp(entry.strikeTicks / (float) RETURN_TICKS, 0.0f, 1.0f);
                float eased = t * t * (3.0f - 2.0f * t);
                Vec3 from = entry.strikeApproachFrom != null ? entry.strikeApproachFrom : zenith;
                proxy.moveToSlot(from.lerp(home, eased));
                proxy.setFiring(false);
                if (entry.strikeTicks >= RETURN_TICKS) {
                    discardProxy(server, entry);
                    clearStrikeRuntime(entry);
                    entry.strikeCooldownTicks = COOLDOWN_TICKS;
                }
            }
            default -> {
            }
        }
    }

    /**
     * Strike hold / fire slot above the target — capped for visibility and entity tracking cost.
     * Real orbital home slot remains {@link MisakaRelayOrbits#orbitSlotWorld}.
     */
    public static Vec3 strikeApex(ServerLevel level, BlockPos impact, double orbitY) {
        int groundY = level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                impact.getX(),
                impact.getZ()
        );
        double baseY = Math.max(impact.getY(), groundY);
        double apexY = Math.min(orbitY, baseY + STRIKE_APEX_HEIGHT);
        return new Vec3(impact.getX() + 0.5, apexY, impact.getZ() + 0.5);
    }

    /**
     * Where the power-beam / sky sat should aim during an active strike.
     * Prefer the live proxy pose so tower beam, sky mark, and downlink share one tip.
     */
    public static @Nullable Vec3 visualAim(ServerLevel level, MisakaRelayEntry entry) {
        if (entry == null
                || entry.strikeMode == MisakaRelayEntry.StrikeMode.IDLE
                || entry.strikeTarget == null) {
            return null;
        }
        var proxy = findProxy(level, entry);
        if (proxy != null) {
            return proxy.position();
        }
        double orbitY = MisakaRelayOrbits.visualOrbitY(level, level.getServer());
        Vec3 zenith = strikeApex(level, entry.strikeTarget, orbitY);
        return switch (entry.strikeMode) {
            case APPROACHING -> {
                float t = Mth.clamp(entry.strikeTicks / (float) APPROACH_TICKS, 0.0f, 1.0f);
                float eased = t * t * (3.0f - 2.0f * t);
                Vec3 from = entry.strikeApproachFrom != null ? entry.strikeApproachFrom : zenith;
                yield from.lerp(zenith, eased);
            }
            case FIRING -> zenith;
            case RETURNING -> {
                long time = level.getGameTime();
                Vec3 home = MisakaRelayOrbits.orbitSlotWorld(
                        entry.laserPos, orbitY, time, entry.satelliteId.hashCode()
                );
                float t = Mth.clamp(entry.strikeTicks / (float) RETURN_TICKS, 0.0f, 1.0f);
                float eased = t * t * (3.0f - 2.0f * t);
                Vec3 from = entry.strikeApproachFrom != null ? entry.strikeApproachFrom : zenith;
                yield from.lerp(home, eased);
            }
            default -> null;
        };
    }

    private static void applyFirePulse(ServerLevel level, MisakaRelayEntry entry, OrbitalStrikeProxyEntity proxy) {
        BlockPos impact = entry.strikeTarget;
        if (impact == null) {
            return;
        }
        double radius = entry.hyper ? HYPER_COLUMN_RADIUS : BASE_COLUMN_RADIUS;
        // Plan: column from 8 blocks above impact down to ground surface at target xz.
        int groundY = level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                impact.getX(),
                impact.getZ()
        );
        double topY = impact.getY() + 8.0;
        double bottomY = Math.min(impact.getY(), groundY);
        if (!(bottomY < topY)) {
            bottomY = topY - 1.0;
        }
        AABB box = new AABB(
                impact.getX() + 0.5 - radius,
                bottomY,
                impact.getZ() + 0.5 - radius,
                impact.getX() + 0.5 + radius,
                topY,
                impact.getZ() + 0.5 + radius
        );
        float damage = entry.hyper ? BASE_DAMAGE * 1.5f : BASE_DAMAGE;
        DamageSource source = damageSource(level, entry);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            living.hurtServer(level, source, damage);
        }

        int meltR = entry.hyper ? HYPER_MELT_RADIUS : MELT_RADIUS;
        // Prefer highest solid blocks (Chebyshev disk around impact).
        var candidates = new ArrayList<BlockPos>();
        for (int dy = 8; dy >= -4; dy--) {
            for (int dx = -meltR; dx <= meltR; dx++) {
                for (int dz = -meltR; dz <= meltR; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) > meltR) {
                        continue;
                    }
                    BlockPos pos = impact.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.liquid()) {
                        continue;
                    }
                    float destroy = state.getDestroySpeed(level, pos);
                    if (destroy < 0.0f || state.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    candidates.add(pos);
                }
            }
        }
        candidates.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        int broken = 0;
        for (BlockPos pos : candidates) {
            if (broken >= 3) {
                break;
            }
            if (tryMeltOrBreak(level, entry, pos)) {
                broken++;
            }
        }
    }

    private static boolean tryMeltOrBreak(ServerLevel level, MisakaRelayEntry entry, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.liquid()) {
            return false;
        }
        float destroy = state.getDestroySpeed(level, pos);
        if (destroy < 0.0f) {
            return false;
        }
        if (state.is(Blocks.BEDROCK)) {
            return false;
        }
        ServerPlayer breaker = designatorPlayer(level, entry);
        // Normal land/adventure gate (same as items / Darkmatter excavator): no anonymous grief.
        if (breaker == null || !level.mayInteract(breaker, pos)) {
            return false;
        }
        boolean stoneLike = state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.COBBLED_DEEPSLATE);
        if (stoneLike && entry.strikeLavaReplacements < MAX_LAVA_REPLACEMENTS) {
            level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
            entry.strikeLavaReplacements++;
            return true;
        }
        // ServerPlayerGameMode.destroyBlock keeps claim-mod / permission hooks intact.
        return breaker.gameMode.destroyBlock(pos);
    }

    private static @Nullable ServerPlayer designatorPlayer(ServerLevel level, MisakaRelayEntry entry) {
        if (entry.strikeDesignatorPlayer == null) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(entry.strikeDesignatorPlayer);
    }

    private static DamageSource damageSource(ServerLevel level, MisakaRelayEntry entry) {
        ServerPlayer player = designatorPlayer(level, entry);
        if (player != null) {
            return level.damageSources().playerAttack(player);
        }
        return level.damageSources().generic();
    }

    private static @Nullable OrbitalStrikeProxyEntity findProxy(ServerLevel level, MisakaRelayEntry entry) {
        if (entry.strikeProxyUuid == null) {
            return null;
        }
        var entity = level.getEntity(entry.strikeProxyUuid);
        return entity instanceof OrbitalStrikeProxyEntity proxy ? proxy : null;
    }

    private static void discardProxy(MinecraftServer server, MisakaRelayEntry entry) {
        if (entry.strikeProxyUuid == null) {
            return;
        }
        var level = server.getLevel(entry.dimension);
        if (level != null) {
            var entity = level.getEntity(entry.strikeProxyUuid);
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
        entry.strikeProxyUuid = null;
    }

    private static void clearStrikeRuntime(MisakaRelayEntry entry) {
        entry.strikeMode = MisakaRelayEntry.StrikeMode.IDLE;
        entry.strikeTarget = null;
        entry.strikeTicks = 0;
        entry.strikeProxyUuid = null;
        entry.strikeDesignatorPlayer = null;
        entry.strikeLavaReplacements = 0;
        entry.strikeApproachFrom = null;
    }
}
