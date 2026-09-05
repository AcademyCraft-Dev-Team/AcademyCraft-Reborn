package org.academy.internal.client.gui.screen

import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.network.chat.Component
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.drawable.TextureDrawable
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.widget.*
import org.academy.api.client.resources.R
import org.academy.internal.common.network.misaka.MisakaPanelDataPacket
import org.academy.internal.common.network.misaka.SetMisakaNetworkNodePacket
import org.academy.internal.common.network.misaka.SetMisakaWanderStylePacket
import org.academy.internal.common.world.entity.misaka.MisakaPersonality
import org.academy.internal.common.world.entity.misaka.MobRelation
import org.academy.internal.common.world.entity.misaka.WanderStyle
import org.misaka.MisakaNetworkClient

class MisakaNetworkPanelScreen(
    private val data: MisakaPanelDataPacket
) : UiScreen(Component.translatable("screen.academy.misaka_network_panel")) {

    private lateinit var mainPage: LinearLayoutWidget
    private lateinit var bindPage: LinearLayoutWidget

    override fun onInit() {
        val panel = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.CENTER)
                .size(PANEL_WIDTH, PANEL_HEIGHT)
        }
        root.addChild("panel", panel)

        panel.addChild("background", FillWidget(ROOT_PLANE).apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        })
        panel.addChild("top_rule", FillWidget(PRIMARY_FOREGROUND).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.TOP)
                .height(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .marginHorizontal(4f)
        })
        panel.addChild("bottom_rule", FillWidget(PRIMARY_FOREGROUND).apply {
            alpha = 0.7f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.BOTTOM)
                .height(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .marginHorizontal(4f)
        })

        val pageHost = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(PANEL_INSET, 7f)
        }
        panel.addChild("page_host", pageHost)

        mainPage = buildMainPage()
        bindPage = buildBindPage()
        pageHost.addChild("main_page", mainPage)
        pageHost.addChild("bind_page", bindPage)
        showMainPage()
    }

    private fun buildMainPage(): LinearLayoutWidget {
        val page = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }

        page.addChild("title", LabelWidget(title.string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
                .gravity(Gravity.CENTER)
        })
        page.addChild("title_rule", FillWidget(PRIMARY_FOREGROUND).apply {
            alpha = 0.8f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(1f)
                .marginTop(1f)
                .marginBottom(1f)
        })

        page.addChild(
            "serial",
            infoRow(
                Component.translatable("screen.academy.misaka_serial_label").string,
                Component.translatable("screen.academy.misaka_serial_value", data.serial()).string
            )
        )
        if (data.reconstructionWork()) {
            page.addChild(
                "badge",
                statusLine(
                    Component.translatable("screen.academy.misaka_reconstruction_badge").string,
                    ACCENT_CAPACITY
                )
            )
        }
        page.addChild(
            "perception",
            infoRow(
                Component.translatable("screen.academy.misaka_perception_label").string,
                data.perception().toString()
            )
        )
        page.addChild(
            "msk",
            infoRow(
                Component.translatable("screen.academy.misaka_msk_label").string,
                Component.translatable("screen.academy.misaka_msk_value", "%.1f".format(data.msk())).string
            )
        )
        page.addChild(
            "personality",
            infoRow(
                Component.translatable("screen.academy.misaka_personality_label").string,
                personalityName()
            )
        )
        page.addChild(
            "node",
            infoRow(
                Component.translatable("screen.academy.misaka_node_label").string,
                data.currentNodeName().ifEmpty {
                    Component.translatable("screen.academy.misaka_node_none").string
                }
            )
        )
        page.addChild(
            "relation",
            infoRow(
                Component.translatable("screen.academy.misaka_relation_label").string,
                relationName()
            )
        )
        if (data.reconstructionBlocked()) {
            page.addChild(
                "reconstruction",
                statusLine(
                    Component.translatable("screen.academy.misaka_reconstruction_blocked").string,
                    ACCENT_WARNING
                )
            )
        }

        if (data.privilege()) {
            page.addChild("section_rule_wander", sectionRule())
            page.addChild(
                "wander_label",
                sectionLabel(Component.translatable("screen.academy.misaka_wander_style").string)
            )
            val wanderRow = LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                spacing = SPACING_MINOR
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(LIST_ITEM_HEIGHT)
            }
            page.addChild("wander_row", wanderRow)
            wanderRow.addChild("waiting", styleButton(WanderStyle.WAITING))
            wanderRow.addChild("free", styleButton(WanderStyle.FREE_MOVE))
            wanderRow.addChild("follow", styleButton(WanderStyle.FOLLOW))
        }

        val canBind = data.relation() >= MobRelation.DEFAULT.ordinal
        if (canBind && data.availableNodes().isNotEmpty()) {
            page.addChild("section_rule_nodes", sectionRule())
            page.addChild("bind_entry", bindMenuEntry())
        }

        page.addChild("section_rule_close", sectionRule())
        page.addChild("close", textActionButton(
            Component.translatable("screen.academy.misaka_close").string
        ) { onClose() })
        return page
    }

    private fun buildBindPage(): LinearLayoutWidget {
        val page = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            visibility = Widget.Visibility.GONE
            isEnabled = false
        }

        page.addChild("top_bar", createSubmenuTopBar())
        page.addChild("top_rule", FillWidget(PRIMARY_FOREGROUND).apply {
            alpha = 0.8f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(1f)
                .marginBottom(1f)
        })
        page.addChild("nodes", createNodeList())
        return page
    }

    private fun createSubmenuTopBar(): LinearLayoutWidget {
        val bar = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = SPACING_MINOR
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(LIST_ITEM_HEIGHT)
        }
        val back = ButtonWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .size(16f, 16f)
                .gravity(Gravity.CENTER_VERTICAL)
            onClickListener = { showMainPage() }
        }
        back.addChild("arrow", ImageWidget(R.textures.gui.icon.arrow_back).apply {
            setSampler(FilterMode.LINEAR, false)
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        })
        bar.addChild("back", back)
        bar.addChild("title", LabelWidget(
            Component.translatable("screen.academy.misaka_bind_node").string
        ).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER)
        })
        return bar
    }

    private fun bindMenuEntry(): ButtonWidget {
        val entry = ButtonWidget().apply {
            background = actionBackground(false)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(LIST_ITEM_HEIGHT)
            onClickListener = { showBindPage() }
        }
        val content = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .paddingHorizontal(7f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        entry.addChild("content", content)
        content.addChild("icon", ImageWidget(R.textures.gui.icon.icon_tonode).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .size(14f, 14f)
                .gravity(Gravity.CENTER)
        })
        content.addChild("label", LabelWidget(
            Component.translatable("screen.academy.misaka_bind_node").string
        ).apply {
            scale = 0.75f
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        content.addChild("count", LabelWidget(
            Component.translatable(
                "screen.academy.misaka_bind_node_count",
                data.availableNodes().size
            ).string
        ).apply {
            scale = 0.75f
            alpha = 0.7f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        content.addChild("chevron", LabelWidget("›").apply {
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        return entry
    }

    private fun showMainPage() {
        mainPage.visibility = Widget.Visibility.VISIBLE
        mainPage.isEnabled = true
        bindPage.visibility = Widget.Visibility.GONE
        bindPage.isEnabled = false
    }

    private fun showBindPage() {
        mainPage.visibility = Widget.Visibility.GONE
        mainPage.isEnabled = false
        bindPage.visibility = Widget.Visibility.VISIBLE
        bindPage.isEnabled = true
    }

    private fun createNodeList(): FrameLayoutWidget {
        val container = FrameLayoutWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(0f)
        }
        val scrollPanel = ScrollPanelWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .marginRight(SCROLLBAR_WIDTH + SPACING_MINOR)
        }
        container.addChild("scroll_panel", scrollPanel)

        val scrollBar = ScrollBarWidget(scrollPanel, Orientation.VERTICAL).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .width(SCROLLBAR_WIDTH)
                .heightMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER_RIGHT)
        }
        container.addChild("scroll_bar", scrollBar)

        val nodeList = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = SPACING_MICRO
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
        }
        scrollPanel.setContent(nodeList)

        for (nodeName in data.availableNodes()) {
            nodeList.addChild("node_$nodeName", bindRow(nodeName))
        }
        return container
    }

    private fun infoRow(label: String, value: String): FrameLayoutWidget {
        val row = FrameLayoutWidget().apply {
            background = ColorDrawable(ROW_PLANE)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(INFO_ROW_HEIGHT)
        }
        val content = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .paddingHorizontal(7f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        row.addChild("content", content)
        content.addChild("label", LabelWidget(label).apply {
            scale = 0.75f
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_LEFT)
        })
        content.addChild("value", LabelWidget(value).apply {
            scale = 0.75f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .height(10f)
                .gravity(Gravity.CENTER_RIGHT)
        })
        return row
    }

    private fun statusLine(text: String, accent: Int): FrameLayoutWidget {
        val row = FrameLayoutWidget().apply {
            background = ColorDrawable(ROW_PLANE)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(INFO_ROW_HEIGHT)
        }
        val content = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .paddingHorizontal(7f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        row.addChild("content", content)
        content.addChild("marker", FillWidget(accent).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .size(6.5f, 6.5f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        content.addChild("label", LabelWidget(text).apply {
            scale = 0.75f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        return row
    }

    private fun sectionLabel(text: String): LabelWidget = LabelWidget(text).apply {
        scale = 0.75f
        alpha = 0.7f
        layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(8f)
    }

    private fun sectionRule(): FillWidget = FillWidget(PRIMARY_FOREGROUND).apply {
        alpha = 0.35f
        layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(1f)
            .marginTop(2f)
            .marginBottom(1f)
    }

    private fun styleButton(style: WanderStyle): ButtonWidget {
        val button = ButtonWidget().apply {
            background = actionBackground(false)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .heightMode(SizeMode.MATCH_PARENT)
            onClickListener = {
                MisakaNetworkClient.send(
                    SetMisakaWanderStylePacket(data.entityUuid(), style.ordinal)
                )
            }
        }
        button.addChild("label", LabelWidget(wanderStyleName(style)).apply {
            scale = 0.75f
            alpha = 0.82f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER)
        })
        return button
    }

    private fun bindRow(nodeName: String): FrameLayoutWidget {
        val row = FrameLayoutWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(LIST_ITEM_HEIGHT)
        }
        row.addChild("back", FillWidget(LIST_ROW_FILL).apply {
            alpha = 0.25f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(2f, 2f)
        })
        val content = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER_VERTICAL)
                .paddingHorizontal(4f)
        }
        row.addChild("content", content)
        content.addChild("icon", ImageWidget(R.textures.gui.icon.icon_node).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .size(14f, 14f)
                .gravity(Gravity.CENTER)
        })
        content.addChild("name", LabelWidget(nodeName).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        val bind = ButtonWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .size(14f, 14f)
                .gravity(Gravity.CENTER)
            onClickListener = {
                MisakaNetworkClient.send(
                    SetMisakaNetworkNodePacket(data.entityUuid(), nodeName)
                )
            }
        }
        content.addChild("bind", bind)
        val iconContent = ImageWidget().apply {
            val resting = TextureDrawable(R.textures.gui.icon.icon_unconnected).apply {
                tintColor = 0xFFE6E6E6.toInt()
            }
            val active = TextureDrawable(R.textures.gui.icon.icon_unconnected).apply {
                tintColor = PRIMARY_FOREGROUND
            }
            background = StateListDrawable().apply {
                setDefault(resting)
                addState(Widget.HOVERED, active)
                addState(Widget.FOCUSED, active)
                addState(Widget.PRESSED, active)
            }
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        bind.addChild("content", iconContent)
        return row
    }

    private fun textActionButton(text: String, onClick: () -> Unit): ButtonWidget {
        val button = ButtonWidget().apply {
            background = actionBackground(false)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(LIST_ITEM_HEIGHT)
            onClickListener = { onClick() }
        }
        button.addChild("label", LabelWidget(text).apply {
            scale = 0.75f
            alpha = 0.82f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER)
        })
        return button
    }

    private fun actionBackground(selected: Boolean): StateListDrawable {
        val resting = ColorDrawable(if (selected) SELECTED_PLANE else ROW_PLANE)
        val hovered = ColorDrawable(HOVER_PLANE)
        return StateListDrawable().apply {
            setDefault(resting)
            addState(Widget.HOVERED, hovered)
            addState(Widget.FOCUSED, hovered)
            addState(Widget.PRESSED, ColorDrawable(SELECTED_PLANE))
        }
    }

    private fun personalityName(): String {
        val values = MisakaPersonality.entries
        val index = data.personality()
        if (index !in values.indices) {
            return Component.translatable("screen.academy.misaka_unknown").string
        }
        return Component.translatable("misaka.personality.${values[index].serializedName}").string
    }

    private fun relationName(): String {
        val values = MobRelation.entries
        val index = data.relation()
        if (index !in values.indices) {
            return Component.translatable("screen.academy.misaka_unknown").string
        }
        return Component.translatable("misaka.relation.${values[index].name.lowercase()}").string
    }

    private fun wanderStyleName(style: WanderStyle): String =
        Component.translatable("misaka.wander_style.${style.name.lowercase()}").string

    companion object {
        private const val PANEL_WIDTH = 280f
        private const val PANEL_HEIGHT = 248f
        private const val PANEL_INSET = 10f
        private const val SPACING_MICRO = 2f
        private const val SPACING_MINOR = 3f
        private const val INFO_ROW_HEIGHT = 14f
        private const val LIST_ITEM_HEIGHT = 18f
        private const val SCROLLBAR_WIDTH = 5f
        private const val ROOT_PLANE = 0x70000000
        private const val ROW_PLANE = 0x28000000
        private const val HOVER_PLANE = 0x40FFFFFF
        private const val SELECTED_PLANE = 0x50FFFFFF
        private const val LIST_ROW_FILL = 0xFFFFFFFF.toInt()
        private const val PRIMARY_FOREGROUND = 0xFFFFFFFF.toInt()
        private const val ACCENT_CAPACITY = 0xFFFF6C00.toInt()
        private const val ACCENT_WARNING = 0xFFFF6C00.toInt()
    }
}
