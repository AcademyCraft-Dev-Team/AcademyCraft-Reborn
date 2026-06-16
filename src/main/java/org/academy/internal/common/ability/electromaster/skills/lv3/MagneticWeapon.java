package org.academy.internal.common.ability.electromaster.skills.lv3;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.renderer.effect.EMFieldEffectWrapper;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import java.util.*;

public class MagneticWeapon extends Skill {
    public MagneticWeapon() { super(Builder.of(AbilityCategories.ELECTROMASTER.get()).level(AbilityLevel.LEVEL3).passive().maintenanceCost(40).dependsOn(Skills.MAGNET_MANIPULATION).dependsOn(Skills.MAGNET_MOMENT_CHARGE)); }

    @Override public void initClient() {
        RendererManager.registerEffectRenderer(EMFieldEffectWrapper.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY, Client.CONFIG.getKeyBinding(Client.KEY,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_M)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))))
        , Client::onToggle);
    }
    @Override public void initServer(MinecraftServerContext c) { MisakaNetworkServer.NETWORK_MANAGER.register(Server.class); }

    public static final class Client {
        public static final String KEY = SkillNames.MAGNETIC_WEAPON + "_toggle";
        public static Config CONFIG = new Config();
        public static void onToggle() {
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
            var p = net.minecraft.client.Minecraft.getInstance().player;
            if (p == null) return;
            EMFieldEffectWrapper.INSTANCE.ensureActive();
            for (var i = 0; i < 3; i++) {
                var angle = i * Math.PI * 2 / 3;
                var r = 1.5;
                EMFieldEffectWrapper.INSTANCE.addFieldLine(
                        p.position().add(Math.cos(angle) * r, 0, Math.sin(angle) * r),
                        p.position().add(Math.cos(angle + Math.PI / 3) * r, 0, Math.sin(angle + Math.PI / 3) * r),
                        0.5f, 0.5f, 1.0f, 0.04f, 0.5f, 0.15f);
            }
        }
        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {} @Override public MagneticWeapon.Client.Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        private static final Map<Player, Context> CONTEXT_MAP = createContextMap();
        @SubscribePacket public static void handleToggle(TogglePacket p) {
            var player = p.getPacketListener().getPlayer(); var skill = Skills.MAGNETIC_WEAPON.get();
            skill.toggle(player);
            if (!skill.isEnabled(player)) { var ctx = CONTEXT_MAP.remove(player); if (ctx != null) ctx.end(); return; }
            if (CONTEXT_MAP.containsKey(player)) return;
            var ctx = new Context(player); CONTEXT_MAP.put(player, ctx); AbilitySystemServer.registerContext(ctx);
        }
    }

    public static final class Context extends ServerContext {
        private static final float RADIUS = 4.0f;
        private static final int ATTACK_INTERVAL = 15;
        private boolean ended;

        private Context(ServerPlayer p) { super(p); }

        @SubscribeEvent public void onTick(ServerTickEvent.Pre e) {
            if (!Skills.MAGNETIC_WEAPON.get().isEnabled(player) || !player.isAlive() || player.hasDisconnected()) { end(); return; }

            if (player.level().getGameTime() % ATTACK_INTERVAL == 0 && player.level() instanceof ServerLevel sl) {
                var box = player.getBoundingBox().inflate(RADIUS);
                var targets = sl.getEntitiesOfClass(LivingEntity.class, box,
                        ee -> ee != player && ee.isAlive() && player.hasLineOfSight(ee));
                if (!targets.isEmpty()) {
                    var target = targets.getFirst();
                    var weaponDamage = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    target.hurtServer(sl, sl.damageSources().mobAttack(player), weaponDamage * 0.6f);
                }
            }
        }

        private void end() { if (ended) return; ended = true; Server.CONTEXT_MAP.remove(player); unregister(); }
    }

    @net.neoforged.fml.common.EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class ShieldEvents {
        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!Skills.MAGNETIC_WEAPON.get().isEnabled(player)) return;
            var source = event.getSource();
            if (source.getDirectEntity() instanceof Projectile) {
                var lookDir = player.getLookAngle();
                var toProjectile = new Vec3(
                        source.getDirectEntity().getX() - player.getX(),
                        source.getDirectEntity().getY() - player.getY(),
                        source.getDirectEntity().getZ() - player.getZ()).normalize();
                if (lookDir.dot(toProjectile) > 0.5) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);
        private TogglePacket() {} @Override public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() { return PacketTypes.MAGNETIC_WEAPON_TOGGLE.get(); }
    }
}
