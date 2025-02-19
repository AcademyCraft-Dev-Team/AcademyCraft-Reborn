package org.academy.api.common.ability;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.MinecraftServer;

// 如果需要，尽量使用名称为Client或Server的 static final 内部类来存储内容
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