package org.academy.api.client.config;

import org.academy.AcademyCraftClient;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.WingFlightMotion;

import java.util.List;

public class WingFlightConfig extends KeyBindingConfig {
    private float remainingMomentum = 1.0f;

    public float getRemainingMomentum() {
        return WingFlightMotion.clampMomentum(remainingMomentum);
    }

    public void setRemainingMomentum(float value) {
        remainingMomentum = WingFlightMotion.clampMomentum(value);
    }

    public void register(Skill skill) {
        SkillSettingsRegistry.register(skill, new SkillSettingsRegistry.Module(
                "wing_flight", "app.academy.skill_settings.advanced.wing_flight",
                List.of(new SkillSettingsRegistry.FloatRange(
                        "remaining_momentum", "app.academy.skill_settings.advanced.wing_momentum",
                        0, 1, 0.01f, this::getRemainingMomentum, this::setRemainingMomentum,
                        () -> {
                            AcademyCraftClient.Config.INSTANCE.setConfig(skill.getKey(), this);
                            AcademyCraftClient.Config.INSTANCE.save();
                        }))));
    }
}
