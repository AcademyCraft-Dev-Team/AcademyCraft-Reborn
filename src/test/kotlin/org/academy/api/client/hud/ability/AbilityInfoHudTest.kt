package org.academy.api.client.hud.ability

import net.minecraft.resources.Identifier
import org.academy.api.common.ability.AbilityCategory
import org.academy.api.common.ability.AbilityResourceSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional

class AbilityInfoHudTest {
    @Test
    fun `resource HUD requires category opt-in`() {
        assertFalse(shouldShowAbilityResource(PlainCategory(), 100f))
        assertTrue(shouldShowAbilityResource(ResourceCategory(), 100f))
    }

    @Test
    fun `resource HUD rejects unavailable capacity`() {
        val category = ResourceCategory()
        assertFalse(shouldShowAbilityResource(category, 0f))
        assertFalse(shouldShowAbilityResource(category, Float.NaN))
        assertFalse(shouldShowAbilityResource(category, Float.POSITIVE_INFINITY))
    }

    private open class PlainCategory : AbilityCategory(1f) {
        override fun getDeveloperIcon(): Identifier = Identifier.parse("academy:test")

        override fun getDisplayName(): String = "Test"
    }

    private class ResourceCategory : PlainCategory() {
        override fun getResourceSpec(): Optional<AbilityResourceSpec> =
            Optional.of(AbilityResourceSpec(0.2f, 2f))
    }
}
