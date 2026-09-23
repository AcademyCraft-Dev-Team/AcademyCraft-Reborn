package org.academy.internal.common.ability.electromaster;

import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.development.AbilityDevelopmentProfiles;

public final class Electromaster extends AbilityCategory {
    public static final AbilityCategory INSTANCE = new Electromaster();

    public Electromaster() {
        super(0.1F, AbilityDevelopmentProfiles.ELECTROMASTER);
    }

    @Override
    public java.util.Optional<org.academy.api.common.ability.AbilityResourceSpec> getResourceSpec() {
        return java.util.Optional.of(org.academy.api.common.ability.AbilityResourceSpec.fixed(100));
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.ability.electromaster.icon;
    }

    @Override
    public String getDisplayName() {
        return "Electromaster";
    }
}
