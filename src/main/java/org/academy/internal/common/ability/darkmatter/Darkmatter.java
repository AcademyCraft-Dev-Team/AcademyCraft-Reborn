package org.academy.internal.common.ability.darkmatter;

import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;

public final class Darkmatter extends AbilityCategory {
    public Darkmatter() {
        super(0.1f);
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
