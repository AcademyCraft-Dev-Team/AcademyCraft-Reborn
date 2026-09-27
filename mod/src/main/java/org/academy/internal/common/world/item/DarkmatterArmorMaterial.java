package org.academy.internal.common.world.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import org.academy.AcademyCraft;

import java.util.EnumMap;

public final class DarkmatterArmorMaterial {
    public static final TagKey<Item> REPAIR_INGREDIENT = TagKey.create(
            Registries.ITEM, AcademyCraft.academy("repairs_dark_matter_armor"));
    public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(
            EquipmentAssets.ROOT_ID, AcademyCraft.academy("dark_matter"));
    public static final ArmorMaterial INSTANCE = create();

    private DarkmatterArmorMaterial() {
    }

    private static ArmorMaterial create() {
        var defense = new EnumMap<ArmorType, Integer>(ArmorType.class);
        defense.put(ArmorType.HELMET, 3);
        defense.put(ArmorType.CHESTPLATE, 8);
        defense.put(ArmorType.LEGGINGS, 6);
        defense.put(ArmorType.BOOTS, 3);
        defense.put(ArmorType.BODY, 8);
        return new ArmorMaterial(
                37,
                defense,
                25,
                SoundEvents.ARMOR_EQUIP_NETHERITE,
                3.0f,
                0.1f,
                REPAIR_INGREDIENT,
                ASSET
        );
    }
}
