package org.academy.internal.client.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.level.ItemLike;
import org.academy.internal.common.world.item.Items;

import java.util.concurrent.CompletableFuture;

public final class AcademyCraftRecipeProvider extends RecipeProvider {
    private AcademyCraftRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        {
            var dowsingRod = shaped(RecipeCategory.MISC, Items.IMAGIPHASE_DOWSING_ROD.get());

            dowsingRod.define('C', net.minecraft.world.item.Items.COMPARATOR);
            dowsingRod.define('L', net.minecraft.world.item.Items.LIGHTNING_ROD);
            dowsingRod.define('P', net.minecraft.world.item.Items.COMPASS);
            dowsingRod.define('S', Items.SCREEN.get());
            dowsingRod.define('M', Items.IMAGIPHASE_METAL.get());
            dowsingRod.pattern(" C ");
            dowsingRod.pattern("LPS");
            dowsingRod.pattern(" M ");

            unlockedByHas(dowsingRod, net.minecraft.world.item.Items.COMPARATOR);
            unlockedByHas(dowsingRod, net.minecraft.world.item.Items.LIGHTNING_ROD);
            unlockedByHas(dowsingRod, net.minecraft.world.item.Items.COMPASS);
            unlockedByHas(dowsingRod, Items.SCREEN.get());
            unlockedByHas(dowsingRod, Items.IMAGIPHASE_METAL.get());

            dowsingRod.save(output);
        }
        {
            var solar = shaped(RecipeCategory.MISC, Items.SOLAR_GEN.get());

            solar.define('G', net.minecraft.world.item.Items.GLASS_PANE);
            solar.define('T', net.minecraft.world.item.Items.IRON_TRAPDOOR);
            solar.define('I', net.minecraft.world.item.Items.IRON_INGOT);
            solar.define('R', net.minecraft.world.item.Items.REPEATER);
            solar.define('A', Items.IMAGIPHASE_AMETHYST.get());
            solar.pattern("GGG");
            solar.pattern("TAT");
            solar.pattern("IRI");

            unlockedByHas(solar, net.minecraft.world.item.Items.COMPARATOR);
            unlockedByHas(solar, net.minecraft.world.item.Items.LIGHTNING_ROD);
            unlockedByHas(solar, net.minecraft.world.item.Items.COMPASS);
            unlockedByHas(solar, Items.SCREEN.get());
            unlockedByHas(solar, Items.IMAGIPHASE_METAL.get());

            solar.save(output);
        }
        {
            var dowsingRod = shaped(RecipeCategory.MISC, Items.IMAGIPHASE_DOWSING_ROD.get());

            dowsingRod.define('C', net.minecraft.world.item.Items.COMPARATOR);
            dowsingRod.define('L', net.minecraft.world.item.Items.LIGHTNING_ROD);
            dowsingRod.define('P', net.minecraft.world.item.Items.COMPASS);
            dowsingRod.define('S', Items.SCREEN.get());
            dowsingRod.define('M', Items.IMAGIPHASE_METAL.get());
            dowsingRod.pattern(" C ");
            dowsingRod.pattern("LPS");
            dowsingRod.pattern(" M ");

            unlockedByHas(dowsingRod, net.minecraft.world.item.Items.COMPARATOR);
            unlockedByHas(dowsingRod, net.minecraft.world.item.Items.LIGHTNING_ROD);
            unlockedByHas(dowsingRod, net.minecraft.world.item.Items.COMPASS);
            unlockedByHas(dowsingRod, Items.SCREEN.get());
            unlockedByHas(dowsingRod, Items.IMAGIPHASE_METAL.get());

            dowsingRod.save(output);
        }
        {
            var dowsingRod = shaped(RecipeCategory.MISC, Items.IMAGIPHASE_DOWSING_ROD.get());

            dowsingRod.define('C', net.minecraft.world.item.Items.COMPARATOR);
            dowsingRod.define('L', net.minecraft.world.item.Items.LIGHTNING_ROD);
            dowsingRod.define('P', net.minecraft.world.item.Items.COMPASS);
            dowsingRod.define('S', Items.SCREEN.get());
            dowsingRod.define('M', Items.IMAGIPHASE_METAL.get());
            dowsingRod.pattern(" C ");
            dowsingRod.pattern("LPS");
            dowsingRod.pattern(" M ");

            unlockedByHas(dowsingRod, net.minecraft.world.item.Items.COMPARATOR);
            unlockedByHas(dowsingRod, net.minecraft.world.item.Items.LIGHTNING_ROD);
            unlockedByHas(dowsingRod, net.minecraft.world.item.Items.COMPASS);
            unlockedByHas(dowsingRod, Items.SCREEN.get());
            unlockedByHas(dowsingRod, Items.IMAGIPHASE_METAL.get());

            dowsingRod.save(output);
        }
        {
            var dowsingRod = shaped(RecipeCategory.MISC, Items.IMAGIPHASE_DOWSING_ROD.get());

            dowsingRod.define('C', net.minecraft.world.item.Items.COMPARATOR);
            dowsingRod.define('L', net.minecraft.world.item.Items.LIGHTNING_ROD);
            dowsingRod.define('P', net.minecraft.world.item.Items.COMPASS);
            dowsingRod.define('S', Items.SCREEN.get());
            dowsingRod.define('M', Items.IMAGIPHASE_METAL.get());
            dowsingRod.pattern(" C ");
            dowsingRod.pattern("LPS");
            dowsingRod.pattern(" M ");

            unlockedByHas(dowsingRod, net.minecraft.world.item.Items.COMPARATOR);
            unlockedByHas(dowsingRod, net.minecraft.world.item.Items.LIGHTNING_ROD);
            unlockedByHas(dowsingRod, net.minecraft.world.item.Items.COMPASS);
            unlockedByHas(dowsingRod, Items.SCREEN.get());
            unlockedByHas(dowsingRod, Items.IMAGIPHASE_METAL.get());

            dowsingRod.save(output);
        }
    }

    public void unlockedByHas(ShapedRecipeBuilder builder, ItemLike item) {
        builder.unlockedBy(getHasName(item), has(item));
    }

    public static final class Runner extends RecipeProvider.Runner {
        public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
            super(output, lookupProvider);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider lookupProvider, RecipeOutput output) {
            return new AcademyCraftRecipeProvider(lookupProvider, output);
        }

        @Override
        public String getName() {
            return "AcademyCraft recipes";
        }
    }
}