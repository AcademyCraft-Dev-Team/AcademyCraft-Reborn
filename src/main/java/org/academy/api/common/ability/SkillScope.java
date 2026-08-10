package org.academy.api.common.ability;

/**
 * Defines which selected ability categories may expose a skill.
 */
public enum SkillScope {
    /**
     * The skill is available only to its owning category.
     */
    CATEGORY,
    /**
     * The skill is available to every category that supports common skills.
     */
    COMMON
}
