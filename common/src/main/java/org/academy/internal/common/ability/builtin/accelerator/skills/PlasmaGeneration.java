package org.academy.internal.common.ability.builtin.accelerator.skills;

import net.minecraft.server.MinecraftServer;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.builtin.SkillNames;

public class PlasmaGeneration extends Skill {
    public static final Skill INSTANCE = new PlasmaGeneration();

    private PlasmaGeneration() {
        super(SkillNames.PLASMA_GENERATION, 5);
    }

    @Override
    public void initClient() {
        super.initClient();
    }

    @Override
    public void initServer(MinecraftServer server) {
        super.initServer(server);
    }

    public static final class Client {
    }

    public static final class Server {
    }
}