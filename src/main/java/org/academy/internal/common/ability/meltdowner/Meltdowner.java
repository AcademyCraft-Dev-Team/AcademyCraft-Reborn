package org.academy.internal.common.ability.meltdowner;

import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;

public final class Meltdowner extends AbilityCategory {
    public Meltdowner() {
        super(0.1F);
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.ICON_MELTDOWNER;
    }

    @Override
    public String getDisplayName() {
        return "Meltdowner";
    }
}
