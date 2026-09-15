package org.academy.api.common.ability.electromaster;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.electromaster.MagneticallyManipulable;
import java.util.List;
import java.util.Locale;

/** Shared magnetic material and entity admission. */
public final class MagneticTargets {
    private static final TagKey<Block> MAGNETIC_BLOCKS = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(AcademyCraft.MOD_ID, "magnetic_blocks")
    );
    private static final TagKey<Item> MAGNETIC_ITEMS = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(AcademyCraft.MOD_ID, "magnetic_items")
    );
    private static final TagKey<EntityType<?>> MAGNETIC_ENTITY_TYPES = TagKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(AcademyCraft.MOD_ID, "magnetic_entities")
    );
    private static final EquipmentSlot[] CHECKED_EQUIPMENT_SLOTS = {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.BODY
    };
    private static final List<String> MAGNETIC_KEYWORDS = List.of(
            "iron", "steel", "ferrous", "ferric", "magnetic", "magnetite", "hematite"
    );

    private MagneticTargets() {}

    public static boolean isIronRelatedPath(String path) {
        return hasMagneticKeyword(path);
    }

    public static boolean hasMagneticKeyword(String path) {
        if (path == null || path.isBlank()) return false;
        var normalized = path.toLowerCase(Locale.ROOT).replace('-', '_');
        var tokens = normalized.split("[/_.]");
        for (var token : tokens) {
            if (MAGNETIC_KEYWORDS.contains(token)) return true;
        }
        return false;
    }

    public static boolean isMagneticTagPath(String path) {
        if (path == null) return false;
        var normalized = path.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("incorrect_for_")
                || normalized.startsWith("needs_")
                || normalized.startsWith("mineable/")
                || normalized.startsWith("enchantable/")) return false;
        return hasMagneticKeyword(normalized);
    }

    public static boolean isMagnetic(BlockState state) {
        var block = state.getBlock();
        var blockPath = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (blockPath.equals("obsidian") || blockPath.equals("crying_obsidian")) return false;
        var sound = state.getSoundType();
        if (state.is(MAGNETIC_BLOCKS)
                || sound == SoundType.IRON
                || sound == SoundType.ANVIL
                || sound == SoundType.CHAIN) {
            return true;
        }
        if (hasMagneticKeyword(blockPath)) return true;
        return block.builtInRegistryHolder().tags()
                .anyMatch(tag -> isMagneticTagPath(tag.location().getPath()));
    }

    public static boolean isMagnetic(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(MAGNETIC_ITEMS)) return true;
        var item = stack.getItem();
        if (hasMagneticKeyword(BuiltInRegistries.ITEM.getKey(item).getPath())) return true;
        return item.builtInRegistryHolder().tags()
                .anyMatch(tag -> isMagneticTagPath(tag.location().getPath()));
    }

    public static boolean isMagnetic(Entity entity) {
        if (entity instanceof MagneticallyManipulable magneticTarget
                && magneticTarget.canBeMagneticallyManipulated()) return true;
        if (entity instanceof FallingBlockEntity fallingBlock && isMagnetic(fallingBlock.getBlockState())) return true;
        if (entity instanceof ItemEntity itemEntity && isMagnetic(itemEntity.getItem())) return true;

        var type = entity.getType();
        if (type.builtInRegistryHolder().is(MAGNETIC_ENTITY_TYPES)
                || hasMagneticKeyword(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath())
                || type.builtInRegistryHolder().tags()
                .anyMatch(tag -> isMagneticTagPath(tag.location().getPath()))) {
            return true;
        }
        if (!(entity instanceof LivingEntity living)) return false;
        for (var slot : CHECKED_EQUIPMENT_SLOTS) {
            if (isMagnetic(living.getItemBySlot(slot))) return true;
        }
        return false;
    }

}
