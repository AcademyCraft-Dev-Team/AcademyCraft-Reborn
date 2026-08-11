package org.academy.internal.common.ability.teleport;

import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.AbilityDevelopmentProfiles;

public final class Teleport extends AbilityCategory {
    public Teleport() {
        super(0.1F, AbilityDevelopmentProfiles.TELEPORT);
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.gui.icon.icon_teleporter;
    }

    @Override
    public String getDisplayName() {
        return "Teleport";
    }
}
