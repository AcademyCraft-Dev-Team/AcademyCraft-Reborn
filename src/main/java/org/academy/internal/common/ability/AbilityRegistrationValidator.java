package org.academy.internal.common.ability;

import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.LearningHelper;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SkillProficiencyProfiles;
import org.academy.api.common.ability.SkillScope;
import org.academy.api.common.registries.Registries;

import java.util.*;
import java.util.function.Function;

/**
 * Verifies the ability registry after deferred skill dependencies have been resolved.
 */
public final class AbilityRegistrationValidator {
    private AbilityRegistrationValidator() {
    }

    public static void validate() {
        Set<AbilityCategory> registeredCategories = identitySet();
        Registries.ABILITY_CATEGORIES.forEach(registeredCategories::add);

        Set<Skill> registeredSkills = identitySet();
        Registries.SKILLS.forEach(registeredSkills::add);

        for (var category : registeredCategories) {
            var categoryKey = Registries.ABILITY_CATEGORIES.getKey(category);
            if (categoryKey == null) {
                throw new IllegalStateException("Unregistered ability category instance: " + category);
            }
            if (AcademyCraft.MOD_ID.equals(categoryKey.getNamespace())
                    && category != AbilityCategories.LEVEL0.get()
                    && category.getDevelopmentProfile().isEmpty()) {
                throw new IllegalStateException("Core ability category " + categoryKey
                        + " has no P.R.O.P.S development profile");
            }

            for (var skill : category.getSkills()) {
                if (skill.getScope() != SkillScope.CATEGORY) {
                    throw new IllegalStateException("Ability category " + categoryKey
                            + " contains non-category skill " + skill.getKeyString());
                }
                if (!registeredSkills.contains(skill)) {
                    throw new IllegalStateException("Ability category " + categoryKey
                            + " contains an unregistered skill instance: " + skill);
                }
                if (skill.getCategory() != category) {
                    throw new IllegalStateException("Skill " + skill.getKeyString()
                            + " is attached to the wrong ability category");
                }
            }
        }

        for (var skill : registeredSkills) {
            var skillKey = Registries.SKILLS.getKey(skill);
            if (skillKey == null) {
                throw new IllegalStateException("Unregistered skill instance: " + skill);
            }
            if (!registeredCategories.contains(skill.getCategory())) {
                throw new IllegalStateException("Skill " + skillKey + " references an unregistered category");
            }
            var attachedToOwner = skill.getCategory().getSkills().contains(skill);
            if (skill.getScope() == SkillScope.CATEGORY && !attachedToOwner) {
                throw new IllegalStateException("Category skill " + skillKey + " is missing from its category");
            }
            if (skill.getScope() == SkillScope.COMMON && attachedToOwner) {
                throw new IllegalStateException("Common skill " + skillKey + " must not be attached to one category");
            }
            if (skill.getScope() == SkillScope.CATEGORY
                    && !SkillProficiencyProfiles.isDeclared(skill.getKeyString())) {
                throw new IllegalStateException("Category skill " + skillKey
                        + " has no explicit proficiency declaration");
            }
            for (var dependency : skill.getDependencies()) {
                if (!registeredSkills.contains(dependency)) {
                    throw new IllegalStateException("Skill " + skillKey
                            + " references an unregistered dependency: " + dependency);
                }
                if (skill.getScope() == SkillScope.COMMON && dependency.getScope() != SkillScope.COMMON) {
                    throw new IllegalStateException("Common skill cannot depend on a category skill: "
                            + skillKey + " -> " + dependency.getKeyString());
                }
                if (skill.getScope() == SkillScope.CATEGORY
                        && !LearningHelper.isSkillAvailableForCategory(skill.getCategory(), dependency)) {
                    throw new IllegalStateException("Skill dependency is unavailable to its category: "
                            + skillKey + " -> " + dependency.getKeyString());
                }
            }
        }

        var categorySkillCount = registeredSkills.stream()
                .filter(skill -> skill.getScope() == SkillScope.CATEGORY)
                .count();
        if (categorySkillCount != SkillProficiencyProfiles.declaredSkillPaths().size()) {
            throw new IllegalStateException("Proficiency declaration count "
                    + SkillProficiencyProfiles.declaredSkillPaths().size()
                    + " does not match registered category skill count " + categorySkillCount);
        }

        validateAcyclic(
                registeredSkills,
                Skill::getDependencies,
                Skill::getKeyString
        );

        AcademyCraft.getLogger().info("Validated {} ability categories and {} skills.",
                registeredCategories.size(), registeredSkills.size());
    }

    static <T> void validateAcyclic(
            Collection<T> nodes,
            Function<T, ? extends Collection<T>> dependencyProvider,
            Function<T, String> nameProvider
    ) {
        Set<T> visited = identitySet();
        Set<T> visiting = identitySet();
        var path = new ArrayDeque<T>();

        for (var node : nodes) {
            visit(node, dependencyProvider, nameProvider, visited, visiting, path);
        }
    }

    private static <T> void visit(
            T node,
            Function<T, ? extends Collection<T>> dependencyProvider,
            Function<T, String> nameProvider,
            Set<T> visited,
            Set<T> visiting,
            ArrayDeque<T> path
    ) {
        if (visited.contains(node)) return;
        if (!visiting.add(node)) {
            var cycle = new StringBuilder();
            for (var entry : path) {
                if (!cycle.isEmpty()) cycle.append(" -> ");
                cycle.append(nameProvider.apply(entry));
            }
            if (!cycle.isEmpty()) cycle.append(" -> ");
            cycle.append(nameProvider.apply(node));
            throw new IllegalStateException("Ability skill dependency cycle: " + cycle);
        }

        path.addLast(node);
        for (var dependency : dependencyProvider.apply(node)) {
            visit(dependency, dependencyProvider, nameProvider, visited, visiting, path);
        }
        path.removeLast();
        visiting.remove(node);
        visited.add(node);
    }

    private static <T> Set<T> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
