package org.academy.internal.common.ability.mentalout.skills;

import com.mojang.blaze3d.platform.InputConstants;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.MentalIntrusionManager;
import org.academy.internal.common.ability.mentalout.MentaloutRequestGuard;
import org.misaka.MisakaNetworkClient;

import java.util.List;

public final class SensoryDistortion extends Skill {
    public SensoryDistortion() {
        super(Builder
                .of(AbilityCategories.MENTALOUT.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(0)
                .iterationTicks(0)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.MENTAL_INTRUSION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
                .devCondition(new DevCondition.DependencyCondition(
                        "Mental Intrusion", "academy:mental_intrusion"))
        );
    }

    @Override
    public void initClient() {
        MentalIntrusionManager.initClient();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_USE,
                InputSystem.combo(
                        InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_Y,
                        InputConstants.PRESS,
                        InputConstants.MOD_ALT
                )
        ), _ -> Client.use());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MentalIntrusionManager.initServer();
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MENTALOUT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.SENSORY_DISTORTION.get(),
                        List.of(MentalIntrusion.Client.SKILL_INFO),
                        R.textures.ability.mentalout.skill.sensory_distortion.icon,
                        164,
                        76
                )
        );
        public static final String KEY_NAME_USE = SkillNames.SENSORY_DISTORTION + "_use";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void use() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(Skills.SENSORY_DISTORTION.get())) return;
            MisakaNetworkClient.send(new MentalIntrusionManager.DistortionPacket(
                    MentaloutRequestGuard.nextClientSequence()
            ));
        }

        public static final class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }
}
