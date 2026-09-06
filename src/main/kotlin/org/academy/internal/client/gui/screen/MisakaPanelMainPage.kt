package org.academy.internal.client.gui.screen

import net.minecraft.network.chat.Component
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*
import org.academy.internal.common.world.entity.misaka.MisakaPersonality
import org.academy.internal.common.world.entity.misaka.MobRelation
import org.academy.internal.common.world.entity.misaka.WanderStyle

internal object MisakaPanelMainPage {
    fun build(host: MisakaPanelHost): LinearLayoutWidget {
        val page = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = MisakaNetworkPanelScreen.SPACING_MINOR
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }

        page.addChild("title", LabelWidget(host.screenTitle).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
                .gravity(Gravity.CENTER)
        })
        page.addChild("title_rule", FillWidget(MisakaNetworkPanelScreen.PRIMARY_FOREGROUND).apply {
            alpha = 0.8f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(1f)
                .marginTop(1f)
                .marginBottom(1f)
        })

        page.addChild(
            "serial",
            host.infoRow(
                Component.translatable("screen.academy.misaka_serial_label").string,
                Component.translatable("screen.academy.misaka_serial_value", host.data.serial()).string
            )
        )
        if (host.data.reconstructionWork()) {
            page.addChild(
                "badge",
                host.statusLine(
                    Component.translatable("screen.academy.misaka_reconstruction_badge").string,
                    MisakaNetworkPanelScreen.ACCENT_CAPACITY
                )
            )
        }
        page.addChild(
            "perception",
            host.infoRow(
                Component.translatable("screen.academy.misaka_perception_label").string,
                host.data.perception().toString()
            )
        )
        page.addChild(
            "msk",
            host.infoRow(
                Component.translatable("screen.academy.misaka_msk_label").string,
                Component.translatable("screen.academy.misaka_msk_value", "%.1f".format(host.data.msk())).string
            )
        )
        page.addChild(
            "personality",
            host.infoRow(
                Component.translatable("screen.academy.misaka_personality_label").string,
                personalityName(host)
            )
        )
        page.addChild(
            "node",
            host.infoRow(
                Component.translatable("screen.academy.misaka_node_label").string,
                host.data.currentNodeName().ifEmpty {
                    Component.translatable("screen.academy.misaka_node_none").string
                }
            )
        )
        page.addChild(
            "relation",
            host.infoRow(
                Component.translatable("screen.academy.misaka_relation_label").string,
                relationName(host)
            )
        )
        if (host.data.reconstructionBlocked()) {
            page.addChild(
                "reconstruction",
                host.statusLine(
                    Component.translatable("screen.academy.misaka_reconstruction_blocked").string,
                    MisakaNetworkPanelScreen.ACCENT_WARNING
                )
            )
        }

        if (host.data.privilege()) {
            page.addChild("section_rule_wander", host.sectionRule())
            page.addChild(
                "wander_label",
                host.sectionLabel(Component.translatable("screen.academy.misaka_wander_style").string)
            )
            val wanderRow = LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                spacing = MisakaNetworkPanelScreen.SPACING_MINOR
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(MisakaNetworkPanelScreen.LIST_ITEM_HEIGHT)
            }
            page.addChild("wander_row", wanderRow)
            wanderRow.addChild("waiting", host.styleButton(WanderStyle.WAITING))
            wanderRow.addChild("free", host.styleButton(WanderStyle.FREE_MOVE))
            wanderRow.addChild("follow", host.styleButton(WanderStyle.FOLLOW))
        }

        if (host.data.reconstructionWork() && host.data.privilege() && host.data.currentNodeName().isNotEmpty()) {
            page.addChild("section_rule_manage", host.sectionRule())
            page.addChild("manage_entry", host.manageMenuEntry())
        }

        val canBind = host.data.relation() >= MobRelation.DEFAULT.ordinal
        if (canBind && host.data.availableNodes().isNotEmpty()) {
            page.addChild("section_rule_nodes", host.sectionRule())
            page.addChild("bind_entry", host.bindMenuEntry())
        }

        page.addChild("section_rule_close", host.sectionRule())
        page.addChild("close", host.textActionButton(
            Component.translatable("screen.academy.misaka_close").string
        ) { host.onClose() })
        return page
    }

    private fun personalityName(host: MisakaPanelHost): String {
        val values = MisakaPersonality.entries
        val index = host.data.personality()
        if (index !in values.indices) {
            return Component.translatable("screen.academy.misaka_unknown").string
        }
        return Component.translatable("misaka.personality.${values[index].serializedName}").string
    }

    private fun relationName(host: MisakaPanelHost): String {
        val values = MobRelation.entries
        val index = host.data.relation()
        if (index !in values.indices) {
            return Component.translatable("screen.academy.misaka_unknown").string
        }
        return Component.translatable("misaka.relation.${values[index].name.lowercase()}").string
    }


}
