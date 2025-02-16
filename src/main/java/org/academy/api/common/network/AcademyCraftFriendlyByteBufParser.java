package org.academy.api.common.network;

import org.academy.AcademyCraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AcademyCraftFriendlyByteBufParser {
    public static final Map<String, FriendlyByteBufParser> FRIENDLY_BYTE_BUF_PARSER_MAP = new HashMap<>();

    static {
        // [int]
        FRIENDLY_BYTE_BUF_PARSER_MAP.put(AcademyCraftFriendlyByteBufIdentities.INTEGER, (friendlyByteBuf, response) -> response.dataList.add(friendlyByteBuf.readVarInt()));
        // [string]
        FRIENDLY_BYTE_BUF_PARSER_MAP.put(AcademyCraftFriendlyByteBufIdentities.STRING, (friendlyByteBuf, response) -> response.dataList.add(friendlyByteBuf.readUtf()));
        // [boolean]
        FRIENDLY_BYTE_BUF_PARSER_MAP.put(AcademyCraftFriendlyByteBufIdentities.BOOLEAN, (friendlyByteBuf, response) -> response.dataList.add(friendlyByteBuf.readBoolean()));
        // [value,value,...]
        FRIENDLY_BYTE_BUF_PARSER_MAP.put(AcademyCraftFriendlyByteBufIdentities.LIST, (friendlyByteBuf, response) -> {
            AcademyCraft.LOGGER.info("Debug 10");
            int length = friendlyByteBuf.readVarInt();
            String identifier = friendlyByteBuf.readUtf();
            AcademyCraft.LOGGER.info(identifier + " : " + length);
            for (int i = 2; i < length; i++) {
                FRIENDLY_BYTE_BUF_PARSER_MAP.get(identifier).parse(friendlyByteBuf, response);
            }
            for (Object o : response.dataList) {
                AcademyCraft.LOGGER.info(o.toString());
            }
        });
        // [value,value,...]
        FRIENDLY_BYTE_BUF_PARSER_MAP.put(AcademyCraftFriendlyByteBufIdentities.CUSTOM, (friendlyByteBuf, response) -> {
            int typeAmount = friendlyByteBuf.readVarInt();
            List<String> typeList = new ArrayList<>();
            for (int i = 0; i < typeAmount; i++) {
                String identifier = friendlyByteBuf.readUtf();
                typeList.add(identifier);
            }
            for (String type : typeList) {
                FRIENDLY_BYTE_BUF_PARSER_MAP.get(type).parse(friendlyByteBuf, response);
            }
        });
    }

    private AcademyCraftFriendlyByteBufParser() {
    }
}
