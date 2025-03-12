package org.academy.api.client.util;

import net.minecraft.client.Minecraft;

public class ClientUtil {
    public static boolean isScreenNull() {
        return Minecraft.getInstance().screen == null;
    }

    private ClientUtil() {
    }
}