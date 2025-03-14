package org.academy.internal.common.ability.builtin.meltdowner.skills;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.AcademyCraftNetworkSystemClient;
import org.academy.api.client.network.packet.C2SRequestPacket;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.FriendlyByteBufIdentifiers;
import org.academy.api.common.network.FriendlyByteBufParsers;
import org.academy.api.common.network.Response;
import org.academy.api.server.network.AcademyCraftRequestHandlersServer;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class HighSpeedElectronBeam extends Skill {
    public static final Skill INSTANCE = new HighSpeedElectronBeam();
    public static final String KEY_NAME_START = "high_speed_electron_beam.start_shoot";
    public static final String KEY_NAME_END = "high_speed_electron_beam.end_shoot";
    public static AcademyCraftClientConfig.InputPair KEY_START;
    public static AcademyCraftClientConfig.InputPair KEY_END;

    private HighSpeedElectronBeam() {
        super("high_speed_electron_beam", 1);
    }

    @Override
    public void initClient() {
        KEY_START = AcademyCraftClient.clientConfig.getKey(KEY_NAME_START,
                new AcademyCraftClientConfig.InputPair(AcademyCraftClientConfig.InputType.MOUSE, new InputSystem.InputEvent(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOUSE_BUTTON_LEFT)),
                        GLFW.GLFW_PRESS,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT))
                )));

        KEY_END = AcademyCraftClient.clientConfig.getKey(KEY_NAME_END,
                new AcademyCraftClientConfig.InputPair(AcademyCraftClientConfig.InputType.MOUSE, new InputSystem.InputEvent(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOUSE_BUTTON_LEFT)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>()
                )));

        Runnable runnableStart = () -> {
            if (ClientUtil.isScreenNull()) {
                Client.pressTime = System.currentTimeMillis();
                Client.pressed = true;
            }
        };
        Runnable runnableEnd = () -> {
            if (Client.pressed) {
                Client.releaseTime = System.currentTimeMillis();
                long time = Client.releaseTime - Client.pressTime;
                AcademyCraftNetworkSystemClient.sendPacket(new C2SRequestPacket(AcademyCraftNetworkResourceLocations.C2S_HIGH_SPEED_ELECTRON_BEAM_REQUEST, FriendlyByteBufIdentifiers.LONG, time));
                Client.pressed = false;
            }
        };
        InputSystem.registerKeyBinding(KEY_NAME_START, KEY_START, runnableStart);
        InputSystem.registerKeyBinding(KEY_NAME_END, KEY_END, runnableEnd);
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftRequestHandlersServer.REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_HIGH_SPEED_ELECTRON_BEAM_REQUEST, (serverGamePacketListenerImpl, packet) -> {
            Response response = new Response();
            FriendlyByteBufParsers.FRIENDLY_BYTE_BUF_PARSER_MAP.get(FriendlyByteBufIdentifiers.LONG).parse(packet.getData(), response);
            long time = (long) response.dataList.get(0);
            AcademyCraft.LOGGER.info(time);
        });
    }

    public static final class Client {
        public static boolean pressed = false;
        public static long pressTime = 0;
        public static long releaseTime = 0;
    }

    public static final class Server {
        public static final Map<UUID, UUID> PLAYER_BEAM = new HashMap<>();
    }
}