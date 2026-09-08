package org.academy.internal.common.ability;

import org.academy.api.common.registries.Registries;
import org.academy.internal.common.ability.program.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.ProgramNodeExtensionIndex;
import org.academy.internal.common.world.damagesource.AbilityDamageProfiles;
import java.util.ArrayList;
import java.util.Comparator;

/** All native and facade registrations pass through this same serial setup phase. */
public final class AbilityRegistrationFinalizer {
    private AbilityRegistrationFinalizer() {
    }

    public static void resolve() {
        var failures = new ArrayList<String>();
        Registries.SKILLS.keySet().stream().sorted(Comparator.comparing(Object::toString)).forEach(id -> {
            try {
                Registries.SKILLS.getValue(id).resolveRegistration();
            } catch (RuntimeException exception) {
                failures.add("skill " + id + ": " + exception.getMessage());
            }
        });
        if (!failures.isEmpty()) throw new IllegalStateException("Invalid Academy registrations:\n" + String.join("\n", failures));
        AbilityDamageProfiles.freeze();
        AbilityProgramDefinitions.includeRegisteredCategories();
        ProgramNodeExtensionIndex.freeze();
    }
}
