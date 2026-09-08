package org.academy.internal.server.world.level.storage;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.data.SkillStateType;
import org.academy.internal.common.skilldata.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AddonSkillStateTest {
    private static Identifier uniqueId() {
        return Identifier.fromNamespaceAndPath("state_test", UUID.randomUUID().toString());
    }

    private static JsonObject raw(Identifier id, int version) {
        var json = new JsonObject();
        json.addProperty("type", id.toString());
        json.addProperty("stateVersion", version);
        json.addProperty("state", 7);
        json.addProperty("enabled", false);
        json.addProperty("proficiency", 1234);
        return json;
    }

    @Test
    void codecStateAndFrameworkFieldsRoundTripSeparately() {
        var type = SkillStateType.of(uniqueId(), Codec.INT, () -> 0);
        SkillDataSerializer.registerStateType(type);
        var gson = WorldData.createGson();
        var loaded = gson.fromJson(raw(type.id(), 1), SkillData.class);
        assertEquals(7, assertInstanceOf(CodecSkillData.class, loaded).value());
        assertFalse(loaded.isEnabled());
        assertEquals(1234, loaded.getProficiency());
        var saved = gson.toJsonTree(loaded, SkillData.class).getAsJsonObject();
        assertEquals(7, saved.get("state").getAsInt());
        assertEquals(type.id().toString(), saved.get("type").getAsString());
        assertEquals(1234, saved.get("proficiency").getAsInt());
        assertFalse(saved.has("exp"));
    }

    @Test
    void migrationRunsOnceAndDoesNotMutateOriginalData() {
        var migrations = new java.util.concurrent.atomic.AtomicInteger();
        var type = new SkillStateType<>(uniqueId(), Codec.INT, () -> 0, 2, (version, state) -> {
            assertEquals(1, version);
            migrations.incrementAndGet();
            return new JsonPrimitive(state.getAsInt() + 10);
        });
        SkillDataSerializer.registerStateType(type);
        var original = raw(type.id(), 1);
        var gson = WorldData.createGson();
        var data = gson.fromJson(original, SkillData.class);
        assertEquals(17, assertInstanceOf(CodecSkillData.class, data).value());
        assertEquals(7, original.get("state").getAsInt());
        var updated = gson.toJsonTree(data, SkillData.class);
        assertEquals(2, updated.getAsJsonObject().get("stateVersion").getAsInt());
        gson.fromJson(updated, SkillData.class);
        assertEquals(1, migrations.get());
    }

    @Test
    void missingAddonKeepsRawStateUntilItIsRegisteredAgain() {
        var id = uniqueId();
        var original = raw(id, 1);
        original.addProperty("addonFutureField", "preserve me");
        var gson = WorldData.createGson();
        var missing = gson.fromJson(original, SkillData.class);
        assertInstanceOf(UnknownSkillData.class, missing);
        assertFalse(missing.isEnabled());
        assertEquals(original, gson.toJsonTree(missing, SkillData.class));
        var type = SkillStateType.of(id, Codec.INT, () -> 0);
        SkillDataSerializer.registerStateType(type);
        var restored = gson.fromJson(gson.toJsonTree(missing, SkillData.class), SkillData.class);
        assertEquals(7, assertInstanceOf(CodecSkillData.class, restored).value());
        assertEquals(1234, restored.getProficiency());
    }

    @Test
    void futureVersionsAndMalformedStateArePreservedWithoutExecution() {
        var type = SkillStateType.of(uniqueId(), Codec.INT, () -> 0);
        SkillDataSerializer.registerStateType(type);
        var future = raw(type.id(), 2);
        var invalid = raw(type.id(), 1);
        invalid.addProperty("state", "broken");
        var invalidId = raw(type.id(), 1);
        invalidId.addProperty("type", "INVALID TYPE");
        var gson = WorldData.createGson();
        for (var raw : java.util.List.of(future, invalid, invalidId)) {
            var loaded = gson.fromJson(raw, SkillData.class);
            assertInstanceOf(UnknownSkillData.class, loaded);
            assertFalse(loaded.isEnabled());
            assertEquals(raw, gson.toJsonTree(loaded, SkillData.class));
        }
    }

    @Test
    void conflictingDescriptorsAndLegacyTypeIdsFailWithoutReplacingTheOwner() {
        var type = SkillStateType.of(uniqueId(), Codec.INT, () -> 0);
        SkillDataSerializer.registerStateType(type);
        assertDoesNotThrow(() -> SkillDataSerializer.registerStateType(type));
        var duplicate = SkillStateType.of(type.id(), Codec.INT, () -> 1);
        assertTrue(assertThrows(IllegalStateException.class,
                () -> SkillDataSerializer.registerStateType(duplicate)).getMessage().contains(type.id().toString()));
        assertThrows(IllegalStateException.class, () -> SkillDataSerializer.registerType(type.id(), CommonSkillData.class));
        var data = WorldData.createGson().fromJson(raw(type.id(), 1), SkillData.class);
        assertSame(type, assertInstanceOf(CodecSkillData.class, data).stateType());
    }

    @Test
    void invalidStateCannotReplaceAValidValueOrBecomeTheDefault() {
        var type = SkillStateType.of(uniqueId(), Codec.intRange(0, 10), () -> 3);
        var data = new CodecSkillData<>(type);
        assertThrows(RuntimeException.class, () -> data.value(11));
        assertEquals(3, data.value());
        assertThrows(RuntimeException.class, () -> new CodecSkillData<>(SkillStateType.of(uniqueId(), Codec.intRange(0, 10), () -> 11)));
    }
}
