package org.academy.internal.common.ability.mentalout;

import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.AbilityDevelopmentProfiles;

public final class Mentalout extends AbilityCategory {
    public Mentalout() {
        super(0.1F, AbilityDevelopmentProfiles.MENTALOUT);
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.ability.mentalout.icon;
    }

    @Override
    public String getDisplayName() {
        return "Mentalout";
    }
}
