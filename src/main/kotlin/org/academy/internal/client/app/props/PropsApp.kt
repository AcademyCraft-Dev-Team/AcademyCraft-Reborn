package org.academy.internal.client.app.props

import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.Minecraft
import net.minecraft.locale.Language
import net.minecraft.resources.Identifier
import org.academy.api.client.app.App
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.ButtonWidget
import org.academy.api.client.gui.widget.FillWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.ImageWidget
import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.TextBoxWidget
import org.academy.api.client.gui.widget.ToggleButtonWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.api.client.gui.widget.WidgetContext
import org.academy.api.client.hud.terminal.TerminalHud
import org.academy.api.client.resources.R
import org.academy.api.common.attribute.AbilityFactor
import org.academy.api.common.attribute.PlayerAttributes
import org.academy.internal.common.attribute.PropsMath
import org.academy.internal.common.attribute.PropsPackets
import org.misaka.MisakaNetworkClient
import java.util.Locale

object PropsApp : App {
    override fun createContext(): WidgetContext = Context()

    override fun name(): String = tr("app.academy.props.name")

    override fun icon(): Identifier = PropsIcon.LOCATION

    private class Context : WidgetContext {
        private var displayedStarted = PropsClientState.isStarted()
        private var consoleStatus = ""
        private val root = object : FrameLayoutWidget() {
            init {
                layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            }

            override fun tick() {
                val started = PropsClientState.isStarted()
                if (displayedStarted != started) {
                    displayedStarted = started
                    rebuild()
                }
                super.tick()
            }
        }

        init {
            rebuild()
        }

        override fun get(): Widget = root

        private fun rebuild() {
            root.clearChildren()
            root.addChild("content", if (displayedStarted) createDashboard() else createConsole())
        }

        private fun createDashboard(): LinearLayoutWidget = LinearLayoutWidget().apply {
                orientation = Orientation.VERTICAL
                spacing = 1f
                layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
                addChild("top_bar", createTopBar())
                addChild("separator", FillWidget(0xBFFFFFFF.toInt()).apply {
                    layoutParams = LinearLayoutWidget.LayoutParams().widthMode(SizeMode.MATCH_PARENT).height(1f).padding(2f, 0f)
                })
                addChild("main", LinearLayoutWidget().apply {
                    orientation = Orientation.HORIZONTAL
                    spacing = 4f
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .weight(1f)
                        .widthMode(SizeMode.MATCH_PARENT)
                        .padding(5f, 4f)
                    addChild("overview", LinearLayoutWidget().apply {
                        orientation = Orientation.VERTICAL
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .weight(1f)
                            .heightMode(SizeMode.MATCH_PARENT)
                        addChild("chart", RadarChartWidget().apply {
                            layoutParams = LinearLayoutWidget.LayoutParams()
                                .weight(1f)
                                .widthMode(SizeMode.MATCH_PARENT)
                        })
                        addChild("footer", dynamicLabel { footerText() }.apply {
                            scale = 0.68f
                            layoutParams = LinearLayoutWidget.LayoutParams()
                                .widthMode(SizeMode.MATCH_PARENT)
                                .height(22f)
                                .gravity(Gravity.CENTER)
                        })
                    })
                    addChild("rows", LinearLayoutWidget().apply {
                        orientation = Orientation.VERTICAL
                        spacing = 2f
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .width(178f)
                            .heightMode(SizeMode.MATCH_PARENT)
                        AbilityFactor.values().forEach { factor -> addChild(factor.name.lowercase(), createFactorRow(factor)) }
                    })
                })
                addChild("warning", dynamicLabel {
                    if (PropsClientState.isLocked(AbilityFactor.NEURAL_ACTIVITY)) tr("app.academy.props.neural_lock_warning") else ""
                }.apply {
                    scale = 0.68f
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(9f)
                        .gravity(Gravity.CENTER)
                })
        }

