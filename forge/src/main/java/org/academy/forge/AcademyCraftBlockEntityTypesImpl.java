package org.academy.forge;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.level.block.entity.forge.AbilityDeveloperBlockEntity;
import org.academy.internal.common.world.level.block.entity.forge.RadioFrequencyEnergyOutputBridgeBlockEntity;

import java.util.Map;

@SuppressWarnings("DataFlowIssue")
public class AcademyCraftBlockEntityTypesImpl {
    public static final BlockEntityType<AbilityDeveloperBlockEntity> ABILITY_DEVELOPER = BlockEntityType.Builder.of(AbilityDeveloperBlockEntity::new, AcademyCraftBlocksImpl.ABILITY_DEVELOPER_BLOCK).build(Util.fetchChoiceType(References.BLOCK_ENTITY, "ability_developer"));
    public static final BlockEntityType<RadioFrequencyEnergyOutputBridgeBlockEntity> RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE = BlockEntityType.Builder.of(RadioFrequencyEnergyOutputBridgeBlockEntity::new, AcademyCraftBlocksImpl.RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK).build(Util.fetchChoiceType(References.BLOCK_ENTITY, "radio_frequency_energy_output_bridge"));

    public static void init(Map<ResourceLocation, BlockEntityType<?>> blockEntityTypeMap) {
        blockEntityTypeMap.put(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "ability_developer"), ABILITY_DEVELOPER);
        blockEntityTypeMap.put(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "radio_frequency_energy_output_bridge"), RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE);
    }

    private AcademyCraftBlockEntityTypesImpl() {
    }
}