package org.academy.api.common.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendlyByteBufParsers {
    public static final Map<String, FriendlyByteBufParser> FRIENDLY_BYTE_BUF_PARSER_MAP = new HashMap<>();

    static {
        // [int]
        FRIENDLY_BYTE_BUF_PARSER_MAP.put(FriendlyByteBufIdentifiers.INTEGER, (friendlyByteBuf, response) -> response.dataList.add(friendlyByteBuf.readVarInt()));
        // [string]
        FRIENDLY_BYTE_BUF_PARSER_MAP.put(FriendlyByteBufIdentifiers.STRING, (friendlyByteBuf, response) -> response.dataList.add(friendlyByteBuf.readUtf()));
        // [boolean]
        FRIENDLY_BYTE_BUF_PARSER_MAP.put(FriendlyByteBufIdentifiers.BOOLEAN, (friendlyByteBuf, response) -> response.dataList.add(friendlyByteBuf.readBoolean()));
        // [value,value,...]
        FRIENDLY_BYTE_BUF_PARSER_MAP.put(FriendlyByteBufIdentifiers.LIST, (friendlyByteBuf, response) -> {
            int length = friendlyByteBuf.readVarInt();
            String identifier = friendlyByteBuf.readUtf();
            for (int i = 2; i < length; i++) {
                FRIENDLY_BYTE_BUF_PARSER_MAP.get(identifier).parse(friendlyByteBuf, response);
            }
        });
        // [int]
        FRIENDLY_BYTE_BUF_PARSER_MAP.put(FriendlyByteBufIdentifiers.FLOAT, (friendlyByteBuf, response) -> response.dataList.add(friendlyByteBuf.readFloat()));
        // [value,value,...]
        FRIENDLY_BYTE_BUF_PARSER_MAP.put(FriendlyByteBufIdentifiers.CUSTOM, (friendlyByteBuf, response) -> {
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
        // [long]
        FRIENDLY_BYTE_BUF_PARSER_MAP.put(FriendlyByteBufIdentifiers.LONG, (friendlyByteBuf, response) -> response.dataList.add(friendlyByteBuf.readLong()));
        // [BlockPos]
        FRIENDLY_BYTE_BUF_PARSER_MAP.put(FriendlyByteBufIdentifiers.BLOCK_POS, (friendlyByteBuf, response) -> response.dataList.add(friendlyByteBuf.readBlockPos()));
    }

    private FriendlyByteBufParsers() {
    }
}
