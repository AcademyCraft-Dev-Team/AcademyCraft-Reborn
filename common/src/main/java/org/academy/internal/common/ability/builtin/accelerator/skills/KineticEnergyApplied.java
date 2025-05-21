package org.academy.internal.common.ability.builtin.accelerator.skills;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftClientConfig;
import org.academy.api.client.config.IClientConfigActions;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.PacketTarget;
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

    static {
        NetworkSystem.registerPacketType(TogglePacket.class);
    }

    private KineticEnergyApplied() {
        super(SkillNames.KINETIC_ENERGY_APPLIED, 1);
    }

    @Override
    public void initClient() {
        AcademyCraftClientConfig.registerConfigActions(INSTANCE.name, new Client.KineticEnergyAppliedClientConfigData());
        Client.CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(
                INSTANCE.name,
                Client.KineticEnergyAppliedClientConfigData.class
        );
        if (Client.CONFIG == null) {
            Client.CONFIG = new Client.KineticEnergyAppliedClientConfigData();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(INSTANCE.name, Client.CONFIG);
        }

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE_ACTION, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE_ACTION,
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
        NetworkSystem.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE_ACTION = SkillNames.KINETIC_ENERGY_APPLIED + "_toggle_action";
        public static KineticEnergyAppliedClientConfigData CONFIG = new KineticEnergyAppliedClientConfigData();

        public static void toggle() {
            NetworkSystemClient.sendPacket(new C2SPacket(new TogglePacket()));
        }

        public static class KineticEnergyAppliedClientConfigData implements IClientConfigActions<KineticEnergyAppliedClientConfigData> {
            @SerializedName("keyBindings")
            private final Map<String, InputSystem.InputPair> keyBindings = new HashMap<>();

            public InputSystem.InputPair getKeyBinding(String name, InputSystem.InputPair defaultConfig) {
                if (!keyBindings.containsKey(name)) {
                    setKeyBinding(name, defaultConfig);
                }
                return keyBindings.get(name);
            }
            public void setKeyBinding(String name, InputSystem.InputPair keyBinding) {
                this.keyBindings.put(name, keyBinding);
            }

            @Override
            public @NotNull KineticEnergyAppliedClientConfigData deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                return gson.fromJson(jsonElement, KineticEnergyAppliedClientConfigData.class);
            }

            @Override
            public @NotNull JsonElement serialize(@NotNull KineticEnergyAppliedClientConfigData configInstance, @NotNull Gson gson) {
                return gson.toJsonTree(configInstance);
            }

            @Override
            public @NotNull KineticEnergyAppliedClientConfigData getDefaultConfig() {
                return new KineticEnergyAppliedClientConfigData();
            }

            @Override
            public @NotNull Class<KineticEnergyAppliedClientConfigData> getConfigClass() {
                return KineticEnergyAppliedClientConfigData.class;
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
        public static float onShoot(Projectile projectile, Entity shooter, float velocity) {
            GlowCircle glowCircle = new GlowCircle(EntityTypes.GLOW_CIRCLE_ENTITY_TYPE, shooter.level());
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