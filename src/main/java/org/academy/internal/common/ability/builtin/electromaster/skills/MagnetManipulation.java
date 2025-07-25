package org.academy.internal.common.ability.builtin.electromaster.skills;

import net.minecraft.server.MinecraftServer;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.builtin.SkillNames;

public class MagnetManipulation extends Skill {
    public static final Skill INSTANCE = new MagnetManipulation();
    public static final String TAG_KEY = "arc_list";

    private MagnetManipulation() {
        super(SkillNames.MAGNET_MANIPULATION, 3);
    }

    @Override
    public void initServer(MinecraftServer server) {
        super.initServer(server);
    }

    @Override
    public void initClient() {
    }

    public static final class Client {

    }

    public static final class Server {

    }
}