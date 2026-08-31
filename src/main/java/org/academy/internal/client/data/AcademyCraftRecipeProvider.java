package org.academy.internal.client.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import org.academy.internal.common.world.item.Items;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.world.item.Items.*;

public final class AcademyCraftRecipeProvider extends RecipeProvider {
    private AcademyCraftRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        shaped(RecipeCategory.MISC, Items.ICON.get())
                .define('A', AMETHYST_SHARD)
                .define('P', PAPER)
                .define('R', REDSTONE)
                .pattern(" A ")
                .pattern("APA")
                .pattern(" R ")
                .unlockedBy("has_amethyst_shard", has(AMETHYST_SHARD))
                .save(output);

        shaped(RecipeCategory.COMBAT, Items.DARK_MATTER_HELMET.get())
                .define('D', Items.DARKMATTER.get())
                .define('N', NETHERITE_HELMET)
                .pattern("DDD")
                .pattern("DND")
                .unlockedBy("has_netherite_helmet", has(NETHERITE_HELMET))
                .save(output);

        shaped(RecipeCategory.COMBAT, Items.DARK_MATTER_CHESTPLATE.get())
                .define('D', Items.DARKMATTER.get())
                .define('N', NETHERITE_CHESTPLATE)
                .pattern("DND")
                .pattern("DDD")
                .pattern("DDD")
                .unlockedBy("has_netherite_chestplate", has(NETHERITE_CHESTPLATE))
                .save(output);

        shaped(RecipeCategory.COMBAT, Items.DARK_MATTER_LEGGINGS.get())
                .define('D', Items.DARKMATTER.get())
                .define('N', NETHERITE_LEGGINGS)
                .pattern("DDD")
                .pattern("DND")
                .pattern("D D")
                .unlockedBy("has_netherite_leggings", has(NETHERITE_LEGGINGS))
                .save(output);

        shaped(RecipeCategory.COMBAT, Items.DARK_MATTER_BOOTS.get())
                .define('D', Items.DARKMATTER.get())
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

        shaped(RecipeCategory.MISC, Items.TUTORIAL.get())
                .define('A', AMETHYST_SHARD)
                .define('B', BOOK)
                .define('I', IRON_INGOT)
                .define('R', REDSTONE)
                .pattern(" A ")
                .pattern("RBR")
                .pattern(" I ")
                .unlockedBy("has_book", has(BOOK))
                .save(output);

        shaped(RecipeCategory.MISC, Items.IMAG_PHASE_DOWSING_ROD.get())
                .define('Q', COMPARATOR)
                .define('L', LIGHTNING_ROD.weathering().unaffected())
                .define('C', COMPASS)
                .define('I', IRON_INGOT)
                .pattern(" Q ")
                .pattern("LCI")
                .pattern("  I")
                .unlockedBy("has_compass", has(COMPASS))
                .save(output);

        shaped(RecipeCategory.MISC, Items.COIN.get())
                .define('N', IRON_NUGGET)
                .pattern("NN")
                .pattern("NN")
                .unlockedBy("has_iron_nugget", has(IRON_NUGGET))
                .save(output);

        shapeless(RecipeCategory.MISC, Items.PAPER_AIRPLANE.get())
                .requires(PAPER)
                .unlockedBy("has_paper", has(PAPER))
                .save(output);

        shaped(RecipeCategory.TOOLS, Items.MAGNETIC_HOOK.get())
                .define('I', IRON_INGOT)
                .pattern(" I ")
                .pattern("III")
                .pattern(" I ")
                .unlockedBy("has_iron_ingot", has(IRON_INGOT))
                .save(output);

        shaped(RecipeCategory.REDSTONE, Items.WIRELESS_NODE.get())
                .define('E', ENDER_PEARL)
                .define('I', Items.IMAG_PHASE_INGOT.get())
                .define('C', Items.IMAG_PHASE_CRYSTAL.get())
                .define('R', Items.IMAG_PHASE_CIRCUIT.get())
                .pattern("IEI")
                .pattern("ECE")
                .pattern("IRI")
                .unlockedBy("has_ender_pearl", has(ENDER_PEARL))
                .save(output);

        shaped(RecipeCategory.REDSTONE, Items.WIND_GEN_BASE.get())
                .define('S', Items.WIND_GEN_BASE_SCREEN.get())
                .define('P', Items.IMAG_PHASE_PLATE.get())
                .define('C', Items.IMAG_PHASE_CIRCUIT.get())
                .define('I', Items.IMAG_PHASE_INGOT.get())
                .pattern(" S ")
                .pattern("PCP")
                .pattern("III")
                .unlockedBy("has_academy_display", has(Items.WIND_GEN_BASE_SCREEN.get()))
                .save(output);

        shaped(RecipeCategory.REDSTONE, Items.WIND_GEN_TOP.get())
                .define('P', Items.IMAG_PHASE_PLATE.get())
                .define('I', Items.IMAG_PHASE_INGOT.get())
                .define('C', Items.IMAG_PHASE_CIRCUIT.get())
                .pattern("PP ")
                .pattern("ICP")
                .pattern("PP ")
                .unlockedBy("has_imag_phase_circuit", has(Items.IMAG_PHASE_CIRCUIT.get()))
                .save(output);

