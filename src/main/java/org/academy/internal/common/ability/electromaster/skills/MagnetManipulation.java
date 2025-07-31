package org.academy.internal.common.ability.electromaster.skills;

import net.minecraft.server.MinecraftServer;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.AbilityCategories;

public class MagnetManipulation extends Skill {
    public static final String TAG_KEY = "arc_list";

    public MagnetManipulation() {
        super(Builder.of(AbilityCategories.ELECTROMASTER.get()).level(AbilityLevel.LEVEL3));
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