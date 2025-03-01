package org.academy.api.server.network;

import net.minecraft.resources.ResourceLocation;
import org.academy.AbilitySystem;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.FriendlyByteBufIdentifiers;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.Response;
import org.academy.api.server.network.packet.S2CRequestPacket;
import org.academy.api.server.network.packet.S2CResponsePacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class AcademyCraftRequestHandlersServer {
    public static final Map<ResourceLocation, AcademyCraftRequestHandlerServer> REQUEST_HANDLER_MAP = new HashMap<>();

    static {
        REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_LEARN_ABILITY_REQUEST, (serverGamePacketListenerImpl, packet) -> {
            Response response = new Response();
            AcademyCraftNetworkSystemServer.SERVER_RESPONSE_MAP.put(AcademyCraftNetworkResourceLocations.C2S_CHANGE_ABILITY_CATEGORY_RESPONSE, response);
            serverGamePacketListenerImpl.send(new S2CRequestPacket(AcademyCraftNetworkResourceLocations.S2C_CHANGE_ABILITY_CATEGORY_REQUEST));

            Executors.newCachedThreadPool().execute(() -> Executors.newCachedThreadPool().execute(() -> {
                while (true) {
                    if ((boolean) response.dataList.get(0)) {
                        serverGamePacketListenerImpl.send(new S2CResponsePacket(FriendlyByteBufIdentifiers.BOOLEAN, AcademyCraftNetworkResourceLocations.S2C_LEARN_ABILITY_RESPONSE, List.of("see u again")));
                        break;
                    }

                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }));
        });
        REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_GET_ALL_SKILL_REQUEST, (serverGamePacketListenerImpl, packet) -> {
            List<String> list = new ArrayList<>();
            list.add(FriendlyByteBufIdentifiers.STRING);
            for (Skill skill : AbilitySystem.abilityCategoryMap.get(AcademyCraft.academyCraftWorldData.getPlayers().get(serverGamePacketListenerImpl.getPlayer().getUUID().toString()).getAbilityCategory()).skillList) {
                list.add(skill.name);
            }
            serverGamePacketListenerImpl.send(new S2CResponsePacket(FriendlyByteBufIdentifiers.LIST, AcademyCraftNetworkResourceLocations.S2C_GET_ALL_SKILL_RESPONSE, list));
        });
        REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_LEARN_SKILL_REQUEST, (serverGamePacketListenerImpl, packet) -> {
            boolean randomBoolean = Math.random() < 0.5;
            serverGamePacketListenerImpl.send(new S2CResponsePacket(FriendlyByteBufIdentifiers.CUSTOM, AcademyCraftNetworkResourceLocations.S2C_LEARN_SKILL_RESPONSE, List.of(2, FriendlyByteBufIdentifiers.BOOLEAN, FriendlyByteBufIdentifiers.STRING, randomBoolean, "test")));
        });
        REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_GET_LEARNED_SKILL_REQUEST, (serverGamePacketListenerImpl, packet) -> {
            List<String> list = new ArrayList<>();
            list.add(FriendlyByteBufIdentifiers.STRING);
            list.addAll(AcademyCraft.academyCraftWorldData.getPlayers().get(serverGamePacketListenerImpl.getPlayer().getUUID().toString()).getSkills());
            for (String skill : list) {
                AcademyCraft.LOGGER.info(skill);
            }
            AcademyCraft.LOGGER.info(list.toString());
            serverGamePacketListenerImpl.send(new S2CResponsePacket(FriendlyByteBufIdentifiers.LIST, AcademyCraftNetworkResourceLocations.S2C_GET_LEARNED_SKILL_RESPONSE, list));
        });
        REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_LEARN_CURRICULUM_REQUEST, (serverGamePacketListenerImpl, packet) -> {
            AcademyCraft.LOGGER.info(packet.getData().readUtf());
            serverGamePacketListenerImpl.send(new S2CResponsePacket(FriendlyByteBufIdentifiers.BOOLEAN, AcademyCraftNetworkResourceLocations.S2C_LEARN_CURRICULUM_RESPONSE, List.of(true)));
        });
    }

    private AcademyCraftRequestHandlersServer() {
    }
}