        private fun createConsole(): LinearLayoutWidget = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 1f
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            addChild("top_bar", createTopBar())
            addChild("separator", FillWidget(0xBFFFFFFF.toInt()).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(1f)
                    .padding(2f, 0f)
            })
            addChild("console", LinearLayoutWidget().apply {
                orientation = Orientation.VERTICAL
                spacing = 5f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .widthMode(SizeMode.MATCH_PARENT)
                    .padding(18f, 16f)
                addChild("heading", LabelWidget(tr("app.academy.props.console.heading")).apply {
                    scale = 1.15f
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(18f)
                        .gravity(Gravity.CENTER_LEFT)
                })
                addChild("description", LabelWidget(tr("app.academy.props.console.description")).apply {
                    scale = 0.78f
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(24f)
                        .gravity(Gravity.CENTER_LEFT)
                })
                addChild("prompt", LinearLayoutWidget().apply {
                    orientation = Orientation.HORIZONTAL
                    spacing = 3f
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(18f)
                    addChild("marker", LabelWidget(">").apply {
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .width(8f)
                            .heightMode(SizeMode.MATCH_PARENT)
                            .gravity(Gravity.CENTER)
                    })
                    addChild("input", TextBoxWidget(16).apply {
                        background = org.academy.api.client.gui.drawable.ColorDrawable(0x28000000)
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .weight(1f)
                            .heightMode(SizeMode.MATCH_PARENT)
                            .padding(4f, 1f)
                        setClearWhenEnter(true)
                        setWhenEnter { command ->
                            if (command.trim().equals("start", ignoreCase = true)) {
                                consoleStatus = tr("app.academy.props.console.starting")
                                MisakaNetworkClient.send(PropsPackets.StartPacket.INSTANCE)
                            } else {
                                consoleStatus = tr("app.academy.props.console.invalid")
                            }
                        }
                    })
                })
                addChild("status", dynamicLabel { consoleStatus }.apply {
                    scale = 0.72f
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(12f)
                        .gravity(Gravity.CENTER_LEFT)
                })
            })
        }

        private fun createTopBar(): LinearLayoutWidget = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
            addChild("back", ButtonWidget().apply {
                layoutParams = LinearLayoutWidget.LayoutParams().margin(2f, 2f, 2f, 0f).size(16f, 16f)
                onClickListener = { TerminalHud.INSTANCE.closeApp() }
                addChild("arrow", ImageWidget(R.textures.gui.icon.arrow_back).apply {
                    setSampler(FilterMode.LINEAR, false)
                    layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
                })
            })
            addChild("title", LabelWidget(tr("app.academy.props.title")).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).height(0f).gravity(Gravity.CENTER)
            })
        }

        private fun createFactorRow(factor: AbilityFactor): FrameLayoutWidget = FrameLayoutWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).widthMode(SizeMode.MATCH_PARENT)
            background = org.academy.api.client.gui.drawable.ColorDrawable(0x28000000)
            addChild("line", LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                spacing = 2f
                layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT).padding(4f, 2f)
                addChild("text", LinearLayoutWidget().apply {
                    orientation = Orientation.VERTICAL
                    layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).heightMode(SizeMode.MATCH_PARENT)
                    addChild("value", dynamicLabel {
                        "%s  %s".format(Locale.ROOT, factorName(factor), formatDecimal(PropsClientState.get(factor)))
                    }.apply {
                        scale = 0.78f
                        layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).widthMode(SizeMode.MATCH_PARENT).gravity(Gravity.CENTER_LEFT)
                    })
                    addChild("effect", dynamicLabel { effectText(factor) }.apply {
                        scale = 0.58f
                        layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).widthMode(SizeMode.MATCH_PARENT).gravity(Gravity.CENTER_LEFT)
                    })
                })
                addChild("lock", ToggleButtonWidget().apply {
                    setChecked(!PropsClientState.isLocked(factor))
                    setOnCheckedChangeListener(object : ToggleButtonWidget.OnCheckedChangeListener {
                        override fun onCheckedChanged(toggle: ToggleButtonWidget, isChecked: Boolean) {
                            val locked = !isChecked
                            PropsClientState.setLockedLocally(factor, locked)
                            MisakaNetworkClient.send(PropsPackets.SetLockPacket(factor, locked))
                        }
                    })
                    layoutParams = LinearLayoutWidget.LayoutParams().size(18f, 10f).gravity(Gravity.CENTER)
                })
            })
        }

        private fun dynamicLabel(value: () -> String): LabelWidget = object : LabelWidget(value()) {
            override fun tick() {
                super.tick()
                val updated = value()
                if (text != updated) text = updated
            }
        }

        private fun effectText(factor: AbilityFactor): String {
            val player = Minecraft.getInstance().player
            val effective = player?.getAttributeValue(attribute(factor)) ?: PropsClientState.get(factor)
            return when (factor) {
                AbilityFactor.MUSCLE_STRENGTH -> tr("app.academy.props.effect.muscle").format(Locale.ROOT, formatDecimal(PropsMath.muscleDamageBonus(effective)))
                AbilityFactor.ENDURANCE -> tr("app.academy.props.effect.endurance").format(Locale.ROOT, formatDecimal(PropsMath.enduranceHealthBonus(effective)))
                AbilityFactor.DEXTERITY -> tr("app.academy.props.effect.dexterity").format(Locale.ROOT, formatDecimal(effective * 0.2), formatDecimal(effective * 0.5))
                AbilityFactor.PERCEPTION -> tr("app.academy.props.effect.perception").format(Locale.ROOT, PropsMath.perceptionEnchantmentBonus(effective), formatDecimal((PropsMath.perceptionExperienceMultiplier(effective) - 1.0) * 100.0))
                AbilityFactor.NEURAL_ACTIVITY -> tr("app.academy.props.effect.neural").format(Locale.ROOT, formatDecimal((PropsMath.neuralIterationMultiplier(effective) - 1.0) * 100.0))
            }
        }

        private fun footerText(): String {
            val total = PropsClientState.total()
            val coefficient = PropsClientState.coefficient() * 100.0
            return if (total >= PropsMath.MAX_TOTAL) {
                tr("app.academy.props.footer.full").format(Locale.ROOT, formatDecimal(total))
            } else {
                tr("app.academy.props.footer").format(Locale.ROOT, formatDecimal(total), formatDecimal(coefficient))
            }
        }

        private fun formatDecimal(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
            .trimEnd('0')
            .trimEnd('.')

        private fun factorName(factor: AbilityFactor): String = when (factor) {
            AbilityFactor.MUSCLE_STRENGTH -> tr("attribute.name.academy.muscle_strength")
            AbilityFactor.ENDURANCE -> tr("attribute.name.academy.endurance")
            AbilityFactor.DEXTERITY -> tr("attribute.name.academy.dexterity")
            AbilityFactor.PERCEPTION -> tr("attribute.name.academy.perception")
            AbilityFactor.NEURAL_ACTIVITY -> tr("attribute.name.academy.neural_activity")
        }

        private fun attribute(factor: AbilityFactor) = when (factor) {
            AbilityFactor.MUSCLE_STRENGTH -> PlayerAttributes.MUSCLE_STRENGTH
            AbilityFactor.ENDURANCE -> PlayerAttributes.ENDURANCE
            AbilityFactor.DEXTERITY -> PlayerAttributes.DEXTERITY
            AbilityFactor.PERCEPTION -> PlayerAttributes.PERCEPTION
            AbilityFactor.NEURAL_ACTIVITY -> PlayerAttributes.NEURAL_ACTIVITY
        }
    }

    private fun tr(key: String): String = Language.getInstance().getOrDefault(key)
}
