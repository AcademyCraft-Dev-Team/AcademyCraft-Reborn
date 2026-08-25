package org.academy.internal.client.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.program.ProgramBookCodec;
import org.jspecify.annotations.Nullable;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Set;

/** Bounded, versioned clipboard exchange for program selections and complete programs. */
public final class ProgramClipboardCodec {
    private static final String FRAGMENT_PREFIX = "ACADEMY_PROGRAM_FRAGMENT_V1:";
    private static final String PROGRAM_PREFIX = "ACADEMY_PROGRAM_V1:";

    private ProgramClipboardCodec() {
    }

    public static String encodeFragment(AbilityProgram source, Set<Integer> selectedNodeIds) {
        if (source == null || selectedNodeIds == null || selectedNodeIds.isEmpty()) {
            throw new IllegalArgumentException("A non-empty program selection is required");
        }
        var nodes = source.graph().nodes().stream()
                .filter(node -> selectedNodeIds.contains(node.id()))
                .toList();
        if (nodes.isEmpty()) throw new IllegalArgumentException("The selected nodes do not exist");
        var retainedIds = nodes.stream().map(ProgramGraph.Node::id)
                .collect(java.util.stream.Collectors.toSet());
        var edges = source.graph().edges().stream().filter(edge ->
                retainedIds.contains(edge.from().nodeId())
                        && retainedIds.contains(edge.to().nodeId())).toList();
        var positions = new LinkedHashMap<Integer, ProgramEditorLayout.NodePosition>();
        source.editorLayout().nodePositions().forEach((id, position) -> {
            if (retainedIds.contains(id)) positions.put(id, position);
        });
        var fragment = new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                source.id(),
                source.name(),
                source.category(),
                new ProgramGraph(nodes, edges),
                new ProgramEditorLayout(positions)
        );
        return FRAGMENT_PREFIX + encode(fragment);
    }

    public static String encodeProgram(AbilityProgram program) {
        if (program == null) throw new IllegalArgumentException("Program is required");
        return PROGRAM_PREFIX + encode(program);
    }

    public static @Nullable AbilityProgram decodeFragment(String clipboard, Identifier category) {
        return decode(clipboard, FRAGMENT_PREFIX, category);
    }

    public static @Nullable AbilityProgram decodeProgram(String clipboard, Identifier category) {
        return decode(clipboard, PROGRAM_PREFIX, category);
    }

    private static String encode(AbilityProgram program) {
        return Base64.getEncoder().encodeToString(ProgramBookCodec.encodeProgram(program));
    }

    private static @Nullable AbilityProgram decode(
            String clipboard,
            String prefix,
            Identifier category
    ) {
        if (clipboard == null || category == null || !clipboard.startsWith(prefix)) return null;
        try {
            var encoded = Base64.getDecoder().decode(clipboard.substring(prefix.length()).strip());
            var result = ProgramBookCodec.decodeProgram(encoded);
            var program = result.program();
            return result.valid() && program != null && category.equals(program.category())
                    ? program : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
