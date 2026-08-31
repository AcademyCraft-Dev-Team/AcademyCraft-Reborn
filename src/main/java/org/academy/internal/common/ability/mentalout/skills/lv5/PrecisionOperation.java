package org.academy.internal.common.ability.mentalout.skills.lv5;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.academy.internal.common.ability.program.*;
import org.academy.internal.common.skilldata.SkillData;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Save-only codec for importing program books from the retired Mentalout placeholder skill.
 */
public final class PrecisionOperation {
    private static final int SLOT_COUNT = AbilityProgramManager.SLOT_COUNT;

    private PrecisionOperation() {
    }

    public static Data normalizeData(Data data) {
        return normalizeData(new UUID(0L, 0L), data);
    }

    public static Data normalizeData(UUID ownerId, Data data) {
        if (data == null) data = new Data();
        if (data.cachedBook != null) return data;
        if (data.encodedBook != null && !data.encodedBook.isBlank()) {
            try {
                var decoded = ProgramBookCodec.decode(Base64.getDecoder().decode(data.encodedBook));
                if (decoded.valid() && validBook(decoded.book())) {
                    data.installBook(decoded.book().resize(SLOT_COUNT));
                    return data;
                }
            } catch (IllegalArgumentException ignored) {
                // Fall through to the legacy slots. Corrupt program-book data never reaches the VM.
            }
        }
        var migration = PrecisionProgramBookMigrator.migrate(ownerId, data);
        data.installBook(migration.book());
        return data;
    }

    public static Data normalizeLegacyData(Data data) {
        if (data == null) data = new Data();
        var migrateFlow = data.schemaVersion == 1;
        var legacy = data.schemaVersion > 0 && data.schemaVersion < 3;
        data.revision = Math.max(0L, data.revision);
        var normalized = new ArrayList<PrecisionGraph>(4);
        var source = data.slots == null ? List.<PrecisionGraph>of() : data.slots;
        for (var slot = 0; slot < 4; slot++) {
            var graph = slot < source.size() && source.get(slot) != null
                    ? source.get(slot)
                    : PrecisionGraph.EMPTY;
            if (migrateFlow) {
                var migration = PrecisionGraph.migrateLegacy(graph);
                normalized.add(migration.valid() ? migration.graph() : PrecisionGraph.EMPTY);
            } else {
                var validation = graph.validate();
                normalized.add(validation.valid() ? validation.normalized() : PrecisionGraph.EMPTY);
            }
        }
        data.slots = normalized;
        if (legacy) data.revision++;
        if (data.schemaVersion < Data.SCHEMA_VERSION) data.schemaVersion = 3;
        return data;
    }

    private static boolean validBook(ProgramBook book) {
        if (book.schemaVersion() != ProgramBook.CURRENT_SCHEMA_VERSION
                || book.slots().isEmpty()
                || book.slots().size() > SLOT_COUNT) {
            return false;
        }
        return book.slots().stream().allMatch(slot -> slot.empty()
                || slot.program().schemaVersion() == AbilityProgram.CURRENT_SCHEMA_VERSION
                && slot.program().category().equals(AbilityCategories.MENTALOUT.get().getKey()));
    }

    public static final class Data extends SkillData {
        public static final int SCHEMA_VERSION = 5;
        public static final Identifier ID = AcademyCraft.academy("precision_operation");

        @SerializedName("schemaVersion")
        private int schemaVersion = SCHEMA_VERSION;
        @SerializedName("revision")
        private long revision;
        @SerializedName("slots")
        private List<PrecisionGraph> slots = new ArrayList<>(List.of(
                PrecisionGraph.EMPTY,
                PrecisionGraph.EMPTY,
                PrecisionGraph.EMPTY,
                PrecisionGraph.EMPTY
        ));
        @SerializedName("programBook")
        private String encodedBook = "";
        private transient ProgramBook cachedBook;

        public long revision() {
            return revision;
        }

        public int schemaVersion() {
            return schemaVersion;
        }

        public ProgramBook programBook(UUID ownerId) {
            normalizeData(ownerId, this);
            return cachedBook;
        }

        public AbilityProgram program(UUID ownerId, int slot) {
            return programBook(ownerId).slot(Mth.clamp(slot, 0, SLOT_COUNT - 1)).program();
        }

        public void replaceProgram(UUID ownerId, int slot, AbilityProgram program) {
            var changed = programBook(ownerId).replaceSlot(
                    Mth.clamp(slot, 0, SLOT_COUNT - 1), program);
            installBook(changed);
        }

        /**
         * Legacy editor adapter. New runtime code should consume {@link #programBook(UUID)}.
         */
        public PrecisionGraph slot(int slot) {
            normalizeData(this);
            var exported = PrecisionProgramExporter.export(cachedBook.slot(Mth.clamp(slot, 0, 3)).program());
            return exported.valid() ? exported.graph() : PrecisionGraph.EMPTY;
        }

        public PrecisionGraph legacySlot(int slot) {
            normalizeLegacyData(this);
            return slots.get(Mth.clamp(slot, 0, 3));
        }

        /**
         * Legacy editor adapter. The next owner-aware read migrates all four slots as one book.
         */
        public void replaceSlot(int slot, PrecisionGraph graph) {
            var validation = graph == null ? PrecisionGraph.EMPTY.validate() : graph.validate();
            if (!validation.valid()) throw new IllegalArgumentException(validation.diagnostic().name());
            var legacy = legacySnapshot();
            var index = Mth.clamp(slot, 0, 3);
            legacy.set(index, validation.normalized());
            slots = legacy;
            revision++;
            schemaVersion = 3;
            encodedBook = "";
            cachedBook = null;
        }

        public Data copy() {
            var copy = new Data();
            copy.setProficiency(getProficiency());
            copy.setEnabled(isEnabled());
            copy.schemaVersion = schemaVersion;
            copy.revision = revision;
            copy.slots = slots == null ? null : new ArrayList<>(slots);
            copy.encodedBook = encodedBook;
            copy.cachedBook = cachedBook;
            return copy;
        }

        private void installBook(ProgramBook book) {
            book = PrecisionProgramAliases.canonicalize(book.resize(SLOT_COUNT));
            cachedBook = book;
            encodedBook = Base64.getEncoder().encodeToString(ProgramBookCodec.encode(book));
            revision = book.revision();
            schemaVersion = SCHEMA_VERSION;
            slots = null;
        }

        private ArrayList<PrecisionGraph> legacySnapshot() {
            if (cachedBook != null || encodedBook != null && !encodedBook.isBlank()) {
                normalizeData(this);
                var legacy = new ArrayList<PrecisionGraph>(4);
                for (var slot = 0; slot < 4; slot++) {
                    var exported = PrecisionProgramExporter.export(cachedBook.slot(slot).program());
                    if (!exported.valid()) {
                        throw new IllegalStateException("Program cannot be represented by the legacy editor");
                    }
                    legacy.add(exported.graph());
                }
                return legacy;
            }
            normalizeLegacyData(this);
            return new ArrayList<>(slots);
        }

        @Override
        public Identifier getType() {
            return ID;
        }
    }

}