        shaped(RecipeCategory.MISC, Items.WIND_GEN_PILLAR.get(), 2)
                .define('P', Items.IMAG_PHASE_PLATE.get())
                .define('I', Items.IMAG_PHASE_INGOT.get())
                .pattern("PIP")
                .pattern("PIP")
                .pattern("PIP")
                .unlockedBy("has_imag_phase_plate", has(Items.IMAG_PHASE_PLATE.get()))
                .save(output);

        shaped(RecipeCategory.MISC, Items.WIND_GEN_FAN_ITEM.get())
                .define('P', Items.IMAG_PHASE_PLATE.get())
                .define('I', Items.IMAG_PHASE_INGOT.get())
                .pattern(" P ")
                .pattern(" I ")
                .pattern("P P")
                .unlockedBy("has_imag_phase_plate", has(Items.IMAG_PHASE_PLATE.get()))
                .save(output);

        shaped(RecipeCategory.MISC, Items.OMNI_CRAFTING_TABLE.get())
                .define('I', Items.IMAG_PHASE_INGOT.get())
                .define('P', Items.IMAG_PHASE_PLATE.get())
                .define('S', Items.WIND_GEN_BASE_SCREEN.get())
                .define('R', Items.IMAG_PHASE_CIRCUIT.get())
                .define('U', Items.EMPTY_UNIT.get())
                .define('O', OBSERVER)
                .define('C', CRAFTING_TABLE)
                .define('F', FURNACE)
                .pattern("UPS")
                .pattern("OCF")
                .pattern("IRI")
                .unlockedBy("has_imag_phase_circuit", has(Items.IMAG_PHASE_CIRCUIT.get()))
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
                .define('G', STAINED_GLASS_PANE.gray())
                .define('I', Items.IMAG_PHASE_INGOT.get())
                .define('D', DAYLIGHT_DETECTOR)
                .define('P', Items.IMAG_PHASE_POLYMER.get())
                .define('R', REDSTONE)
                .pattern("GGG")
                .pattern("IDI")
                .pattern("PRP")
                .unlockedBy("has_daylight_detector", has(DAYLIGHT_DETECTOR))
                .save(output);

        shaped(RecipeCategory.MISC, Items.IMAG_PHASE_PLATE.get())
                .define('P', Items.IMAG_PHASE_POLYMER.get())
                .pattern("PP")
                .pattern("PP")
                .unlockedBy("has_imag_phase_polymer", has(Items.IMAG_PHASE_POLYMER.get()))
                .save(output);

        shaped(RecipeCategory.MISC, Items.WIND_GEN_BASE_SCREEN.get())
                .define('G', STAINED_GLASS_PANE.gray())
                .define('I', Items.IMAG_PHASE_INGOT.get())
                .define('P', Items.IMAG_PHASE_POLYMER.get())
                .define('R', REDSTONE)
                .pattern("GGG")
                .pattern("III")
                .pattern("PRP")
                .unlockedBy("has_imag_phase_ingot", has(Items.IMAG_PHASE_INGOT.get()))
                .save(output);

        shaped(RecipeCategory.REDSTONE, Items.IMAG_PHASE_CIRCUIT.get())
                .define('G', GLOWSTONE_DUST)
                .define('R', REDSTONE)
                .define('P', Items.IMAG_PHASE_PLATE.get())
                .pattern("GRG")
                .pattern("PPP")
                .unlockedBy("has_imag_phase_plate", has(Items.IMAG_PHASE_PLATE.get()))
                .save(output);

        shaped(RecipeCategory.MISC, Items.ABILITY_CONTROL_TABLET.get())
                .define('P', Items.IMAG_PHASE_PLATE.get())
                .define('S', Items.WIND_GEN_BASE_SCREEN.get())
                .define('C', Items.IMAG_PHASE_CIRCUIT.get())
                .define('I', Items.IMAG_PHASE_INGOT.get())
                .define('Q', COMPARATOR)
                .pattern("PQS")
                .pattern("PCQ")
                .pattern("IPP")
                .unlockedBy("has_academy_display", has(Items.WIND_GEN_BASE_SCREEN.get()))
                .save(output);

        shaped(RecipeCategory.COMBAT, Items.NEEDLE.get())
                .define('N', IRON_NUGGET)
                .pattern("N")
                .pattern("N")
                .pattern("N")
                .unlockedBy("has_iron_nugget", has(IRON_NUGGET))
                .save(output);

        shaped(RecipeCategory.MISC, Items.EMPTY_UNIT.get())
                .define('I', IRON_INGOT)
                .define('G', GLASS)
                .pattern(" I ")
                .pattern("IGI")
                .pattern(" I ")
                .unlockedBy("has_iron_ingot", has(IRON_INGOT))
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
