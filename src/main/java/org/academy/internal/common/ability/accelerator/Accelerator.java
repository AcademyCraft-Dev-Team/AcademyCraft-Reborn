package org.academy.internal.common.ability.accelerator;

import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;

public final class Accelerator extends AbilityCategory {
    public Accelerator() {
        super(0.1F);
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.gui.icon.icon_accelerator;
    }

    @Override
    public String getDisplayName() {
        return "Accelerator";
    }
}
