package org.academy.internal.client.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramNodeIds;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog.Entry;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog.PresentationStatus;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ProgramPaletteCatalogTest {
    private static final Function<String, String> KEYS = Function.identity();

    @Test
    void advancedAndCompatibilityPartitionTheRealCatalogWithoutChangingEntries() {
        for (var definition : AbilityProgramDefinitions.all()) {
            var entries = definition.editorCatalog().entries();
            var capabilities = allCapabilities(entries);
            var advanced = cards(entries, capabilities, ProgramPaletteCatalog.Layer.ADVANCED, "");
            var compatibility = cards(entries, capabilities, ProgramPaletteCatalog.Layer.COMPATIBILITY, "");
            assertEquals(entries.stream().filter(entry -> entry.visible()
                            && entry.presentationStatus() == PresentationStatus.AVAILABLE).toList(), members(advanced));
            assertEquals(entries.stream().filter(entry -> entry.presentationStatus()
                    != PresentationStatus.AVAILABLE).toList(), members(compatibility));
            assertTrue(advanced.stream().noneMatch(ProgramPaletteCatalog.Card::family));
            assertTrue(compatibility.stream().noneMatch(ProgramPaletteCatalog.Card::family));
            for (var card : advanced) assertSame(card.members().getFirst(),
                    entries.stream().filter(entry -> entry == card.members().getFirst()).findFirst().orElseThrow());
        }
    }

    @Test
    void commonLayerIsCuratedAndGroupControlComesBeforeSingleControl() {
        for (var definition : AbilityProgramDefinitions.all()) {
            var entries = definition.editorCatalog().entries();
            var common = cards(entries, allCapabilities(entries), ProgramPaletteCatalog.Layer.COMMON, "");
            assertTrue(common.size() >= 6 && common.size() <= 12, definition.category() + ": " + common.size());
            assertTrue(common.stream().allMatch(ProgramPaletteCatalog.Card::family));
            assertFalse(members(common).stream().anyMatch(entry ->
                    entry.id().getPath().endsWith("/action/structure/control")));
            assertFalse(members(common).stream().anyMatch(entry ->
                    entry.id().equals(CommonProgramNodeIds.SHARED_VARIABLE_GET)));
            assertTrue(members(common).size() < entries.stream().filter(Entry::visible).count() / 2,
                    "Common families must not merely relocate the complete palette");
        }
        var definition = AbilityProgramDefinitions.require(Identifier.parse("academy:accelerator"));
        var entries = definition.editorCatalog().entries();
        var common = cards(entries, allCapabilities(entries), ProgramPaletteCatalog.Layer.COMMON, "");
        assertEquals(List.of(AcceleratorProgramNodeIds.STRUCTURE_MOVE_GROUP, AcceleratorProgramNodeIds.STRUCTURE_MOVE),
                family(common, "structure_move").members().stream().map(Entry::id).toList());
        assertEquals(List.of(AcceleratorProgramNodeIds.STRUCTURE_EXTRACT),
                family(common, "structure_extract").members().stream().map(Entry::id).toList());
        assertEquals(AcceleratorProgramNodeIds.STRUCTURE_ROTATE_GROUP,
                family(common, "structure_pose").members().getFirst().id());
    }

    @Test
    void permissionsAndExistingEntryApplyToEveryLayerAndSearchPath() {
        for (var definition : AbilityProgramDefinitions.all()) {
            var entries = definition.editorCatalog().entries();
            for (var layer : ProgramPaletteCatalog.Layer.values()) {
                for (var query : List.of("", "structure", "action")) {
                    var cards = ProgramPaletteCatalog.cards(entries, Set.of(), true, layer, query, KEYS);
                    assertTrue(members(cards).stream().allMatch(entry -> entry.type().scope().requiredCapabilities().isEmpty()));
                    assertTrue(members(cards).stream().noneMatch(entry -> entry.type().role() == ProgramNodeRole.ENTRY));
                }
            }
        }
        var lockedEntries = AbilityProgramDefinitions.all().stream()
                .flatMap(definition -> definition.editorCatalog().entries().stream())
                .filter(entry -> !entry.type().scope().requiredCapabilities().isEmpty()).count();
        assertTrue(lockedEntries > 0, "Use actual skill-restricted entries in this contract test");
    }

    @Test
    void commonSearchFindsAdvancedIdsButNeverHiddenOrUnreleasedEntries() {
        var entries = AbilityProgramDefinitions.require(Identifier.parse("academy:accelerator")).editorCatalog().entries();
        var capabilities = allCapabilities(entries);
        var advanced = cards(entries, capabilities, ProgramPaletteCatalog.Layer.COMMON,
                CommonProgramNodeIds.SHARED_VARIABLE_GET.toString());
        assertEquals(List.of(CommonProgramNodeIds.SHARED_VARIABLE_GET), members(advanced).stream().map(Entry::id).toList());
        assertFalse(advanced.getFirst().family());
        assertTrue(cards(entries, capabilities, ProgramPaletteCatalog.Layer.COMMON,
                CommonProgramNodeIds.INTEGER_ADD.toString()).isEmpty());
        assertTrue(cards(entries, capabilities, ProgramPaletteCatalog.Layer.COMMON,
                AcceleratorProgramNodeIds.STRUCTURE_STATICIZE.toString()).isEmpty());
        assertEquals(List.of(AcceleratorProgramNodeIds.STRUCTURE_STATICIZE),
                members(cards(entries, capabilities, ProgramPaletteCatalog.Layer.COMPATIBILITY,
                        AcceleratorProgramNodeIds.STRUCTURE_STATICIZE.toString())).stream().map(Entry::id).toList());
    }

    @Test
    void localizedFamilyAndEntryDescriptionsAndProviderLabelsAreSearchable() {
        var entries = AbilityProgramDefinitions.mentalout().editorCatalog().entries();
        var translations = Map.of(
                "screen.academy.program.palette.family.values", "常用数值与空间",
                "screen.academy.program.palette.family.values.description", "基础常量",
                entries.stream().filter(entry -> entry.id().equals(CommonProgramNodeIds.SHARED_VARIABLE_GET))
                        .findFirst().orElseThrow().descriptionTranslationKey(), "Retrieve persistent shared data");
        Function<String, String> translate = key -> translations.getOrDefault(key, key);
        var family = ProgramPaletteCatalog.cards(entries, allCapabilities(entries), false,
                ProgramPaletteCatalog.Layer.COMMON, "数值", translate);
        assertEquals("family.values", family.getFirst().id());
        assertTrue(family.getFirst().members().size() > 1);
        var advancedFamily = ProgramPaletteCatalog.cards(entries, allCapabilities(entries), false,
                ProgramPaletteCatalog.Layer.ADVANCED, "数值", translate);
        assertEquals(Set.copyOf(family.getFirst().members()), Set.copyOf(members(advancedFamily)));
        assertTrue(advancedFamily.stream().noneMatch(ProgramPaletteCatalog.Card::family));
        assertEquals(List.of(CommonProgramNodeIds.SHARED_VARIABLE_GET), members(ProgramPaletteCatalog.cards(
                entries, allCapabilities(entries), false, ProgramPaletteCatalog.Layer.COMMON,
                "PERSISTENT SHARED", translate)).stream().map(Entry::id).toList());
        var provider = ProgramPaletteCatalog.cards(entries, allCapabilities(entries), false,
                ProgramPaletteCatalog.Layer.ADVANCED, "自定义预设", translate,
                entry -> entry.id().equals(CommonProgramNodeIds.SCALAR_CONSTANT) ? "自定义预设" : "");
        assertEquals(List.of(CommonProgramNodeIds.SCALAR_CONSTANT), members(provider).stream().map(Entry::id).toList());
    }

    @Test
    void presetsRetainIdentityConfigurationAndTypedSchemaAndExtensionsRemainReachable() {
        var catalog = AbilityProgramDefinitions.mentalout().editorCatalog();
        var original = catalog.entry(CommonProgramNodeIds.SCALAR_CONSTANT);
        assertNotNull(original);
        var configuration = original.defaultConfiguration().getAsJsonObject();
        configuration.addProperty("type", "float");
        configuration.addProperty("value", "1.5");
        var preset = new Entry(original.id(), original.type(), configuration,
                catalog.schema(original.id(), configuration), original.group(), original.displayName(),
                original.translationKey(), original.portTranslationPrefix(), original.visible(), original.metadata());
        var extension = new Entry(Identifier.parse("thirdparty:program/query/custom"), original.type(),
                original.defaultConfiguration(), original.defaultSchema(), original.group(), "Plugin choice",
                "thirdparty.node.custom", original.portTranslationPrefix(), true, null);
        var entries = List.of(original, preset, extension);
        var advanced = cards(entries, Set.of(), ProgramPaletteCatalog.Layer.ADVANCED, "");
        assertEquals(3, advanced.size());
        assertEquals(3, advanced.stream().map(ProgramPaletteCatalog.Card::id).distinct().count());
        assertSame(original, advanced.get(0).members().getFirst());
        assertSame(preset, advanced.get(1).members().getFirst());
        assertSame(extension, advanced.get(2).members().getFirst());
        assertNotEquals(original.defaultSchema(), preset.defaultSchema());
        var common = cards(entries, Set.of(), ProgramPaletteCatalog.Layer.COMMON, "");
        assertEquals(List.of(original, preset), members(common));
        assertSame(preset, common.getFirst().members().get(1));
        var foundExtension = cards(entries, Set.of(), ProgramPaletteCatalog.Layer.COMMON, "thirdparty:");
        assertEquals(List.of(extension), members(foundExtension));
        assertFalse(foundExtension.getFirst().family());
    }

    private static ProgramPaletteCatalog.Card family(List<ProgramPaletteCatalog.Card> cards, String id) {
        return cards.stream().filter(card -> card.id().equals("family." + id)).findFirst().orElseThrow();
    }

    private static List<ProgramPaletteCatalog.Card> cards(List<Entry> entries, Set<Identifier> capabilities,
                                                         ProgramPaletteCatalog.Layer layer, String query) {
        return ProgramPaletteCatalog.cards(entries, capabilities, false, layer, query, KEYS);
    }

    private static List<Entry> members(List<ProgramPaletteCatalog.Card> cards) {
        var result = new ArrayList<Entry>();
        cards.forEach(card -> result.addAll(card.members()));
        return List.copyOf(result);
    }

    private static Set<Identifier> allCapabilities(List<Entry> entries) {
        return entries.stream().flatMap(entry -> entry.type().scope().requiredCapabilities().stream())
                .collect(Collectors.toSet());
    }
}
