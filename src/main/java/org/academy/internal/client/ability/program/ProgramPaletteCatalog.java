package org.academy.internal.client.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramNodeExtension;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog.Entry;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog.Group;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog.PresentationStatus;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Client-only choices for adding nodes. A family is a menu, never a new runtime node type.
 * Selecting a member must use that exact entry's id, configuration and typed schema.
 */
public final class ProgramPaletteCatalog {
    private static final String FAMILY_PREFIX = "screen.academy.program.palette.family.";
    private static final List<String> STRUCTURE_CATEGORIES =
            List.of("accelerator", "electromaster", "aeromanip");
    private static final List<Identifier> TARGETS = List.of(
            CommonProgramNodeIds.CASTER, CommonProgramNodeIds.LOOK_TARGET,
            CommonProgramNodeIds.LOOK_STRUCTURE, CommonProgramNodeIds.NEAREST_STRUCTURE,
            CommonProgramNodeIds.ENTITIES_AROUND, CommonProgramNodeIds.STRUCTURES_AROUND);
    private static final List<Identifier> VALUES = List.of(
            CommonProgramNodeIds.SCALAR_CONSTANT, CommonProgramNodeIds.DIRECTION_CONSTANT,
            CommonProgramNodeIds.WORLD_POSITION_CONSTANT, CommonProgramNodeIds.BLOCK_POSITION_CONSTANT,
            CommonProgramNodeIds.VECTOR_CONSTRUCT, CommonProgramNodeIds.TEXT_CONSTANT);
    private static final List<Identifier> FLOW = List.of(
            CommonProgramNodeIds.BRANCH, CommonProgramNodeIds.WAIT, CommonProgramNodeIds.STOP);
    private static final List<Identifier> COLLECTIONS = commonCollections();
    private static final List<String> MOVE = List.of("move_group", "move");
    private static final List<String> POSE = List.of(
            "rotate_group", "reset_group", "spin_group", "rotate", "reset_orientation", "spin");

    private ProgramPaletteCatalog() {
    }

    public enum Layer {
        COMMON,
        ADVANCED,
        COMPATIBILITY;

        public String translationKey() {
            return "screen.academy.program.palette.layer." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Members retain their original entry references, including provider-specific presets. */
    public record Card(
            String id,
            String titleTranslationKey,
            String descriptionTranslationKey,
            Group group,
            List<Entry> members,
            boolean family
    ) {
        public Card {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(titleTranslationKey, "titleTranslationKey");
            Objects.requireNonNull(descriptionTranslationKey, "descriptionTranslationKey");
            Objects.requireNonNull(group, "group");
            members = List.copyOf(members);
            if (members.isEmpty()) throw new IllegalArgumentException("Palette card requires a member");
        }
    }

    public static List<Card> cards(
            List<Entry> entries,
            Set<Identifier> capabilities,
            boolean hasEntry,
            Layer layer,
            String query,
            Function<String, String> translate
    ) {
        return cards(entries, capabilities, hasEntry, layer, query, translate,
                entry -> translate.apply(entry.translationKey()) + "\n"
                        + translate.apply(entry.descriptionTranslationKey()));
    }

    /** The optional entry text callback lets the UI include localized provider preset labels. */
    public static List<Card> cards(
            List<Entry> entries,
            Set<Identifier> capabilities,
            boolean hasEntry,
            Layer layer,
            String query,
            Function<String, String> translate,
            Function<Entry, String> entrySearchText
    ) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(translate, "translate");
        Objects.requireNonNull(entrySearchText, "entrySearchText");
        var search = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        var eligible = entries.stream()
                .filter(entry -> capabilities.containsAll(entry.type().scope().requiredCapabilities()))
                .filter(entry -> !hasEntry || entry.type().role() != ProgramNodeRole.ENTRY)
                .filter(entry -> layer == Layer.COMPATIBILITY
                        ? entry.presentationStatus() != PresentationStatus.AVAILABLE
                        : entry.visible() && entry.presentationStatus() == PresentationStatus.AVAILABLE)
                .toList();
        if (layer != Layer.COMMON) {
            if (layer == Layer.ADVANCED && !search.isEmpty()) {
                var familyMatches = Collections.newSetFromMap(new IdentityHashMap<Entry, Boolean>());
                for (var family : commonFamilies(eligible)) {
                    if (matchesFamily(family, search, translate)) familyMatches.addAll(family.members());
                }
                return singletonCards(eligible.stream().filter(entry -> familyMatches.contains(entry)
                        || matches(entry, search, entrySearchText)).toList(), "", entrySearchText);
            }
            return singletonCards(eligible, search, entrySearchText);
        }

        var families = commonFamilies(eligible);
        if (search.isEmpty()) return families;
        var result = new ArrayList<Card>();
        var covered = Collections.newSetFromMap(new IdentityHashMap<Entry, Boolean>());
        for (var family : families) {
            var familyMatches = matchesFamily(family, search, translate);
            var members = familyMatches ? family.members() : family.members().stream()
                    .filter(entry -> matches(entry, search, entrySearchText)).toList();
            if (members.isEmpty()) continue;
            result.add(new Card(family.id(), family.titleTranslationKey(), family.descriptionTranslationKey(),
                    family.group(), members, true));
            covered.addAll(members);
        }
        // Searching the default layer also finds advanced and undeclared extension nodes.
        result.addAll(singletonCards(eligible.stream().filter(entry -> !covered.contains(entry)).toList(),
                search, entrySearchText));
        return List.copyOf(result);
    }

