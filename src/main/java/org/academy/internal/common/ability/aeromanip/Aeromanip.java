package org.academy.internal.common.ability.aeromanip;

import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.AbilityDevelopmentProfiles;

public final class Aeromanip extends AbilityCategory {
    public Aeromanip() {
        super(0.2f, AbilityDevelopmentProfiles.AEROMANIP);
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.ICON_AEROMANIP;
    }

    @Override
    public String getDisplayName() {
        return "Aeromanipulation";
    }
}
