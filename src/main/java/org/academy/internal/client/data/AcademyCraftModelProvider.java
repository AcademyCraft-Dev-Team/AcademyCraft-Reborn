package org.academy.internal.client.data;

import java.util.List;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.*;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.properties.numeric.CrossbowPull;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.client.renderer.item.properties.select.Charge;
import net.minecraft.client.renderer.item.properties.select.DisplayContext;
import net.minecraft.client.renderer.special.TridentSpecialRenderer;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.CrossbowItem;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder;
import org.academy.AcademyCraft;
import org.academy.api.client.resources.model.cuboid.CoinModelGenerator;
import org.academy.internal.client.renderer.special.*;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.Blocks;

import static net.minecraft.client.data.models.model.TexturedModel.createDefault;
import static org.academy.AcademyCraft.academy;

public final class AcademyCraftModelProvider extends ModelProvider {
    public AcademyCraftModelProvider(PackOutput output) {
        super(output, AcademyCraft.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        var providerW = createDefault(
                _ ->
                        new TextureMapping().put(
                                TextureSlot.ALL, new Material(academy("break_w").withPrefix("block/"))
                        ), ModelTemplates.CUBE_ALL);
        var providerDB = createDefault(
                _ ->
                        new TextureMapping().put(
                                TextureSlot.ALL, new Material(academy("break_db").withPrefix("block/"))
                        ), ModelTemplates.CUBE_ALL);
        var providerBlack = createDefault(
                _ ->
                        new TextureMapping().put(
                                TextureSlot.ALL, new Material(academy("black").withPrefix("block/"))
                        ), ModelTemplates.CUBE_ALL);
        var darkmatterBlock = createDefault(
                _ -> new TextureMapping().put(
                        TextureSlot.ALL,
                        new Material(academy("darkmatter").withPrefix("block/"))),
                ModelTemplates.CUBE_ALL);
        var imagPhaseVegetation = createDefault(
                _ -> new TextureMapping()
                        .put(
                                TextureSlot.PARTICLE,
                                new Material(academy("imag_phase_vegetation_side").withPrefix("block/"))
                        )
                        .put(
                                TextureSlot.NORTH,
                                new Material(academy("imag_phase_vegetation_side").withPrefix("block/"))
                        )
                        .put(
                                TextureSlot.SOUTH,
                                new Material(academy("imag_phase_vegetation_side").withPrefix("block/"))
                        )
                        .put(
                                TextureSlot.EAST,
                                new Material(academy("imag_phase_vegetation_side").withPrefix("block/"))
                        )
                        .put(
                                TextureSlot.WEST,
                                new Material(academy("imag_phase_vegetation_side").withPrefix("block/"))
                        )
                        .put(
                                TextureSlot.UP,
                                new Material(academy("imag_phase_vegetation_top").withPrefix("block/"))
                        )
                        .put(
                                TextureSlot.DOWN,
                                new Material(AcademyCraft.vanilla("deepslate_top").withPrefix("block/"))
                        ),
                ModelTemplates.CUBE
        );
        var imagPhaseLog = createDefault(
                _ -> new TextureMapping()
                        .put(
                                TextureSlot.END,
                                new Material(academy("imag_phase_log_top").withPrefix("block/"))
                        )
                        .put(
                                TextureSlot.SIDE,
                                new Material(academy("imag_phase_log").withPrefix("block/"))
                        ),
                ModelTemplates.CUBE_COLUMN
        );
        var imagPhaseLogHorizontal = createDefault(
                _ -> new TextureMapping()
                        .put(
                                TextureSlot.END,
                                new Material(academy("imag_phase_log_top").withPrefix("block/"))
                        )
                        .put(
                                TextureSlot.SIDE,
                                new Material(academy("imag_phase_log").withPrefix("block/"))
                        ),
                ModelTemplates.CUBE_COLUMN_HORIZONTAL
        );


        blockModels.createTrivialBlock(Blocks.WIRELESS_NODE.get(), providerW);
        blockModels.createTrivialBlock(Blocks.WIND_GEN_BASE.get(), providerDB);
        blockModels.createTrivialBlock(Blocks.WIND_GEN_TOP.get(), providerW);
        blockModels.createTrivialBlock(Blocks.WIND_GEN_PILLAR.get(), providerW);
        blockModels.createTrivialBlock(Blocks.OMNI_CRAFTING_TABLE.get(), providerDB);
        blockModels.createTrivialBlock(Blocks.SOLAR_GEN.get(), providerDB);
        blockModels.createTrivialBlock(Blocks.ABILITY_DEVELOPER.get(), providerDB);
        blockModels.createTrivialBlock(Blocks.IMAG_PHASE.get(), providerBlack);
        var configurableDarkmatterBlock = Blocks.DARKMATTER_BLOCK.get();
        blockModels.createTrivialBlock(configurableDarkmatterBlock, darkmatterBlock);
        blockModels.registerSimpleItemModel(configurableDarkmatterBlock,
                ModelLocationUtils.getModelLocation(configurableDarkmatterBlock));
        blockModels.createTrivialBlock(Blocks.COMPRESSED_AIR_PLATFORM.get(), providerW);

        var vegetationBlock = Blocks.IMAG_PHASE_VEGETATION.get();
        blockModels.createTrivialBlock(vegetationBlock, imagPhaseVegetation);
        blockModels.registerSimpleItemModel(vegetationBlock, ModelLocationUtils.getModelLocation(vegetationBlock));

        var leavesBlock = Blocks.IMAG_PHASE_LEAVES.get();
        blockModels.createTrivialBlock(leavesBlock, TexturedModel.LEAVES);
        blockModels.registerSimpleItemModel(leavesBlock, ModelLocationUtils.getModelLocation(leavesBlock));

        var logBlock = Blocks.IMAG_PHASE_LOG.get();
        blockModels.createRotatedPillarWithHorizontalVariant(
                logBlock,
                imagPhaseLog,
                imagPhaseLogHorizontal
        );
        blockModels.registerSimpleItemModel(logBlock, ModelLocationUtils.getModelLocation(logBlock));

        var lichenBlock = Blocks.IMAG_PHASE_LICHEN.get();
        blockModels.createMultifaceBlockStates(lichenBlock);
        blockModels.registerSimpleFlatItemModel(lichenBlock);

        blockModels.createTrivialBlock(Blocks.CAT_ENGINE.get(), createDefault(
                _ ->
                        new TextureMapping().put(
                                TextureSlot.ALL, new Material(academy("cat_engine").withPrefix("item/"))
                        ), ModelTemplates.CUBE_ALL)
        );

        itemModels.generateFlatItem(Items.ICON.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.CAT_ENGINE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.DATA_TERMINAL.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.TUTORIAL.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.DARKMATTER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.itemModelOutput.accept(Items.DARKMATTER_COATING.get(),
                ItemModelUtils.plainModel(ModelTemplates.FLAT_ITEM.create(
                        ModelLocationUtils.getModelLocation(Items.DARKMATTER_COATING.get()),
                        TextureMapping.layer0(Items.DARKMATTER.get()),
                        itemModels.modelOutput)));
        itemModels.generateFlatItem(Items.DARKMATTER_TOOL.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(Items.DARKMATTER_SWORD.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        generateDarkmatterSpear(itemModels);
        generateDarkmatterTrident(itemModels);
        generateDarkmatterBow(itemModels);
        generateDarkmatterCrossbow(itemModels);
        itemModels.generateFlatItem(Items.DARKMATTER_MACE.get(), ModelTemplates.FLAT_HANDHELD_MACE_ITEM);
        itemModels.generateFlatItem(Items.DARKMATTER_ARROW.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(Items.DARKMATTER_FEATHER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.IMAG_PHASE_INGOT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.IMAG_PHASE_CRYSTAL.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.IMAG_PHASE_POLYMER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.IMAG_PHASE_PLATE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.IMAG_PHASE_CIRCUIT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.NEEDLE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.DARK_MATTER_HELMET.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.DARK_MATTER_CHESTPLATE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.DARK_MATTER_LEGGINGS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.DARK_MATTER_BOOTS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.EMPTY_UNIT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.IMAG_PHASE_UNIT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.itemModelOutput.accept(Items.PAPER_AIRPLANE.get(), ItemModelUtils.plainModel(
                ModelTemplates.FLAT_ITEM.create(
                        ModelLocationUtils.getModelLocation(Items.PAPER_AIRPLANE.get()),
                        new TextureMapping().put(
                                TextureSlot.LAYER0,
                                new Material(AcademyCraft.vanilla("paper").withPrefix("item/"))
                        ),
                        itemModels.modelOutput
                )
        ));
        itemModels.generateFlatItem(Items.ABILITY_DEVELOPER.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(Items.WIND_GEN_BASE.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(Items.WIND_GEN_TOP.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(Items.WIND_GEN_PILLAR.get(), ModelTemplates.FLAT_ITEM);

        {
            var screenItem = Items.WIND_GEN_BASE_SCREEN.get();
            itemModels.itemModelOutput.accept(screenItem, ItemModelUtils.plainModel(
                    ModelTemplates.FLAT_ITEM.create(
                            ModelLocationUtils.getModelLocation(screenItem),
                            new TextureMapping().put(
                                    TextureSlot.LAYER0,
                                    new Material(academy("screen").withPrefix("item/"))
                            ),
                            itemModels.modelOutput
                    )
            ));
        }

        {
            var coinItem = Items.COIN.get();
            var modelTemplate =
                    ExtendedModelTemplateBuilder
                            .builder()
                            .parent(CoinModelGenerator.COIN_ITEM_MODEL_ID)
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
                                transformVecBuilder.scale(0.5f);
                            })
                            .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, transformVecBuilder -> {
                                transformVecBuilder.translation(0, 2, 1);
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
                                    .put(TextureSlot.BACK, TextureMapping.getItemTexture(coinItem, "_back")),
                            itemModels.modelOutput)
            ));
        }

        itemModels.generateFlatItem(Items.WIND_GEN_FAN_ITEM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.itemModelOutput.accept(
                Items.WIRELESS_NODE.get(),
                ItemModelUtils.specialModel(
                        AcademyCraft.vanilla("block").withPrefix("block/"),
                        WirelessNodeSpecialRenderer.Unbaked.INSTANCE
                )
        );
        itemModels.itemModelOutput.accept(
                Items.ABILITY_CONTROL_TABLET.get(),
                ItemModelUtils.specialModel(
                        academy("ability_control_tablet").withPrefix("item/"),
                        AbilityControlTabletSpecialRenderer.Unbaked.INSTANCE
                )
        );
        itemModels.itemModelOutput.accept(
                Items.IMAG_PHASE_DOWSING_ROD.get(),
                ItemModelUtils.specialModel(
                        academy("imag_phase_dowsing_rod").withPrefix("item/"),
                        ImagPhaseDowsingRodSpecialRenderer.Unbaked.INSTANCE
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
                Items.SOLAR_GEN.get(),
                ItemModelUtils.specialModel(
                        AcademyCraft.vanilla("block").withPrefix("block/"),
                        SolarGenSpecialRenderer.Unbaked.INSTANCE
                )
        );
    }

    private static void generateDarkmatterSpear(ItemModelGenerators itemModels) {
        var item = Items.DARKMATTER_SPEAR.get();
        var flatLocation = ModelTemplates.FLAT_ITEM.create(
                ModelLocationUtils.getModelLocation(item),
                TextureMapping.layer0(item),
                itemModels.modelOutput);
        var inHandLocation = ModelTemplates.SPEAR_IN_HAND.create(
                item,
                TextureMapping.layer0(TextureMapping.getItemTexture(item, "_in_hand")),
                itemModels.modelOutput);
        var flat = ItemModelUtils.plainModel(flatLocation);
        var inHand = ItemModelUtils.plainModel(inHandLocation);
        itemModels.itemModelOutput.accept(
                item,
                ItemModelUtils.select(
                        new DisplayContext(),
                        inHand,
                        ItemModelUtils.when(List.of(
                                ItemDisplayContext.GUI,
                                ItemDisplayContext.GROUND,
                                ItemDisplayContext.FIXED,
                                ItemDisplayContext.ON_SHELF), flat)),
                new ClientItem.Properties(true, false, 1.95f));
    }

    private static void generateDarkmatterBow(ItemModelGenerators itemModels) {
        var item = Items.DARKMATTER_BOW.get();
        var base = ItemModelUtils.plainModel(ModelTemplates.BOW.create(item,
                TextureMapping.layer0(item), itemModels.modelOutput));
        var pulling0 = darkmatterStateModel(itemModels, item, "_pulling_0", ModelTemplates.BOW);
        var pulling1 = darkmatterStateModel(itemModels, item, "_pulling_1", ModelTemplates.BOW);
        var pulling2 = darkmatterStateModel(itemModels, item, "_pulling_2", ModelTemplates.BOW);
        var hand = ItemModelUtils.conditional(
                ItemModelUtils.isUsingItem(),
                ItemModelUtils.rangeSelect(new UseDuration(false), 0.05f, pulling0,
                        ItemModelUtils.override(pulling1, 0.65f),
                        ItemModelUtils.override(pulling2, 0.9f)),
                base);
        itemModels.itemModelOutput.accept(item, stableInventoryModel(base, hand));
    }

    private static void generateDarkmatterCrossbow(ItemModelGenerators itemModels) {
        var item = Items.DARKMATTER_CROSSBOW.get();
        var base = ItemModelUtils.plainModel(ModelTemplates.CROSSBOW.create(item,
                TextureMapping.layer0(item), itemModels.modelOutput));
        var pulling0 = darkmatterStateModel(itemModels, item, "_pulling_0", ModelTemplates.CROSSBOW);
        var pulling1 = darkmatterStateModel(itemModels, item, "_pulling_1", ModelTemplates.CROSSBOW);
        var pulling2 = darkmatterStateModel(itemModels, item, "_pulling_2", ModelTemplates.CROSSBOW);
        var loadedArrow = darkmatterStateModel(itemModels, item, "_arrow", ModelTemplates.CROSSBOW);
        var loadedFirework = darkmatterStateModel(itemModels, item, "_firework", ModelTemplates.CROSSBOW);
        var charging = ItemModelUtils.conditional(
                ItemModelUtils.isUsingItem(),
                ItemModelUtils.rangeSelect(new CrossbowPull(), pulling0,
                        ItemModelUtils.override(pulling1, 0.58f),
                        ItemModelUtils.override(pulling2, 1.0f)),
                base);
        var hand = ItemModelUtils.select(new Charge(), charging,
                ItemModelUtils.when(CrossbowItem.ChargeType.ARROW, loadedArrow),
                ItemModelUtils.when(CrossbowItem.ChargeType.ROCKET, loadedFirework));
        itemModels.itemModelOutput.accept(item, stableInventoryModel(base, hand));
    }

    private static ItemModel.Unbaked darkmatterStateModel(
            ItemModelGenerators itemModels, net.minecraft.world.item.Item item,
            String suffix, ModelTemplate template
    ) {
        var location = template.create(ModelLocationUtils.getModelLocation(item, suffix),
                TextureMapping.layer0(TextureMapping.getItemTexture(item, suffix)),
                itemModels.modelOutput);
        return ItemModelUtils.plainModel(location);
    }

    private static ItemModel.Unbaked stableInventoryModel(
            ItemModel.Unbaked inventory, ItemModel.Unbaked hand
    ) {
        return ItemModelUtils.select(new DisplayContext(), hand,
                ItemModelUtils.when(List.of(
                        ItemDisplayContext.GUI,
                        ItemDisplayContext.GROUND,
                        ItemDisplayContext.FIXED,
                        ItemDisplayContext.ON_SHELF), inventory));
    }

    private static void generateDarkmatterTrident(ItemModelGenerators itemModels) {
        var item = Items.DARKMATTER_TRIDENT.get();
        var flatLocation = ModelTemplates.FLAT_ITEM.create(
                ModelLocationUtils.getModelLocation(item),
                TextureMapping.layer0(item), itemModels.modelOutput);
        var flat = ItemModelUtils.plainModel(flatLocation);
        var inHand = ItemModelUtils.specialModel(
                AcademyCraft.vanilla("trident_in_hand").withPrefix("item/"),
                DarkmatterTridentSpecialRenderer.Unbaked.INSTANCE);
        var throwing = ItemModelUtils.specialModel(
                AcademyCraft.vanilla("trident_throwing").withPrefix("item/"),
                DarkmatterTridentSpecialRenderer.Unbaked.INSTANCE);
        var held = ItemModelUtils.conditional(
                TridentSpecialRenderer.DEFAULT_TRANSFORMATION,
                ItemModelUtils.isUsingItem(), throwing, inHand);
        itemModels.itemModelOutput.accept(item, ItemModelUtils.select(
                new DisplayContext(), held,
                ItemModelUtils.when(List.of(
                        ItemDisplayContext.GUI,
                        ItemDisplayContext.GROUND,
                        ItemDisplayContext.FIXED,
                        ItemDisplayContext.ON_SHELF), flat)));
    }
}
