package org.academy.internal.common.ability;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.registries.Registries;
import org.academy.internal.common.ability.accelerator.Accelerator;
import org.academy.internal.common.ability.electromaster.Electromaster;
import org.academy.internal.common.ability.level0.Level0;
import org.academy.internal.common.ability.meltdowner.Meltdowner;
import org.academy.internal.common.ability.teleport.Teleport;

import java.util.ArrayList;
import java.util.List;

public final class AbilityCategories {
    public static final DeferredRegister<AbilityCategory> ABILITY_CATEGORIES = DeferredRegister.create(Registries.Keys.ABILITY_CATEGORIES, AcademyCraft.MOD_ID);
    public static final DeferredHolder<AbilityCategory, Level0> LEVEL0 = ABILITY_CATEGORIES.register(AbilityCategoryNames.LEVEL0, Level0::new);
    public static final DeferredHolder<AbilityCategory, Electromaster> ELECTROMASTER = ABILITY_CATEGORIES.register(AbilityCategoryNames.ELECTROMASTER, Electromaster::new);
    public static final DeferredHolder<AbilityCategory, Teleport> TELEPORT = ABILITY_CATEGORIES.register(AbilityCategoryNames.TELEPORT, Teleport::new);
    public static final DeferredHolder<AbilityCategory, Accelerator> ACCELERATOR = ABILITY_CATEGORIES.register(AbilityCategoryNames.ACCELERATOR, Accelerator::new);
    public static final DeferredHolder<AbilityCategory, Meltdowner> MELTDOWNER = ABILITY_CATEGORIES.register(AbilityCategoryNames.MELTDOWNER, Meltdowner::new);

    private AbilityCategories() {
    }
}