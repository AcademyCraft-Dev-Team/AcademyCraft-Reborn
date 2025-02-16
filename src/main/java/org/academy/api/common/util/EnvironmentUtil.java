package org.academy.api.common.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public class EnvironmentUtil {
    public static final boolean isClient = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    public static final boolean isServer = FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
}