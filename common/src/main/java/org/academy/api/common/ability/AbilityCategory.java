package org.academy.api.common.ability;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * 名称可以参考 <a href="https://toarumajutsunoindex.fandom.com/wiki/Category:Esper_Abilities">...</a>
 * 也应该遵守于此
 */
public abstract class AbilityCategory {
    public final String name;
    public final float probability;
    public final List<Skill> skillList = new ArrayList<>();

    protected AbilityCategory(final String newName) {
        name = newName;
        probability = 1.0F;
    }

    protected AbilityCategory(final String newName, final float newProbability) {
        name = newName;
        probability = newProbability;
    }

    public void initClient() {
    }

    public void initServer(MinecraftServer server) {
    }
}