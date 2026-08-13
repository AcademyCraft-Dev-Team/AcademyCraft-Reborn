package org.academy.internal.common.ability.darkmatter;

import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.AbilityDevelopmentProfiles;

public final class Darkmatter extends AbilityCategory {
    public Darkmatter() {
        super(0.1f, AbilityDevelopmentProfiles.DARKMATTER);
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.ICON_DARKMATTER;
    }

    @Override
    public String getDisplayName() {
        return "Dark Matter";
    }
}
