package org.academy.api.server.vanilla;

import net.minecraft.server.MinecraftServer;
import org.academy.AcademyCraftServer;

public interface MinecraftServerContext {
    void setAcademyCraftServer(AcademyCraftServer academyCraftServer);

    AcademyCraftServer getAcademyCraftServer();

    MinecraftServer getMinecraftServer();
}