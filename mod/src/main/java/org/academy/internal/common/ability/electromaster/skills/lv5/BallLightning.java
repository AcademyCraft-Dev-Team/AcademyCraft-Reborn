package org.academy.internal.common.ability.electromaster.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.DamageComposition;
import org.academy.api.common.damage.MaxHealthDamage;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.ability.ElectromasterGraphEffects;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.util.QuantumUtil;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.skill.LightOrb;
import org.academy.internal.server.ability.AbilitySystemServer;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class BallLightning extends Skill {
    public static final String KEY_NAME_ACTIVATE = SkillNames.BALL_LIGHTNING + "_activate";
    public static final float MAX_RADIUS = 64.0F;
    public static final int MAX_DURATION_TICKS = 2000;

    public BallLightning() {
        super(Builder.of(AbilityCategories.ELECTROMASTER.get())
                .damage()
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(80)
                .iterationTicks(20)
                .maxStacks(1)
                .dependsOn(Skills.LIGHTNING_NOVA)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.BallLightningConfig.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        var newDefault = InputSystem.combo(
                InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                InputConstants.PRESS, InputConstants.MOD_CONTROL
        );
        var configured = Client.CONFIG.getKeyBinding(KEY_NAME_ACTIVATE, newDefault);
        AcademyCraftClient.Config.INSTANCE.setConfig(key, Client.CONFIG);
        AcademyCraftClient.Config.INSTANCE.save();
        InputSystem.addKeyBinding(KEY_NAME_ACTIVATE, configured, ctx -> Client.handler());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ELECTROMASTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.BALL_LIGHTNING.get(),
                        List.of(),
                        R.textures.ball_lightning_icon,
                        184,
                        72
                )
        );
        public static BallLightningConfig CONFIG = new BallLightningConfig();

        public static void handler() {
            var minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.BALL_LIGHTNING.get())) return;
            MisakaNetworkClient.send(ActivatePacket.INSTANCE);
        }

        public static class BallLightningConfig extends KeyBindingConfig {
            public static final class Action implements TypeHandler<BallLightningConfig> {
                public static final TypeHandler<BallLightningConfig> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public BallLightningConfig getDefault() {
                    return new BallLightningConfig();
                }

                @Override
                public Class<BallLightningConfig> getTypeClass() {
                    return BallLightningConfig.class;
                }
            }
        }
    }

    public static final class Server {
        private static final MaxHealthDamage IMPACT_DAMAGE = MaxHealthDamage.rebalance(10.0f, 0.30f);
        private static final Map<ServerPlayer, Context> ACTIVE = new WeakHashMap<>();

        public static float calculateImpactDamage(float maxHealth, float abilityPower, float playerMultiplier) {
            return IMPACT_DAMAGE.calculate(maxHealth,
                    Math.max(0.0f, abilityPower) * Math.max(0.0f, playerMultiplier));
        }

        @SubscribePacket
        public static void handle(ActivatePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (ACTIVE.containsKey(player)) return;
            Skills.BALL_LIGHTNING.get().executeActive(player, (skillContext, _) -> {
                var context = new Context(player, skillContext.milestone());
                ACTIVE.put(player, context);
                AbilitySystemServer.registerContext(context);
            });
        }

        public static class Context extends ServerContext {
            private final LightOrb coreOrb;
            private final float timeSeed;
            private final int proficiencyMilestone;

            private int existedTicks = 0;
            private Vec3 position;
            private Vec3 velocity;
            @Nullable
            private Vec3 roamTarget;
            private int strafeDir = 1;

            private boolean hasTarget = false;
            @Nullable
            private Entity targetEntity = null;

            private BehaviorState currentState;
            private int stateTimer;
            private boolean ended;

            public Context(ServerPlayer player, int proficiencyMilestone) {
                super(player);
                this.proficiencyMilestone = proficiencyMilestone;
                position = player.getEyePosition().add(0, 1, 0);
                timeSeed = (float) (MathUtil.RANDOM.nextDouble() * 10000);

                var look = player.getLookAngle();
                velocity = look.scale(0.5f);

                currentState = BehaviorState.START;
                stateTimer = 12;

                var level = level();
                coreOrb = new LightOrb(level, MAX_DURATION_TICKS, 0.15f, this::updateCoreOrb);
                coreOrb.setPos(position);
                level.addFreshEntity(coreOrb);
                ElectromasterGraphEffects.spawnBallLightning(level, position, coreOrb.getId(),
                        MAX_DURATION_TICKS / 20f, MathUtil.RANDOM.nextLong());
            }

            @SubscribeEvent
            public void onTick(ServerTickEvent.Post event) {
                if (player.hasDisconnected() || !player.isAlive()
                        || !Skills.BALL_LIGHTNING.get().isEnabled(player)
                        || existedTicks++ >= MAX_DURATION_TICKS) {
                    end();
                    return;
                }

                Skills.BALL_LIGHTNING.get().reportActivity(player, true);
                updateTarget();
                updateStateTransition();
                updatePhysics();
                checkImpact();
            }

            private void updateCoreOrb() {
                var lifeTime = coreOrb.getLifeTime();
                if (lifeTime <= 5) {
                    coreOrb.setScale(coreOrb.getScale() * 0.5f);
                } else if (lifeTime <= 10) {
                    coreOrb.setScale(coreOrb.getScale() * 2f);
                } else {
                    var scale = 0.1f + 0.2f * Mth.sin(lifeTime * 0.2f);
                    coreOrb.setScale(scale);
                    var bluePulse = 0.6f + 0.4f * Mth.sin(lifeTime * 0.1f);
                    coreOrb.setColor(0.3f, 0.6f, bluePulse);
                }
            }

            private void updateTarget() {
                if (targetEntity != null && (targetEntity.isRemoved() || !targetEntity.isAlive())) {
                    hasTarget = false;
                    targetEntity = null;
                }

                if (!hasTarget && currentState != BehaviorState.START) {
                    var searchRadius = Skills.BALL_LIGHTNING.get().scaledRange(player,
                            proficiencyMilestone >= 2 ? MAX_RADIUS * 1.2f : MAX_RADIUS);
                    var entities = MathUtil.getEntitiesInSphereByHP(level(), position, searchRadius, e -> e != player);
                    if (!entities.isEmpty()) {
                        targetEntity = entities.getFirst();
                        hasTarget = true;
                    }
                }
            }

            private void updateStateTransition() {
                if (stateTimer > 0) {
                    stateTimer--;
                    return;
                }

                switch (currentState) {
                    case START -> {
                        currentState = BehaviorState.ROAMING;
                        stateTimer = 0;
                    }
                    case ROAMING -> {
                        if (hasTarget) {
                            currentState = BehaviorState.TRACK;
                        } else {
                            var rx = position.x + (MathUtil.RANDOM.nextDouble() - 0.5) * 40;
                            var rz = position.z + (MathUtil.RANDOM.nextDouble() - 0.5) * 40;
                            var ry = position.y + (MathUtil.RANDOM.nextDouble() - 0.5) * 10;
                            roamTarget = new Vec3(rx, ry, rz);
                            stateTimer = 40 + MathUtil.RANDOM.nextInt(40);
                        }
                    }
                    case TRACK -> {
                        var target = targetEntity;
                        if (!hasTarget || target == null) {
                            currentState = BehaviorState.ROAMING;
                            return;
                        }
                        var dist = position.distanceTo(target.getBoundingBox().getCenter());
                        if (dist <= 12.0) {
                            selectCombatState();
                        } else {
                            stateTimer = 5;
                        }
                    }
                    case STAY, SLIDE, DASH -> {
                        if (!hasTarget) {
                            currentState = BehaviorState.ROAMING;
                        } else {
                            currentState = BehaviorState.TRACK;
                            stateTimer = 0;
                        }
                    }
                }
            }

            private void selectCombatState() {
                var rng = MathUtil.RANDOM.nextFloat();
                if (rng < 0.2f) {
                    currentState = BehaviorState.SLIDE;
                    stateTimer = 20 + MathUtil.RANDOM.nextInt(30);
                    strafeDir = MathUtil.RANDOM.nextBoolean() ? 1 : -1;
                } else if (rng < 0.6f) {
                    currentState = BehaviorState.STAY;
                    stateTimer = 15 + MathUtil.RANDOM.nextInt(25);
                } else {
                    currentState = BehaviorState.DASH;
                    stateTimer = 30 + MathUtil.RANDOM.nextInt(15);
                }
            }

            private void updatePhysics() {
                var force = Vec3.ZERO;
                var drag = 0.96f;
                var maxSpeed = 0.5;

                if (currentState == BehaviorState.ROAMING) {
                    if (roamTarget != null) {
                        var toRoam = roamTarget.subtract(position);
                        force = toRoam.normalize().scale(0.03);
                    }
                }

                var target = targetEntity;
                if (hasTarget && target != null) {
                    var toTarget = target.getBoundingBox().getCenter().subtract(position);

                    switch (currentState) {
                        case TRACK -> {
                            force = toTarget.normalize().scale(0.08);
                            maxSpeed = 1.2;
                            drag = 0.92f;
                        }
                        case STAY -> {
                            var t = existedTicks * 0.15 + timeSeed;
                            var noise = new Vec3(Mth.sin(t), Mth.cos(t * 0.8), Mth.sin(t * 1.2)).scale(0.04);
                            force = noise.add(toTarget.normalize().scale(0.015));
                            drag = 0.85f;
                            maxSpeed = 0.1;
                        }
                        case SLIDE -> {
                            var up = new Vec3(0, 1, 0);
                            var tangent = toTarget.cross(up).normalize().scale(strafeDir);
                            force = tangent.scale(0.12).add(toTarget.normalize().scale(0.04));
                            var heightDiff = target.getY() + target.getBbHeight() / 2.0 - position.y;
                            force = force.add(0, heightDiff * 0.03, 0);
                            drag = 0.95f;
                        }
                        case DASH -> {
                            force = toTarget.normalize().scale(0.25);
                            drag = 0.99f;
                            maxSpeed = 2.0;
                        }
                    }
                }

                velocity = velocity.add(force);
                if (proficiencyMilestone >= 2) maxSpeed *= 1.2;
                if (velocity.lengthSqr() > maxSpeed * maxSpeed) {
                    velocity = velocity.normalize().scale(maxSpeed);
                }

                velocity = velocity.scale(drag);
                position = position.add(velocity);

                coreOrb.setPos(position);
                coreOrb.setDeltaMovement(velocity);
            }

            private void checkImpact() {
                if (targetEntity != null && position.distanceTo(targetEntity.position()) <= 4.0) {
                    var level = level();
                    var impactRadius = Skills.BALL_LIGHTNING.get().scaledRange(player, 5.0f);
                    var entities = MathUtil.getEntitiesInSphereByHP(level, position, impactRadius, e -> e != player);
                    var damageSource = SkillDamageSource.of(player, Skills.BALL_LIGHTNING.get());
                    var system = AbilitySystemServer.getSystem(player);
                    for (var entity : entities) {
                        DamageComposition.hurt(entity, level, damageSource,
                                calculateImpactDamage(
                                        entity.getMaxHealth(),
                                        system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                                        system.getPlayerDamageMultiplier(player.getUUID())
                                ), entity.getMaxHealth() * IMPACT_DAMAGE.maxHealthRatio());
                        QuantumUtil.enableQuantum(entity, 0.5f, 0x3366FF);
                    }
                    if (proficiencyMilestone >= 3) {
                        AbilitySystemServer.registerContext(new MiniContext(player, position,
                                new Vec3(1, 0.15, 0).normalize()));
                        AbilitySystemServer.registerContext(new MiniContext(player, position,
                                new Vec3(-1, 0.15, 0).normalize()));
                    }
                    end();
                }
            }

            private static final class MiniContext extends ServerContext {
                private final LightOrb orb;
                private Vec3 position;
                private Vec3 velocity;
                private int ticks;
                private boolean ended;

                private MiniContext(ServerPlayer player, Vec3 position, Vec3 direction) {
                    super(player);
                    this.position = position;
                    velocity = direction.scale(0.8);
                    orb = new LightOrb(player.level(), 100, 0.08f, () -> {
                    });
                    orb.setColor(0.25f, 0.55f, 1.0f);
                    orb.setPos(position);
                    player.level().addFreshEntity(orb);
                }

                @SubscribeEvent
                public void onTick(ServerTickEvent.Post event) {
                    if (ended || player.hasDisconnected() || !player.isAlive() || ticks++ >= 100) {
                        endMini();
                        return;
                    }
                    var seekRadius = Skills.BALL_LIGHTNING.get().scaledRange(player, 24.0f);
                    var targets = MathUtil.getEntitiesInSphereByHP(level(), position, seekRadius, entity -> entity != player);
                    if (!targets.isEmpty()) {
                        var delta = targets.getFirst().getBoundingBox().getCenter().subtract(position);
                        if (delta.lengthSqr() > 1.0e-8)
                            velocity = velocity.scale(0.75).add(delta.normalize().scale(0.25));
                    }
                    if (velocity.lengthSqr() > 0.96 * 0.96) velocity = velocity.normalize().scale(0.96);
                    position = position.add(velocity);
                    orb.setPos(position);
                    if (!targets.isEmpty() && position.distanceToSqr(targets.getFirst().position()) <= 4.0) {
                        var system = AbilitySystemServer.getSystem(player);
                        var source = SkillDamageSource.of(player, Skills.BALL_LIGHTNING.get()).withElectricalChargePoints(1);
                        var miniImpactRadius = Skills.BALL_LIGHTNING.get().scaledRange(player, 3.0f);
                        for (var target : MathUtil.getEntitiesInSphereByHP(
                                level(), position, miniImpactRadius, entity -> entity != player)) {
                            DamageComposition.hurt(
                                    target, level(), source, calculateImpactDamage(target.getMaxHealth(),
                                            system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                                            system.getPlayerDamageMultiplier(player.getUUID())) * 0.3f,
                                    target.getMaxHealth() * IMPACT_DAMAGE.maxHealthRatio() * 0.3f);
                        }
                        endMini();
                    }
                }

                private void endMini() {
                    if (ended) return;
                    ended = true;
                    orb.discard();
                    unregister();
                }
            }

            private void end() {
                if (ended) return;
                ended = true;
                ACTIVE.remove(player, this);
                unregister();
                coreOrb.setLifeTime(10);
            }

            private enum BehaviorState {
                START,
                ROAMING,
                TRACK,
                STAY,
                SLIDE,
                DASH
            }
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
            return PacketTypes.LIGHTNING_NOVA_ACTIVATE.get();
        }
    }
}
