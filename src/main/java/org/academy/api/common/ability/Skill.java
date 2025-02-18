package org.academy.api.common.ability;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.MinecraftServer;

public abstract class Skill {
    public String name;
    public int level;

    public Skill(String name, int level) {
        this.name = name;
        this.level = level;
    }

    @Environment(EnvType.CLIENT)
    public void initClient() {
    }

    public void initServer(MinecraftServer server) {
    }
}