package org.academy.api.common.ability;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class Skill {
    public final String name;
    public final int level;
    public final List<Skill> dependencies = new ArrayList<>();

    /**
     * @param name  技能名称
     * @param level 推荐学习等级
     */
    protected Skill(String name, int level) {
        this.name = name;
        this.level = level;
    }

    protected Skill(String name, int level, @NotNull List<Skill> dependencies) {
        this(name, level);
        this.dependencies.addAll(dependencies);
    }

    public void initClient() {
    }

    public void initServer(MinecraftServer server) {
    }
}