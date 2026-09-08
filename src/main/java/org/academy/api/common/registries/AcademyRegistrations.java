package org.academy.api.common.registries;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.program.ProgramNodeType;
import org.academy.api.common.damage.AbilityDamageProfile;
import java.util.Objects;

/** Optional convenience facade. Native DeferredRegister/RegisterEvent entries use the same finalization. */
public final class AcademyRegistrations {
    private final DeferredRegister<AbilityCategory> categories;
    private final DeferredRegister<Skill> skills;
    private final DeferredRegister<AbilityDamageProfile> damageProfiles;
    private final DeferredRegister<ProgramNodeType<?>> programNodes;
    private boolean bound;

    private AcademyRegistrations(String modId) {
        Identifier.fromNamespaceAndPath(Objects.requireNonNull(modId), "content");
        categories = DeferredRegister.create(Registries.Keys.ABILITY_CATEGORIES, modId);
        skills = DeferredRegister.create(Registries.Keys.SKILLS, modId);
        damageProfiles = DeferredRegister.create(Registries.Keys.DAMAGE_PROFILES, modId);
        programNodes = DeferredRegister.create(Registries.Keys.PROGRAM_NODE_TYPES, modId);
    }

    public static AcademyRegistrations create(String modId) { return new AcademyRegistrations(modId); }
    public DeferredRegister<AbilityCategory> categories() { return categories; }
    public DeferredRegister<Skill> skills() { return skills; }
    public DeferredRegister<AbilityDamageProfile> damageProfiles() { return damageProfiles; }
    public DeferredRegister<ProgramNodeType<?>> programNodes() { return programNodes; }

    public synchronized void bind(IEventBus modEventBus) {
        Objects.requireNonNull(modEventBus);
        if (bound) throw new IllegalStateException("Academy registrations already bound");
        categories.register(modEventBus);
        skills.register(modEventBus);
        damageProfiles.register(modEventBus);
        programNodes.register(modEventBus);
        bound = true;
    }
}
