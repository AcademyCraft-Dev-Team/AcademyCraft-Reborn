package org.academy.internal.common.ability.mentalout.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
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
import org.academy.internal.client.ability.mentalout.PlayerControlClientState;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.MentaloutRequestGuard;
import org.academy.internal.common.ability.mentalout.PlayerControlSessionManager;
import org.academy.internal.common.ability.mentalout.skills.lv1.MentalIntrusion;
import org.academy.internal.common.ability.mentalout.skills.lv2.MentalStupor;
import org.academy.internal.common.ability.mentalout.skills.lv3.CommandPositioning;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;

public final class MentalTakeover extends Skill {
    public MentalTakeover() {
        super(Builder
                .of(AbilityCategories.MENTALOUT.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .cpCost(0)
                .iterationTicks(10)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.MENTAL_INTRUSION, Skills.COMMAND_POSITIONING, Skills.MENTAL_STUPOR)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition(
                        "Mental Intrusion", "academy:mental_intrusion"))
                .devCondition(new DevCondition.DependencyCondition(
                        "Command Positioning", "academy:command_positioning"))
                .devCondition(new DevCondition.DependencyCondition(
                        "Mental Stupor", "academy:mental_stupor"))
        );
    }

    @Override
    public void initClient() {
        PlayerControlSessionManager.initClient();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_USE,
                InputSystem.combo(
                        InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_Y,
                        InputConstants.PRESS,
                        GLFW.GLFW_MOD_ALT
                )
        ), _ -> Client.use());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        PlayerControlSessionManager.initServer();
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MENTALOUT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.MENTAL_TAKEOVER.get(),
                        List.of(
                                MentalIntrusion.Client.SKILL_INFO,
                                CommandPositioning.Client.SKILL_INFO,
                                MentalStupor.Client.SKILL_INFO
                        ),
                        R.textures.ability.mentalout.skill.mental_takeover.icon,
                        164,
                        112
                )
        );
        public static final String KEY_NAME_USE = SkillNames.MENTAL_TAKEOVER + "_use";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void use() {
            if (ClientUtil.hasScreen()) return;
            if (!PlayerControlClientState.isActive()
                    && !AbilitySystemClient.canUseSkill(Skills.MENTAL_TAKEOVER.get())) return;
            MisakaNetworkClient.send(new PlayerControlSessionManager.TogglePacket(
                    MentaloutRequestGuard.nextClientSequence()));
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
