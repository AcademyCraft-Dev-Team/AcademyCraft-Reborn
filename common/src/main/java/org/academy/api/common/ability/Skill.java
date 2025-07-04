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

    protected Skill(String newName, int newLevel) {
        name = newName;
        level = newLevel;
        energy = 5000;
    }

    protected Skill(String newName, int newLevel, int newEnergy) {
        name = newName;
        level = newLevel;
        energy = newEnergy;
    }

    protected Skill(String newName, int newLevel, @NotNull List<Skill> newDependencies) {
        this(newName, newLevel);
        dependencies.addAll(newDependencies);
    }

    protected Skill(String newName, int newLevel, int newEnergy, @NotNull List<Skill> newDependencies) {
        name = newName;
        level = newLevel;
        energy = newEnergy;
        dependencies.addAll(newDependencies);
    }

    public void init() {
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