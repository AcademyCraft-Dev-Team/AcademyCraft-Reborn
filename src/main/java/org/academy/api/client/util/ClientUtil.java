package org.academy.api.client.util;

import net.minecraft.client.Minecraft;

public class ClientUtil {
    public static boolean hasScreen() {
        return Minecraft.getInstance().screen != null;
    }

    private ClientUtil() {
    }
}
