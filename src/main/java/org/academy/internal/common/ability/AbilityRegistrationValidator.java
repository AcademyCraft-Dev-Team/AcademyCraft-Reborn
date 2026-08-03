package org.academy.internal.common.ability;

import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.LearningHelper;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SkillScope;
import org.academy.api.common.registries.Registries;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
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
