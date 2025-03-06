package org.academy.internal.common.world.level.block.entity;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.level.block.AcademyCraftBlocks;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("DataFlowIssue")
public class AcademyCraftBlockEntityTypes {
    public static final Map<ResourceLocation, BlockEntityType<?>> BLOCK_ENTITY_TYPES = new HashMap<>();
    public static final BlockEntityType<AbilityDeveloperBlockEntity> ABILITY_DEVELOPER = BlockEntityType.Builder.of(AbilityDeveloperBlockEntity::new, AcademyCraftBlocks.ABILITY_DEVELOPER_BLOCK).build(Util.fetchChoiceType(References.BLOCK_ENTITY, "ability_developer"));

    static {
        BLOCK_ENTITY_TYPES.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer"), ABILITY_DEVELOPER);
    }

    private AcademyCraftBlockEntityTypes() {
    }
}
