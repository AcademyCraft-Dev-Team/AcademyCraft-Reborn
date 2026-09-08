package org.academy.api.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceKey;
import org.academy.api.common.ability.AbilityCategory;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ProgramNodes {
    private ProgramNodes() {
    }

    public static ProgramNodeExtension<VoidConfiguration> manualEntry(ResourceKey<AbilityCategory> category) {
        Objects.requireNonNull(category);
        return new ProgramNodeExtension<>() {
            @Override public Codec<VoidConfiguration> configurationCodec() {
                return MapCodec.unit(VoidConfiguration.INSTANCE).codec();
            }
            @Override public int schemaVersion() { return 1; }
            @Override public ProgramNodeSchema schema(VoidConfiguration configuration) {
                return new ProgramNodeSchema(List.of(),
                        List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW)));
            }
            @Override public ProgramNodeRole role() { return ProgramNodeRole.ENTRY; }
            @Override public ProgramNodePurity purity() { return ProgramNodePurity.PURE; }
            @Override public ProgramNodeScope scope() { return ProgramNodeScope.category(category.identifier()); }
            @Override public ProgramNodeExecution<VoidConfiguration> execution() {
                return (_, _, _) -> ProgramNodeStep.next("flow");
            }
            @Override public ProgramNodeEditorMetadata editorMetadata() {
                return new ProgramNodeEditorMetadata(new JsonObject(), ProgramNodeEditorMetadata.Group.FLOW,
                        "screen.academy.program.node.on_cast", "screen.academy.program.port.", true, Map.of());
            }
        };
    }

    public enum VoidConfiguration { INSTANCE }
}
