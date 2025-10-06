package org.academy.internal.client.data;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.*;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder;
import org.academy.AcademyCraft;
import org.academy.internal.client.renderer.special.*;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.minecraft.client.data.models.model.TexturedModel.createDefault;
import static org.academy.AcademyCraft.academy;
import static org.academy.api.client.Resource.Models.COIN_ITEM_MODEL_ID;

public class AcademyCraftModelProvider extends ModelProvider {
    public AcademyCraftModelProvider(PackOutput output) {
        super(output, AcademyCraft.MOD_ID);
    }

    @Override
    protected void registerModels(@NotNull BlockModelGenerators blockModels, @NotNull ItemModelGenerators itemModels) {
        var providerW = createDefault(
                block ->
                        new TextureMapping().put(
                                TextureSlot.ALL, academy("break_w").withPrefix("block/")
                        ), ModelTemplates.CUBE_ALL);
        var providerDB = createDefault(
                block ->
                        new TextureMapping().put(
                                TextureSlot.ALL, academy("break_db").withPrefix("block/")
                        ), ModelTemplates.CUBE_ALL);


        blockModels.createTrivialBlock(Blocks.WIRELESS_NODE.get(), providerW);
        blockModels.createTrivialBlock(Blocks.WIND_GEN_BASE.get(), providerW);
        blockModels.createTrivialBlock(Blocks.WIND_GEN_TOP.get(), providerW);
        blockModels.createTrivialBlock(Blocks.WIND_GEN_PILLAR.get(), providerW);
        blockModels.createTrivialBlock(Blocks.OMNI_CRAFTING_TABLE.get(), providerDB);

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(
                        Blocks.ABILITY_DEVELOPER.get(),
                        BlockModelGenerators.createRotatedVariants(
                                BlockModelGenerators.plainModel(
                                        providerDB.create(Blocks.ABILITY_DEVELOPER.get(), blockModels.modelOutput)
                                )
                        )
                )
        );
        blockModels.createTrivialCube(Blocks.IMAGIPHASE_PLASMA.get());
        blockModels.createTrivialBlock(Blocks.IMAGIPHASE_VEGETATION.get(), TexturedModel.COLUMN);
        blockModels.createMultiface(Blocks.IMAGIPHASE_LICHEN.get());
        blockModels.createTrivialBlock(Blocks.CAT_ENGINE.get(), createDefault(
                block ->
                        new TextureMapping().put(
                                TextureSlot.ALL, academy("cat_engine").withPrefix("item/")
                        ), ModelTemplates.CUBE_ALL)
        );
        blockModels.createTrivialCube(Blocks.IMAGIPHASE_AMETHYST_BLOCK.get());
        blockModels.blockStateOutput
                .accept(
                        MultiVariantGenerator.dispatch(
                                Blocks.IMAGIPHASE_METAL_BLOCK.get(),
                                plainVariant(ModelLocationUtils.getModelLocation(Blocks.IMAGIPHASE_METAL_BLOCK.get()))
                        )
                );
        blockModels.createAxisAlignedPillarBlock(Blocks.IMAGIPHASE_LOG.get(), TexturedModel.COLUMN);

        blockModels.createTrivialBlock(Blocks.IMAGIPHASE_LEAVES.get(), TexturedModel.LEAVES);

        itemModels.generateFlatItem(Items.ICON.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.CAT_ENGINE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.DATA_TERMINAL.get(), ModelTemplates.FLAT_ITEM);

