package org.academy.api.common.ability;

import net.minecraft.server.MinecraftServer;
import org.academy.internal.server.world.level.storage.WorldData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class Skill {
    public final String name;
    public final int level;
    public final List<Skill> dependencies = new ArrayList<>();
    public final int energy;

    /**
     * @param name  技能名称
     * @param level 推荐学习等级
     */
    protected Skill(String name, int level) {
        this.name = name;
        this.level = level;
        this.energy = 5000;
    }

    protected Skill(String name, int level, int energy) {
        this.name = name;
        this.level = level;
        this.energy = energy;
    }

    protected Skill(String name, int level, @NotNull List<Skill> dependencies) {
        this(name, level);
        this.dependencies.addAll(dependencies);
    }

    protected Skill(String name, int level, int energy, @NotNull List<Skill> dependencies) {
        this.name = name;
        this.level = level;
        this.energy = energy;
        this.dependencies.addAll(dependencies);
    }

    public void initClient() {
    }

    public void initServer(MinecraftServer server) {
    }

    public WorldData.Player.SkillData getDefaultSkillData() {
        return new WorldData.Player.SkillData() {
        };
    }
}