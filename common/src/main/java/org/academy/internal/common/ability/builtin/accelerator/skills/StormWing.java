package org.academy.internal.common.ability.builtin.accelerator.skills;

import net.minecraft.server.MinecraftServer;
import org.academy.api.common.ability.Skill;
import org.academy.internal.client.renderer.effect.StormWingEffectRenderer;
import org.academy.internal.client.renderer.entity.layers.SkillEffectsLayer;

public class StormWing extends Skill {
    public static final Skill INSTANCE = new StormWing();

    private StormWing() {
        super("storm_wing", 4);
    }

    @Override
    public void initClient() {
        SkillEffectsLayer.EFFECT_RENDERER_MAP.add(StormWingEffectRenderer.INSTANCE);
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
