package org.academy.api.client.config

import net.minecraft.resources.Identifier
import org.academy.api.common.ability.Skill
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.IntConsumer
import java.util.function.IntSupplier
import kotlin.math.roundToInt

/**
 * Registers client-side, per-skill settings displayed by the Skill Settings app.
 * Modules keep unrelated skill options isolated and allow new settings to be added
 * without changing the app implementation.
 */
object SkillSettingsRegistry {
    private val modulesBySkill = LinkedHashMap<Identifier, LinkedHashMap<String, Module>>()

    @JvmStatic
    @Synchronized
    fun register(skill: Skill, module: Module) {
        register(skill.key, module)
    }

    @JvmStatic
    @Synchronized
    fun register(skillId: Identifier, module: Module) {
        val modules = modulesBySkill.getOrPut(skillId) { LinkedHashMap() }
        check(modules.putIfAbsent(module.id, module) == null) {
            "Skill settings module '${module.id}' is already registered for $skillId"
        }
    }

    @JvmStatic
    @Synchronized
    fun unregister(skillId: Identifier, moduleId: String) {
        modulesBySkill[skillId]?.let { modules ->
            modules.remove(moduleId)
            if (modules.isEmpty()) modulesBySkill.remove(skillId)
        }
    }

    @JvmStatic
    @Synchronized
    fun getModules(skill: Skill): List<Module> {
        return modulesBySkill[skill.key]?.values?.toList() ?: emptyList()
    }

    data class Module(
        val id: String,
        val titleKey: String,
        val entries: List<Entry>
    ) {
        init {
            require(id.isNotBlank()) { "Skill settings module id cannot be blank" }
            require(entries.map(Entry::id).distinct().size == entries.size) {
                "Skill settings module '$id' contains duplicate entry ids"
            }
        }
    }

    sealed interface Entry {
        val id: String
        val labelKey: String
    }

    data class Toggle(
        override val id: String,
        override val labelKey: String,
        val getter: BooleanSupplier,
        val setter: Consumer<Boolean>
    ) : Entry

    data class IntegerRange(
        override val id: String,
        override val labelKey: String,
        val min: Int,
        val max: Int,
        val step: Int = 1,
        val getter: IntSupplier,
        val setter: IntConsumer
    ) : Entry {
        init {
            require(min <= max) { "Integer setting '$id' has an invalid range" }
            require(step > 0) { "Integer setting '$id' must use a positive step" }
        }
    }

    fun interface FloatSupplier {
        fun getAsFloat(): Float
    }

    data class FloatRange(
        override val id: String,
        override val labelKey: String,
        val min: Float,
        val max: Float,
        val step: Float = 0.05f,
        val getter: FloatSupplier,
        val setter: Consumer<Float>,
        val commit: Runnable
    ) : Entry {
        init {
            require(min.isFinite() && max.isFinite() && min <= max) {
                "Float setting '$id' has an invalid range"
            }
            require(step.isFinite() && step > 0f) {
                "Float setting '$id' must use a positive finite step"
            }
        }

        fun quantize(value: Float): Float {
            if (!value.isFinite()) return min
            val index = ((value - min) / step).roundToInt()
            return (min + index * step).coerceIn(min, max)
        }
    }

    data class Action(
        override val id: String,
        override val labelKey: String,
        val buttonKey: String,
        val action: Runnable
    ) : Entry
}
