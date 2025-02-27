package org.academy.api.common.network;

import org.academy.AcademyCraft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AcademyCraftFriendlyByteBufFactories {
    public static final Map<String, FriendlyByteBufFactory> FRIENDLY_BYTE_BUF_FACTORY_MAP = new HashMap<>();

    static {
        // [int]
        FRIENDLY_BYTE_BUF_FACTORY_MAP.put(AcademyCraftFriendlyByteBufIdentifiers.INTEGER, (friendlyByteBuf, value) -> friendlyByteBuf.writeVarInt((int) value.get(0)));
        // [string]
        FRIENDLY_BYTE_BUF_FACTORY_MAP.put(AcademyCraftFriendlyByteBufIdentifiers.STRING, (friendlyByteBuf, value) -> friendlyByteBuf.writeUtf((String) value.get(0)));
        // [boolean]
        FRIENDLY_BYTE_BUF_FACTORY_MAP.put(AcademyCraftFriendlyByteBufIdentifiers.BOOLEAN, (friendlyByteBuf, value) -> {
            friendlyByteBuf.writeBoolean((Boolean) value.get(0));
            return friendlyByteBuf;
        });
        // [int,string,value,value,...]
        FRIENDLY_BYTE_BUF_FACTORY_MAP.put(AcademyCraftFriendlyByteBufIdentifiers.LIST, (friendlyByteBuf, value) -> {
            for (Object o : value) {
                AcademyCraft.LOGGER.info(o.toString());
            }
            String identifier = (String) value.get(0);

            AcademyCraft.LOGGER.info(identifier);
            AcademyCraftFriendlyByteBufFactories.FRIENDLY_BYTE_BUF_FACTORY_MAP.get(AcademyCraftFriendlyByteBufIdentifiers.INTEGER).create(friendlyByteBuf, List.of(value.size() + 1));
            for (Object o : value) {
                AcademyCraftFriendlyByteBufFactories.FRIENDLY_BYTE_BUF_FACTORY_MAP.get(identifier).create(friendlyByteBuf, List.of(o));
            }

            return friendlyByteBuf;
        });
        // [int,string,string...,value,value,...]
        FRIENDLY_BYTE_BUF_FACTORY_MAP.put(AcademyCraftFriendlyByteBufIdentifiers.CUSTOM, (friendlyByteBuf, value) -> {
            int typeAmount = (int) value.get(0);

            AcademyCraftFriendlyByteBufFactories.FRIENDLY_BYTE_BUF_FACTORY_MAP.get(AcademyCraftFriendlyByteBufIdentifiers.INTEGER).create(friendlyByteBuf,List.of(typeAmount));

            for (int i = 1; i < (typeAmount + 1); i++) {
                FRIENDLY_BYTE_BUF_FACTORY_MAP.get(AcademyCraftFriendlyByteBufIdentifiers.STRING).create(friendlyByteBuf, List.of(value.get(i)));
            }

            for (int i = 1; i < (typeAmount + 1); i++) {
                String identifier = (String) value.get(i);
                AcademyCraft.LOGGER.info(identifier);
                FRIENDLY_BYTE_BUF_FACTORY_MAP.get(identifier).create(friendlyByteBuf, List.of(value.get(typeAmount + i)));
            }

            return friendlyByteBuf;
        });
    }

    private AcademyCraftFriendlyByteBufFactories() {
    }
}