package org.academy.api.server.vanilla;

import net.minecraft.server.MinecraftServer;
import org.academy.AcademyCraftServer;

/**
 * 一个 MinecraftServerContext 实例对应一对 MinecraftServer 和 AcademyCraftServer 实例
 */
public interface MinecraftServerContext {
    void setAcademyCraftServer(AcademyCraftServer academyCraftServer);

    AcademyCraftServer getAcademyCraftServer();

    MinecraftServer getMinecraftServer();
}