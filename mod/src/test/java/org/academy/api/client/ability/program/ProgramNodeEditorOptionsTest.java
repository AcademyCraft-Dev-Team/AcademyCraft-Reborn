package org.academy.api.client.ability.program;

import com.google.gson.JsonPrimitive;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramNodeEditorOptionsTest {
    @Test
    void registrationSuppliesLiteralPlayerSpecificChoicesAndCanBeRemoved() {
        var node = Identifier.fromNamespaceAndPath("test", "learned_method");
        try (var registration = ProgramNodeEditorOptions.register(node, (field, current) ->
                field.equals("binding")
                        ? List.of(new ProgramNodeEditorOptions.Option(
                        new JsonPrimitive("method-1"), Component.literal("Entity.getX()")))
                        : List.of())) {
            var options = ProgramNodeEditorOptions.options(
                    node, "binding", new JsonPrimitive("unselected"));
            assertEquals(1, options.size());
            assertEquals("method-1", options.getFirst().value().getAsString());
            assertEquals("Entity.getX()", options.getFirst().label().getString());
        }

        assertTrue(ProgramNodeEditorOptions.options(
                node, "binding", new JsonPrimitive("unselected")).isEmpty());
    }
}
