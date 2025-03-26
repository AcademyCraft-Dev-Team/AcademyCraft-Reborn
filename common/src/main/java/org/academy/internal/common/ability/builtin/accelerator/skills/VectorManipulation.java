package org.academy.internal.common.ability.builtin.accelerator.skills;

import net.minecraft.server.MinecraftServer;
import org.academy.api.common.ability.Skill;

public class VectorManipulation extends Skill {
    public static final VectorManipulation INSTANCE = new VectorManipulation();

    public VectorManipulation() {
        super("vec_manipulation", 1);
    }

    @Override
    public void initClient() {
    }

    @Override
    public void initServer(MinecraftServer server) {
    }
}