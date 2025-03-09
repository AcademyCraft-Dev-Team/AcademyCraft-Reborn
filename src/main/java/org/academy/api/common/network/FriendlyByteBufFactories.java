package org.academy.api.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendlyByteBufFactories {
    public static final Map<String, FriendlyByteBufFactory> FRIENDLY_BYTE_BUF_FACTORY_MAP = new HashMap<>();

    static {
        // [int]
        FRIENDLY_BYTE_BUF_FACTORY_MAP.put(FriendlyByteBufIdentifiers.INTEGER, (friendlyByteBuf, value) -> friendlyByteBuf.writeVarInt((int) value.get(0)));
        // [string]
        FRIENDLY_BYTE_BUF_FACTORY_MAP.put(FriendlyByteBufIdentifiers.STRING, (friendlyByteBuf, value) -> friendlyByteBuf.writeUtf((String) value.get(0)));
        // [boolean]
        FRIENDLY_BYTE_BUF_FACTORY_MAP.put(FriendlyByteBufIdentifiers.BOOLEAN, (friendlyByteBuf, value) -> {
            friendlyByteBuf.writeBoolean((boolean) value.get(0));
            return friendlyByteBuf;
        });
        // [int,string,value,value,...]
        FRIENDLY_BYTE_BUF_FACTORY_MAP.put(FriendlyByteBufIdentifiers.LIST, (friendlyByteBuf, value) -> {
            String identifier = (String) value.get(0);

            FriendlyByteBufFactories.FRIENDLY_BYTE_BUF_FACTORY_MAP.get(FriendlyByteBufIdentifiers.INTEGER).create(friendlyByteBuf, List.of(value.size() + 1));
            FriendlyByteBufFactories.FRIENDLY_BYTE_BUF_FACTORY_MAP.get(FriendlyByteBufIdentifiers.STRING).create(friendlyByteBuf, List.of(identifier));

            for (int i = 1; i < value.size(); i++) {
                Object o = value.get(i);
                FriendlyByteBufFactories.FRIENDLY_BYTE_BUF_FACTORY_MAP.get(identifier).create(friendlyByteBuf, List.of(o));
            }

            return friendlyByteBuf;
        });
        // [float]
        FRIENDLY_BYTE_BUF_FACTORY_MAP.put(FriendlyByteBufIdentifiers.FLOAT, (friendlyByteBuf, value) -> {
            friendlyByteBuf.writeFloat((float) value.get(0));
            return friendlyByteBuf;
        });
        // [int,string,string...,value,value,...]
        FRIENDLY_BYTE_BUF_FACTORY_MAP.put(FriendlyByteBufIdentifiers.CUSTOM, (friendlyByteBuf, value) -> {
            int typeAmount = (int) value.get(0);

            FriendlyByteBufFactories.FRIENDLY_BYTE_BUF_FACTORY_MAP.get(FriendlyByteBufIdentifiers.INTEGER).create(friendlyByteBuf, List.of(typeAmount));

            for (int i = 1; i < (typeAmount + 1); i++) {
                FRIENDLY_BYTE_BUF_FACTORY_MAP.get(FriendlyByteBufIdentifiers.STRING).create(friendlyByteBuf, List.of(value.get(i)));
            }

            for (int i = 1; i < (typeAmount + 1); i++) {
                String identifier = (String) value.get(i);
                FRIENDLY_BYTE_BUF_FACTORY_MAP.get(identifier).create(friendlyByteBuf, List.of(value.get(typeAmount + i)));
            }

            return friendlyByteBuf;
        });
        // [long]
        FRIENDLY_BYTE_BUF_FACTORY_MAP.put(FriendlyByteBufIdentifiers.LONG, (friendlyByteBuf, value) -> {
            friendlyByteBuf.writeLong((long) value.get(0));
            return friendlyByteBuf;
        });
        // [long(BlockPos)]
        FRIENDLY_BYTE_BUF_FACTORY_MAP.put(FriendlyByteBufIdentifiers.BLOCK_POS, (friendlyByteBuf, value) -> friendlyByteBuf.writeBlockPos((BlockPos) value.get(0)));
        // [string)]
        FRIENDLY_BYTE_BUF_FACTORY_MAP.put(FriendlyByteBufIdentifiers.RESOURCE_LOCATION, (friendlyByteBuf, value) -> friendlyByteBuf.writeResourceLocation((ResourceLocation) value.get(0)));
    }

    private FriendlyByteBufFactories() {
    }
}