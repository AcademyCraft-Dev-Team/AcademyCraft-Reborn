package org.academy.internal.common.ability.teleport.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.event.SkillExecutionFinishEvent;
import org.academy.api.common.ability.event.SkillExecutionStartEvent;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.team.TeamRelations;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.render.vfx.SpacialExcisionVfxClient;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.SpacialExcisionMath;
import org.academy.internal.common.ability.teleport.TeleportCompletedEvent;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** A 30-second state that records the owner's successful same-dimension teleports. */
public final class SpacialExcision extends Skill {
    public static final int ACTIVATION_CP = 100;
    public static final int DURATION_TICKS = 600;
    private static final double OBSERVER_RANGE = 512.0;

    public SpacialExcision() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(ACTIVATION_CP)
                .iterationTicks(20)
                .maxStacks(1)
                .dependsOn(Skills.AREA_TELEPORT_SELECT)
        );
    }

    @Override
    public void initClient() {
        Client.init();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY, Client.CONFIG.getKeyBinding(Client.KEY,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X,
                                InputConstants.PRESS, InputConstants.MOD_ALT)),
                ctx -> Client.onUse());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY = SkillNames.SPACIAL_EXCISION + "_use";
        public static Config CONFIG = new Config();
        private static boolean initialized;

        private Client() {
        }

        private static void init() {
            if (initialized) return;
            initialized = true;
            MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
            NeoForge.EVENT_BUS.register(Client.class);
            SpacialExcisionVfxClient.register();
        }

        public static void onUse() {
            MisakaNetworkClient.send(ActivatePacket.INSTANCE);
        }

        @SubscribePacket
        public static void handleSegment(SegmentPacket packet) {
            SpacialExcisionVfxClient.addSegment(
                    packet.sessionId(), packet.startTick(), packet.createdTick(), packet.endTick(), packet.dimension(),
                    packet.start(), packet.end(), packet.preTeleportYaw(), packet.seed()
            );
        }

        @SubscribePacket
        public static void handleEnd(EndPacket packet) {
            if (packet.sessionId() == null) return;
            SpacialExcisionVfxClient.endSession(packet.sessionId());
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            SpacialExcisionVfxClient.releaseTransientResources();
        }

        @SubscribeEvent
        public static void onLevelUnload(LevelEvent.Unload event) {
            if (event.getLevel() instanceof ClientLevel) {
                SpacialExcisionVfxClient.releaseTransientResources();
            }
        }

        @SubscribeEvent
        public static void onResourceLoadFinished(ClientResourceLoadFinishedEvent event) {
            if (!event.isInitial()) {
                SpacialExcisionVfxClient.releaseTransientResources();
            }
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements org.academy.api.common.gson.TypeHandler<Config> {
                public static final Action INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        private static final Map<ServerPlayer, Context> ACTIVE = new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handle(ActivatePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (ACTIVE.containsKey(player)) return;

            var skill = Skills.SPACIAL_EXCISION.get();
            skill.executeActive(player, (ctx, actualCost) -> {
                var server = player.level().getServer();
                var startTick = server.getTickCount();
                var context = new Context(player, UUID.randomUUID(), startTick,
                        startTick + DURATION_TICKS);
                ACTIVE.put(player, context);
                AbilitySystemServer.registerContext(context);
            });
        }

        private static void tick(MinecraftServer server) {
            var now = server.getTickCount();
            for (var context : List.copyOf(ACTIVE.values())) {
                context.checkLifetime(server, now);
                if (context.server == server && !context.ended) {
                    context.tickCombat(now);
                }
            }
        }

        private static void stop(MinecraftServer server) {
            for (var context : List.copyOf(ACTIVE.values())) {
                if (context.server == server) context.end();
            }
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onTick(ServerTickEvent.Pre event) {
            Server.tick(event.getServer());
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {
            Server.stop(event.getServer());
        }
    }

    public static final class Context extends ServerContext {
        private final MinecraftServer server;
        private final UUID sessionId;
        private final long startTick;
        private final long endTick;
        private final ArrayList<RecordedSegment> segments = new ArrayList<>();
        private final Map<Skill.ActiveExecutionContext, List<TeleportCompletedEvent>> pending =
                new IdentityHashMap<>();
        private final Deque<Skill.ActiveExecutionContext> teleportExecutions = new ArrayDeque<>();
        private final Set<ServerPlayer> observers =
                Collections.newSetFromMap(new IdentityHashMap<>());
        // Queries are executed serially on the server thread. Reuse the scan
        // scratch so each segment does not allocate a new candidate list.
        private final ArrayList<LivingEntity> queryCandidates = new ArrayList<>();
        private boolean ended;

        private Context(ServerPlayer player, UUID sessionId, long startTick, long endTick) {
            super(player);
            server = player.level().getServer();
            this.sessionId = sessionId;
            this.startTick = startTick;
            this.endTick = endTick;
            observers.add(player);
        }

        @SubscribeEvent
        public void onTeleportSkillStart(SkillExecutionStartEvent event) {
            if (ended || !isOwnerTeleportExecution(event.context())) return;
            teleportExecutions.push(event.context());
            pending.put(event.context(), new ArrayList<>());
        }

        @SubscribeEvent
        public void onTeleportCompleted(TeleportCompletedEvent event) {
            if (ended || teleportExecutions.isEmpty() || event.entity() != player) return;
            var recorded = pending.get(teleportExecutions.peek());
            if (recorded != null) recorded.add(event);
        }

        @SubscribeEvent
        public void onTeleportSkillFinish(SkillExecutionFinishEvent event) {
            if (!isOwnerTeleportExecution(event.context())) return;
            var recorded = pending.remove(event.context());
            removeTeleportExecutionByIdentity(event.context());
            if (!event.successful() || recorded == null) return;
            for (var teleport : recorded) recordSuccessfulTeleport(teleport);
        }

        private void removeTeleportExecutionByIdentity(Skill.ActiveExecutionContext target) {
            var iterator = teleportExecutions.iterator();
            while (iterator.hasNext()) {
                if (iterator.next() == target) {
                    iterator.remove();
                    return;
                }
            }
        }

        private void checkLifetime(MinecraftServer tickingServer, long now) {
            if (server != tickingServer) return;
            if (ended || player.hasDisconnected() || !player.isAlive() || now >= endTick) {
                end();
            }
        }

        private void tickCombat(long now) {
            if (ended) return;
            for (var segment : segments) {
                querySegment(now, segment);
            }
        }

        private void querySegment(long now, RecordedSegment segment) {
            var dimension = ResourceKey.create(Registries.DIMENSION, segment.dimension());
            var level = server.getLevel(dimension);
            if (level == null) return;

            var searchBox = new AABB(segment.start(), segment.end())
                    .inflate(Field.ATTRACTION_HALF_EXTENT);
            queryCandidates.clear();
            level.getEntities().get(
                    EntityTypeTest.forClass(LivingEntity.class), searchBox, target -> {
                        if (EntitySelector.NO_SPECTATORS.test(target)) queryCandidates.add(target);
                        return AbortableIterationConsumer.Continuation.CONTINUE;
                    });
            var age = now - segment.createdTick();
            for (var target : queryCandidates) {
                if (!isEligibleCombatTarget(target)) continue;
                var bounds = target.getBoundingBox();
                var lastHitAge = segment.persistentLastHitAges()
                        .getOrDefault(target.getUUID(), -1L);
                var actions = Field.tickActions(
                        age, !segment.spawnStrikeClaims().contains(target.getUUID()),
                        Field.isPersistentDamageDue(age, lastHitAge),
                        bounds, segment.start(), segment.end());
                if (actions.spawnStrike()
                        && segment.spawnStrikeClaims().add(target.getUUID())) {
                    SkillDamageUtil.apply(
                            player, target, Skills.SPACIAL_EXCISION.get(), DamageTypes.SPACE_DAMAGE,
                            (float) Field.SPAWN_STRIKE_DAMAGE);
                }
                if (actions.persistentDamage() && target.isAlive() && !target.isRemoved()) {
                    var damaged = SkillDamageUtil.apply(
                            player, target, Skills.SPACIAL_EXCISION.get(), DamageTypes.SPACE_DAMAGE,
                            (float) Field.PERSISTENT_DAMAGE);
                    if (damaged) {
                        segment.persistentLastHitAges().put(target.getUUID(), age);
                    }
                }
                if (isAttractionTarget(target)) {
                    applyPull(target, bounds, segment);
                }
            }
        }

        private void applyPull(
                LivingEntity target, AABB bounds, RecordedSegment segment
        ) {
            var distance = Field.attractionDistance(
                    bounds, segment.start(), segment.end());
            if (!Double.isFinite(distance)) return;
            target.setDeltaMovement(Field.pulledVelocity(
                    target.getDeltaMovement(), bounds,
                    segment.start(), segment.end(), distance));
            target.hurtMarked = true;
            target.resetFallDistance();
            if (target instanceof ServerPlayer targetPlayer) {
                targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
            }
        }

        private boolean isEligibleCombatTarget(LivingEntity target) {
            return target != player
                    && target.isAlive()
                    && !target.isRemoved()
                    && !(target instanceof Player immune && DamageTypes.isImmunePlayer(immune))
                    && !PvpSetting.shouldPrevent(player, target);
        }

        private boolean isAttractionTarget(LivingEntity target) {
            return isEligibleCombatTarget(target) && !TeamRelations.areAllied(player, target);
        }

        private boolean isOwnerTeleportExecution(Skill.ActiveExecutionContext context) {
            if (context == null || context.player() != player || context.skill() == null) return false;
            var skill = context.skill();
            return skill != Skills.SPACIAL_EXCISION.get()
                    && skill.getCategory() == AbilityCategories.TELEPORT.get();
        }

        private void recordSuccessfulTeleport(TeleportCompletedEvent event) {
            if (ended || event.sourceLevel() == null || event.destinationLevel() == null
                    || !event.sourceLevel().dimension().equals(event.destinationLevel().dimension())
                    || !SpacialExcisionMath.isFinite(event.origin())
                    || !SpacialExcisionMath.isFinite(event.destination())
                    || SpacialExcisionMath.planeBasis(
                            event.origin(), event.destination(), event.preTeleportYaw()).isEmpty()) {
                return;
            }
            var now = server.getTickCount();
            if (now >= endTick) return;

            var segment = new RecordedSegment(
                    event.origin(), event.destination(), event.preTeleportYaw(),
                    event.sourceLevel().dimension().identifier(),
                    event.sourceLevel().getRandom().nextLong(), now, new HashSet<>(),
                    new HashMap<>()
            );
            segments.add(segment);
            var packet = new SegmentPacket(
                    sessionId, startTick, segment.createdTick(), endTick,
                    segment.dimension(), segment.start(), segment.end(),
                    segment.preTeleportYaw(), segment.seed()
            );
            broadcast(segment, event, packet);
        }

        private void broadcast(RecordedSegment segment, TeleportCompletedEvent event, SegmentPacket packet) {
            ServerLevel sourceLevel = event.sourceLevel();
            var delta = segment.end().subtract(segment.start());
            var lengthSquared = delta.lengthSqr();
            if (!Double.isFinite(lengthSquared) || lengthSquared <= 0.0) return;
            var rangeSquared = OBSERVER_RANGE * OBSERVER_RANGE;
            for (var observer : sourceLevel.players()) {
                var along = observer.position().subtract(segment.start()).dot(delta) / lengthSquared;
                along = Math.max(0.0, Math.min(1.0, along));
                var closest = segment.start().add(delta.scale(along));
                if (observer.position().distanceToSqr(closest) > rangeSquared) continue;
                MisakaNetworkServer.send(observer, packet);
                observers.add(observer);
            }
        }

        private void end() {
            if (ended) return;
            ended = true;
            Server.ACTIVE.remove(player, this);
            unregister();
        }

        @Override
        protected void onUnregistered() {
            Server.ACTIVE.remove(player, this);
            pending.clear();
            teleportExecutions.clear();
            segments.clear();
            queryCandidates.clear();
            var packet = new EndPacket(sessionId);
            for (var observer : List.copyOf(observers)) {
                if (!observer.hasDisconnected()) MisakaNetworkServer.send(observer, packet);
            }
            observers.clear();
        }

        private record RecordedSegment(
                Vec3 start,
                Vec3 end,
                float preTeleportYaw,
                Identifier dimension,
                long seed,
                long createdTick,
                Set<UUID> spawnStrikeClaims,
                Map<UUID, Long> persistentLastHitAges
        ) {
        }
    }

    /** Server-only combat geometry for one spatial-excision segment. */
    static final class Field {
        /** Original 3x3 crack damage range, independent from attraction. */
        static final double DAMAGE_HALF_EXTENT = 1.5;
        static final double ATTRACTION_HALF_EXTENT = 3.5;
        static final int PULSE_INTERVAL_TICKS = 10;
        static final int SPAWN_STRIKE_WINDOW_TICKS = 20;
        static final double PERSISTENT_DAMAGE = 12.0;
        static final double SPAWN_STRIKE_DAMAGE = 40.0;
        static final double MAX_PULL_SPEED = 1.10;

        private static final double OUTER_TARGET_SPEED = 0.35;
        private static final double OUTER_RESPONSE = 0.20;
        private static final double INNER_RESPONSE = 0.78;
        private static final double MIN_DISTANCE_SQUARED = 1.0e-8;

        private Field() {
        }

        static boolean intersectsExpandedAabb(
                Vec3 start, Vec3 end, AABB bounds, double halfExtent
        ) {
            if (!isFinite(start) || !isFinite(end) || !isFinite(bounds)
                    || !Double.isFinite(halfExtent) || halfExtent < 0.0) {
                return false;
            }
            var minX = bounds.minX - halfExtent;
            var minY = bounds.minY - halfExtent;
            var minZ = bounds.minZ - halfExtent;
            var maxX = bounds.maxX + halfExtent;
            var maxY = bounds.maxY + halfExtent;
            var maxZ = bounds.maxZ + halfExtent;
            if (!Double.isFinite(minX) || !Double.isFinite(minY)
                    || !Double.isFinite(minZ) || !Double.isFinite(maxX)
                    || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) return false;

            var directionX = end.x - start.x;
            var directionY = end.y - start.y;
            var directionZ = end.z - start.z;
            if (!Double.isFinite(directionX) || !Double.isFinite(directionY)
                    || !Double.isFinite(directionZ)) return false;

            var enter = 0.0;
            var exit = 1.0;
            if (Math.abs(directionX) <= MIN_DISTANCE_SQUARED) {
                if (start.x < minX || start.x > maxX) return false;
            } else {
                var first = (minX - start.x) / directionX;
                var second = (maxX - start.x) / directionX;
                if (!Double.isFinite(first) || !Double.isFinite(second)) return false;
                enter = Math.max(enter, Math.min(first, second));
                exit = Math.min(exit, Math.max(first, second));
                if (enter > exit) return false;
            }
            if (Math.abs(directionY) <= MIN_DISTANCE_SQUARED) {
                if (start.y < minY || start.y > maxY) return false;
            } else {
                var first = (minY - start.y) / directionY;
                var second = (maxY - start.y) / directionY;
                if (!Double.isFinite(first) || !Double.isFinite(second)) return false;
                enter = Math.max(enter, Math.min(first, second));
                exit = Math.min(exit, Math.max(first, second));
                if (enter > exit) return false;
            }
            if (Math.abs(directionZ) <= MIN_DISTANCE_SQUARED) {
                return start.z >= minZ && start.z <= maxZ;
            }
            var first = (minZ - start.z) / directionZ;
            var second = (maxZ - start.z) / directionZ;
            if (!Double.isFinite(first) || !Double.isFinite(second)) return false;
            enter = Math.max(enter, Math.min(first, second));
            exit = Math.min(exit, Math.max(first, second));
            return enter <= exit;
        }

        static boolean isSpawnStrikeAge(long age) {
            return age >= 0 && age < SPAWN_STRIKE_WINDOW_TICKS;
        }

        static boolean isPersistentDamageDue(long age, long lastHitAge) {
            if (age < 0) return false;
            if (lastHitAge < 0) return true;
            return age >= lastHitAge && age - lastHitAge >= PULSE_INTERVAL_TICKS;
        }

        static TickActions tickActions(
                long age,
                boolean spawnUnclaimed,
                boolean persistentDamageDue,
                AABB bounds,
                Vec3 start,
                Vec3 end
        ) {
            var intersectsDamageCorridor = intersectsExpandedAabb(
                    start, end, bounds, DAMAGE_HALF_EXTENT);
            var spawnStrike = spawnUnclaimed
                    && isSpawnStrikeAge(age)
                    && intersectsDamageCorridor;
            var persistentDamage = persistentDamageDue && intersectsDamageCorridor;
            return new TickActions(spawnStrike, persistentDamage);
        }

        static Vec3 pulledVelocity(Vec3 velocity, AABB bounds, Vec3 start, Vec3 end) {
            return pulledVelocity(velocity, bounds, start, end, Double.NaN);
        }

        static Vec3 pulledVelocity(
                Vec3 velocity, AABB bounds, Vec3 start, Vec3 end, double knownDistance
        ) {
            var boundedVelocity = clampVelocity(isFinite(velocity) ? velocity : Vec3.ZERO);
            if (!isFinite(bounds) || !isFinite(start) || !isFinite(end)) {
                return boundedVelocity;
            }

            var targetX = (bounds.minX + bounds.maxX) * 0.5;
            var targetY = (bounds.minY + bounds.maxY) * 0.5;
            var targetZ = (bounds.minZ + bounds.maxZ) * 0.5;
            if (!Double.isFinite(targetX) || !Double.isFinite(targetY)
                    || !Double.isFinite(targetZ)) return boundedVelocity;
            var directionX = end.x - start.x;
            var directionY = end.y - start.y;
            var directionZ = end.z - start.z;
            var lengthSquared = Math.fma(directionX, directionX,
                    Math.fma(directionY, directionY, directionZ * directionZ));
            if (!Double.isFinite(lengthSquared) || lengthSquared <= MIN_DISTANCE_SQUARED) {
                return Vec3.ZERO;
            }
            var projection = Math.fma(targetX - start.x, directionX,
                    Math.fma(targetY - start.y, directionY, (targetZ - start.z) * directionZ))
                    / lengthSquared;
            if (!Double.isFinite(projection)) return boundedVelocity;
            projection = Math.clamp(projection, 0.0, 1.0);
            var closestX = Math.fma(directionX, projection, start.x);
            var closestY = Math.fma(directionY, projection, start.y);
            var closestZ = Math.fma(directionZ, projection, start.z);
            var towardX = closestX - targetX;
            var towardY = closestY - targetY;
            var towardZ = closestZ - targetZ;
            var directionLength = Math.sqrt(Math.fma(towardX, towardX,
                    Math.fma(towardY, towardY, towardZ * towardZ)));
            if (!Double.isFinite(directionLength) || directionLength <= MIN_DISTANCE_SQUARED) {
                return Vec3.ZERO;
            }
            var distance = Double.isFinite(knownDistance)
                    ? knownDistance
                    : Math.max(Math.abs(targetX - closestX),
                    Math.max(Math.abs(targetY - closestY), Math.abs(targetZ - closestZ)));
            if (!Double.isFinite(distance) || distance < 0.0
                    || distance > ATTRACTION_HALF_EXTENT) {
                return boundedVelocity;
            }
            var direction = new Vec3(
                    towardX / directionLength, towardY / directionLength, towardZ / directionLength);
            var proximity = 1.0 - Math.clamp(distance / ATTRACTION_HALF_EXTENT, 0.0, 1.0);
            var targetSpeed = lerp(OUTER_TARGET_SPEED, MAX_PULL_SPEED,
                    smootherstep(0.0, 1.0, proximity));
            var capture = 1.0 - smootherstep(0.75, 1.75, distance);
            var response = lerp(OUTER_RESPONSE, INNER_RESPONSE, capture);
            var targetVelocity = direction.scale(targetSpeed);
            return clampVelocity(boundedVelocity.lerp(targetVelocity, response));
        }

        static double attractionDistance(AABB bounds, Vec3 start, Vec3 end) {
            if (!isFinite(bounds) || !isFinite(start) || !isFinite(end)) {
                return Double.POSITIVE_INFINITY;
            }
            var centerX = (bounds.minX + bounds.maxX) * 0.5;
            var centerY = (bounds.minY + bounds.maxY) * 0.5;
            var centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
            var directionX = end.x - start.x;
            var directionY = end.y - start.y;
            var directionZ = end.z - start.z;
            var lengthSquared = Math.fma(directionX, directionX,
                    Math.fma(directionY, directionY, directionZ * directionZ));
            if (!Double.isFinite(centerX) || !Double.isFinite(centerY)
                    || !Double.isFinite(centerZ) || !Double.isFinite(lengthSquared)
                    || lengthSquared <= MIN_DISTANCE_SQUARED) return Double.POSITIVE_INFINITY;
            var projection = Math.fma(centerX - start.x, directionX,
                    Math.fma(centerY - start.y, directionY,
                            (centerZ - start.z) * directionZ)) / lengthSquared;
            if (!Double.isFinite(projection)) return Double.POSITIVE_INFINITY;
            projection = Math.clamp(projection, 0.0, 1.0);
            var closestX = Math.fma(directionX, projection, start.x);
            var closestY = Math.fma(directionY, projection, start.y);
            var closestZ = Math.fma(directionZ, projection, start.z);
            var distance = Math.max(Math.abs(centerX - closestX),
                    Math.max(Math.abs(centerY - closestY), Math.abs(centerZ - closestZ)));
            return Double.isFinite(distance) && distance <= ATTRACTION_HALF_EXTENT
                    ? distance
                    : Double.POSITIVE_INFINITY;
        }

        private static double smootherstep(double edge0, double edge1, double value) {
            if (!(edge1 > edge0) || !Double.isFinite(value)) return 0.0;
            var t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
            return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
        }

        private static double lerp(double from, double to, double amount) {
            return from + (to - from) * amount;
        }

        static boolean isFinite(Vec3 value) {
            return value != null && Double.isFinite(value.x)
                    && Double.isFinite(value.y) && Double.isFinite(value.z);
        }

        private static boolean isFinite(AABB bounds) {
            return bounds != null && Double.isFinite(bounds.minX)
                    && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ)
                    && Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY)
                    && Double.isFinite(bounds.maxZ);
        }

        private static Vec3 clampVelocity(Vec3 velocity) {
            var largestComponent = Math.max(Math.abs(velocity.x),
                    Math.max(Math.abs(velocity.y), Math.abs(velocity.z)));
            if (largestComponent == 0.0) return velocity;

            var normalized = velocity.scale(1.0 / largestComponent);
            var normalizedLength = normalized.length();
            if (!Double.isFinite(normalizedLength) || normalizedLength <= 0.0) return Vec3.ZERO;
            if (largestComponent <= MAX_PULL_SPEED / normalizedLength) return velocity;
            return normalized.scale(MAX_PULL_SPEED / normalizedLength);
        }

        record TickActions(boolean spawnStrike, boolean persistentDamage) {
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActivatePacket extends Packet<ServerGamePacketListenerImpl, ActivatePacket> {
        public static final ActivatePacket INSTANCE = new ActivatePacket();
        public static final StreamCodec<ByteBuf, ActivatePacket> CODEC = StreamCodec.unit(INSTANCE);

        private ActivatePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActivatePacket> getPacketType() {
            return PacketTypes.SPACIAL_EXCISION_ACTIVATE.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SegmentPacket extends Packet<ClientPacketListener, SegmentPacket> {
        public static final StreamCodec<ByteBuf, SegmentPacket> CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    writeUuid(buffer, packet.sessionId);
                    ByteBufCodecs.LONG.encode(buffer, packet.startTick);
                    ByteBufCodecs.LONG.encode(buffer, packet.createdTick);
                    ByteBufCodecs.LONG.encode(buffer, packet.endTick);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, packet.dimension.toString());
                    Vec3.STREAM_CODEC.encode(buffer, packet.start);
                    Vec3.STREAM_CODEC.encode(buffer, packet.end);
                    ByteBufCodecs.FLOAT.encode(buffer, packet.preTeleportYaw);
                    ByteBufCodecs.LONG.encode(buffer, packet.seed);
                },
                buffer -> new SegmentPacket(
                        readUuid(buffer),
                        ByteBufCodecs.LONG.decode(buffer),
                        ByteBufCodecs.LONG.decode(buffer),
                        ByteBufCodecs.LONG.decode(buffer),
                        parseIdentifier(ByteBufCodecs.STRING_UTF8.decode(buffer)),
                        Vec3.STREAM_CODEC.decode(buffer),
                        Vec3.STREAM_CODEC.decode(buffer),
                        ByteBufCodecs.FLOAT.decode(buffer),
                        ByteBufCodecs.LONG.decode(buffer)
                )
        );

        private final UUID sessionId;
        private final long startTick;
        private final long createdTick;
        private final long endTick;
        private final Identifier dimension;
        private final Vec3 start;
        private final Vec3 end;
        private final float preTeleportYaw;
        private final long seed;

        public SegmentPacket(UUID sessionId, long startTick, long createdTick, long endTick, Identifier dimension,
                             Vec3 start, Vec3 end, float preTeleportYaw, long seed) {
            this.sessionId = sessionId;
            this.startTick = startTick;
            this.createdTick = createdTick;
            this.endTick = endTick;
            this.dimension = dimension;
            this.start = start;
            this.end = end;
            this.preTeleportYaw = preTeleportYaw;
            this.seed = seed;
        }

        public UUID sessionId() {
            return sessionId;
        }

        public long startTick() {
            return startTick;
        }

        public long createdTick() {
            return createdTick;
        }

        public long endTick() {
            return endTick;
        }

        public Identifier dimension() {
            return dimension;
        }

        public Vec3 start() {
            return start;
        }

        public Vec3 end() {
            return end;
        }

        public float preTeleportYaw() {
            return preTeleportYaw;
        }

        public long seed() {
            return seed;
        }

        @Override
        public PacketType<ClientPacketListener, SegmentPacket> getPacketType() {
            return PacketTypes.SPACIAL_EXCISION_SEGMENT.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class EndPacket extends Packet<ClientPacketListener, EndPacket> {
        public static final StreamCodec<ByteBuf, EndPacket> CODEC = StreamCodec.of(
                (buffer, packet) -> writeUuid(buffer, packet.sessionId),
                buffer -> new EndPacket(readUuid(buffer))
        );

        private final UUID sessionId;

        public EndPacket(UUID sessionId) {
            this.sessionId = sessionId;
        }

        public UUID sessionId() {
            return sessionId;
        }

        @Override
        public PacketType<ClientPacketListener, EndPacket> getPacketType() {
            return PacketTypes.SPACIAL_EXCISION_END.get();
        }
    }

    private static void writeUuid(ByteBuf buffer, UUID uuid) {
        buffer.writeLong(uuid.getMostSignificantBits());
        buffer.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuf buffer) {
        return new UUID(buffer.readLong(), buffer.readLong());
    }

    private static Identifier parseIdentifier(String value) {
        return Identifier.tryParse(value);
    }
}
