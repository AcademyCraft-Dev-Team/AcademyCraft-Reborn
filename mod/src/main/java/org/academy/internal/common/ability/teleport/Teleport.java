package org.academy.internal.common.ability.teleport;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.AbilityDevelopmentProfiles;
import org.academy.internal.common.world.damagesource.DamageTypes;

import java.util.Optional;

public final class Teleport extends AbilityCategory {
    public Teleport() {
        super(0.1F, AbilityDevelopmentProfiles.TELEPORT);
    }

    @Override
    public Optional<ResourceKey<DamageType>> getDefaultDamageType() {
        return Optional.of(DamageTypes.SPACE_DAMAGE);
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.ability.teleport.icon;
    }

    @Override
    public String getDisplayName() {
        return "Teleport";
    }
}
