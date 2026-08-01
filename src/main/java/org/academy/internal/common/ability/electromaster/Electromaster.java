package org.academy.internal.common.ability.electromaster;

import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;

public final class Electromaster extends AbilityCategory {
    public static final AbilityCategory INSTANCE = new Electromaster();

    public Electromaster() {
        super(0.1F);
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.gui.icon.icon_electromaster;
    }

    @Override
    public String getDisplayName() {
        return "Electromaster";
    }
}
