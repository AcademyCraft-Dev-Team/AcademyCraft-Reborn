package org.academy.internal.common.ability.builtin.accelerator.skills;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraftClient;
import org.academy.api.client.config.SkillClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.annotation.PacketHandler;
import org.academy.api.common.network.Packets;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.common.ability.builtin.SkillNames;
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
                        new InputSystem.InputEvent(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_K)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>()
                        )
                )
        ), Client::toggle);
    }

    @Override
    public void initServer(MinecraftServer server) {
        NetworkSystemServer.SERVER_PACKET_HANDLER_CLASSES.add(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME = INSTANCE.name + "_toggle";
        public static KineticEnergyAppliedClientConfig CONFIG = new KineticEnergyAppliedClientConfig();

        public static void toggle() {
            NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_KINETIC_ENERGY_APPLIED_TOGGLE));
        }

        public static final class KineticEnergyAppliedClientConfig extends SkillClientConfig.SkillClientKeyBindingConfig {
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
    }
}
