package org.academy.api.common.ability;

import net.minecraft.server.MinecraftServer;

public abstract class Skill {
    public String name;
    public int level;

    /**
     * @param name  技能名称
     * @param level 推荐学习等级
     */
    protected Skill(String name, int level) {
        this.name = name;
        this.level = level;
    }

    public void initClient() {
    }

    public void initServer(MinecraftServer server) {
    }
}