package org.academy.internal.common.ability.accelerator.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.vfx.PlasmaChargeVisuals;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv5.PlasmaGeneration;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.Plasma;
import org.academy.internal.server.ability.AbilitySystemServer;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import org.slf4j.Logger;
import org.academy.api.common.vfx.SkillVfxState;
import org.academy.internal.client.render.vfxgraph.SkillVfxStates;






public class PlasmaGeneration extends Skill {
    public static final int TICKS_PER_STAGE = 40;
    public static final int MAX_STAGE = 6;
    public static final int MAX_CHARGE_TICKS = TICKS_PER_STAGE * MAX_STAGE;
    public static final double MAX_TARGET_RANGE = 128.0;
    public static final double TRAVEL_SPEED = 2.5;
    public static final float RADIUS_PER_STAGE = 5.0f;
    public static final float DAMAGE_PER_STAGE = 50.0f;
    public static final float BLOCK_BLAST_RADIUS_PER_STAGE = 2.5f;
    public static final int CP_PER_SECOND = 40;

    public PlasmaGeneration() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .damage()
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(CP_PER_SECOND)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.STORM_WING)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition("Storm Wing", "academy:storm_wing"))
        );
    }

    public static float calculateDamage(long chargeTicks) {
        return calculateStage(chargeTicks) * DAMAGE_PER_STAGE;
    }

    public static float calculateExplosionRadius(long chargeTicks) {
        return calculateStage(chargeTicks) * RADIUS_PER_STAGE;
    }

    public static int calculateStage(long chargeTicks) {
        return Mth.clamp((int) (Math.max(0L, chargeTicks) / TICKS_PER_STAGE), 0, MAX_STAGE);
    }

    public static float getChargeProgress(long startTick, long currentTick) {
        return Mth.clamp((float) Math.max(0, currentTick - startTick) / MAX_CHARGE_TICKS, 0.0f, 1.0f);
    }

    @Override
    public void initClient() {
        Client.registerPlasmaSession();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_CHARGE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_CHARGE,
                        InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_4, InputConstants.PRESS, 0)),
                _ -> Client.onChargeStart());
        InputSystem.addKeyBinding(Client.KEY_NAME_RELEASE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_RELEASE,
                        InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_4, InputConstants.RELEASE, 0)),
                _ -> Client.onFire());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.PLASMA_GENERATION.get(),
                        List.of(),
                        R.textures.ability.accelerator.skill.plasma_generation.icon,
                        175,
                        14
                )
        );
        public static final String KEY_NAME_CHARGE = SkillNames.PLASMA_GENERATION + "_charge";
        public static final String KEY_NAME_RELEASE = SkillNames.PLASMA_GENERATION + "_release";
        public static Config CONFIG = new Config();
        private static boolean charging;

        private Client() {
        }
        private static final Map<Long, PlasmaReplica> PLASMA_REPLICAS = new HashMap<>();
        private static final Map<Long, Long> PLASMA_RECEIVED_AT = new HashMap<>();

        private static void registerPlasmaSession() {
            SkillVfxStates.registerSession(new SkillVfxStates.Session() {
                @Override
                public void accept(long id, SkillVfxState state, long tick) {
                    if (state instanceof SkillVfxState.Plasma plasma) {
                        acceptPlasma(id, plasma, tick);
                    } else if (state instanceof SkillVfxState.End end) {
                        var replica = PLASMA_REPLICAS.remove(id);
                        PLASMA_RECEIVED_AT.remove(id);
                        if (replica != null) replica.release();
                    }
                }

                @Override
                public void tick(long tick) {
                    var iterator = PLASMA_REPLICAS.entrySet().iterator();
                    while (iterator.hasNext()) {
                        var entry = iterator.next();
                        if (tick - PLASMA_RECEIVED_AT.getOrDefault(entry.getKey(), tick) > 100) {
                            entry.getValue().release();
                            PLASMA_RECEIVED_AT.remove(entry.getKey());
                            iterator.remove();
                            continue;
                        }
                        entry.getValue().apply(tick);
                    }
                }

                @Override
                public void reset() {
                    PLASMA_REPLICAS.values().forEach(PlasmaReplica::release);
                    PLASMA_REPLICAS.clear();
                    PLASMA_RECEIVED_AT.clear();
                }
            });
        }

        private static void acceptPlasma(long id, SkillVfxState.Plasma state, long tick) {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            var existing = PLASMA_REPLICAS.get(id);
            if (existing == null) {
                var mirror = new Plasma(EntityTypes.PLASMA.get(), level);
                var replica = new PlasmaReplica(mirror, state, tick);
                PLASMA_REPLICAS.put(id, replica);
                PLASMA_RECEIVED_AT.put(id, tick);
                replica.apply(tick);
                PlasmaVfxClient.spawnCharge(mirror);
                return;
            }
            existing.state = state;
            PLASMA_RECEIVED_AT.put(id, tick);
            existing.apply(tick);
        }

        private static final class PlasmaReplica {
            final Plasma entity;
            SkillVfxState.Plasma state;
            final long receivedAt;

            PlasmaReplica(Plasma entity, SkillVfxState.Plasma state, long now) {
                this.entity = entity;
                this.state = state;
                receivedAt = now;
            }

            void apply(long now) {
                entity.applyVisualSnapshot(state, Math.min(25f, (float) (now - receivedAt)));
            }

            void release() {
                PlasmaVfxClient.release(entity);
            }
        }

        @EventBusSubscriber(Dist.CLIENT)
        public static final class PlasmaVfxClient {
            private static final Logger LOGGER = AcademyCraft.getLogger();
            private static final Identifier CHARGE_ASSET = Identifier.fromNamespaceAndPath(
                    AcademyCraft.MOD_ID, "vfxgraph/plasma_cannon_charge"
            );
            private static final Identifier FOCUS_ASSET = Identifier.fromNamespaceAndPath(
                    AcademyCraft.MOD_ID, "vfxgraph/plasma_cannon_focus"
            );
            private static final Identifier PROJECTILE_ASSET = Identifier.fromNamespaceAndPath(
                    AcademyCraft.MOD_ID, "vfxgraph/plasma_cannon_projectile"
            );
            private static final Identifier IMPACT_ASSET = Identifier.fromNamespaceAndPath(
                    AcademyCraft.MOD_ID, "vfxgraph/plasma_cannon_impact"
            );
            private static final float FOCUS_START = PlasmaChargeVisuals.FOCUS_START;
            private static final float TORNADO_EXPAND_MIN = 0.5f;
            private static final float TORNADO_EXPAND_MAX = 3.0f;
            private static final float PLASMA_MINIMUM_FAR_PLANE = 512.0f;
            private static final int IMPACT_LIFETIME_TICKS = 60;
            private static final Map<Plasma, PlasmaEffects> EFFECTS = new IdentityHashMap<>();
            private static final ArrayList<TimedEffect> IMPACTS = new ArrayList<>();

            private PlasmaVfxClient() {
            }

            public static void register() {
                // All plasma cannon visuals are graph-authored. The entity renderer remains empty.
            }

            @SubscribeEvent
            public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
                if (event.getLevel().isClientSide() && event.getEntity() instanceof Plasma plasma) {
                    spawnCharge(plasma);
                }
            }

            @SubscribeEvent
            public static void onClientTick(ClientTickEvent.Post event) {
                tickImpacts();
                var manager = VfxGraphManager.INSTANCE;
                var iterator = EFFECTS.entrySet().iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    var plasma = entry.getKey();
                    var effects = entry.getValue();
                    effects.capturePosition(plasma);

                    if (plasma.isRemoved()) {
                        stop(manager, effects.gather);
                        stop(manager, effects.focus);
                        stop(manager, effects.projectile);
                        iterator.remove();
                        continue;
                    }

                    if (!plasma.isLaunched()) {
                        if (plasma.getGatherProgress() >= FOCUS_START && effects.focus == null) {
                            effects.focus = spawnFocus(plasma, effects);
                        }
                        continue;
                    }

                    if (!effects.launched) {
                        effects.launched = true;
                        stop(manager, effects.gather);
                        effects.gather = null;
                        stop(manager, effects.focus);
                        effects.focus = null;
                        effects.projectile = spawnProjectile(plasma, effects);
                    }
                    effects.updateProjectilePosition(plasma);
                }
            }

            public static void spawnCharge(Plasma plasma) {
                var manager = VfxGraphManager.INSTANCE;
                // 实体重新加入客户端世界时可能再次触发 join；先停止同一实体的旧图实例，
                // 避免两套 focus 电弧同时存活。
                var previous = EFFECTS.remove(plasma);
                if (previous != null) {
                    stop(manager, previous.gather);
                    stop(manager, previous.focus);
                    stop(manager, previous.projectile);
                }
                ActiveEffect gather = null;
                try {
                    var fixedOrigin = chargeOrigin(plasma);
                    var fixedFocus = worldPosition(plasma);
                    var fixedFocusOffset = new Vector3f(fixedFocus).sub(fixedOrigin);
                    if (plasma.isLaunched()) {
                        var effects = new PlasmaEffects(null, fixedFocus, fixedOrigin);
                        effects.launched = true;
                        effects.projectile = spawnProjectile(plasma, effects);
                        EFFECTS.put(plasma, effects);
                        return;
                    }
                    gather = manager.spawn(CHARGE_ASSET, new Vector3f(fixedOrigin));
                    configureVisibility(gather, new Vector3f(fixedOrigin).add(0f, 64f, 0f), 152f);
                    gather.bind("emission", () -> Value.of(gatherEmission(plasma)));
                    gather.bind("charge_progress", () -> Value.of(plasma.getGatherProgress()));
                    gather.bind("expand_rate", () -> Value.of(tornadoExpandRate(plasma)));
                    gather.bind("focus_offset", () -> Value.of(new Vector3f(fixedFocusOffset)));
                    EFFECTS.put(plasma, new PlasmaEffects(gather, fixedFocus, fixedOrigin));
                } catch (RuntimeException exception) {
                    stop(manager, gather);
                    LOGGER.error("Unable to spawn graph-authored plasma charge VFX", exception);
                }
            }

            public static void release(Plasma plasma) {
                var effects = EFFECTS.remove(plasma);
                if (effects == null) return;
                var manager = VfxGraphManager.INSTANCE;
                stop(manager, effects.gather);
                stop(manager, effects.focus);
                stop(manager, effects.projectile);
            }

            private static @Nullable ActiveEffect spawnFocus(Plasma plasma, PlasmaEffects effects) {
                try {
                    var effect = VfxGraphManager.INSTANCE.spawn(FOCUS_ASSET, new Vector3f(effects.fixedFocus));
                    configureVisibility(effect, effects.fixedFocus, 32f);
                    effect.bind("convergence_progress", () -> Value.of(convergenceProgress(plasma)));
                    effect.bind("formation_progress", () -> Value.of(formationProgress(plasma)));
                    return effect;
                } catch (RuntimeException exception) {
                    LOGGER.error("Unable to spawn graph-authored plasma focus VFX", exception);
                    return null;
                }
            }

            private static @Nullable ActiveEffect spawnProjectile(Plasma plasma, PlasmaEffects effects) {
                try {
                    effects.projectileOrigin.set(worldPosition(plasma));
                    effects.projectilePosition.zero();
                    var direction = initialDirection(plasma);
                    effects.projectileDirection.set((float) direction.x, (float) direction.y, (float) direction.z);
                    var effect = VfxGraphManager.INSTANCE.spawn(PROJECTILE_ASSET, new Vector3f(effects.projectileOrigin));
                    configureVisibility(effect, effects.projectileOrigin, 128f);
                    effect.bind("projectile_position", () -> {
                        effects.updateProjectilePosition(plasma);
                        effect.setCullingSphere(worldPosition(plasma), 128f);
                        return Value.of(new Vector3f(effects.projectilePosition));
                    });
                    effect.bind("launch_scale", () -> Value.of(plasma.getLaunchScale()));
                    effect.bind("projectile_direction", () -> Value.of(new Vector3f(effects.projectileDirection)));
                    return effect;
                } catch (RuntimeException exception) {
                    LOGGER.error("Unable to spawn graph-authored plasma projectile VFX", exception);
                    return null;
                }
            }

            public static void spawnImpact(Vector3f position) {
                try {
                    var effect = VfxGraphManager.INSTANCE.spawn(IMPACT_ASSET, new Vector3f(position));
                    configureVisibility(effect, position, 96f);
                    IMPACTS.add(new TimedEffect(effect));
                } catch (RuntimeException exception) {
                    LOGGER.error("Unable to spawn graph-authored plasma impact VFX", exception);
                }
            }

            private static void tickImpacts() {
                var iterator = IMPACTS.iterator();
                while (iterator.hasNext()) {
                    var timed = iterator.next();
                    timed.age++;
                    if (timed.age > IMPACT_LIFETIME_TICKS) {
                        VfxGraphManager.INSTANCE.stop(timed.effect);
                        iterator.remove();
                    }
                }
            }

            public static void clear() {
                var manager = VfxGraphManager.INSTANCE;
                for (var e : EFFECTS.values()) {
                    stop(manager, e.gather);
                    stop(manager, e.focus);
                    stop(manager, e.projectile);
                }
                for (var impact : IMPACTS) stop(manager, impact.effect);
                EFFECTS.clear();
                IMPACTS.clear();
            }

            private static Vector3f chargeOrigin(Plasma plasma) {
                if (plasma.visualChargeOrigin() != null) return plasma.visualChargeOrigin().toVector3f();
                var owner = owner(plasma);
                if (owner != null) {
                    return worldPosition(owner);
                }
                return new Vector3f((float) plasma.getX(), (float) plasma.getY() - 31f, (float) plasma.getZ());
            }

            private static @Nullable Entity owner(Plasma plasma) {
                var ownerId = plasma.getOwnerEntityId();
                return ownerId == 0 ? null : plasma.level().getEntity(ownerId);
            }

            private static Vec3 initialDirection(Plasma plasma) {
                var owner = owner(plasma);
                var look = owner == null ? Vec3.ZERO : owner.getLookAngle();
                return look.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
            }

            private static Vector3f worldPosition(Entity entity) {
                return entity instanceof Plasma plasma && !Minecraft.getInstance().isPaused()
                        ? plasma.visualPosition().toVector3f()
                        : new Vector3f((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
            }

            private static float gatherEmission(Plasma plasma) {
                if (plasma.isRemoved() || plasma.isLaunched()) {
                    return 0.0f;
                }
                var eased = (float) Mth.smoothstep(Mth.clamp(plasma.getGatherProgress(), 0.0f, 1.0f));
                return Mth.clamp(0.32f + eased * 0.68f, 0.0f, 1.0f);
            }

            /**
             * 第一蓄力层正好持续 40 tick（2 秒），风暴缩放在这一层内线性走完 0.5 → 3.0。
             */
            private static float tornadoExpandRate(Plasma plasma) {
                float firstStage = Mth.clamp(
                        plasma.getGatherProgress() * PlasmaGeneration.MAX_STAGE,
                        0.0f,
                        1.0f
                );
                return Mth.lerp(firstStage, TORNADO_EXPAND_MIN, TORNADO_EXPAND_MAX);
            }

            private static float convergenceProgress(Plasma plasma) {
                return PlasmaChargeVisuals.convergence(plasma.getGatherProgress());
            }

            private static float formationProgress(Plasma plasma) {
                return PlasmaChargeVisuals.formation(plasma.getGatherProgress());
            }

            private static void configureVisibility(ActiveEffect effect, Vector3f center, float radius) {
                effect.setAlwaysVisible(false);
                effect.setCullingSphere(center, radius);
                effect.setRenderDistance(256f);
                effect.setMinimumFarPlane(PLASMA_MINIMUM_FAR_PLANE);
            }

            private static void stop(VfxGraphManager manager, @Nullable ActiveEffect effect) {
                if (effect != null) {
                    manager.stop(effect);
                }
            }

            private static final class PlasmaEffects {
                private @Nullable ActiveEffect gather;
                private @Nullable ActiveEffect focus;
                private @Nullable ActiveEffect projectile;
                private final Vector3f projectileOrigin = new Vector3f();
                private final Vector3f projectilePosition = new Vector3f();
                private final Vector3f projectileDirection = new Vector3f(0f, 0f, 1f);
                private final Vector3f lastPosition;
                private final Vector3f fixedOrigin;
                private final Vector3f fixedFocus;
                private boolean launched;

                private PlasmaEffects(@Nullable ActiveEffect gather, Vector3f initialPosition, Vector3f fixedOrigin) {
                    this.gather = gather;
                    this.lastPosition = new Vector3f(initialPosition);
                    this.fixedOrigin = new Vector3f(fixedOrigin);
                    this.fixedFocus = new Vector3f(initialPosition);
                }

                private void capturePosition(Plasma plasma) {
                    var current = worldPosition(plasma);
                    float dx = current.x - lastPosition.x;
                    float dy = current.y - lastPosition.y;
                    float dz = current.z - lastPosition.z;
                    float lengthSquared = dx * dx + dy * dy + dz * dz;
                    if (launched && lengthSquared > 1.0E-5f) {
                        float inverseLength = 1f / (float) Math.sqrt(lengthSquared);
                        projectileDirection.set(dx * inverseLength, dy * inverseLength, dz * inverseLength);
                    }
                    lastPosition.set(current);
                }

                private void updateProjectilePosition(Plasma plasma) {
                    projectilePosition.set(worldPosition(plasma)).sub(projectileOrigin);
                }
            }

            private static final class TimedEffect {
                private final ActiveEffect effect;
                private int age;

                private TimedEffect(ActiveEffect effect) {
                    this.effect = effect;
                }
            }
        }

        public static void onChargeStart() {
            if (charging || Minecraft.getInstance().gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.PLASMA_GENERATION.get())) return;
            charging = true;
            MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        public static void onFire() {
            if (!charging) return;
            charging = false;
            MisakaNetworkClient.send(ReleasePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

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
        private static final Map<UUID, ChargeState> CHARGE_STATES = new HashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.PLASMA_GENERATION.get().isEnabled(player)) return;
            clearCharge(player);
            if (!(player.level() instanceof ServerLevel level)) return;
            var plasma = new Plasma(EntityTypes.PLASMA.get(), level);
            var focus = chargeFocus(player, level);
            plasma.setPos(focus.x, focus.y, focus.z);
            plasma.setOwnerEntityId(player.getId());
            plasma.setGatherProgress(0.01f);
            if (!level.addFreshEntity(plasma)) return;
            CHARGE_STATES.put(player.getUUID(), new ChargeState(
                    level.dimension(), plasma.getId(), level.getGameTime()
            ));
            Skills.PLASMA_GENERATION.get().reportTrigger(player);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLASMA_GENERATION.get(),
                    SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        @SubscribePacket
        public static void handleRelease(ReleasePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var state = CHARGE_STATES.remove(player.getUUID());
            if (state == null || !state.dimension().equals(player.level().dimension())) return;
            if (!(player.level() instanceof ServerLevel level)
                    || !(level.getEntity(state.plasmaEntityId()) instanceof Plasma plasma)) {
                return;
            }
            var chargeTicks = Math.max(0L, level.getGameTime() - state.startTick());
            var skill = Skills.PLASMA_GENERATION.get();
            var effectiveChargeTicks = skill.hasProficiencyMilestone(player, 2)
                    ? Math.round(chargeTicks / 0.8)
                    : chargeTicks;
            var stage = calculateStage(effectiveChargeTicks);
            if (stage == 0) {
                plasma.discard();
                return;
            }
            var progress = getChargeProgress(state.startTick(), level.getGameTime());
            launch(player, plasma, stage, PlasmaChargeVisuals.formation(progress));
        }

        private static void launch(ServerPlayer player, Plasma plasma, int stage, float launchScale) {
            var skill = Skills.PLASMA_GENERATION.get();
            var targetPos = findTarget(player);
            var damage = stage * DAMAGE_PER_STAGE
                    * AbilitySystemServer.getSystem(player).getPlayerAbilityPowerMultiplier(player.getUUID())
                    * AbilitySystemServer.getSystem(player).getPlayerDamageMultiplier(player.getUUID());
            var destroyBlocks = DestroyBlocksSetting.canDestroyBlocks(player, skill);
            var damageRadius = skill.scaledRange(player, stage * RADIUS_PER_STAGE
                    * (skill.hasProficiencyMilestone(player, 2) ? 1.1f : 1.0f));
            plasma.launch(
                    player.getUUID(),
                    targetPos,
                    TRAVEL_SPEED,
                    damage,
                    damageRadius,
                    destroyBlocks ? stage * BLOCK_BLAST_RADIUS_PER_STAGE : 0.0f,
                    destroyBlocks,
                    skill.getEffectiveProficiencyMilestone(player),
                    launchScale
            );
        }

        private static Vec3 findTarget(ServerPlayer player) {
            var targetRange = Skills.PLASMA_GENERATION.get().scaledRange(player, MAX_TARGET_RANGE);
            var start = player.getEyePosition();
            var look = player.getLookAngle();
            var end = start.add(look.scale(targetRange));
            var searchBox = player.getBoundingBox().expandTowards(look.scale(targetRange)).inflate(1.0);
            var entityHit = ProjectileUtil.getEntityHitResult(
                    player.level(), player, start, end, searchBox,
                    entity -> entity != player && !entity.isSpectator()
                            && entity.isAlive() && entity.isPickable()
                            && !PvpSetting.shouldPrevent(player, entity),
                    0.3f
            );
            if (entityHit != null) {
                return entityHit.getEntity().getBoundingBox().getCenter();
            }
            var blockHit = player.level().clip(new ClipContext(
                    start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
            ));
            return blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        }

        private static void tick(ServerPlayer player) {
            var state = CHARGE_STATES.get(player.getUUID());
            if (state == null) return;
            if (!player.isAlive() || player.hasDisconnected()
                    || !state.dimension().equals(player.level().dimension())
                    || !Skills.PLASMA_GENERATION.get().isEnabled(player)) {
                clearCharge(player);
                return;
            }
            if (!(player.level() instanceof ServerLevel level)
                    || !(level.getEntity(state.plasmaEntityId()) instanceof Plasma plasma)) {
                CHARGE_STATES.remove(player.getUUID());
                return;
            }
            var ticks = Math.max(0, player.level().getGameTime() - state.startTick());
            var skill = Skills.PLASMA_GENERATION.get();
            skill.reportActivity(player, true);
            var seconds = ticks / 20;
            if (seconds > 0 && seconds > state.lastConsumedSecond()) {
                var paid = skill.executeContinuous(player, (_, _) -> {
                }, true);
                if (!paid) {
                    clearCharge(player);
                    return;
                }
                state.lastConsumedSecond = seconds;
            }
            plasma.setGatherProgress(Math.max(
                    0.01f,
                    getChargeProgress(state.startTick(), player.level().getGameTime())
            ));
            // The charge focus is captured once in handleStart. Keeping the entity at that
            // position makes the whole charge composition independent from later player motion.
            plasma.setDeltaMovement(Vec3.ZERO);
        }

        private static Vec3 chargeFocus(ServerPlayer player, ServerLevel level) {
            var look = player.getLookAngle();
            var horizontal = new Vec3(look.x, 0.0, look.z);
            if (horizontal.lengthSqr() < 1.0E-6) {
                horizontal = new Vec3(0.0, 0.0, 1.0);
            } else {
                horizontal = horizontal.normalize();
            }
            var y = Mth.clamp(player.getY() + 31.0, level.getMinY() + 1.0, level.getMaxY() - 1.0);
            return new Vec3(
                    player.getX() + horizontal.x * 6.0,
                    y,
                    player.getZ() + horizontal.z * 6.0
            );
        }

        private static void clearCharge(ServerPlayer player) {
            var state = CHARGE_STATES.remove(player.getUUID());
            if (state == null) return;
            var level = player.level().getServer().getLevel(state.dimension());
            if (level != null && level.getEntity(state.plasmaEntityId()) instanceof Plasma plasma) {
                plasma.discard();
            }
        }

        private static final class ChargeState {
            private final ResourceKey<Level> dimension;
            private final int plasmaEntityId;
            private final long startTick;
            private long lastConsumedSecond;

            private ChargeState(ResourceKey<Level> dimension,
                                int plasmaEntityId, long startTick) {
                this.dimension = dimension;
                this.plasmaEntityId = plasmaEntityId;
                this.startTick = startTick;
            }

            private ResourceKey<Level> dimension() {
                return dimension;
            }

            private long startTick() {
                return startTick;
            }

            private int plasmaEntityId() {
                return plasmaEntityId;
            }

            private long lastConsumedSecond() {
                return lastConsumedSecond;
            }
        }

    }

    @EventBusSubscriber
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clearCharge(player);
        }

        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clearCharge(player);
        }

        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clearCharge(player);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> {
        public static final StartPacket INSTANCE = new StartPacket();
        public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StartPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() {
            return PacketTypes.PLASMA_GENERATION_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ReleasePacket extends Packet<ServerGamePacketListenerImpl, ReleasePacket> {
        public static final ReleasePacket INSTANCE = new ReleasePacket();
        public static final StreamCodec<ByteBuf, ReleasePacket> CODEC = StreamCodec.unit(INSTANCE);

        private ReleasePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ReleasePacket> getPacketType() {
            return PacketTypes.PLASMA_GENERATION_RELEASE.get();
        }
    }
}