    private static List<Card> singletonCards(List<Entry> entries, String query,
                                              Function<Entry, String> entrySearchText) {
        var result = new ArrayList<Card>();
        for (var index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            if (!matches(entry, query, entrySearchText)) continue;
            result.add(new Card("node:" + entry.id() + ":" + index, entry.translationKey(),
                    entry.descriptionTranslationKey(), entry.group(), List.of(entry), false));
        }
        return List.copyOf(result);
    }

    private static boolean matches(Entry entry, String query, Function<Entry, String> entrySearchText) {
        return query.isEmpty() || contains(entry.id().toString(), query)
                || contains(entrySearchText.apply(entry), query);
    }

    private static boolean matchesFamily(Card family, String query, Function<String, String> translate) {
        return contains(translate.apply(family.titleTranslationKey()), query)
                || contains(translate.apply(family.descriptionTranslationKey()), query)
                || contains(family.id(), query);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static List<Card> commonFamilies(List<Entry> entries) {
        var result = new ArrayList<Card>();
        addFamily(result, entries, "triggers", Group.FLOW,
                entry -> builtIn(entry) && (entry.id().getPath().endsWith("/entry/on_cast")
                        || List.of(CommonProgramNodeIds.TRIGGER_LOOP, CommonProgramNodeIds.TRIGGER_HURT,
                        CommonProgramNodeIds.TRIGGER_CHAT).contains(entry.id())));
        addFamily(result, entries, "actions", Group.ACTION,
                entry -> builtIn(entry) && entry.group() == Group.ACTION
                        && entry.type().role() == ProgramNodeRole.ACTION
                        && !entry.id().getPath().startsWith("program/core/")
                        && !isStructureAction(entry));
        addOrderedFamily(result, entries, "targets", Group.TARGET, TARGETS);
        addOrderedFamily(result, entries, "collections", Group.COLLECTION, COLLECTIONS);
        addOrderedFamily(result, entries, "values", Group.VALUE, VALUES);
        addOrderedFamily(result, entries, "flow", Group.FLOW, FLOW);
        addOrderedFamily(result, entries, "debug", Group.ACTION, List.of(CommonProgramNodeIds.DEBUG_OUTPUT));
        addStructureFamily(result, entries, "structure_extract", List.of("extract"));
        addStructureFamily(result, entries, "structure_move", MOVE);
        addStructureFamily(result, entries, "structure_pose", POSE);
        addStructureFamily(result, entries, "structure_launch", List.of("launch"));
        addStructureFamily(result, entries, "structure_release", List.of("release"));
        return List.copyOf(result);
    }

    private static void addOrderedFamily(List<Card> result, List<Entry> entries, String id,
                                         Group group, List<Identifier> orderedIds) {
        var members = entries.stream().filter(entry -> orderedIds.contains(entry.id()))
                .sorted(Comparator.comparingInt(entry -> orderedIds.indexOf(entry.id()))).toList();
        addFamily(result, id, group, members);
    }

    private static void addStructureFamily(List<Card> result, List<Entry> entries, String id,
                                           List<String> operations) {
        var members = entries.stream().filter(ProgramPaletteCatalog::isStructureAction)
                .filter(entry -> operations.contains(structureOperation(entry)))
                .sorted(Comparator.comparingInt(entry -> operations.indexOf(structureOperation(entry))))
                .toList();
        addFamily(result, id, Group.ACTION, members);
    }

    private static void addFamily(List<Card> result, List<Entry> entries, String id, Group group,
                                  Predicate<Entry> predicate) {
        addFamily(result, id, group, entries.stream().filter(predicate).toList());
    }

    private static void addFamily(List<Card> result, String id, Group group, List<Entry> members) {
        if (!members.isEmpty()) result.add(new Card("family." + id, FAMILY_PREFIX + id,
                FAMILY_PREFIX + id + ".description", group, members, true));
    }

    private static boolean builtIn(Entry entry) {
        return "academy".equals(entry.id().getNamespace())
                && !(entry.type() instanceof ProgramNodeExtension<?>);
    }

    private static boolean isStructureAction(Entry entry) {
        if (!builtIn(entry)) return false;
        return STRUCTURE_CATEGORIES.stream().anyMatch(category ->
                entry.id().getPath().startsWith("program/" + category + "/action/structure/"));
    }

    private static String structureOperation(Entry entry) {
        var path = entry.id().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static List<Identifier> commonCollections() {
        var result = new ArrayList<Identifier>();
        for (var domain : List.of("entity", "structure")) {
            for (var operation : List.of("empty", "singleton", "foreach", "size", "get")) {
                result.add(CommonProgramNodeIds.collection(domain, operation));
            }
        }
        result.add(CommonProgramNodeIds.NEAREST_ENTITY_TO_POSITION);
        result.add(CommonProgramNodeIds.NEAREST_STRUCTURE_TO_POSITION);
        return List.copyOf(result);
    }
}
