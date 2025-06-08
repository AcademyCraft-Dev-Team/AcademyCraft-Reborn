package org.academy.internal.common.ability.builtin.accelerator.skills;

import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.common.ability.Skill;
import org.academy.internal.client.gui.screen.AbilityDeveloperScreen;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.ability.builtin.accelerator.Accelerator;

import java.util.List;

public class PlasmaGeneration extends Skill {
    public static final Skill INSTANCE = new PlasmaGeneration();

    private PlasmaGeneration() {
        super(SkillNames.PLASMA_GENERATION, 5, List.of(VectorReflection.INSTANCE, StormWing.INSTANCE));
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Accelerator.INSTANCE, INSTANCE, List.of(VectorReflection.Client.SKILL_INFO),
                        TextureResources.TEXTURE_DIR_STRIKE_ICON, 100, 110);
    }

    public static final class Server {
    }
}