package org.academy.internal.common.ability.accelerator.skills.lv1;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.GlowCircle;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

public class KineticEnergyApplied extends Skill {
    public KineticEnergyApplied() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL1)
                .cpCost(10)
                .iterationTicks(5)
                .passive()
                .maxStacks(10)
        );
    }

    public float getVelocityMultiplier(int level) {
        if (level >= 1) return 8.0f;
        return 3.0f;
    }

    public float getExtraDamage(int level) {
        if (level >= 2) return 5.0f;
        return 2.0f;
    }

    @Override
    public int getIterationTicks(int skillLevel) {
        if (skillLevel >= 3) return 1;
        return super.getIterationTicks(skillLevel);
    }

    @Override
    public int getMaxStacks(int skillLevel) {
        if (skillLevel >= 3) return NO_STACK_LIMIT;
        return super.getMaxStacks(skillLevel);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.KineticEnergyAppliedConfig.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_K)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>()
                        )
                )
        ), Client::toggle);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.KINETIC_ENERGY_APPLIED + "_toggle";
        public static KineticEnergyAppliedConfig CONFIG = new KineticEnergyAppliedConfig();

        public static void toggle() {
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static class KineticEnergyAppliedConfig extends KeyBindingConfig {
            public static final class Action implements TypeHandler<KineticEnergyAppliedConfig> {
                public static final TypeHandler<KineticEnergyAppliedConfig> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public KineticEnergyApplied.Client.KineticEnergyAppliedConfig getDefault() {
                    return new KineticEnergyAppliedConfig();
                }

                @Override
                public Class<KineticEnergyAppliedConfig> getTypeClass() {
                    return KineticEnergyAppliedConfig.class;
                }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.KINETIC_ENERGY_APPLIED.get().toggle(player);
        }

        public static float onProjectileShoot(Projectile projectile, Entity shooter, float velocity) {
            if (!(shooter instanceof ServerPlayer player)) return velocity;

            var skill = Skills.KINETIC_ENERGY_APPLIED.get();
            var level = skill.getLevel(player);
            final var resultVelocity = new float[]{velocity};

            skill.executeActive(player,
                    (ctx, actualCost) -> {
                        resultVelocity[0] = velocity * skill.getVelocityMultiplier(level);
                        var damage = skill.getExtraDamage(level);
                        projectile.setData(AttachmentTypes.PROJECTILE_EXTRA_DAMAGE, damage);
                        spawnEffects(projectile, player);
                    }
            );
            return resultVelocity[0];
        }

        private static void spawnEffects(Projectile projectile, Entity shooter) {
            var glowCircle = new GlowCircle(EntityTypes.GLOW_CIRCLE.get(), shooter.level());
            var lookVec = shooter.getLookAngle().scale(0.5);
            glowCircle.setPos(projectile.getX() + lookVec.x, projectile.getY() + lookVec.y, projectile.getZ() + lookVec.z);
            glowCircle.setYRot(shooter.getYRot());
            glowCircle.setXRot(shooter.getXRot());
            shooter.level().addFreshEntity(glowCircle);
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
            return PacketTypes.KINETIC_ENERGY_APPLIED_TOGGLE.get();
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            var source = event.getSource();
            var directEntity = source.getDirectEntity();

            if (directEntity instanceof Projectile projectile) {
                if (projectile.hasData(AttachmentTypes.PROJECTILE_EXTRA_DAMAGE)) {
                    var bonus = projectile.getData(AttachmentTypes.PROJECTILE_EXTRA_DAMAGE);
                    event.setAmount(event.getAmount() + bonus);
                }
            }
        }
    }
}