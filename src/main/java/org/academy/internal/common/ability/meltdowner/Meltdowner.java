package org.academy.internal.common.ability.meltdowner;

import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.AbilityDevelopmentProfiles;

public final class Meltdowner extends AbilityCategory {
    public Meltdowner() {
        super(0.1F, AbilityDevelopmentProfiles.MELTDOWNER);
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.gui.icon.icon_meltdowner;
    }

    @Override
    public String getDisplayName() {
        return "Meltdowner";
    }
}
