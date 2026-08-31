package org.academy.api.client.hud.ability

import com.google.gson.Gson
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.resources.Identifier
import org.academy.api.client.ability.AbilitySystemClient
import org.academy.api.client.input.InputSystem
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

    @Test
    fun `HUD default migration is persisted and never reclaims a user binding`() {
        val name = "test_hud_wheel_up"
        val legacy = InputSystem.combo(
            InputSystem.InputType.KEYBOARD,
            InputConstants.KEY_UP,
            InputConstants.PRESS,
            InputSystem.ANY_MODIFIER
        )
        val current = InputSystem.combo(
            InputSystem.InputType.KEYBOARD,
            InputConstants.KEY_Z,
            InputConstants.PRESS,
            0
        )
        val config = AbilitySystemClient.Config()
        config.setKeyBinding(name, legacy)

        assertTrue(current == getHudBindingMigratingDefaults(
            config,
            name,
            InputConstants.KEY_Z,
            InputConstants.PRESS,
            InputConstants.KEY_UP
        ))

        val restarted = Gson().fromJson(
            Gson().toJson(config),
            AbilitySystemClient.Config::class.java
        )
        restarted.setKeyBinding(name, legacy)

        assertTrue(legacy == getHudBindingMigratingDefaults(
            restarted,
            name,
            InputConstants.KEY_Z,
            InputConstants.PRESS,
            InputConstants.KEY_UP
        ))
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
