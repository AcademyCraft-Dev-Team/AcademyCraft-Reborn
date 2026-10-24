package org.academy.internal.client.gui.screen

import net.minecraft.network.chat.Component
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.widget.*
import org.academy.internal.common.network.misaka.MisakaNetManageDataPacket
import org.academy.internal.common.network.misaka.MisakaPanelDataPacket
import org.academy.internal.common.world.entity.misaka.WanderStyle
import java.util.UUID

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
    var manageDemandMsk: Float
    var manageYourAllocatedMsk: Float
    var manageYourSatisfaction: Float
    var managePriorityAllocatedMsk: Float
    var manageSharedAllocatedMsk: Float
    /** Integrated network: player UUID → percent. Empty when unintegrated (alloc tab locked). */
    var manageIntegrated: Boolean
    var localPlayerPercents: MutableMap<UUID, Int>
    val allocSeekBars: MutableMap<UUID, SeekBarWidget>
    val allocInputs: MutableMap<UUID, TextInputWidget>
    var suppressAllocCallbacks: Boolean

    var sistersTabContent: FrameLayoutWidget
    var allocTabContent: FrameLayoutWidget
    var allocRowsHost: LinearLayoutWidget
    var membersTabContent: FrameLayoutWidget
    /** Current manage-page sister rows (source of truth for selection / rebuild). */
    var manageSisters: List<MisakaNetManageDataPacket.SisterSummary>
    var sistersRows: LinearLayoutWidget
    var selectedSisterUuid: UUID?
    var sisterSelectionLabel: TextWidget?
    var sisterDisconnectButton: ButtonWidget?
    var pageLabel: TextWidget
    var allocatedLabel: TextWidget
    var sistersTabButton: ButtonWidget
    var allocTabButton: ButtonWidget
    var membersTabButton: ButtonWidget
    var emptySistersLabel: TextWidget
    var networkTotalMskLabel: TextWidget
    var networkDemandLabel: TextWidget
    var networkSatisfactionLabel: TextWidget
    var membersListLabel: TextWidget
    var membersListHost: LinearLayoutWidget
    var membersAccessLabel: TextWidget
    var memberTargetLabel: TextWidget
    var membersPermHintLabel: TextWidget
    var memberCycleButton: ButtonWidget
    var memberGrantButton: ButtonWidget
    var memberRevokeButton: ButtonWidget
    var membersEditorHost: LinearLayoutWidget
    var manageAdminNames: List<String>
    var manageMembers: List<MisakaNetManageDataPacket.MemberSummary>
    var manageCanEditMembers: Boolean
    var memberNameInput: TextInputWidget
    var memberPermIndex: Int
    /** Player name currently targeted by grant/revoke (empty = no target). */
    var selectedMemberName: String

    val sistersRowsInitialized: Boolean
    val allocatedLabelInitialized: Boolean
    val networkTotalMskLabelInitialized: Boolean
    val networkDemandLabelInitialized: Boolean
    val networkSatisfactionLabelInitialized: Boolean
    val membersListLabelInitialized: Boolean
    val membersListHostInitialized: Boolean
    val membersAccessLabelInitialized: Boolean
    val memberTargetLabelInitialized: Boolean
    val membersPermHintLabelInitialized: Boolean
    val memberEditButtonsInitialized: Boolean
    val membersEditorHostInitialized: Boolean

    fun showMainPage()
    fun showBindPage()
    fun showManagePage()
    fun showManageTab(tab: MisakaPanelManageTab)
    fun onClose()

    fun infoRow(label: String, value: String): FrameLayoutWidget
    fun statusLine(text: String, accent: Int): FrameLayoutWidget
    fun sectionLabel(text: String): TextWidget
    fun sectionRule(): FillWidget
    fun styleButton(style: WanderStyle): ButtonWidget
    fun textActionButton(text: String, onClick: () -> Unit): ButtonWidget
    fun setWanderAnchor()
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
