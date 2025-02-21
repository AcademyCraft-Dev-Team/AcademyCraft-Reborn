package org.academy.api.common.ability;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.MinecraftServer;

/**
 * 为了简化开发与使用难度，以 Skill 为单位来实现原著中的能力
 */
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