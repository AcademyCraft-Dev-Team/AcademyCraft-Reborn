package org.academy.internal.common.ability.aeromanip;

import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilityResourceSpec;
import org.academy.internal.common.ability.AbilityDevelopmentProfiles;

import java.util.Optional;

public final class Aeromanip extends AbilityCategory {
    public static final int DEFAULT_COMPRESSED_AIR_CAPACITY = 128;
    public static final float DEFAULT_COMPRESSED_AIR_RECOVERY_PER_TICK = 4.0f;
    public static final AbilityResourceSpec COMPRESSED_AIR_RESOURCE =
            AbilityResourceSpec.fixed(DEFAULT_COMPRESSED_AIR_CAPACITY);

    public Aeromanip() {
        super(0.2f, AbilityDevelopmentProfiles.AEROMANIP);
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.ICON_AEROMANIP;
    }

    @Override
    public String getDisplayName() {
        return "Aeromanipulation";
    }

    @Override
    public Optional<AbilityResourceSpec> getResourceSpec() {
        return Optional.of(COMPRESSED_AIR_RESOURCE);
    }
}
