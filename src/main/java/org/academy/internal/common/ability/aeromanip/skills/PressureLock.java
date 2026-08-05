package org.academy.internal.common.ability.aeromanip.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class PressureLock extends Skill {
    public PressureLock() {
        super(Builder.of(AbilityCategories.AEROMANIP.get()).level(AbilityLevel.LEVEL4).energyCost(60_000)
                .cpCost(65).iterationTicks(10).maxStacks(1).dependsOn(Skills.LAMINAR_CUTTER)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4)));
    }
    @Override public void initClient() {
        var key = getKey(); AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE); Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CONFIG.getKeyBinding(Client.KEY_NAME_START, InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_P, InputConstants.PRESS, 0)), _ -> Client.start());
        InputSystem.addKeyBinding(Client.KEY_NAME_STOP, Client.CONFIG.getKeyBinding(Client.KEY_NAME_STOP, InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_P, InputConstants.RELEASE, 0)), _ -> Client.stop());
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(AbilityCategories.AEROMANIP.get(), new AbilitySystemClient.SkillInfo(Skills.PRESSURE_LOCK.get(), List.of(LaminarCutter.Client.SKILL_INFO), R.textures.pressure_lock_icon, 130, 136));
    }
    @Override public void initServer(MinecraftServerContext context) { MisakaNetworkServer.NETWORK_MANAGER.register(Server.class); }
    public static final class Client {
        public static AbilitySystemClient.SkillInfo SKILL_INFO; public static final String KEY_NAME_START = SkillNames.PRESSURE_LOCK + "_start"; public static final String KEY_NAME_STOP = SkillNames.PRESSURE_LOCK + "_stop"; public static Config CONFIG = new Config();
        private static void start() { if (AbilitySystemClient.canUseSkill(Skills.PRESSURE_LOCK.get())) MisakaNetworkClient.send(StartPacket.INSTANCE); }
        private static void stop() { MisakaNetworkClient.send(StopPacket.INSTANCE); }
        public static final class Config extends KeyBindingConfig { public static final class Action implements TypeHandler<Config> { public static final TypeHandler<Config> INSTANCE = new Action(); private Action() { } @Override public Config getDefault() { return new Config(); } @Override public Class<Config> getTypeClass() { return Config.class; } } }
    }
    public static final class Server {
        private static final Map<ServerPlayer, Context> ACTIVE = new WeakHashMap<>();
        @SubscribePacket public static void handle(StartPacket packet) { var player = packet.getPacketListener().getPlayer(); if (ACTIVE.containsKey(player) || !Skills.PRESSURE_LOCK.get().isEnabled(player)) return; var context = new Context(player); ACTIVE.put(player, context); AbilitySystemServer.registerContext(context); }
        @SubscribePacket public static void handle(StopPacket packet) { var context = ACTIVE.get(packet.getPacketListener().getPlayer()); if (context != null) context.end(); }
        private static final class Context extends ServerContext {
            private int age; private boolean ended; private boolean pvpEngaged;
            private Context(ServerPlayer player) { super(player); }
            private void end() { if (!ended) { ended = true; unregister(); } }
            @SubscribeEvent public void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event) {
                age++;
                var duration = Math.max(1, Math.round(50 * AeromanipConfig.durationMultiplier(player, SkillNames.PRESSURE_LOCK)));
                if (ended || age > (pvpEngaged ? Math.min(duration, 16) : duration)
                        || !player.isAlive() || !Skills.PRESSURE_LOCK.get().isEnabled(player)) { end(); return; }
                if ((age & 1) != 0) return;
                var eye = player.getEyePosition(); var look = player.getLookAngle().normalize(); var box = new AABB(eye, eye.add(look.scale(18))).inflate(1.0);
                var target = player.level().getEntities(player, box, entity -> entity instanceof LivingEntity living && living.isAlive() && !AeromanipTargeting.isBoss(living) && player.hasLineOfSight(living) && AeromanipTargeting.canAffectNegatively(player, living)).stream().min((a, b) -> Double.compare(a.distanceToSqr(eye), b.distanceToSqr(eye))).orElse(null);
                if (target == null) return;
                var pvpTarget = target instanceof net.minecraft.world.entity.player.Player;
                if (pvpTarget) pvpEngaged = true;
                var multiplier = pvpTarget ? AeromanipConfig.pvpForce(player) : 0.15;
                var current = target.getDeltaMovement();
                AeromanipTargeting.addClampedVelocity(target, current.scale(multiplier - 1.0));
                if (!AbilitySystemServer.getSystem(player).tryTimedOccupation(player.getUUID(),
                        5.0f * AeromanipConfig.cpMultiplier(player, SkillNames.PRESSURE_LOCK),
                        Skills.PRESSURE_LOCK.get(), 10)) end();
            }
            @Override protected void onUnregistered() { ACTIVE.remove(player, this); }
        }
    }
    @PacketTarget(ThreadType.SERVER) public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> { public static final StartPacket INSTANCE = new StartPacket(); public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE); private StartPacket() { } @Override public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() { return PacketTypes.PRESSURE_LOCK_START.get(); } }
    @PacketTarget(ThreadType.SERVER) public static final class StopPacket extends Packet<ServerGamePacketListenerImpl, StopPacket> { public static final StopPacket INSTANCE = new StopPacket(); public static final StreamCodec<ByteBuf, StopPacket> CODEC = StreamCodec.unit(INSTANCE); private StopPacket() { } @Override public PacketType<ServerGamePacketListenerImpl, StopPacket> getPacketType() { return PacketTypes.PRESSURE_LOCK_STOP.get(); } }
}
