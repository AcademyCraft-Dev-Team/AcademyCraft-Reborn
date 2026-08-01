package org.academy.internal.common.ability.teleport;

import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;

public final class Teleport extends AbilityCategory {
    public Teleport() {
        super(0.1F);
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.ICON_TELEPORTER;
    }

    @Override
    public String getDisplayName() {
        return "Teleport";
    }
}
