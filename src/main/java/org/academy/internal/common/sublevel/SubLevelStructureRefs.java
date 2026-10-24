package org.academy.internal.common.sublevel;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureRef;

import java.util.Objects;
import java.util.Optional;

/** Backend identity adapter between plot-backed SubLevels and backend-independent StructureRefs. */
public final class SubLevelStructureRefs {
    public static final Identifier BACKEND = AcademyCraft.academy("sublevel");

    private SubLevelStructureRefs() {
    }

    public static StructureRef of(SubLevel subLevel, Identifier dimension, long generation) {
        Objects.requireNonNull(subLevel, "subLevel");
        Objects.requireNonNull(dimension, "dimension");
        if (generation < 0) throw new IllegalArgumentException("generation cannot be negative");
        return new StructureRef(subLevel.id().value(), dimension, BACKEND, generation);
    }

    public static boolean isSubLevel(StructureRef reference) {
        return reference != null && BACKEND.equals(reference.backend());
    }

    public static Optional<SubLevelId> id(StructureRef reference) {
        return isSubLevel(reference) ? Optional.of(new SubLevelId(reference.id())) : Optional.empty();
    }

    public static Optional<SubLevelId> id(StructureRef reference, long generation) {
        return isSubLevel(reference) && reference.generation() == generation
                ? Optional.of(new SubLevelId(reference.id())) : Optional.empty();
    }
}
