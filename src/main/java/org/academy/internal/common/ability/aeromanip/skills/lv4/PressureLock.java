package org.academy.internal.common.ability.aeromanip.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.LineBoxRenderer;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SkillProficiencyProfile;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.skills.lv3.AtmosphereShield;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public final class PressureLock extends Skill {
    public PressureLock() {
        super(Builder.of(AbilityCategories.AEROMANIP.get()).level(AbilityLevel.LEVEL4).energyCost(60_000)
                .cpCost(40).iterationTicks(15).maxStacks(1).dependsOn(Skills.VORTEX_PULL)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4)));
    }
    @Override public void initClient() {
        var key = getKey(); AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE); Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
        NeoForge.EVENT_BUS.register(Client.class);
        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CONFIG.getKeyBinding(Client.KEY_NAME_START, InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_P, InputConstants.PRESS, 0)), _ -> Client.start());
        InputSystem.addKeyBinding(Client.KEY_NAME_STOP, Client.CONFIG.getKeyBinding(Client.KEY_NAME_STOP, InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_P, InputConstants.RELEASE, 0)), _ -> Client.stop());
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(AbilityCategories.AEROMANIP.get(), new AbilitySystemClient.SkillInfo(Skills.PRESSURE_LOCK.get(), List.of(), R.textures.pressure_lock_icon, 130, 136));
    }
    @Override public void initServer(MinecraftServerContext context) { MisakaNetworkServer.NETWORK_MANAGER.register(Server.class); }
    public static final class Client {
        public static AbilitySystemClient.SkillInfo SKILL_INFO; public static final String KEY_NAME_START = SkillNames.PRESSURE_LOCK + "_start"; public static final String KEY_NAME_STOP = SkillNames.PRESSURE_LOCK + "_stop"; public static Config CONFIG = new Config();
        private static int targetId = -1;
        private static void start() { if (AbilitySystemClient.canUseSkill(Skills.PRESSURE_LOCK.get())) { targetId = -1; MisakaNetworkClient.send(StartPacket.INSTANCE); } }
        private static void stop() { MisakaNetworkClient.send(StopPacket.INSTANCE); }
        @SubscribePacket public static void handle(TargetPacket packet) { targetId = packet.targetId; }
        @SubscribeEvent public static void onRender(LevelRenderEvent event) {
            if (targetId < 0) return;
            var minecraft = Minecraft.getInstance();
            if (minecraft.level == null) { targetId = -1; return; }
            var entity = minecraft.level.getEntity(targetId);
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) { targetId = -1; return; }
            var camera = minecraft.gameRenderer.mainCamera().position();
            var matrices = event.getMatrixStack();
            matrices.pushPose();
            matrices.translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
            event.submitCustomGeometry(Render.RenderTypes.MINE_DETECT_LINES,
                    (snapshot, consumer) -> LineBoxRenderer.renderWireframeBox(
                            snapshot, consumer, living.getBoundingBox().inflate(0.04),
                            0.35f, 0.85f, 1.0f, 1.0f));
            matrices.popPose();
        }
        public static final class Config extends KeyBindingConfig { public static final class Action implements TypeHandler<Config> { public static final TypeHandler<Config> INSTANCE = new Action(); private Action() { } @Override public Config getDefault() { return new Config(); } @Override public Class<Config> getTypeClass() { return Config.class; } } }
    }
    public static final class Server {
        private static final Map<ServerPlayer, Context> ACTIVE = new WeakHashMap<>();
        @SubscribePacket public static void handle(StartPacket packet) { var player = packet.getPacketListener().getPlayer(); var skill = Skills.PRESSURE_LOCK.get(); if (ACTIVE.containsKey(player) || !skill.isEnabled(player)) return; var context = new Context(player); ACTIVE.put(player, context); AbilitySystemServer.registerContext(context); skill.reportTrigger(player); }
        @SubscribePacket public static void handle(StopPacket packet) {
            var context = ACTIVE.get(packet.getPacketListener().getPlayer());
            if (context != null && context.target == null) context.end();
        }
        private static final class Context extends ServerContext {
            private int age;
            private int lockAge;
            private boolean ended;
            private LivingEntity target;
            private final int proficiencyMilestone;
            private int lostSightTicks;
            private Vec3 attemptedDisplacement = Vec3.ZERO;
            private final String imprisonmentSource;
            private Context(ServerPlayer player) {
                super(player);
                proficiencyMilestone = Skills.PRESSURE_LOCK.get().getEffectiveProficiencyMilestone(player);
                imprisonmentSource = "pressure_lock:" + player.getStringUUID();
            }
            private void end() { if (!ended) { ended = true; MisakaNetworkServer.send(player, new TargetPacket(-1)); unregister(); } }
            @SubscribeEvent public void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event) {
                age++;
                if (ended || !player.isAlive() || player.hasDisconnected()
                        || !Skills.PRESSURE_LOCK.get().isEnabled(player)) {
                    end();
                    return;
                }
                var skill = Skills.PRESSURE_LOCK.get();
                skill.reportActivity(player, false);
                if (target == null) {
                    if ((age & 1) != 0) return;
                    target = findTarget();
                    if (target == null) return;
                    if (isProtected(target)) {
                        end();
                        return;
                    }
                    if (!AbilitySystemServer.getSystem(player).tryTimedOccupation(
                            player.getUUID(),
                            skill.adjustProficiencyCost(player, SkillProficiencyProfile.CostKind.CAST,
                                    skill.getCpCost(skill.getLevel(player))
                                            * AeromanipConfig.cpMultiplier(player, SkillNames.PRESSURE_LOCK)),
                            skill,
                            15
                    )) {
                        end();
                        return;
                    }
                    MisakaNetworkServer.send(player, new TargetPacket(target.getId()));
                    target.stopRiding();
                }
                var durationTicks = proficiencyMilestone >= 2 ? 240 : 200;
                var duration = Math.max(1, Math.round(durationTicks
                        * AeromanipConfig.durationMultiplier(player, SkillNames.PRESSURE_LOCK)));
                if (lockAge++ >= duration || !target.isAlive() || target.isRemoved()
                        || target.level() != player.level() || isProtected(target)) {
                    end();
                    return;
                }
                if (!player.hasLineOfSight(target)) {
                    lostSightTicks++;
                    if (proficiencyMilestone < 2 || lostSightTicks > 10) {
                        end();
                        return;
                    }
                } else {
                    lostSightTicks = 0;
                }
                if (proficiencyMilestone >= 3) {
                    var movement = target.getDeltaMovement();
                    if (Double.isFinite(movement.x) && Double.isFinite(movement.y) && Double.isFinite(movement.z)) {
                        attemptedDisplacement = attemptedDisplacement.add(movement);
                    }
                }
                EntityMotionGuard.imprison(target, imprisonmentSource, 2L);
                skill.reportActivity(player, true);
            }

            private LivingEntity findTarget() {
                var eye = player.getEyePosition();
                var look = player.getLookAngle().normalize();
                var box = new AABB(eye, eye.add(look.scale(18.0))).inflate(1.0);
                return player.level().getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        living -> living != player
                                && living.isAlive()
                                && player.hasLineOfSight(living)
                                && AeromanipTargeting.canAffectNegatively(player, living)
                ).stream().min((a, b) -> Double.compare(
                        a.distanceToSqr(eye),
                        b.distanceToSqr(eye)
                )).orElse(null);
            }

            private boolean isProtected(LivingEntity target) {
                if (!(target instanceof ServerPlayer protectedPlayer)) return false;
                if (!EntityMotionGuard.isImprisoned(protectedPlayer)
                        && !EntityMotionGuard.canApplyMotionFrom(player, protectedPlayer)) return true;
                return !EntityMotionGuard.canBeImprisoned(protectedPlayer)
                        || DarkmatterSixWings.Server.isActive(protectedPlayer)
                        || AtmosphereShield.Server.isActive(protectedPlayer);
            }

            @Override protected void onUnregistered() {
                if (target != null) {
                    EntityMotionGuard.release(target, imprisonmentSource);
                    if (proficiencyMilestone >= 3 && target.isAlive() && attemptedDisplacement.lengthSqr() > 1.0e-8) {
                        var direction = attemptedDisplacement.normalize();
                        var speed = Math.min(1.5, attemptedDisplacement.length());
                        AeromanipTargeting.addClampedVelocity(target, direction.scale(speed));
                    }
                }
                ACTIVE.remove(player, this);
            }
        }
    }
    @PacketTarget(ThreadType.SERVER) public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> { public static final StartPacket INSTANCE = new StartPacket(); public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE); private StartPacket() { } @Override public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() { return PacketTypes.PRESSURE_LOCK_START.get(); } }
    @PacketTarget(ThreadType.SERVER) public static final class StopPacket extends Packet<ServerGamePacketListenerImpl, StopPacket> { public static final StopPacket INSTANCE = new StopPacket(); public static final StreamCodec<ByteBuf, StopPacket> CODEC = StreamCodec.unit(INSTANCE); private StopPacket() { } @Override public PacketType<ServerGamePacketListenerImpl, StopPacket> getPacketType() { return PacketTypes.PRESSURE_LOCK_STOP.get(); } }
    @PacketTarget(ThreadType.CLIENT) public static final class TargetPacket extends Packet<ClientPacketListener, TargetPacket> {
        public static final StreamCodec<ByteBuf, TargetPacket> CODEC = ByteBufCodecs.VAR_INT.map(TargetPacket::new, packet -> packet.targetId);
        private final int targetId;
        public TargetPacket(int targetId) { this.targetId = targetId; }
        @Override public PacketType<ClientPacketListener, TargetPacket> getPacketType() { return PacketTypes.PRESSURE_LOCK_TARGET.get(); }
    }
}
