package org.academy.internal.common.ability.darkmatter;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilityResourceSpec;
import org.academy.internal.common.ability.AbilityDevelopmentProfiles;
import org.academy.internal.common.world.damagesource.DamageTypes;

import java.util.Optional;

public final class Darkmatter extends AbilityCategory {
    public static final AbilityResourceSpec MATTER_RESOURCE = new AbilityResourceSpec(0.20f, 2.0f);

    public Darkmatter() {
        super(0.1f, AbilityDevelopmentProfiles.DARKMATTER);
    }

    @Override
    public Optional<ResourceKey<DamageType>> getDefaultDamageType() {
        return Optional.of(DamageTypes.DM_DAMAGE);
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.ICON_DARKMATTER;
    }

    @Override
    public String getDisplayName() {
        return "Dark Matter";
    }

    @Override
    public Optional<AbilityResourceSpec> getResourceSpec() {
        return Optional.of(MATTER_RESOURCE);
    }
}
