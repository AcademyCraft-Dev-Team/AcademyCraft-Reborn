package org.academy.internal.common.ability.electromaster.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public class LightningNova extends Skill {
    private static final int MAX_RADIUS = 16;
    private static final float DAMAGE = 4.0f;
    private static final int PULSE_DURATION = 200;

    public LightningNova() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(60)
                .iterationTicks(30)
                .maxStacks(1)
                .dependsOn(Skills.ELECTRICAL_CONTACT)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N, InputConstants.PRESS, InputConstants.MOD_CONTROL))
                , ctx -> Client.onUse());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.LIGHTNING_NOVA + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.LIGHTNING_NOVA.get())) return;
            MisakaNetworkClient.send(ActivatePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public LightningNova.Client.Config getDefault() {
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
        public static float calculateDamage(float abilityPower, float playerMultiplier) {
            return DAMAGE * Math.max(0.0f, abilityPower) * Math.max(0.0f, playerMultiplier);
        }

        @SubscribePacket
        public static void handle(ActivatePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.LIGHTNING_NOVA.get().executeActive(player, (ctx, actualCost) -> AbilitySystemServer.registerContext(new Context(player)));
        }
    }

    public static final class Context extends ServerContext {
        private int ticks;
        private boolean ended;

        private Context(ServerPlayer player) {
            super(player);
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            ticks++;
            if (player.hasDisconnected() || !player.isAlive() || ticks >= PULSE_DURATION) {
                end();
                return;
            }

            var currentRadius = 1.0f + (float) ticks / PULSE_DURATION * MAX_RADIUS;
            var innerRadius = Math.max(0, currentRadius - 1.5f);

            if (level() instanceof ServerLevel serverLevel) {
                if ((ticks & 1) == 0) {
                    var center = player.position().add(0, 1.0, 0);
                    for (var i = 0; i < 16; i++) {
                        var angle = i * Math.PI * 2.0 / 16.0;
                        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                center.x + Math.cos(angle) * currentRadius,
                                center.y,
                                center.z + Math.sin(angle) * currentRadius,
                                1, 0.02, 0.08, 0.02, 0.01);
                    }
                }
                var targets = level().getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(currentRadius + 1),
                        e -> e != player && e.isAlive());
                var system = AbilitySystemServer.getSystem(player);
                var damage = Server.calculateDamage(
                        system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                        system.getPlayerDamageMultiplier(player.getUUID())
                );
                var source = SkillDamageSource.of(player, Skills.LIGHTNING_NOVA.get(),
                        net.minecraft.world.damagesource.DamageTypes.LIGHTNING_BOLT);
                for (var target : targets) {
                    var dist = target.distanceTo(player);
                    if (dist <= currentRadius && dist >= innerRadius) {
                        target.hurtServer(serverLevel, source, damage);
                    }
                }
            }
        }

        private void end() {
            if (ended) return;
            ended = true;
            unregister();
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
            return PacketTypes.LIGHTNING_NOVA_ACTIVATE_P4.get();
        }
    }
}
