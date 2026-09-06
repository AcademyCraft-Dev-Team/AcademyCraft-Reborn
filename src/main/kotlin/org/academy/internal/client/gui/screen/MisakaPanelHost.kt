package org.academy.internal.client.gui.screen

import net.minecraft.network.chat.Component
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.widget.*
import org.academy.internal.common.network.misaka.MisakaNetManageDataPacket
import org.academy.internal.common.network.misaka.MisakaPanelDataPacket
import org.academy.internal.common.world.entity.misaka.WanderStyle

/**
 * Host surface for Misaka panel page extracts. Implemented by [MisakaNetworkPanelScreen]
 * (no global singletons).
 */
internal interface MisakaPanelHost {
    val data: MisakaPanelDataPacket
    val screenTitle: String

    var manageTab: MisakaPanelManageTab
    var managePageIndex: Int
    var manageTotalCount: Int
    var manageTotalMsk: Float
    var localPercents: IntArray
    val allocSeekBars: Array<SeekBarWidget?>
    val allocInputs: Array<TextBoxWidget?>
    var suppressAllocCallbacks: Boolean

    var sistersTabContent: FrameLayoutWidget
    var allocTabContent: FrameLayoutWidget
    var sistersList: ListWidget<MisakaNetManageDataPacket.SisterSummary>
    var pageLabel: LabelWidget
    var allocatedLabel: LabelWidget
    var sistersTabButton: ButtonWidget
    var allocTabButton: ButtonWidget
    var emptySistersLabel: LabelWidget
    var networkTotalMskLabel: LabelWidget

    val allocatedLabelInitialized: Boolean

    fun showMainPage()
    fun showBindPage()
    fun showManagePage()
    fun showManageTab(tab: MisakaPanelManageTab)
    fun onClose()

    fun infoRow(label: String, value: String): FrameLayoutWidget
    fun statusLine(text: String, accent: Int): FrameLayoutWidget
    fun sectionLabel(text: String): LabelWidget
    fun sectionRule(): FillWidget
    fun styleButton(style: WanderStyle): ButtonWidget
    fun textActionButton(text: String, onClick: () -> Unit): ButtonWidget
    fun actionBackground(selected: Boolean): StateListDrawable
    fun createSubmenuTopBar(titleText: String): LinearLayoutWidget
    fun manageMenuEntry(): ButtonWidget
    fun bindMenuEntry(): ButtonWidget
    fun wirelessStyleNodeRow(
        nodeName: String,
        isConnected: Boolean,
        isNone: Boolean
    ): FrameLayoutWidget
}
