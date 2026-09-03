package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramExecutionContext;
import org.academy.api.common.ability.program.ProgramInputView;
import org.academy.api.common.ability.program.ProgramNodeEditorMetadata;
import org.academy.api.common.ability.program.ProgramNodeExtension;
import org.academy.api.common.ability.program.ProgramNodePurity;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.api.common.ability.program.ProgramNodeSchema;
import org.academy.api.common.ability.program.ProgramNodeScope;
import org.academy.api.common.ability.program.ProgramNodeStep;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramNodeExtensionIndexTest {
    private static final Identifier CATEGORY = AcademyCraft.academy("accelerator");
    private static final Identifier FIRST = Identifier.fromNamespaceAndPath(
            "extension_fixture", "program/query/first");
    private static final Identifier SECOND = Identifier.fromNamespaceAndPath(
            "extension_fixture", "program/query/second");

    @org.junit.jupiter.api.Test
    void buildsDeterministicValidatedSnapshotIndependentOfCandidateOrder() {
        var first = new FixtureExtension("first");
        var second = new FixtureExtension("second");
        var forward = new LinkedHashMap<Identifier, org.academy.api.common.ability.program.ProgramNodeType<?>>();
        forward.put(FIRST, first);
        forward.put(SECOND, second);
        var reverse = new LinkedHashMap<Identifier, org.academy.api.common.ability.program.ProgramNodeType<?>>();
        reverse.put(SECOND, second);
        reverse.put(FIRST, first);

        var left = ProgramNodeExtensionIndex.build(CATEGORY, forward);
        var right = ProgramNodeExtensionIndex.build(CATEGORY, reverse);

        assertEquals(2, left.registrations().size());
        assertEquals(left.fingerprint(), right.fingerprint());
        assertEquals(64, left.fingerprint().length());
        assertEquals(FIRST, left.registrations().getFirst().id());
        assertNotNull(left.find(SECOND));
        assertEquals("first", left.find(FIRST).editorMetadata()
                .defaultConfiguration().getAsJsonObject().get("value").getAsString());
    }

    @org.junit.jupiter.api.Test
    void excludesExtensionsFromAnotherAbilityCategory() {
        var snapshot = ProgramNodeExtensionIndex.build(
                AcademyCraft.academy("mentalout"),
                Map.of(FIRST, new FixtureExtension("first"))
        );

        assertTrue(snapshot.registrations().isEmpty());
        assertNull(snapshot.find(FIRST));
    }

    private static final class FixtureExtension implements ProgramNodeExtension<String> {
        private final ProgramNodeEditorMetadata metadata;

        private FixtureExtension(String value) {
            var configuration = new JsonObject();
            configuration.addProperty("value", value);
            metadata = new ProgramNodeEditorMetadata(
                    configuration,
                    ProgramNodeEditorMetadata.Group.VALUE,
                    "fixture.node",
                    "fixture.port."
            );
        }

        @Override
        public Codec<String> configurationCodec() {
            return Codec.STRING.fieldOf("value").codec();
        }

        @Override
        public int schemaVersion() {
            return 1;
        }

        @Override
        public ProgramNodeSchema schema(String configuration) {
            return ProgramNodeSchema.EMPTY;
        }

        @Override
        public ProgramNodeRole role() {
            return ProgramNodeRole.QUERY;
        }

        @Override
        public ProgramNodePurity purity() {
            return ProgramNodePurity.PURE;
        }

        @Override
        public ProgramNodeScope scope() {
            return ProgramNodeScope.category(CATEGORY);
        }

        @Override
        public org.academy.api.common.ability.program.ProgramNodeExecution<String> execution() {
            return FixtureExtension::execute;
        }

        @Override
        public ProgramNodeEditorMetadata editorMetadata() {
            return metadata;
        }

        private static ProgramNodeStep execute(
                ProgramExecutionContext context,
                String configuration,
                ProgramInputView inputs
        ) {
            return ProgramNodeStep.data(Map.of());
        }
    }
}
