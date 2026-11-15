package org.academy.internal.common.ability.level0;

import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;

public final class Level0 extends AbilityCategory {
    public Level0() {
        super(100);
    }

    @Override
    public boolean supportsCommonSkills() {
        return false;
    }

    @Override
    public Identifier getIcon() {
        return R.textures.ability.level0.icon;
    }

    @Override
    public String getDisplayName() {
        return "N/A";
    }
}
