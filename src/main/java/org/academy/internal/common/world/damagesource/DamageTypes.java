package org.academy.internal.common.world.damagesource;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import org.academy.AcademyCraft;

public class DamageTypes {
    public static final ResourceKey<DamageType> RAILGUN = ResourceKey.create(Registries.DAMAGE_TYPE, AcademyCraft.academy("railgun"));
}