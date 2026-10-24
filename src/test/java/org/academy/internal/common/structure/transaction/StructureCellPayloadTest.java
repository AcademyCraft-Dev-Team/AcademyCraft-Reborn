package org.academy.internal.common.structure.transaction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructureCellPayloadTest {
    @Test
    void relocationAndCompoundInsertionOrderDoNotChangeInventoryIdentity() {
        var source = new CompoundTag();
        source.putString("id", "minecraft:chest");
        source.putInt("x", 12); source.putInt("y", 64); source.putInt("z", -4);
        source.put("Items", items(19, "named diamonds"));
        var target = new CompoundTag();
        target.put("Items", items(19, "named diamonds"));
        target.putInt("z", 500000); target.putInt("y", 3); target.putInt("x", 500000);
        target.putString("id", "minecraft:chest");
        var original = source.copy();
        assertEquals(StructureCellPayload.digestEntity(source), StructureCellPayload.digestEntity(target));
        assertEquals(original, source, "fingerprinting must not mutate the saved image");
    }

    @Test
    void inventoryCountsComponentsAndUnopenedLootRemainPartOfIdentity() {
        var source = new CompoundTag();
        source.put("Items", items(19, "before capture"));
        var countChanged = source.copy(); countChanged.put("Items", items(37, "before capture"));
        var componentChanged = source.copy(); componentChanged.put("Items", items(19, "after capture"));
        assertNotEquals(StructureCellPayload.digestEntity(source), StructureCellPayload.digestEntity(countChanged));
        assertNotEquals(StructureCellPayload.digestEntity(source), StructureCellPayload.digestEntity(componentChanged));
        var loot = new CompoundTag(); loot.putString("LootTable", "minecraft:chests/simple_dungeon"); loot.putLong("LootTableSeed", 9);
        var changed = loot.copy(); changed.putLong("LootTableSeed", 10);
        assertNotEquals(StructureCellPayload.digestEntity(loot), StructureCellPayload.digestEntity(changed));
        assertEquals("", StructureCellPayload.digestEntity(null));
        assertNotEquals("", StructureCellPayload.digestEntity(new CompoundTag()));
    }

    private static ListTag items(int count, String name) {
        var item = new CompoundTag();
        item.putByte("Slot", (byte) 0); item.putString("id", "minecraft:diamond"); item.putInt("count", count);
        var components = new CompoundTag(); components.putString("minecraft:custom_name", name); item.put("components", components);
        var result = new ListTag(); result.add(item); return result;
    }
}
