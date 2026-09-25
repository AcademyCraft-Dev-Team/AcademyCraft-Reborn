package org.academy.api.client.ability.program;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ProgramNodePaletteTest {
    @Test
    void presetsFollowServerSnapshotAndOwnTheirConfiguration() {
        var id = Identifier.fromNamespaceAndPath("test", UUID.randomUUID().toString());
        var config = new JsonObject();
        config.addProperty("package_id", "one");
        var preset = new ProgramNodePalette.Preset(config, Component.literal("Named spell"));
        var current = new AtomicReference<>(List.of(preset));
        ProgramNodePalette.register(id, current::get);
        config.addProperty("package_id", "forged");
        assertEquals("one", ProgramNodePalette.presets(id).getFirst().configuration().getAsJsonObject().get("package_id").getAsString());
        assertEquals("Named spell", ProgramNodePalette.label(id, preset.configuration()).getString());
        current.set(List.of());
        assertTrue(ProgramNodePalette.presets(id).isEmpty());
        assertTrue(ProgramNodePalette.hasProvider(id));
        assertNull(ProgramNodePalette.label(id, preset.configuration()));
    }

    @Test
    void duplicateProvidersAreRejected() {
        var id = Identifier.fromNamespaceAndPath("test", UUID.randomUUID().toString());
        ProgramNodePalette.register(id, List::of);
        assertThrows(IllegalStateException.class, () -> ProgramNodePalette.register(id, List::of));
    }
}
