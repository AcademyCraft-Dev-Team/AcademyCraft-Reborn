package org.academy.internal.common.world.damagesource;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import org.academy.AcademyCraft;

public class AcademyCraftDamageTypes {
    public static final ResourceKey<DamageType> RAILGUN = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(AcademyCraft.MOD_ID, "railgun"));
}
