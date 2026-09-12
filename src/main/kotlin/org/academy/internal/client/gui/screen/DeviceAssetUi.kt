package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*
import org.academy.internal.common.network.misaka.TransferDeviceOwnerPacket
import org.misaka.MisakaNetworkClient

/**
 * Shared owner-only asset pane for physical Misaka devices (design §15.2).
 *
 * Candidates come from the client's own player list, so no device needs to sync one. The
 * transfer packet carries the chosen name rather than a row index, which keeps the confirmed
 * target exact even if players join or leave between picking and confirming. Transfer is
 * irreversible for the giver, so picking only arms the action and a second row confirms it.
 */
internal class DeviceAssetUi(
    private val devicePos: BlockPos,
    private val isOwner: () -> Boolean,
    private val onClose: () -> Unit
) {
    private var candidateColumn: LinearLayoutWidget? = null
    private var confirmLabel: LabelWidget? = null
    private var confirmButton: ButtonWidget? = null
    private var armedName: String? = null
    private var lastCandidates: List<String>? = null

    fun build(): FrameLayoutWidget {
        val pane = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        pane.addChild("back", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.5f
        })

        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 2f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.TOP)
                .margin(6f, 6f, 6f, 6f)
        }

        val topBar = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 4f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(BAR_HEIGHT)
        }
        topBar.addChild("back_btn", MisakaMachineUi.localActionButton(
            "gui.academy.device_asset.back"
        ) { onClose() }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(44f)
                .height(BAR_HEIGHT)
        })
        topBar.addChild("title", LabelWidget(
            Component.translatable("gui.academy.device_asset.title").string
        ).apply {
            scale = 0.8f
            alpha = 0.95f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(12f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        column.addChild("top_bar", topBar)

        column.addChild("warning", LabelWidget(
            Component.translatable("gui.academy.device_asset.warning").string
        ).apply {
            scale = 0.7f
            alpha = 0.78f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(20f)
        })

        column.addChild("rule", FillWidget(0xFFFFFFFF.toInt()).apply {
            alpha = 0.35f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(1f)
        })

        val listHost = FrameLayoutWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(0f)
        }
        val scroll = ScrollPanelWidget(Orientation.VERTICAL).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .margin(0f, 1f, 0f, 1f)
        }
        val rows = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 1f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .heightMode(SizeMode.WRAP_CONTENT)
        }
        candidateColumn = rows
        scroll.setContent(rows)
        listHost.addChild("scroll", scroll)
        column.addChild("candidates", listHost)

        val confirmRow = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 4f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(BAR_HEIGHT)
        }
        val armed = LabelWidget(armedText()).apply {
            scale = 0.7f
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        confirmLabel = armed
        confirmRow.addChild("armed", armed)
        val confirm = MisakaMachineUi.localActionButton(
            "gui.academy.device_asset.confirm"
        ) { sendTransfer() }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(52f)
                .height(BAR_HEIGHT)
        }
        confirmButton = confirm
        confirmRow.addChild("confirm", confirm)
        column.addChild("confirm_row", confirmRow)

        pane.addChild("column", column)
        refresh(force = true)
        return pane
    }

    /** Rebuilds the candidate rows when the online roster or the armed target changed. */
    fun refresh(force: Boolean = false) {
        val column = candidateColumn ?: return
        val candidates = onlineCandidates()
        if (armedName != null && armedName !in candidates) {
            armedName = null
        }
        if (!force && candidates == lastCandidates) {
            refreshConfirmRow()
            return
        }
        lastCandidates = candidates
        column.clearChildren()
        if (candidates.isEmpty()) {
            column.addChild("empty", LabelWidget(
                Component.translatable("gui.academy.device_asset.no_targets").string
            ).apply {
                scale = 0.75f
                alpha = 0.85f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(16f)
            })
        } else {
            candidates.forEachIndexed { index, name ->
                column.addChild("candidate_$index", candidateRow(name))
            }
        }
        refreshConfirmRow()
    }

    private fun candidateRow(name: String): ButtonWidget {
        val selected = name == armedName
        val button = ButtonWidget()
        button.layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(ROW_HEIGHT)
        button.onClickListener = OnClickListener {
            armedName = if (selected) null else name
            refresh(force = true)
        }
        button.isSelected = selected
        button.background = MisakaNetworkPanelScreen.actionBackground(selected)
        button.addChild("label", LabelWidget(name).apply {
            scale = 0.75f
            alpha = if (selected) 1f else 0.85f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER_LEFT)
                .margin(4f, 0f, 4f, 0f)
        })
        return button
    }

    private fun refreshConfirmRow() {
        confirmLabel?.text = armedText()
        val ready = armedName != null && isOwner()
        confirmButton?.let {
            it.isEnabled = ready
            it.alpha = if (ready) 1f else 0.4f
        }
    }

    private fun armedText(): String {
        val target = armedName
            ?: return Component.translatable("gui.academy.device_asset.pick_hint").string
        return Component.translatable("gui.academy.device_asset.armed", target).string
    }

    private fun sendTransfer() {
        val target = armedName ?: return
        if (!isOwner()) {
            return
        }
        armedName = null
        MisakaNetworkClient.send(TransferDeviceOwnerPacket(devicePos, target))
        refresh(force = true)
        onClose()
    }

    /** Every other online player, by real profile name so the server can resolve it. */
    private fun onlineCandidates(): List<String> {
        val connection = Minecraft.getInstance().connection ?: return emptyList()
        val self = Minecraft.getInstance().player?.gameProfile?.name
        return connection.onlinePlayers
            .mapNotNull { it.profile.name }
            .filter { it.isNotBlank() && it != self }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    companion object {
        private const val BAR_HEIGHT = 14f
        private const val ROW_HEIGHT = 14f
    }
}
