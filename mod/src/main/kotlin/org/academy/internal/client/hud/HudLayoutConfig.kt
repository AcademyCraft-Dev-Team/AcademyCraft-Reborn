package org.academy.internal.client.hud

import com.google.gson.annotations.SerializedName
import org.academy.AcademyCraftClient
import org.academy.AcademyCraftConfig
import org.academy.api.common.gson.TypeHandler

object HudLayoutConfig {
    const val CONFIG_KEY = "hud_layout"

    private var config: Config? = null

    @JvmStatic
    fun init() {
        AcademyCraftConfig.registerTypeHandler(CONFIG_KEY, Config.Action.INSTANCE)
        config = AcademyCraftClient.Config.INSTANCE.getConfig(CONFIG_KEY)
    }

    @JvmStatic
    fun get(): Config {
        if (config == null) init()
        return config!!
    }

    @JvmStatic
    fun save() {
        AcademyCraftClient.Config.INSTANCE.setConfig(CONFIG_KEY, get())
        AcademyCraftClient.Config.INSTANCE.save()
    }

    fun state(id: String): RegionState = get().regions.getOrPut(id) { RegionState() }

    class RegionState {
        @SerializedName("translateX")
        var translateX: Float = 0f

        @SerializedName("translateY")
        var translateY: Float = 0f

        @SerializedName("scaleXY")
        var scaleXY: Float = 1f

        @SerializedName("hidden")
        var hidden: Boolean = false
    }

    class Config {
        @SerializedName("regions")
        var regions: MutableMap<String, RegionState> = LinkedHashMap()

        class Action private constructor() : TypeHandler<Config> {
            override fun getDefault(): Config = Config()

            override fun getTypeClass(): Class<Config> = Config::class.java

            companion object {
                val INSTANCE: TypeHandler<Config> = Action()
            }
        }
    }
}
