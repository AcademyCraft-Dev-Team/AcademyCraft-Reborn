package org.academy.forge.internal.common.world.level.block.entity.forge;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.academy.AcademyCraft;
import org.academy.forge.internal.common.world.level.block.forge.AcademyCraftBlocksForge;
import org.academy.internal.common.world.level.block.entity.AcademyCraftBlockEntityTypes;

@SuppressWarnings("DataFlowIssue")
public class AcademyCraftBlockEntityTypesForge {
    public static final BlockEntityType<AbilityDeveloperBlockEntityForge> ABILITY_DEVELOPER = BlockEntityType.Builder.of(AbilityDeveloperBlockEntityForge::new, AcademyCraftBlocksForge.ABILITY_DEVELOPER_BLOCK).build(Util.fetchChoiceType(References.BLOCK_ENTITY, "ability_developer"));
    public static final BlockEntityType<RadioFrequencyEnergyOutputBridgeBlockEntity> RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE = BlockEntityType.Builder.of(RadioFrequencyEnergyOutputBridgeBlockEntity::new, AcademyCraftBlocksForge.RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE_BLOCK).build(Util.fetchChoiceType(References.BLOCK_ENTITY, "radio_frequency_energy_output_bridge"));

    public static void init() {
        AcademyCraftBlockEntityTypes.BLOCK_ENTITY_TYPES.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer"), ABILITY_DEVELOPER);
        AcademyCraftBlockEntityTypes.BLOCK_ENTITY_TYPES.put(new ResourceLocation(AcademyCraft.MOD_ID, "radio_frequency_energy_output_bridge"), RADIO_FREQUENCY_ENERGY_OUTPUT_BRIDGE);
    }

    private AcademyCraftBlockEntityTypesForge() {
    }
}