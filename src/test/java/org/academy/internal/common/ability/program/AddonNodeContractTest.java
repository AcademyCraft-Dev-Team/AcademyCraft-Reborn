package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AddonNodeContractTest {
    private static final Identifier CATEGORY = Identifier.parse("addon_test:category");
    private static final Identifier NODE = Identifier.parse("addon_test:node");

    private record Node(String translation, ProgramValueType type, int revision, boolean valid) implements ProgramNodeExtension<Integer> {
        @Override public Codec<Integer> configurationCodec() { return Codec.INT.fieldOf("value").codec(); }
        @Override public int schemaVersion() { return 1; }
        @Override public int compatibilityVersion() { return revision; }
        @Override public ProgramNodeSchema schema(Integer configuration) {
            return new ProgramNodeSchema(List.of(), List.of(ProgramPortDefinition.output("value", type)));
        }
        @Override public ProgramNodeRole role() { return ProgramNodeRole.VALUE; }
        @Override public ProgramNodePurity purity() { return ProgramNodePurity.PURE; }
        @Override public ProgramNodeScope scope() { return ProgramNodeScope.category(CATEGORY); }
        @Override public ProgramNodeExecution<Integer> execution() { return (_, _, _) -> ProgramNodeStep.data(Map.of()); }
        @Override public ProgramNodeEditorMetadata editorMetadata() {
            var config = new JsonObject();
            if (valid) config.addProperty("value", 1);
            return new ProgramNodeEditorMetadata(config, ProgramNodeEditorMetadata.Group.VALUE, translation, translation + ".port.");
        }
    }
    private static String fingerprint(Node node) { return ProgramNodeExtensionIndex.build(CATEGORY, Map.of(NODE, node)).fingerprint(); }

    @Test
    void translationsDoNotChangeTheGameplayFingerprint() {
        assertEquals(fingerprint(new Node("first", ProgramValueTypes.FLOAT, 1, true)),
                fingerprint(new Node("second", ProgramValueTypes.FLOAT, 1, true)));
    }
    @Test
    void portsAndSemanticRevisionsChangeTheFingerprint() {
        var baseline = fingerprint(new Node("first", ProgramValueTypes.FLOAT, 1, true));
        assertNotEquals(baseline, fingerprint(new Node("first", ProgramValueTypes.BOOLEAN, 1, true)));
        assertNotEquals(baseline, fingerprint(new Node("first", ProgramValueTypes.FLOAT, 2, true)));
    }
    @Test
    void invalidExtensionFailsRegistrationWithItsFullId() {
        var error = assertThrows(IllegalStateException.class,
                () -> fingerprint(new Node("first", ProgramValueTypes.FLOAT, 1, false)));
        assertTrue(error.getMessage().contains(NODE.toString()));
        assertTrue(error.getMessage().contains("default"));
    }
}
