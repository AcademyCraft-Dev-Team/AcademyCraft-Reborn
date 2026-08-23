package org.academy.internal.client.ability;

import net.minecraft.client.Minecraft;
import org.academy.api.client.config.SkillSettingsRegistry;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.ProficiencySkillSettings;
import org.academy.internal.common.ability.Skills;
import org.misaka.MisakaNetworkClient;

import java.util.List;

public final class ProficiencySkillSettingsClient {
    private static boolean initialized;

    private ProficiencySkillSettingsClient() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        registerToggle(
                Skills.DARKMATTER_SHAPING.get(),
                "auto_repair",
                "app.academy.skill_settings.advanced.darkmatter_auto_repair",
                ProficiencySkillSettings.DARKMATTER_SHAPING_AUTO_REPAIR
        );
        registerToggle(
                Skills.FLASHING.get(),
                "auto_escape",
                "app.academy.skill_settings.advanced.flashing_auto_escape",
                ProficiencySkillSettings.FLASHING_AUTO_ESCAPE
        );
    }

    private static void registerToggle(
            Skill skill,
            String id,
            String labelKey,
            String option
    ) {
        SkillSettingsRegistry.INSTANCE.register(
                skill,
                new SkillSettingsRegistry.Module(
                        "proficiency",
                        "",
                        List.of(new SkillSettingsRegistry.Toggle(
                                id,
                                labelKey,
                                () -> ProficiencySkillSettings.isEnabled(Minecraft.getInstance().player, option),
                                enabled -> {
                                    var player = Minecraft.getInstance().player;
                                    if (player == null) return;
                                    ProficiencySkillSettings.setEnabled(player, option, enabled);
                                    MisakaNetworkClient.send(new ProficiencySkillSettings.SetPacket(option, enabled));
                                }
                        ))
                )
        );
    }
}
