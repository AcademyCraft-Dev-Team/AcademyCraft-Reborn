package org.academy.internal.common.ability.electromaster.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.electromaster.IronSandTuning;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilityActorContext;
import org.academy.api.server.ability.HostileProjectiles;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.ability.electromaster.IronSandActions;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.hud.ability.ToggleStatusHud;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.Accelerator;
import org.academy.internal.common.ability.electromaster.Electromaster;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.ability.AbilitySystemServer;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

public class IronSandArsenal extends Skill {
    public static final float SWEEP_DAMAGE = 10;
    public static final double SWEEP_RADIUS = 12;

    public IronSandArsenal() {
        super(Builder.of(AbilityCategories.ELECTROMASTER.get()).damage().level(AbilityLevel.LEVEL4)
                .energyCost(60_000).iterationTicks(20).maxStacks(NO_STACK_LIMIT).dependsOn(Skills.MAGNET_MANIPULATION));
    }

    @Override
    public void initClient() {
        Client.init(this);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    private boolean whip(ServerPlayer player) {
        return executeActiveWithResource(player, _ -> 0, ctx -> (float) IronSandTuning.massCost(5, ctx.milestone()),
                (_, _) -> {
                    IronSandActions.whip(Server.actor(player), scaledRange(player,
                            IronSandTuning.whipRange(getEffectiveProficiencyMilestone(player))));
                    Server.SWING_UNTIL.put(player, player.level().getServer().getTickCount() + 10L);
                    Server.COOLDOWN_UNTIL.put(player, (long) player.level().getServer().getTickCount() + IronSandTuning.HIT_INTERVAL_TICKS);
                    Server.syncData(player);
                    var packet = new SweepVisualPacket(player.getId());
                    for (var observer : player.level().players()) {
                        if (observer.distanceToSqr(player) <= 128 * 128) MisakaNetworkServer.send(observer, packet);
                    }
                });
    }

    private boolean cloudTick(ServerPlayer player, Context context) {
        return executeContinuousWithResource(player, _ -> 0,
                ctx -> (float) IronSandTuning.massCost(0.5, ctx.milestone()), (_, _) -> {
                    if (++context.paidTicks == 1) reportTrigger(player);
                    if (context.paidTicks % IronSandTuning.HIT_INTERVAL_TICKS == 0) {
                        var hits = IronSandActions.cloudPulse(Server.actor(player), scaledRange(player,
                                IronSandTuning.cloudRadius(getEffectiveProficiencyMilestone(player))));
                        if (hits > 0) reportActivity(player, true);
                    }
                }, false);
    }

    public static final class Client {
        public static final int IRON_SAND_PARTICLES = 24;

        public static Accelerator.Client.FirstPersonSweepGeometry.Point ironSandPosition(
                float handSide, float progress, int particleIndex
        ) {
            var clampedProgress = Mth.clamp(progress, 0.0f, 1.0f);
            var radialProgress = particleIndex / (float) (IRON_SAND_PARTICLES - 1);
            var eased = clampedProgress * clampedProgress * (3.0f - 2.0f * clampedProgress);
            var sweepDegrees = handSide * (60.0f - eased * 120.0f);
            var trailingDegrees = handSide * (1.0f - radialProgress) * 24.0f;
            var angle = (sweepDegrees + trailingDegrees) * Mth.DEG_TO_RAD;
            var radius = 0.55f + radialProgress * 2.20f;
            var x = Mth.sin(angle) * radius;
            var y = -0.48f + radialProgress * 0.45f
                    + Mth.sin(particleIndex * 0.72f) * 0.05f
                    + Mth.sin(Mth.PI * clampedProgress) * 0.12f;
            var z = -0.55f - radialProgress * 3.45f;
            return new Accelerator.Client.FirstPersonSweepGeometry.Point(x, y, z);
        }

        public static float ironSandScale(int particleIndex) {
            var radialProgress = particleIndex / (float) (IRON_SAND_PARTICLES - 1);
            return 0.08f + radialProgress * 0.18f;
        }

        public static float ironSandAlpha(float progress, int particleIndex) {
            var clampedProgress = Mth.clamp(progress, 0.0f, 1.0f);
            var radialProgress = particleIndex / (float) (IRON_SAND_PARTICLES - 1);
            return (0.75f - radialProgress * 0.20f)
                    * Mth.sin(Mth.PI * clampedProgress);
        }

        public static final class IronSandVfxClient {
            private static final Map<Integer, ActiveEffect> RINGS = new HashMap<>();
            private static final Map<Integer, ActiveEffect> CLOUDS = new HashMap<>();
            private static final ArrayDeque<ActiveEffect> TRANSIENTS = new ArrayDeque<>();
            private static @Nullable ClientLevel level;

            private IronSandVfxClient() {
            }

            public static void tick() {
                var current = Minecraft.getInstance().level;
                if (level != current) {
                    clear();
                    level = current;
                }
                if (level == null) return;
                prune(RINGS, false);
                prune(CLOUDS, true);
                TRANSIENTS.removeIf(ActiveEffect::isExpired);
                for (var player : level.players()) {
                    if (!player.isAlive() || player.isRemoved()) continue;
                    var state = player.getData(AttachmentTypes.IRON_SAND_DATA.get());
                    if (state.defense()) RINGS.computeIfAbsent(player.getId(), _ -> attached("defense", player, false));
                    if (state.cloudRadius() > 0)
                        CLOUDS.computeIfAbsent(player.getId(), _ -> attached("cloud", player, true));
                }
            }

            private static void prune(Map<Integer, ActiveEffect> effects, boolean cloud) {
                var currentLevel = level;
                if (currentLevel == null) return;
                effects.entrySet().removeIf(entry -> {
                    var entity = currentLevel.getEntity(entry.getKey());
                    var state = entity == null ? null : entity.getData(AttachmentTypes.IRON_SAND_DATA.get());
                    boolean remove = entity == null || !entity.isAlive() || entity.isRemoved()
                            || (cloud ? state.cloudRadius() <= 0 : !state.defense());
                    if (remove) entry.getValue().stop();
                    return remove;
                });
            }

            private static ActiveEffect attached(String name, Entity entity, boolean cloud) {
                var effect = spawn(name, entity.position());
                effect.follow(entity);
                effect.bindFrame((active, camera, partialTick) -> {
                    if (entity.level() != Minecraft.getInstance().level || !entity.isAlive()) return false;
                    float radius = cloud ? entity.getData(AttachmentTypes.IRON_SAND_DATA.get()).cloudRadius()
                            : "defense".equals(name) ? 1.2f : 1.4f;
                    active.effect().setLiveParam("radius", Value.of(radius));
                    active.setCullingSphere(active.position(), radius + 3);
                    active.effect().setLiveParam("detail", Value.of(detail(entity)));
                    return true;
                });
                return effect;
            }

            public static void whip(int id) {
                tick();
                var entity = level == null ? null : level.getEntity(id);
                if (entity == null) return;
                var effect = attached("whip", entity, false);
                effect.setRotation(new Quaternionf().rotationY((float) Math.toRadians(-entity.getYRot())));
                effect.bindFrame((active, camera, partialTick) -> {
                    float radius = entity.getData(AttachmentTypes.IRON_SAND_DATA.get()).whipRange();
                    active.effect().setLiveParam("radius", Value.of(radius));
                    active.effect().setLiveParam("detail", Value.of(detail(entity)));
                    active.setCullingSphere(active.position(), radius + 2);
                    return entity.isAlive() && entity.level() == Minecraft.getInstance().level;
                });
                transientEffect(effect, 0.5f);
            }

            public static void defenseEvent(int id, boolean intercept, Vec3 vector) {
                tick();
                var entity = level == null ? null : level.getEntity(id);
                if (entity == null || !Double.isFinite(vector.lengthSqr())) return;
                if (intercept) {
                    var effect = spawn("intercept", vector);
                    effect.bindFrame((active, camera, partialTick) -> entity.level() == Minecraft.getInstance().level);
                    effect.setCullingSphere(vector.toVector3f(), 3);
                    transientEffect(effect, 0.24f);
                } else {
                    var effect = attached("guard", entity, false);
                    effect.setRotation(new Quaternionf().rotationY((float) Math.atan2(vector.x, vector.z)));
                    effect.effect().setLiveParam("source_elevation", Value.of((float) Math.clamp(vector.y, -1, 1)));
                    transientEffect(effect, 0.65f);
                }
            }

            private static ActiveEffect spawn(String name, Vec3 position) {
                var effect = VfxGraphManager.INSTANCE.spawn(AcademyCraft.academy("vfxgraph/iron_sand_" + name), position.toVector3f());
                effect.bindGameTime("time");
                effect.setRenderDistance(64);
                effect.effect().setLiveParam("seed", Value.of((float) (level == null ? 42 : level.getGameTime() % 100000)));
                return effect;
            }

            private static void transientEffect(ActiveEffect effect, float duration) {
                effect.setGameTimeLifetimeSeconds(duration);
                TRANSIENTS.removeIf(ActiveEffect::isExpired);
                while (TRANSIENTS.size() >= 24) TRANSIENTS.removeFirst().stop();
                TRANSIENTS.addLast(effect);
            }

            private static float detail(Entity entity) {
                var mc = Minecraft.getInstance();
                float quality = mc.options.particles().get() == ParticleStatus.MINIMAL ? 0.35f
                        : mc.options.particles().get() == ParticleStatus.DECREASED ? 0.65f : 1;
                return mc.player != null && mc.player.distanceToSqr(entity) > 32 * 32 ? quality * 0.5f : quality;
            }

            public static void clear() {
                RINGS.values().forEach(ActiveEffect::stop);
                CLOUDS.values().forEach(ActiveEffect::stop);
                TRANSIENTS.forEach(ActiveEffect::stop);
                RINGS.clear();
                CLOUDS.clear();
                TRANSIENTS.clear();
                level = null;
            }
        }

        public static final class ElectromasterWeaponVfxClient {
            private ElectromasterWeaponVfxClient() {
            }

            private static boolean registered;

            public static void register() {
                if (registered) return;
                registered = true;
                IronSandArsenal.Client.IronSandVfxClient.clear();
            }
        }

        public static final String KEY_TOGGLE = SkillNames.IRON_SAND_ARSENAL + "_toggle";
        public static final String KEY_USE = SkillNames.IRON_SAND_ARSENAL + "_use";
        public static Config CONFIG = new Config();
        private static boolean initialized;
        private static int sequence;
        private static int activeSequence;

        private static void init(IronSandArsenal skill) {
            if (initialized) return;
            initialized = true;
            MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
            NeoForge.EVENT_BUS.register(Client.class);
            AcademyCraftConfig.registerTypeHandler(skill.getKey(), Config.Action.INSTANCE);
            CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(skill.getKey());
            // Keep custom shield bindings when iron sand itself was left at its old default.
            AcademyCraftConfig.registerTypeHandler(Skills.ELECTROMAGNETIC_SHIELD.get().getKey(), ElectromagneticShield.Client.Config.Action.INSTANCE);
            ElectromagneticShield.Client.Config legacy = AcademyCraftClient.Config.INSTANCE.getConfig(Skills.ELECTROMAGNETIC_SHIELD.get().getKey());
            var oldShield = legacy.getKeyBinding(ElectromagneticShield.Client.KEY_NAME_TOGGLE);
            var oldSand = CONFIG.getKeyBinding(KEY_TOGGLE);
            var oldSandDefault = InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G, InputConstants.PRESS, InputConstants.MOD_ALT);
            var oldShieldDefault = InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G, InputConstants.RELEASE, InputConstants.MOD_SHIFT);
            if (oldShield != null && !oldShield.equals(oldShieldDefault) && (oldSand == null || oldSand.equals(oldSandDefault))) {
                CONFIG.setKeyBinding(KEY_TOGGLE, InputSystem.withAction(oldShield, InputConstants.PRESS));
            }
            legacy.removeKeyBinding(ElectromagneticShield.Client.KEY_NAME_TOGGLE);
            InputSystem.addKeyBinding(KEY_TOGGLE, CONFIG.getKeyBindingMigratingDefaults(KEY_TOGGLE,
                            InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G, InputConstants.PRESS, InputConstants.MOD_SHIFT),
                            InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G, InputConstants.PRESS, InputConstants.MOD_ALT)),
                    _ -> {
                        if (AbilitySystemClient.canUseSkill(skill)) MisakaNetworkClient.send(TogglePacket.INSTANCE);
                    });
            InputSystem.addMaintainedKeyBinding(KEY_USE, CONFIG.getKeyBinding(KEY_USE,
                            InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G, InputConstants.PRESS, InputConstants.MOD_ALT)),
                    _ -> {
                        if (!AbilitySystemClient.canUseSkill(skill) || Minecraft.getInstance().gui.screen() != null)
                            return;
                        activeSequence = ++sequence;
                        MisakaNetworkClient.send(new ActionPacket(activeSequence, 0));
                    }, _ -> stop(false), _ -> {
                        // The maintained-binding heartbeat fires every 20 client ticks; the server
                        // drops the context after 40 silent ticks, so every callback must refresh it.
                        if (activeSequence != 0) MisakaNetworkClient.send(new ActionPacket(activeSequence, 3));
                    }, () -> Minecraft.getInstance().player != null && Minecraft.getInstance().player.isAlive()
                            && Minecraft.getInstance().gui.screen() == null && AbilitySystemClient.canUseSkill(skill));
            ToggleStatusHud.Companion.registerStateProvider(skill, () -> Minecraft.getInstance().player != null
                    && Minecraft.getInstance().player.getData(AttachmentTypes.IRON_SAND_DATA.get()).defense());
            ToggleStatusHud.Companion.registerDetailProvider(skill, () -> String.format(Locale.ROOT, "%.1f / %.1f MP",
                    AbilitySystemClient.getCurrMP(), AbilitySystemClient.getMaxMP()));
            IronSandArsenal.Client.ElectromasterWeaponVfxClient.register();
        }

        private static void stop(boolean cancel) {
            if (activeSequence == 0) return;
            var mc = Minecraft.getInstance();
            if (mc.player != null) MisakaNetworkClient.send(new ActionPacket(activeSequence,
                    cancel || mc.gui.screen() != null || !mc.isWindowActive() || !mc.player.isAlive() ? 2 : 1));
            activeSequence = 0;
        }

        @SubscribePacket
        public static void handleSweepVisual(SweepVisualPacket packet) {
            IronSandArsenal.Client.IronSandVfxClient.whip(packet.entityId());
        }

        @SubscribePacket
        public static void handleDefenseVisual(DefenseVisualPacket packet) {
            IronSandArsenal.Client.IronSandVfxClient.defenseEvent(packet.entityId, packet.intercept, packet.vector);
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            IronSandArsenal.Client.IronSandVfxClient.tick();
            if (activeSequence != 0 && (Minecraft.getInstance().player == null || Minecraft.getInstance().gui.screen() != null))
                stop(true);
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            activeSequence = 0;
            sequence = 0;
            IronSandArsenal.Client.IronSandVfxClient.clear();
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

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
        private static final Map<Player, Context> CONTEXTS = createContextMap();
        private static final Map<Player, Integer> LAST_SEQUENCE = new WeakHashMap<>();
        private static final Map<Player, Long> COOLDOWN_UNTIL = new WeakHashMap<>();
        private static final Map<Player, Long> SWING_UNTIL = new WeakHashMap<>();

        public static float calculateDamage(float base, float power, float multiplier) {
            return (float) IronSandTuning.scaleDamage(base, power, multiplier);
        }

        public static boolean isActive(ServerPlayer player) {
            return Electromaster.resourceService().isDefenseActive(player) || CONTEXTS.containsKey(player);
        }

        public static void forceDisable(ServerPlayer player) {
            var context = CONTEXTS.get(player);
            if (context != null) context.unregister();
            Electromaster.resourceService().setDefense(player, false);
        }

        /**
         * Old swing packets must no longer trigger a second attack.
         */
        @Deprecated
        public static void onLeftClickSwing(ServerPlayer player) {
        }

        public static AbilityActorContext actor(ServerPlayer player) {
            return AbilityActorContext.of(player, Skills.IRON_SAND_ARSENAL.get(),
                    Electromaster.resourceService().account(player));
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var service = Electromaster.resourceService();
            service.setDefense(player, !service.isDefenseActive(player));
        }

        @SubscribePacket
        public static void handleAction(ActionPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var now = player.level().getServer().getTickCount();
            var existing = CONTEXTS.get(player);
            if (packet.action == 0) {
                if (existing != null || packet.sequence <= LAST_SEQUENCE.getOrDefault(player, 0)) return;
                LAST_SEQUENCE.put(player, packet.sequence);
                if (!Skills.IRON_SAND_ARSENAL.get().isEnabled(player) || !player.isAlive() || player.isSpectator()
                        || now < COOLDOWN_UNTIL.getOrDefault(player, 0L)) return;
                var context = new Context(player, packet.sequence);
                CONTEXTS.put(player, context);
                AbilitySystemServer.registerContext(context);
            } else if (existing != null && packet.sequence == existing.sequence) {
                if (packet.action == 3) existing.heartbeat = now;
                else if (packet.action == 1 || packet.action == 2) {
                    var tap = packet.action == 1 && !existing.cloud && now - existing.started < IronSandTuning.HOLD_TICKS;
                    existing.unregister();
                    if (tap) Skills.IRON_SAND_ARSENAL.get().whip(player);
                }
            }
        }

        public static void syncData(ServerPlayer player) {
            var context = CONTEXTS.get(player);
            var defense = Electromaster.resourceService().isDefenseActive(player);
            var swing = Math.max(0, (int) (SWING_UNTIL.getOrDefault(player, 0L) - player.level().getServer().getTickCount()));
            var cloud = context != null && context.cloud;
            var milestone = Skills.IRON_SAND_ARSENAL.get().getEffectiveProficiencyMilestone(player);
            var skill = Skills.IRON_SAND_ARSENAL.get();
            var data = new Data(defense || cloud || swing > 0, swing, defense,
                    cloud ? (float) skill.scaledRange(player, IronSandTuning.cloudRadius(milestone)) : 0,
                    (float) skill.scaledRange(player, IronSandTuning.whipRange(milestone)));
            if (!data.equals(player.getData(AttachmentTypes.IRON_SAND_DATA.get()))) {
                player.setData(AttachmentTypes.IRON_SAND_DATA.get(), data);
                player.syncData(AttachmentTypes.IRON_SAND_DATA.get());
            }
        }

        public static boolean intercept(ServerPlayer player, Projectile projectile) {
            var service = Electromaster.resourceService();
            if (!service.isDefenseActive(player) || !player.isAlive()) return false;
            var cost = IronSandTuning.massCost(10, Skills.IRON_SAND_ARSENAL.get().getEffectiveProficiencyMilestone(player));
            var impact = HostileProjectiles.sphereEntry(projectile.position(), projectile.position().add(projectile.getDeltaMovement()),
                    player.getBoundingBox().getCenter(), IronSandTuning.INTERCEPTION_RADIUS);
            var destroyed = HostileProjectiles.tryDestroy(player, projectile, service.account(player), IronSandTuning.INTERCEPTION_RADIUS, cost);
            if (destroyed) {
                Skills.IRON_SAND_ARSENAL.get().reportActivity(player, true);
                if (impact != null) broadcast(player, new DefenseVisualPacket(player.getId(), true, impact));
            }
            return destroyed;
        }

        public static void absorbed(ServerPlayer player, @Nullable DamageSource source) {
            // Read this health-write transaction, not the previous hurt animation's source.
            var origin = source == null ? null : source.getDirectEntity() != null
                    ? source.getDirectEntity().getBoundingBox().getCenter() : source.getSourcePosition();
            if (origin == null && source != null && source.getEntity() != null)
                origin = source.getEntity().getBoundingBox().getCenter();
            var direction = origin == null ? player.getLookAngle() : origin.subtract(player.getBoundingBox().getCenter());
            if (direction.lengthSqr() < 1.0e-8) direction = player.getLookAngle();
            broadcast(player, new DefenseVisualPacket(player.getId(), false, direction.normalize()));
        }

        private static void broadcast(ServerPlayer player, DefenseVisualPacket packet) {
            for (var observer : player.level().players()) {
                if (observer.distanceToSqr(player) <= 64 * 64) MisakaNetworkServer.send(observer, packet);
            }
        }

        public static void interceptNearby(ServerPlayer player) {
            for (var projectile : player.level().getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(2)))
                intercept(player, projectile);
        }
    }

    public static final class Context extends ServerContext {
        private final int sequence;
        private final long started;
        private final ResourceKey<Level> dimension;
        private long heartbeat;
        private int paidTicks;
        private boolean cloud;
        private boolean ended;

        private Context(ServerPlayer player, int sequence) {
            super(player);
            this.sequence = sequence;
            started = heartbeat = player.level().getServer().getTickCount();
            dimension = player.level().dimension();
        }

        @SubscribeEvent
        public void tick(ServerTickEvent.Pre event) {
            if (ended) return;
            var now = player.level().getServer().getTickCount();
            var skill = Skills.IRON_SAND_ARSENAL.get();
            if (!player.isAlive() || player.hasDisconnected() || player.isSpectator() || !skill.isEnabled(player)
                    || !player.level().dimension().equals(dimension) || now - heartbeat > 40) {
                unregister();
                return;
            }
            if (now - started < IronSandTuning.HOLD_TICKS) return;
            cloud = true;
            if (!skill.cloudTick(player, this)) {
                unregister();
                return;
            }
            Server.syncData(player);
        }

        @Override
        protected void onUnregistered() {
            ended = true;
            Server.CONTEXTS.remove(player, this);
            Server.syncData(player);
        }
    }

    @EventBusSubscriber
    public static final class Events {
        @SubscribeEvent
        public static void beforeProjectileTick(EntityTickEvent.Pre event) {
            if (!(event.getEntity() instanceof Projectile projectile) || !(projectile.level() instanceof ServerLevel level))
                return;
            var path = projectile.getBoundingBox().expandTowards(projectile.getDeltaMovement()).inflate(3);
            for (var player : level.getEntitiesOfClass(ServerPlayer.class, path)) {
                if (Server.intercept(player, projectile)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }

        @SubscribeEvent
        public static void playerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.syncData(player);
        }
    }

    public record Data(boolean active, int swingTicks, boolean defense, float cloudRadius, float whipRange) {
        public static final Data DEFAULT = new Data(false, 0, false, 0, 12);

        public Data(boolean active, int swingTicks) {
            this(active, swingTicks, active, 0, 12);
        }

        public static final StreamCodec<ByteBuf, Data> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Data::active, ByteBufCodecs.VAR_INT, Data::swingTicks,
                ByteBufCodecs.BOOL, Data::defense, ByteBufCodecs.FLOAT, Data::cloudRadius,
                ByteBufCodecs.FLOAT, Data::whipRange, Data::new);
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActionPacket extends Packet<ServerGamePacketListenerImpl, ActionPacket> {
        public static final StreamCodec<ByteBuf, ActionPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, value -> value.sequence, ByteBufCodecs.VAR_INT, value -> value.action, ActionPacket::new);
        private final int sequence;
        private final int action;

        public ActionPacket(int sequence, int action) {
            this.sequence = sequence;
            this.action = action;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActionPacket> getPacketType() {
            return PacketTypes.IRON_SAND_ACTION.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class DefenseVisualPacket extends Packet<ClientPacketListener, DefenseVisualPacket> {
        public static final StreamCodec<ByteBuf, DefenseVisualPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, p -> p.entityId, ByteBufCodecs.BOOL, p -> p.intercept,
                ByteBufCodecs.DOUBLE, p -> p.vector.x, ByteBufCodecs.DOUBLE, p -> p.vector.y,
                ByteBufCodecs.DOUBLE, p -> p.vector.z,
                (id, intercept, x, y, z) -> new DefenseVisualPacket(id, intercept, new Vec3(x, y, z)));
        public final int entityId;
        public final boolean intercept;
        public final Vec3 vector;

        public DefenseVisualPacket(int entityId, boolean intercept, Vec3 vector) {
            this.entityId = entityId;
            this.intercept = intercept;
            this.vector = vector;
        }

        @Override
        public PacketType<ClientPacketListener, DefenseVisualPacket> getPacketType() {
            return PacketTypes.IRON_SAND_DEFENSE_VISUAL.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SweepVisualPacket extends Packet<ClientPacketListener, SweepVisualPacket> {
        public static final StreamCodec<ByteBuf, SweepVisualPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SweepVisualPacket::entityId, SweepVisualPacket::new);
        private final int entityId;

        public SweepVisualPacket(int entityId) {
            this.entityId = entityId;
        }

        public int entityId() {
            return entityId;
        }

        @Override
        public PacketType<ClientPacketListener, SweepVisualPacket> getPacketType() {
            return PacketTypes.IRON_SAND_ARSENAL_SWEEP_VISUAL.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.IRON_SAND_ARSENAL_TOGGLE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class FormSelectPacket extends Packet<ServerGamePacketListenerImpl, FormSelectPacket> {
        public static final FormSelectPacket INSTANCE = new FormSelectPacket();
        public static final StreamCodec<ByteBuf, FormSelectPacket> CODEC = StreamCodec.unit(INSTANCE);

        private FormSelectPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, FormSelectPacket> getPacketType() {
            return PacketTypes.IRON_SAND_ARSENAL_FORM_SELECT.get();
        }
    }
}
