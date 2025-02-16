package org.academy.api.common.ability;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

public abstract class Skill {
    public String name;
    public int level;

    public Skill(String name, int level) {
        this.name = name;
        this.level = level;
    }

    public abstract void init();

    /**
     * Like KeyBinding.
     */
    @Environment(EnvType.CLIENT)
    public abstract void initClient();

    /**
     * Like Event.
     */
    public abstract void initServer();
}