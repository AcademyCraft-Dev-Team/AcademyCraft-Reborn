package org.academy.api.common.ability;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

public abstract class AbilityCategory {
    public final String name;
    public boolean customProbability = false;
    public float probability;
    public final List<Skill> skillList = new ArrayList<>();

    public AbilityCategory(String name) {
        this.name = name;
    }

    public AbilityCategory(String name, float probability) {
        this.name = name;
        this.probability = probability;
        this.customProbability = true;
    }

    public void initClient(){
    }

    public void initServer(MinecraftServer server){
    }
}