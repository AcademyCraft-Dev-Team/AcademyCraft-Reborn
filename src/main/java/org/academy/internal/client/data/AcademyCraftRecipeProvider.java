package org.academy.internal.client.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceKey;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.item.crafting.DarkmatterDuplicationRecipe;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.world.item.Items.*;

public final class AcademyCraftRecipeProvider extends RecipeProvider {
    private AcademyCraftRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        output.accept(
                ResourceKey.create(Registries.RECIPE, AcademyCraft.academy("darkmatter_duplication")),
                new DarkmatterDuplicationRecipe(),
                null
        );

        buildTemporaryVanillaRecipes();
    }

    /**
     * Temporary recipes using only vanilla ingredients until the AcademyCraft material chain is restored.
     */
    private void buildTemporaryVanillaRecipes() {
        shaped(RecipeCategory.MISC, Items.ICON.get())
                .define('A', AMETHYST_SHARD)
                .define('P', PAPER)
                .define('R', REDSTONE)
                .pattern(" A ")
                .pattern("APA")
                .pattern(" R ")
                .unlockedBy("has_amethyst_shard", has(AMETHYST_SHARD))
                .save(output);

        shaped(RecipeCategory.MISC, Items.DARKMATTER.get())
                .define('E', ECHO_SHARD)
                .define('N', NETHERITE_SCRAP)
                .define('O', OBSIDIAN)
                .pattern("OEO")
                .pattern("ENE")
                .pattern("OEO")
                .unlockedBy("has_echo_shard", has(ECHO_SHARD))
                .save(output);

        shaped(RecipeCategory.COMBAT, Items.DARK_MATTER_HELMET.get())
                .define('D', ECHO_SHARD)
                .define('N', NETHERITE_HELMET)
                .pattern("DDD")
                .pattern("DND")
                .unlockedBy("has_netherite_helmet", has(NETHERITE_HELMET))
                .save(output);

        shaped(RecipeCategory.COMBAT, Items.DARK_MATTER_CHESTPLATE.get())
                .define('D', ECHO_SHARD)
                .define('N', NETHERITE_CHESTPLATE)
                .pattern("DND")
                .pattern("DDD")
                .pattern("DDD")
                .unlockedBy("has_netherite_chestplate", has(NETHERITE_CHESTPLATE))
                .save(output);

        shaped(RecipeCategory.COMBAT, Items.DARK_MATTER_LEGGINGS.get())
                .define('D', ECHO_SHARD)
                .define('N', NETHERITE_LEGGINGS)
                .pattern("DDD")
                .pattern("DND")
                .pattern("D D")
                .unlockedBy("has_netherite_leggings", has(NETHERITE_LEGGINGS))
                .save(output);

        shaped(RecipeCategory.COMBAT, Items.DARK_MATTER_BOOTS.get())
                .define('D', ECHO_SHARD)
                .define('N', NETHERITE_BOOTS)
                .pattern("DND")
                .pattern("D D")
                .unlockedBy("has_netherite_boots", has(NETHERITE_BOOTS))
                .save(output);

        shaped(RecipeCategory.MISC, Items.DATA_TERMINAL.get())
                .define('G', GLASS_PANE)
                .define('I', IRON_INGOT)
                .define('R', REDSTONE)
                .pattern("IRI")
                .pattern("RGR")
                .pattern("III")
                .unlockedBy("has_redstone", has(REDSTONE))
                .save(output);

        shapeless(RecipeCategory.MISC, Items.COIN.get(), 4)
                .requires(GOLD_NUGGET)
                .unlockedBy("has_gold_nugget", has(GOLD_NUGGET))
                .save(output);

        shaped(RecipeCategory.REDSTONE, Items.WIRELESS_NODE.get())
                .define('E', ENDER_PEARL)
                .define('I', IRON_INGOT)
                .define('R', REDSTONE)
                .pattern("IRI")
                .pattern("RER")
                .pattern("IRI")
                .unlockedBy("has_ender_pearl", has(ENDER_PEARL))
                .save(output);

        shaped(RecipeCategory.REDSTONE, Items.WIND_GEN_BASE.get())
                .define('C', COPPER_INGOT)
                .define('F', FURNACE)
                .define('I', IRON_INGOT)
                .define('R', REDSTONE)
                .pattern("CIC")
                .pattern("RFR")
                .pattern("III")
                .unlockedBy("has_copper_ingot", has(COPPER_INGOT))
                .save(output);

        shaped(RecipeCategory.REDSTONE, Items.WIND_GEN_TOP.get())
                .define('C', COPPER_INGOT)
                .define('I', IRON_INGOT)
                .define('P', PISTON)
                .define('R', REDSTONE)
                .pattern(" C ")
                .pattern("IPI")
                .pattern(" R ")
                .unlockedBy("has_piston", has(PISTON))
                .save(output);

        shaped(RecipeCategory.MISC, Items.WIND_GEN_PILLAR.get(), 4)
                .define('I', IRON_INGOT)
                .pattern("I")
                .pattern("I")
                .pattern("I")
                .unlockedBy("has_iron_ingot", has(IRON_INGOT))
                .save(output);

        shaped(RecipeCategory.REDSTONE, Items.ABILITY_DEVELOPER.get())
                .define('A', AMETHYST_SHARD)
                .define('C', COMPARATOR)
                .define('G', GOLD_INGOT)
                .define('I', IRON_INGOT)
                .define('R', REDSTONE)
                .pattern("GAG")
                .pattern("RCR")
                .pattern("III")
                .unlockedBy("has_comparator", has(COMPARATOR))
                .save(output);

        shaped(RecipeCategory.MISC, Items.WIND_GEN_FAN_ITEM.get())
                .define('C', COPPER_INGOT)
                .define('I', IRON_INGOT)
                .pattern(" I ")
                .pattern("ICI")
                .pattern(" I ")
                .unlockedBy("has_copper_ingot", has(COPPER_INGOT))
                .save(output);

        shaped(RecipeCategory.MISC, Items.OMNI_CRAFTING_TABLE.get())
                .define('C', CRAFTING_TABLE)
                .define('F', FURNACE)
                .define('I', IRON_INGOT)
                .define('R', REDSTONE)
                .define('S', STONECUTTER)
                .pattern("SCF")
                .pattern("IRI")
                .pattern("III")
                .unlockedBy("has_crafting_table", has(CRAFTING_TABLE))
                .save(output);

        shaped(RecipeCategory.REDSTONE, Items.CAT_ENGINE.get())
                .define('B', BREAD)
                .define('C', COOKED_COD)
                .define('I', IRON_INGOT)
                .define('R', REDSTONE)
                .pattern("B B")
                .pattern(" C ")
                .pattern("IRI")
                .unlockedBy("has_cooked_cod", has(COOKED_COD))
                .save(output);

        shaped(RecipeCategory.REDSTONE, Items.SOLAR_GEN.get())
                .define('C', COPPER_INGOT)
                .define('D', DAYLIGHT_DETECTOR)
                .define('G', GLASS_PANE)
                .define('I', IRON_INGOT)
                .define('R', REDSTONE)
                .pattern("GGG")
                .pattern("CDC")
                .pattern("IRI")
                .unlockedBy("has_daylight_detector", has(DAYLIGHT_DETECTOR))
                .save(output);
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
