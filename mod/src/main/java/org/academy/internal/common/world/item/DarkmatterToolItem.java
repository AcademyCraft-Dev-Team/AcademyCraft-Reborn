package org.academy.internal.common.world.item;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;

import static net.minecraft.core.registries.Registries.BLOCK;

public final class DarkmatterToolItem extends DarkmatterEquipmentItem {
    public static final TagKey<Block> EFFECTIVE_BLOCKS = TagKey.create(
            BLOCK, AcademyCraft.academy("mineable/darkmatter_tool"));

    public DarkmatterToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public DarkmatterShape darkmatterShape() {
        return DarkmatterShape.TOOL;
    }


}
