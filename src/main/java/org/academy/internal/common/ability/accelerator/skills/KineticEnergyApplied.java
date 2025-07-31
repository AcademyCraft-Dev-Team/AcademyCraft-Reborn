package org.academy.internal.common.ability.accelerator.skills;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.GlowCircle;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class KineticEnergyApplied extends Skill {
    public KineticEnergyApplied() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL1)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.KineticEnergyAppliedConfig.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(key);
        if (Client.CONFIG == null) {
            Client.CONFIG = new Client.KineticEnergyAppliedConfig();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(key, Client.CONFIG);
        }

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
    public void initServer(MinecraftServer server) {
        AcademyCraftServer.SERVER_NETWORK_MANAGER.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.KINETIC_ENERGY_APPLIED + "_toggle";
        public static KineticEnergyAppliedConfig CONFIG = new KineticEnergyAppliedConfig();

        public static void toggle() {
            AcademyCraftClient.sendPacket(new C2SPacket(new TogglePacket()));
        }

        public static class KineticEnergyAppliedConfig extends KeyBindingConfig {
            public static final class Action implements TypeHandler<KineticEnergyAppliedConfig> {
                public static final TypeHandler<KineticEnergyAppliedConfig> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public @NotNull KineticEnergyApplied.Client.KineticEnergyAppliedConfig getDefault() {
                    return new KineticEnergyAppliedConfig();
                }

                @Override
                public @NotNull Class<KineticEnergyAppliedConfig> getTypeClass() {
                    return KineticEnergyAppliedConfig.class;
                }
            }
        }
    }

    public static final class Server {
        public static final Map<UUID, Boolean> SKILL_STATS = new HashMap<>();

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            ServerPlayer player = packet.getPacketListener().getPlayer();
            if (SKILL_STATS.containsKey(player.getUUID())) {
                SKILL_STATS.put(player.getUUID(), !SKILL_STATS.get(player.getUUID()));
            } else {
                SKILL_STATS.put(player.getUUID(), true);
            }
        }

        @SuppressWarnings("resource")
        public static float onProjectileShoot(Projectile projectile, Entity shooter, float velocity) {
            GlowCircle glowCircle = new GlowCircle(EntityTypes.GLOW_CIRCLE.get(), shooter.level());
            Vec3 vec3 = shooter.getLookAngle().scale(1);
            glowCircle.setPos(projectile.getX() + vec3.x, projectile.getY() + vec3.y, projectile.getZ() + vec3.z);
            glowCircle.setYRot(shooter.getYRot());
            glowCircle.setXRot(shooter.getXRot());
            shooter.level().addFreshEntity(glowCircle);
            return velocity * 2;
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends EmptyPacket<ServerGamePacketListenerImpl> {
        public TogglePacket(ServerGamePacketListenerImpl listener) {
            super(listener);
        }

        public TogglePacket() {
            super(null);
        }

        @Override
        public @NotNull PacketType<ServerGamePacketListenerImpl, ? extends IPacket<ServerGamePacketListenerImpl>> getPacketType() {
            return PacketTypes.KINETIC_ENERGY_APPLIED_TOGGLE.get();
        }
    }
}