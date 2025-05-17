package org.academy.internal.common.ability.builtin.accelerator.skills;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.api.client.config.ClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.annotation.PacketHandler;
import org.academy.api.common.network.Packets;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.GlowCircle;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class KineticEnergyApplied extends Skill {
    public static final Skill INSTANCE = new KineticEnergyApplied();

    private KineticEnergyApplied() {
        super(SkillNames.KINETIC_ENERGY_APPLIED, 1);
    }

    @Override
    public void initClient() {
        Client.CONFIG = AcademyCraftClient.CLIENT_CONFIG.getSkillClientConfig(INSTANCE.name, Client.CONFIG);
        InputSystem.addKeyBinding(Client.KEY_NAME, Client.CONFIG.getKeyBinding(Client.KEY_NAME,
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
        NetworkSystemServer.registerPacketHandlerClass(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME = INSTANCE.name + "_toggle";
        public static KineticEnergyAppliedClientConfig CONFIG = new KineticEnergyAppliedClientConfig();

        public static void toggle() {
            NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_KINETIC_ENERGY_APPLIED_TOGGLE));
        }

        public static final class KineticEnergyAppliedClientConfig extends ClientConfig.KeyBindingConfig {
        }
    }

    public static final class Server {
        public static final Map<UUID, Boolean> SKILL_STATS = new HashMap<>();

        @SuppressWarnings("unused")
        @PacketHandler(packet = Packets.C2S_KINETIC_ENERGY_APPLIED_TOGGLE)
        public static void handleToggle(ServerPlayer player) {
            if (SKILL_STATS.containsKey(player.getUUID())) {
                SKILL_STATS.put(player.getUUID(), !SKILL_STATS.get(player.getUUID()));
            } else {
                SKILL_STATS.put(player.getUUID(), true);
            }
        }

        public static float onShoot(Projectile projectile, Entity shooter, float x, float y, float z, float velocity, float inaccuracy) {
            GlowCircle glowCircle = new GlowCircle(EntityTypes.GLOW_CIRCLE_ENTITY_TYPE, shooter.level());
            Vec3 vec3 = shooter.getLookAngle().scale(1);
            glowCircle.setPos(projectile.getX() + vec3.x, projectile.getY() + vec3.y, projectile.getZ() + vec3.z);
            glowCircle.setYRot(shooter.getYRot());
            glowCircle.setXRot(shooter.getXRot());
            shooter.level().addFreshEntity(glowCircle);
            return velocity * 2;
        }
    }
}