        {
            var coinItem = Items.COIN.get();
            var modelTemplate =
                    ExtendedModelTemplateBuilder
                            .builder()
                            .parent(COIN_ITEM_MODEL_ID)
                            .requiredTextureSlot(TextureSlot.BACK)
                            .requiredTextureSlot(TextureSlot.FRONT)
                            .guiLight(UnbakedModel.GuiLight.FRONT)
                            .transform(ItemDisplayContext.GROUND, transformVecBuilder -> {
                                transformVecBuilder.translation(0, 2, 0);
                                transformVecBuilder.scale(0.25f);
                            })
                            .transform(ItemDisplayContext.HEAD, transformVecBuilder -> {
                                transformVecBuilder.rotation(0, 180, 0);
                                transformVecBuilder.translation(0, 13, 7);
                            })
                            .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, transformVecBuilder -> {
                                transformVecBuilder.translation(0, 3, 0);
                                transformVecBuilder.scale(0.275f);
                            })
                            .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, transformVecBuilder -> {
                                transformVecBuilder.rotation(0, -90, 25);
                                transformVecBuilder.translation(1.13f, 3.2f, 1.13f);
                                transformVecBuilder.scale(0.34f);
                            })
                            .transform(ItemDisplayContext.FIXED, transformVecBuilder -> {
                                transformVecBuilder.rotation(0, 180, 0);
                                transformVecBuilder.scale(0.5f);
                            })
                            .build();
            itemModels.itemModelOutput.accept(coinItem, ItemModelUtils.plainModel(
                    modelTemplate.create(
                            ModelLocationUtils.getModelLocation(coinItem),
                            new TextureMapping()
                                    .put(TextureSlot.FRONT, TextureMapping.getItemTexture(coinItem))
                                    .put(TextureSlot.BACK, TextureMapping.getItemTexture(coinItem).withSuffix("_back")),
                            itemModels.modelOutput)
            ));
        }

        itemModels.generateFlatItem(Items.WIND_GEN_FAN_ITEM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.IMAGIPHASE_METAL.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.EMPTY_UNIT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.IMAGIPHASE_UNIT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.WIND_GEN_BASE_SCREEN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.IMAGIPHASE_CIRCUIT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.IMAGIPHASE_POLYMER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.IMAGIPHASE_PLATE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.IMAGIPHASE_AMETHYST.get(), ModelTemplates.FLAT_ITEM);
        itemModels.itemModelOutput.accept(
                Items.WIRELESS_NODE.get(),
                ItemModelUtils.specialModel(
                        AcademyCraft.vanilla("block").withPrefix("block/"),
                        WirelessNodeSpecialRenderer.Unbaked.INSTANCE
                )
        );
        itemModels.itemModelOutput.accept(
                Items.IMAGIPHASE_DOWSING_ROD.get(),
                ItemModelUtils.specialModel(
                        AcademyCraft.vanilla("block").withPrefix("block/"),
                        ImagiphaseDowsingRodSpecialRenderer.Unbaked.INSTANCE
                )
        );
        itemModels.itemModelOutput.accept(
                Items.ABILITY_DEVELOPER.get(),
                ItemModelUtils.specialModel(
                        AcademyCraft.vanilla("block").withPrefix("block/"),
                        AbilityDeveloperSpecialRenderer.Unbaked.INSTANCE
                )
        );
        itemModels.itemModelOutput.accept(
                Items.OMNI_CRAFTING_TABLE.get(),
                ItemModelUtils.specialModel(
                        AcademyCraft.vanilla("block").withPrefix("block/"),
                        OmniCraftingTableSpecialRenderer.Unbaked.INSTANCE
                )
        );
        itemModels.itemModelOutput.accept(
                Items.WIND_GEN_BASE.get(),
                ItemModelUtils.specialModel(
                        AcademyCraft.vanilla("block").withPrefix("block/"),
                        WindGenBaseSpecialRenderer.Unbaked.INSTANCE
                )
        );
        itemModels.itemModelOutput.accept(
                Items.WIND_GEN_PILLAR.get(),
                ItemModelUtils.specialModel(
                        AcademyCraft.vanilla("block").withPrefix("block/"),
                        WindGenPillarSpecialRenderer.Unbaked.INSTANCE
                )
        );
        itemModels.itemModelOutput.accept(
                Items.WIND_GEN_TOP.get(),
                ItemModelUtils.specialModel(
                        AcademyCraft.vanilla("block").withPrefix("block/"),
                        WindGenTopSpecialRenderer.Unbaked.INSTANCE
                )
        );
    }
}