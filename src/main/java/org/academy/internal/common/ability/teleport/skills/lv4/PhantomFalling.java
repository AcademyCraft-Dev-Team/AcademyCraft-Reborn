package org.academy.internal.common.ability.teleport.skills.lv4;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.DistortionEffectWrapper;
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

public class PhantomFalling extends Skill {
    private static final float RADIUS = 10.0f;
    private static final float BASE_DAMAGE = 8.0f;

    public PhantomFalling() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL4)
                .cpCost(60)
                .iterationTicks(15)
                .maxStacks(1)
                .dependsOn(Skills.CUT_THROUGH)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        RendererManager.registerEffectRenderer(DistortionEffectWrapper.INSTANCE);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_F)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))))
        , Client::onUse);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.PHANTOM_FALLING + "_use";
        public static Config CONFIG = new Config();
        public static void onUse() {
            MisakaNetworkClient.send(UsePacket.INSTANCE);
            var p = net.minecraft.client.Minecraft.getInstance().player;
            if (p == null) return;
            DistortionEffectWrapper.INSTANCE.trigger(
                    (float) p.getX(), (float) p.getY(), (float) p.getZ(),
                    1.5f, 1.2f,
                    0.3f, 0.1f, 0.7f, 0.6f,
                    0.1f, 0.0f, 0.5f, 0.0f);
        }
        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {}
                @Override public PhantomFalling.Client.Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(UsePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.PHANTOM_FALLING.get().executeActive(player, (ctx, actualCost) -> AbilitySystemServer.registerContext(new PhantomContext(player)));
        }
    }

    public static final class PhantomContext extends ServerContext {
        private static final int DURATION = 100;
        private static final float TELEPORT_HEIGHT = 8.0f;
        private int ticks;
        private boolean ended;

        private PhantomContext(ServerPlayer player) { super(player); }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            ticks++;
            if (player.hasDisconnected() || !player.isAlive() || ticks >= DURATION) { end(); return; }
            if (ticks % 10 != 0) return;

            if (level() instanceof ServerLevel serverLevel) {
                var targets = serverLevel.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(RADIUS),
                        e -> e != player && e.isAlive() && !e.isSpectator());

                for (var target : targets) {
                    target.teleportTo(target.getX(), target.getY() + TELEPORT_HEIGHT, target.getZ());
                    target.setDeltaMovement(target.getDeltaMovement().add(0, -2.0, 0));
                    target.hurtMarked = true;
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, false));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false));
                    if (ticks % 30 == 0) {
                        target.hurtServer(serverLevel, serverLevel.damageSources().fall(), BASE_DAMAGE);
                    }
                }
            }
        }

        private void end() { if (ended) return; ended = true; unregister(); }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final UsePacket INSTANCE = new UsePacket();
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = StreamCodec.unit(INSTANCE);
        private UsePacket() {}
        @Override public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() {
            return PacketTypes.PHANTOM_FALLING_USE.get();
        }
    }
}
