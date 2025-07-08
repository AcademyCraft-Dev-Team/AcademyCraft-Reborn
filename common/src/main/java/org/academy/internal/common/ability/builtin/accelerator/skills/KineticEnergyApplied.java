package org.academy.internal.common.ability.builtin.accelerator.skills;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
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
import org.academy.api.client.network.NetworkManagerClient;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.config.IConfigAction;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.GlowCircle;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class KineticEnergyApplied extends Skill {
    public static final Skill INSTANCE = new KineticEnergyApplied();

    private KineticEnergyApplied() {
        super(SkillNames.KINETIC_ENERGY_APPLIED, 1);
    }

    @Override
    public void initClient() {
        AcademyCraftConfig.registerConfigActions(INSTANCE.name, Client.KineticEnergyAppliedConfig.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(INSTANCE.name);
        if (Client.CONFIG == null) {
            Client.CONFIG = new Client.KineticEnergyAppliedConfig();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(INSTANCE.name, Client.CONFIG);
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
            NetworkManagerClient.sendPacket(new C2SPacket(new TogglePacket()));
        }

        public static class KineticEnergyAppliedConfig extends KeyBindingConfig {
            public static final class Action implements IConfigAction<KineticEnergyAppliedConfig> {
                public static final IConfigAction<KineticEnergyAppliedConfig> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public @NotNull KineticEnergyApplied.Client.KineticEnergyAppliedConfig deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                    return gson.fromJson(jsonElement, KineticEnergyAppliedConfig.class);
                }

                @Override
                public @NotNull JsonElement serialize(@NotNull KineticEnergyApplied.Client.KineticEnergyAppliedConfig configInstance, @NotNull Gson gson) {
                    return gson.toJsonTree(configInstance);
                }

                @Override
                public @NotNull KineticEnergyApplied.Client.KineticEnergyAppliedConfig getDefaultConfig() {
                    return new KineticEnergyAppliedConfig();
                }

                @Override
                public @NotNull Class<KineticEnergyAppliedConfig> getConfigClass() {
                    return KineticEnergyAppliedConfig.class;
                }
            }
        }
    }

    public static final class Server {
        public static final Map<UUID, Boolean> SKILL_STATS = new HashMap<>();

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            ServerPlayer player = packet.packetListenerSupplier.get().getPlayer();
            if (SKILL_STATS.containsKey(player.getUUID())) {
                SKILL_STATS.put(player.getUUID(), !SKILL_STATS.get(player.getUUID()));
            } else {
                SKILL_STATS.put(player.getUUID(), true);
            }
        }

        @SuppressWarnings("resource")
        public static float onProjectileShoot(Projectile projectile, Entity shooter, float velocity) {
            GlowCircle glowCircle = new GlowCircle(EntityTypes.GLOW_CIRCLE, shooter.level());
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
    }
}