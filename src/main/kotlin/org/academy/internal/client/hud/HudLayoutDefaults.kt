package org.academy.internal.client.hud

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import org.academy.AcademyCraft
import org.academy.api.client.gui.serialize.UiJson
import org.academy.api.client.gui.serialize.WidgetSerializer
import java.nio.file.Files

object HudLayoutDefaults {
    const val FILE_NAME = "hud_layout_defaults.json"

    enum class Anchor {
        TOP_LEFT,
        CENTER_LEFT,
        TOP_RIGHT,
        CENTER_RIGHT
    }

    data class RegionValue(
        var anchor: Anchor,
        var offsetX: Float,
        var offsetY: Float,
        var scale: Float
    )

    data class Config(val regions: MutableMap<String, RegionValue>) {
        fun copyDeep(): Config = Config(regions.mapValuesTo(LinkedHashMap()) { (_, value) -> value.copy() })
    }

    @Volatile
    private var loaded: Config? = null

    fun get(): Config {
        loaded?.let { return it }
        synchronized(this) {
            loaded?.let { return it }
            return load().also { loaded = it }
        }
    }

    fun region(name: String): RegionValue {
        return get().regions[name] ?: defaults().regions.getValue(name)
    }

    fun replace(config: Config) {
        loaded = config.copyDeep()
    }

    fun defaults(): Config = Config(
        linkedMapOf(
            "toggle_status" to RegionValue(Anchor.TOP_LEFT, 8f, 8f, 1f),
            "mental_control" to RegionValue(Anchor.CENTER_LEFT, 8f, 0f, 1f),
            "cp" to RegionValue(Anchor.TOP_RIGHT, -4f, 4f, 1f),
            "skill_wheel" to RegionValue(Anchor.CENTER_RIGHT, 0f, 0f, 1f)
        )
    )

    fun loadJson(json: JsonObject): Config {
        val result = defaults()
        val regions = json.getAsJsonObject("regions") ?: return result
        for ((name, fallback) in result.regions) {
            val value = regions.getAsJsonObject(name) ?: continue
            val anchor = runCatching {
                Anchor.valueOf(value.get("anchor")?.asString ?: fallback.anchor.name)
            }.getOrDefault(fallback.anchor)
            val x = value.get("offset_x")?.asFloat ?: fallback.offsetX
            val y = value.get("offset_y")?.asFloat ?: fallback.offsetY
            val scale = value.get("scale")?.asFloat ?: fallback.scale
            result.regions[name] = RegionValue(
                anchor,
                if (x.isFinite()) x else fallback.offsetX,
                if (y.isFinite()) y else fallback.offsetY,
                if (scale.isFinite()) scale.coerceIn(HudLayout.MIN_SCALE, HudLayout.MAX_SCALE) else fallback.scale
            )
        }
        return result
    }

    fun toJson(config: Config): JsonObject {
        val root = JsonObject()
        root.addProperty("version", 1)
        val regions = JsonObject()
        for ((name, value) in config.regions) {
            val region = JsonObject()
            region.addProperty("anchor", value.anchor.name)
            region.addProperty("offset_x", value.offsetX)
            region.addProperty("offset_y", value.offsetY)
            region.addProperty("scale", value.scale)
            regions.add(name, region)
        }
        root.add("regions", regions)
        return root
    }

    fun loadSourceJson(): JsonObject {
        val override = WidgetSerializer.layoutDir().resolve(FILE_NAME)
        if (Files.isRegularFile(override)) {
            runCatching { return JsonParser.parseString(Files.readString(override)).asJsonObject }
                .onFailure { AcademyCraft.getLogger().warn("[UiDebug] Invalid HUD defaults override {}", override, it) }
        }
        val id = AcademyCraft.academy("ui/layout/$FILE_NAME")
        return Minecraft.getInstance().resourceManager.open(id).use {
            UiJson.GSON.fromJson(it.reader(), JsonObject::class.java)
        }
    }

    private fun load(): Config = loadJson(loadSourceJson())
}
