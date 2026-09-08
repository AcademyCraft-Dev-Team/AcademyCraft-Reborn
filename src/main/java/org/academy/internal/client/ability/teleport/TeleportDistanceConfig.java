package org.academy.internal.client.ability.teleport;

import org.academy.AcademyCraftClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.config.SkillSettingsRegistry;
import org.academy.api.common.ability.Skill;

import java.util.List;

/** Each teleport skill owns a separate persisted instance of this configuration. */
public abstract class TeleportDistanceConfig extends KeyBindingConfig {
    private double defaultDistance = 40.0;

    public double getDefaultDistance() {
        return Double.isFinite(defaultDistance) ? Math.clamp(defaultDistance, 0.0, 64.0) : 40.0;
    }

    public void setDefaultDistance(double distance) {
        defaultDistance = Double.isFinite(distance) ? Math.clamp(distance, 0.0, 64.0) : 40.0;
    }

    public void registerSettings(Skill skill) {
        SkillSettingsRegistry.INSTANCE.register(skill, new SkillSettingsRegistry.Module(
                "teleport_distance", "", List.of(new SkillSettingsRegistry.FloatRange(
                "default_distance", "app.academy.skill_settings.advanced.teleport.default_distance",
                0.0f, 64.0f, 1.0f,
                () -> (float) getDefaultDistance(),
                value -> setDefaultDistance(value),
                () -> AcademyCraftClient.Config.INSTANCE.save(),
                value -> Integer.toString(Math.round(value))
        ))));
    }
}
