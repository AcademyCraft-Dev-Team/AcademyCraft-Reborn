package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*
import org.academy.internal.common.world.inventory.AerospaceSignalCabinMenu
import org.academy.internal.common.world.level.block.entity.AerospaceSignalCabinBlockEntity
import org.academy.internal.common.world.level.block.entity.AerospaceSignalCabinBlockEntity.ManagedSatRow
import org.academy.internal.common.world.level.block.entity.AerospaceSignalCabinBlockEntity.RetargetNetRow
import org.academy.internal.server.misaka.MisakaNetworkLasers.LaserRow

/**
 * Ops tab UI for [AerospaceSignalCabinScreen]: satellite list/detail, laser/network pick
 * menus, force-crash confirm, and related refresh/format helpers.
 */
internal class AerospaceCabinOpsUi(
    private val host: Host
) {
    interface Host {
        val blockEntity: AerospaceSignalCabinBlockEntity
        val cabinMenu: AerospaceSignalCabinMenu
        val client: Minecraft
        fun createActionButton(labelKey: String, buttonId: Int): ButtonWidget
        fun createLocalActionButton(labelKey: String, onClick: () -> Unit): ButtonWidget
    }

    private var opsCountSetter: (String) -> Unit = {}
    private var detailTitleSetter: (String) -> Unit = {}
    private var detailBodySetter: (String) -> Unit = {}
    private var detailFeedbackSetter: (String) -> Unit = {}
    private var detailNetworkSetter: (String) -> Unit = {}
    private var detailLaserSetter: (String) -> Unit = {}
    private var satListColumn: LinearLayoutWidget? = null
    private var satListHeader: LinearLayoutWidget? = null
    private var networkListColumn: LinearLayoutWidget? = null
    private var laserListColumn: LinearLayoutWidget? = null
    private var assetPane: FrameLayoutWidget? = null
    private var assetEntryButton: ButtonWidget? = null
    private var assetUi: DeviceAssetUi? = null
    private var viewingAsset = false
    private var lastSatList: List<ManagedSatRow>? = null
    private var lastNetworkList: List<RetargetNetRow>? = null
    private var lastLaserList: List<LaserRow>? = null
    private var lastSatCanView: Boolean? = null
    private var opsListPane: FrameLayoutWidget? = null
    private var opsDetailPane: FrameLayoutWidget? = null
    private var laserPickPane: FrameLayoutWidget? = null
    private var networkPickPane: FrameLayoutWidget? = null
    private var forceConfirmOverlay: FrameLayoutWidget? = null
    private var forceCountdownSetter: (String) -> Unit = {}
    private var forceCountdownLabel: LabelWidget? = null
    private var lastManageOps: Boolean? = null
    private var forceArmButton: ButtonWidget? = null
    private var forceCancelButton: ButtonWidget? = null
    private var bindDesignatorButton: ButtonWidget? = null
    private var unbindDesignatorButton: ButtonWidget? = null
    private var permMatrixHost: LinearLayoutWidget? = null
    private var lastPermTier: Int? = null
    private var strikeStatusSetter: (String) -> Unit = {}
    private var viewingDetail: Boolean = false
    private var viewingLaserPick: Boolean = false
    private var viewingNetworkPick: Boolean = false
    private var pendingDetailRow: ManagedSatRow? = null
    private var forceConfirmOpen: Boolean = false
    private var cachedSatColWidths: SatColWidths? = null

    fun onContainerTick() {
        opsCountSetter(opsCountText())
        refreshSatelliteListUi()
        refreshPermTierUi()
        // Asset entry lives on the list pane; keep owner gating live even before a row is opened.
        refreshManageOpsUi()
        if (viewingDetail && !viewingLaserPick && !viewingNetworkPick) {
            detailTitleSetter(detailTitleText())
            detailBodySetter(detailBodyText())
            detailLaserSetter(detailLaserText())
            detailNetworkSetter(detailNetworkText())
            detailFeedbackSetter(opsFeedbackText())
            refreshForceCrashUi()
            refreshStrikeStatusUi()
        }
        if (viewingLaserPick) {
            detailLaserSetter(detailLaserText())
            refreshLaserListUi()
        }
        if (viewingNetworkPick) {
            detailNetworkSetter(detailNetworkText())
            refreshNetworkListUi()
        }
    }

    fun createOpsPage(): FrameLayoutWidget {
        val page = FrameLayoutWidget()
        page.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)

        val listPane = createOpsListPane()
        val detailPane = createOpsDetailPane()
        val laserPane = createLaserPickPane()
        val networkPane = createNetworkPickPane()
        val confirmOverlay = createForceCrashConfirmOverlay()
        val asset = DeviceAssetUi(
            devicePos = host.blockEntity.blockPos,
            isOwner = { viewerIsOwner() },
            onClose = { closeAssetPane() }
        )
        val assetPaneWidget = asset.build()

        page.addChild("list", listPane)
        page.addChild("detail", detailPane)
        page.addChild("laser_pick", laserPane)
        page.addChild("network_pick", networkPane)
        page.addChild("asset", assetPaneWidget)
        page.addChild("force_confirm", confirmOverlay)
        opsListPane = listPane
        opsDetailPane = detailPane
        laserPickPane = laserPane
        networkPickPane = networkPane
        assetUi = asset
        assetPane = assetPaneWidget
        forceConfirmOverlay = confirmOverlay
        // List active; other layers GONE until opened.
        showOpsLayer(listPane)
        refreshSatelliteListUi(force = true)
        refreshManageOpsUi()
        return page
    }

    fun createForceCrashConfirmOverlay(): FrameLayoutWidget {
        val overlay = FrameLayoutWidget()
        overlay.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)

        overlay.addChild("dim", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.72f
        })

        val panel = FrameLayoutWidget()
        panel.layoutParams = FrameLayoutWidget.LayoutParams()
            .width(160f)
            .height(96f)
            .gravity(Gravity.CENTER)

        panel.addChild("back", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.55f
        })

        val column = LinearLayoutWidget()
        column.orientation = Orientation.VERTICAL
        column.spacing = 4f
        column.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)
            .margin(10f, 10f, 10f, 10f)

        column.addChild("title", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_force_crash_confirm_title").string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(14f)
            scale = 0.85f
        })

        column.addChild("body", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_force_crash_confirm_body").string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(36f)
            scale = 0.7f
            alpha = 0.92f
        })

        val actions = LinearLayoutWidget()
        actions.orientation = Orientation.HORIZONTAL
        actions.spacing = 8f
        actions.layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(18f)
            .margin(0f, 4f, 0f, 0f)

        actions.addChild("dismiss", host.createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_force_crash_confirm_no") {
            closeForceCrashConfirm()
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(18f)
        })

        actions.addChild("confirm", host.createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_force_crash_confirm_yes") {
            closeForceCrashConfirm()
            host.client.gameMode?.handleInventoryButtonClick(
                host.cabinMenu.containerId,
                AerospaceSignalCabinMenu.BUTTON_FORCE_CRASH
            )
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(18f)
        })

        column.addChild("actions", actions)
        panel.addChild("column", column)
        overlay.addChild("panel", panel)
        return overlay
    }

    fun createOpsListPane(): FrameLayoutWidget {
        val pane = FrameLayoutWidget()
        pane.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)

        pane.addChild("back", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.5f
        })

        val column = LinearLayoutWidget()
        column.orientation = Orientation.VERTICAL
        column.spacing = 2f
        column.layoutParams = FrameLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .heightMode(SizeMode.MATCH_PARENT)
            .gravity(Gravity.TOP)
            .margin(6f, 6f, 6f, 6f)

        // Title row doubles as the asset entry so the owner action costs no extra height.
        val titleRow = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 4f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(12f)
        }
        titleRow.addChild("title", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_title").string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(12f)
                .gravity(Gravity.CENTER_VERTICAL)
            scale = SCALE_TITLE
        })
        val assetEntry = host.createLocalActionButton("gui.academy.device_asset.entry") {
            openAssetPane()
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(44f)
                .height(12f)
            val owner = viewerIsOwner()
            visibility = if (owner) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
            isEnabled = owner
        }
        assetEntryButton = assetEntry
        titleRow.addChild("asset_entry", assetEntry)
        column.addChild("title", titleRow)

        val permMatrix = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 1f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(54f)
        }
        permMatrixHost = permMatrix
        lastPermTier = null
        refreshPermTierUi()
        column.addChild("perm_tiers", permMatrix)

        column.addChild("list_hint", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_hint").string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(12f)
            scale = SCALE_SECTION
            alpha = 0.72f
        })

        val countLabel = LabelWidget(opsCountText()).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
            scale = SCALE_BODY
        }
        opsCountSetter = { countLabel.text = it }
        column.addChild("count", countLabel)

        val header = satelliteColumnsRow(
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_col_index").string,
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_col_dim").string,
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_col_phase").string,
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_col_power").string,
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_col_kind").string,
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_col_id").string,
            header = true
        ).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(11f)
                .margin(0f, 2f, 0f, 0f)
        }
        satListHeader = header
        column.addChild("header", header)

        // LaunchPad pattern: weight host FrameLayout, ScrollPanel MATCH_PARENT inside.
        // A direct weight ScrollPanel can stay height=0 (no hits) while labels still paint
        // because zero scissor fail-opens in CommandExecutor.
        val listHost = FrameLayoutWidget().apply {
            // Fixed viewport: weight+0 can stay height 0 on first Ops layout while labels still paint.
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(SAT_LIST_VIEWPORT_HEIGHT)
                .margin(0f, 2f, 0f, 0f)
        }
        val listScroll = ScrollPanelWidget(Orientation.VERTICAL).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
        }
        val listColumn = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 2f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .heightMode(SizeMode.WRAP_CONTENT)
        }
        satListColumn = listColumn
        listScroll.setContent(listColumn)
        listHost.addChild("scroll", listScroll)
        column.addChild("sat_list", listHost)

        pane.addChild("column", column)
        return pane
    }

    fun createOpsDetailPane(): FrameLayoutWidget {
        val pane = FrameLayoutWidget()
        pane.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)

        pane.addChild("back", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.5f
        })

        // Body scroll clips the face; laser/network pick lists live on separate layers.
        val bodyScroll = ScrollPanelWidget(Orientation.VERTICAL).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .margin(6f, 6f, 6f, 6f)
        }
        val column = LinearLayoutWidget()
        column.orientation = Orientation.VERTICAL
        column.spacing = DETAIL_SPACING
        column.layoutParams = FrameLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .heightMode(SizeMode.WRAP_CONTENT)

        val topBar = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 4f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(14f)
        }
        topBar.addChild("back_btn", host.createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_back") {
            closeOpsDetail()
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(44f)
                .height(14f)
        })
        val title = LabelWidget(detailTitleText()).apply {
            scale = SCALE_TITLE
            alpha = 0.95f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(12f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        detailTitleSetter = { title.text = it }
        topBar.addChild("title", title)
        column.addChild("top_bar", topBar)

        val status = LabelWidget(detailBodyText()).apply {
            scale = SCALE_BODY
            alpha = 0.88f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        detailBodySetter = { status.text = it }
        column.addChild("status", status)

        val laserStatus = LabelWidget(detailLaserText()).apply {
            scale = SCALE_BODY
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        detailLaserSetter = { laserStatus.text = it }
        column.addChild("laser_status", laserStatus)

        val networkStatus = LabelWidget(detailNetworkText()).apply {
            scale = SCALE_BODY
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        detailNetworkSetter = { networkStatus.text = it }
        column.addChild("network_status", networkStatus)

        val forceCountdown = LabelWidget("").apply {
            scale = SCALE_BODY
            alpha = 0.9f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
            visibility = Widget.Visibility.GONE
        }
        forceCountdownSetter = { forceCountdown.text = it }
        forceCountdownLabel = forceCountdown
        column.addChild("force_countdown", forceCountdown)

        val forceActions = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 4f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(14f)
        }
        val forceArm = host.createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_force_crash") {
            openForceCrashConfirm()
        }.apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(72f)
                .height(14f)
        }
        forceArmButton = forceArm
        forceActions.addChild("force_crash", forceArm)

        val forceCancel = host.createActionButton(
            "gui.academy.aerospace_signal_cabin.ops_force_crash_cancel",
            AerospaceSignalCabinMenu.BUTTON_CANCEL_FORCE_CRASH
        ).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(72f)
                .height(14f)
            visibility = Widget.Visibility.GONE
            isEnabled = false
        }
        forceCancelButton = forceCancel
        forceActions.addChild("force_crash_cancel", forceCancel)
        column.addChild("force_actions", forceActions)

        val strikeStatus = LabelWidget("").apply {
            scale = SCALE_BODY
            alpha = 0.88f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        strikeStatusSetter = { strikeStatus.text = it }
        column.addChild("strike_status", strikeStatus)

        val designatorActions = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 4f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(14f)
        }
        designatorActions.addChild(
            "bind_designator",
            host.createActionButton(
                "gui.academy.aerospace_signal_cabin.ops_bind_designator",
                AerospaceSignalCabinMenu.BUTTON_BIND_DESIGNATOR
            ).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .width(72f)
                    .height(14f)
                bindDesignatorButton = this
            }
        )
        designatorActions.addChild(
            "unbind_designator",
            host.createActionButton(
                "gui.academy.aerospace_signal_cabin.ops_unbind_designator",
                AerospaceSignalCabinMenu.BUTTON_UNBIND_DESIGNATOR
            ).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .width(72f)
                    .height(14f)
                unbindDesignatorButton = this
            }
        )
        column.addChild("designator_actions", designatorActions)

        val feedback = LabelWidget(opsFeedbackText()).apply {
            scale = SCALE_BODY
            alpha = 0.78f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        detailFeedbackSetter = { feedback.text = it }
        column.addChild("feedback", feedback)

        val pickMenus = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 4f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(14f)
                .margin(0f, 2f, 0f, 0f)
        }
        pickMenus.addChild(
            "open_laser",
            host.createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_menu_rebind_laser") {
                openLaserPick()
            }.apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(14f)
            }
        )
        pickMenus.addChild(
            "open_network",
            host.createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_menu_retarget_net") {
                openNetworkPick()
            }.apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(14f)
            }
        )
        column.addChild("pick_menus", pickMenus)

        bodyScroll.setContent(column)
        pane.addChild("body_scroll", bodyScroll)
        return pane
    }

    fun createLaserPickPane(): FrameLayoutWidget {
        return createOpsPickPane(
            titleKey = "gui.academy.aerospace_signal_cabin.ops_laser_pick",
            statusText = { detailLaserText() },
            bindStatus = { setter ->
                val prev = detailLaserSetter
                detailLaserSetter = { text ->
                    prev(text)
                    setter(text)
                }
            },
            bindColumn = { laserListColumn = it },
            onBack = { closeLaserPick() }
        )
    }

    fun createNetworkPickPane(): FrameLayoutWidget {
        return createOpsPickPane(
            titleKey = "gui.academy.aerospace_signal_cabin.ops_network_pick",
            statusText = { detailNetworkText() },
            bindStatus = { setter ->
                val prev = detailNetworkSetter
                detailNetworkSetter = { text ->
                    prev(text)
                    setter(text)
                }
            },
            bindColumn = { networkListColumn = it },
            onBack = { closeNetworkPick() }
        )
    }

    /**
     * Dedicated pick menu: short chrome + weight(1) scroll host (LaunchPad pattern).
     * Kept off the detail face so nested lists never compete with detail chrome.
     */
    private fun createOpsPickPane(
        titleKey: String,
        statusText: () -> String,
        bindStatus: ((String) -> Unit) -> Unit,
        bindColumn: (LinearLayoutWidget) -> Unit,
        onBack: () -> Unit
    ): FrameLayoutWidget {
        val pane = FrameLayoutWidget()
        pane.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)

        pane.addChild("back", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.5f
        })

        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = DETAIL_SPACING
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .margin(6f, 6f, 6f, 6f)
        }

        val topBar = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 4f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(14f)
        }
        topBar.addChild(
            "back_btn",
            host.createLocalActionButton("gui.academy.aerospace_signal_cabin.ops_back", onBack).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .width(44f)
                    .height(14f)
            }
        )
        topBar.addChild(
            "title",
            LabelWidget(Component.translatable(titleKey).string).apply {
                scale = SCALE_TITLE
                alpha = 0.95f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .height(12f)
                    .gravity(Gravity.CENTER_VERTICAL)
            }
        )
        column.addChild("top_bar", topBar)

        val status = LabelWidget(statusText()).apply {
            scale = SCALE_BODY
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        bindStatus { status.text = it }
        column.addChild("status", status)

        val listHost = FrameLayoutWidget().apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .height(0f)
                .margin(0f, 2f, 0f, 0f)
        }
        listHost.addChild("frame", BlendQuadWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            alpha = 0.22f
        })
        val scroll = ScrollPanelWidget(Orientation.VERTICAL).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .margin(2f, 2f, 2f, 2f)
        }
        val listColumn = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 1f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .heightMode(SizeMode.WRAP_CONTENT)
        }
        bindColumn(listColumn)
        scroll.setContent(listColumn)
        listHost.addChild("scroll", scroll)
        column.addChild("list", listHost)

        pane.addChild("column", column)
        return pane
    }

    fun openOpsDetail(row: ManagedSatRow) {
        if (!viewerCanViewDetail()) {
            return
        }
        pendingDetailRow = row
        host.client.gameMode?.handleInventoryButtonClick(
            host.cabinMenu.containerId,
            AerospaceSignalCabinMenu.BUTTON_SELECT_SAT_BASE + row.index
        )
        viewingDetail = true
        viewingLaserPick = false
        viewingNetworkPick = false
        viewingAsset = false
        forceConfirmOpen = false
        showOpsLayer(opsDetailPane)
        detailTitleSetter(detailTitleText())
        detailBodySetter(detailBodyText())
        detailLaserSetter(detailLaserText())
        detailNetworkSetter(detailNetworkText())
        detailFeedbackSetter(opsFeedbackText())
        refreshForceCrashUi()
        refreshStrikeStatusUi()
    }

    fun closeOpsDetail(immediate: Boolean = false) {
        pendingDetailRow = null
        viewingLaserPick = false
        viewingNetworkPick = false
        closeForceCrashConfirm(immediate = true)
        if (viewingAsset) {
            closeAssetPane(immediate = true)
            return
        }
        viewingDetail = false
        showOpsLayer(opsListPane)
    }

    fun openLaserPick() {
        if (!viewerCanViewDetail()) {
            return
        }
        viewingLaserPick = true
        viewingNetworkPick = false
        forceConfirmOpen = false
        showOpsLayer(laserPickPane)
        detailLaserSetter(detailLaserText())
        refreshLaserListUi(force = true)
    }

    fun closeLaserPick() {
        viewingLaserPick = false
        showOpsLayer(opsDetailPane)
    }

    fun openNetworkPick() {
        if (!viewerCanViewDetail()) {
            return
        }
        viewingNetworkPick = true
        viewingLaserPick = false
        forceConfirmOpen = false
        showOpsLayer(networkPickPane)
        detailNetworkSetter(detailNetworkText())
        refreshNetworkListUi(force = true)
    }

    fun closeNetworkPick() {
        viewingNetworkPick = false
        showOpsLayer(opsDetailPane)
    }

    fun openForceCrashConfirm() {
        if (!host.cabinMenu.viewerCanManageOps()) {
            return
        }
        val overlay = forceConfirmOverlay ?: return
        forceConfirmOpen = true
        overlay.cancelAnimations()
        overlay.visibility = Widget.Visibility.VISIBLE
        overlay.isEnabled = true
        overlay.alpha = 1f
        overlay.translationY = 0f
    }

    fun closeForceCrashConfirm(immediate: Boolean = false) {
        val overlay = forceConfirmOverlay ?: return
        forceConfirmOpen = false
        overlay.cancelAnimations()
        overlay.visibility = Widget.Visibility.GONE
        overlay.isEnabled = false
        overlay.alpha = 0f
        overlay.translationY = 0f
    }

    fun openAssetPane() {
        if (!viewerIsOwner()) {
            return
        }
        viewingAsset = true
        viewingDetail = false
        viewingLaserPick = false
        viewingNetworkPick = false
        forceConfirmOpen = false
        assetUi?.refresh(force = true)
        showOpsLayer(assetPane)
    }

    fun closeAssetPane(immediate: Boolean = false) {
        viewingAsset = false
        showOpsLayer(opsListPane)
    }

    /** Exactly one interactive layer; inactive layers are GONE (out of hit path). */
    private fun showOpsLayer(active: FrameLayoutWidget?) {
        val layers = listOf(
            opsListPane,
            opsDetailPane,
            laserPickPane,
            networkPickPane,
            assetPane,
            forceConfirmOverlay
        )
        for (layer in layers) {
            if (layer == null) continue
            layer.cancelAnimations()
            val interactive = when {
                layer === forceConfirmOverlay -> forceConfirmOpen
                else -> layer === active
            }
            if (interactive) {
                layer.visibility = Widget.Visibility.VISIBLE
                layer.isEnabled = true
                layer.alpha = 1f
            } else {
                layer.visibility = Widget.Visibility.GONE
                layer.isEnabled = false
                layer.alpha = 0f
            }
            layer.translationY = 0f
        }
        active?.requestLayout()
    }

    fun onOpsPageShown() {
        viewingDetail = false
        viewingLaserPick = false
        viewingNetworkPick = false
        viewingAsset = false
        forceConfirmOpen = false
        showOpsLayer(opsListPane)
        refreshSatelliteListUi(force = true)
        refreshManageOpsUi()
        opsListPane?.requestLayout()
    }

    fun refreshManageOpsUi() {
        val manage = host.cabinMenu.viewerCanManageOps()
        bindDesignatorButton?.let {
            it.isEnabled = manage
            it.alpha = if (manage) 1f else 0.45f
        }
        unbindDesignatorButton?.let {
            it.isEnabled = manage
            it.alpha = if (manage) 1f else 0.45f
        }
        if (lastManageOps != manage) {
            lastManageOps = manage
            // Permission can flip without list identity changing — rebuild so buttons re-gate.
            if (viewingLaserPick) {
                refreshLaserListUi(force = true)
            }
            if (viewingNetworkPick) {
                refreshNetworkListUi(force = true)
            }
        }
        val owner = viewerIsOwner()
        // Apply every tick: ContainerData can arrive while Ops is still GONE, and a
        // GONE→VISIBLE flip must be re-applied after the page is shown so the title row measures.
        assetEntryButton?.let {
            val target = if (owner) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
            if (it.visibility != target || it.isEnabled != owner) {
                it.visibility = target
                it.isEnabled = owner
            }
        }
        // Sat-list buttons bake canView at build time; rebuild when access catches up
        // (ContainerData / owner UUID often arrive after the first list build).
        val canView = viewerCanViewDetail()
        if (lastSatCanView != canView) {
            lastSatCanView = canView
            if (!viewingDetail && !viewingAsset) {
                refreshSatelliteListUi(force = true)
            }
        }
        if (!owner && viewingAsset) {
            closeAssetPane(immediate = true)
        }
        if (viewingAsset) {
            assetUi?.refresh()
        }
    }

    /** Menu ContainerData may lag one open; the cabin BE already carries the placer UUID. */
    private fun viewerIsOwner(): Boolean {
        if (host.cabinMenu.viewerPermissionTier() >= AerospaceSignalCabinMenu.PERM_TIER_OWNER) {
            return true
        }
        val player = host.client.player ?: return false
        return host.blockEntity.isOwner(player)
    }

    /** Access+ from synced tier, or owner via BE before ContainerData arrives. */
    private fun viewerCanViewDetail(): Boolean {
        return host.cabinMenu.viewerPermissionTier() >= 1 || viewerIsOwner()
    }

    fun refreshPermTierUi() {
        val hostWidget = permMatrixHost ?: return
        val tier = host.cabinMenu.viewerPermissionTier().coerceIn(0, 3)
        if (lastPermTier == tier && hostWidget.children.isNotEmpty()) {
            return
        }
        lastPermTier = tier
        hostWidget.clearChildren()
        fillPermMatrix(hostWidget, tier)
    }

    fun fillPermMatrix(column: LinearLayoutWidget, currentTier: Int) {
        fun t(key: String): String = Component.translatable(key).string
        val tierW = permTierColWidth()
        column.addChild(
            "hdr",
            permMatrixColumnsRow(
                action = t("gui.academy.aerospace_signal_cabin.ops_perm_matrix_op"),
                cells = arrayOf(
                    t("gui.academy.aerospace_signal_cabin.ops_perm_tier_none"),
                    t("gui.academy.aerospace_signal_cabin.ops_perm_tier_access"),
                    t("gui.academy.aerospace_signal_cabin.ops_perm_tier_manage"),
                    t("gui.academy.aerospace_signal_cabin.ops_perm_tier_owner")
                ),
                header = true,
                currentTier = currentTier,
                tierWidth = tierW
            )
        )
        val rows = listOf(
            "list" to booleanArrayOf(true, true, true, true),
            "view" to booleanArrayOf(false, true, true, true),
            "ops" to booleanArrayOf(false, false, true, true),
            "owner" to booleanArrayOf(false, false, false, true)
        )
        for ((name, allowed) in rows) {
            column.addChild(
                name,
                permMatrixColumnsRow(
                    action = t("gui.academy.aerospace_signal_cabin.ops_perm_matrix_$name"),
                    cells = Array(4) { i -> if (allowed[i]) "Y" else "N" },
                    header = false,
                    currentTier = currentTier,
                    tierWidth = tierW
                )
            )
        }
    }

    fun permMatrixColumnsRow(
        action: String,
        cells: Array<String>,
        header: Boolean,
        currentTier: Int,
        tierWidth: Float
    ): LinearLayoutWidget {
        val row = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = COL_SPACING
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        row.addChild("action", LabelWidget(action).apply {
            scale = 1f
            baseFontSize = LIST_FONT_SIZE
            alpha = if (header) 0.62f else 0.88f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        })
        for (i in 0 until 4) {
            val active = i == currentTier
            row.addChild("t$i", LabelWidget(cells[i]).apply {
                scale = 1f
                baseFontSize = LIST_FONT_SIZE
                alpha = when {
                    header && active -> 1f
                    header -> 0.62f
                    active -> 1f
                    else -> 0.78f
                }
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .width(tierWidth)
                    .height(10f)
                    .gravity(Gravity.CENTER_VERTICAL)
            })
        }
        return row
    }

    fun permTierColWidth(): Float {
        fun need(sample: String): Float =
            kotlin.math.ceil(
                LabelWidget.getTextWidth(sample, LIST_FONT_SIZE).toDouble()
            ).toFloat() + COL_PAD
        var max = need("Y")
        max = maxOf(max, need("N"))
        for (key in arrayOf(
            "gui.academy.aerospace_signal_cabin.ops_perm_tier_none",
            "gui.academy.aerospace_signal_cabin.ops_perm_tier_access",
            "gui.academy.aerospace_signal_cabin.ops_perm_tier_manage",
            "gui.academy.aerospace_signal_cabin.ops_perm_tier_owner"
        )) {
            max = maxOf(max, need(Component.translatable(key).string))
        }
        return max
    }

    fun refreshForceCrashUi() {
        val row = selectedSatRow()
        val manage = host.cabinMenu.viewerCanManageOps()
        val armed = row != null && row.forceCrashTicks > 0 && row.phase != "CRASHING"
        val canArm = manage && row != null && row.phase != "CRASHING" && row.forceCrashTicks <= 0
        val remainingSec = if (row != null && row.forceCrashTicks > 0) {
            (row.forceCrashTicks + 19) / 20
        } else {
            0
        }
        forceCountdownSetter(
            if (armed) {
                Component.translatable(
                    "gui.academy.aerospace_signal_cabin.ops_force_crash_countdown",
                    remainingSec
                ).string
            } else {
                ""
            }
        )
        forceCountdownLabel?.let {
            it.visibility = if (armed) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
        }
        forceArmButton?.let {
            it.visibility = if (canArm) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
            it.isEnabled = canArm
            it.alpha = if (canArm) 1f else 0.35f
        }
        forceCancelButton?.let {
            val show = manage && armed
            it.visibility = if (show) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
            it.isEnabled = show
        }
        if (armed && forceConfirmOpen) {
            closeForceCrashConfirm(immediate = true)
        }
        if (!manage) {
            val key = host.blockEntity.opsFeedbackKey
            if (key.isEmpty() && host.cabinMenu.viewerPermissionTier() < 2) {
                detailFeedbackSetter(
                    Component.translatable("gui.academy.aerospace_signal_cabin.ops_no_permission").string
                )
            }
        }
    }

    fun refreshStrikeStatusUi() {
        val row = selectedSatRow()
        strikeStatusSetter(strikeStatusText(row))
    }

    fun strikeStatusText(row: ManagedSatRow?): String {
        if (row == null) {
            return ""
        }
        val mode = row.strikeMode
        if (mode != "IDLE") {
            val modeLabel = when (mode) {
                "APPROACHING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_strike_approaching").string
                "FIRING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_strike_firing").string
                "RETURNING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_strike_returning").string
                else -> mode
            }
            return Component.translatable(
                "gui.academy.aerospace_signal_cabin.ops_strike_active",
                modeLabel
            ).string
        }
        if (row.strikeCooldownTicks > 0) {
            val sec = (row.strikeCooldownTicks + 19) / 20
            return Component.translatable(
                "gui.academy.aerospace_signal_cabin.ops_strike_cooldown",
                sec
            ).string
        }
        return Component.translatable("gui.academy.aerospace_signal_cabin.ops_strike_idle").string
    }

    fun refreshSatelliteListUi(force: Boolean = false) {
        val column = satListColumn ?: return
        val rows = host.blockEntity.managedSatelliteList
        if (!force && rows == lastSatList) {
            return
        }
        lastSatList = rows
        column.clearChildren()
        val hasRows = rows.isNotEmpty()
        satListHeader?.visibility = if (hasRows) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
        if (!hasRows) {
            column.addChild("empty", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_none").string).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(16f)
                scale = 0.75f
                alpha = 0.85f
            })
            return
        }
        rows.forEachIndexed { rowIndex, parsed ->
            if (rowIndex >= AerospaceSignalCabinMenu.BUTTON_SELECT_SAT_MAX) {
                return@forEachIndexed
            }
            val power = if (parsed.powered) {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_power_on").string
            } else {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_power_off").string
            }
            val kind = if (parsed.hyper) {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_kind_hyper").string
            } else {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_kind_normal").string
            }
            val columns = satelliteColumnsRow(
                parsed.index.toString(),
                formatDimPathShort(parsed.dimPath),
                phaseLabelShort(parsed.phase),
                power,
                kind,
                parsed.id8.take(ID_DISPLAY_LEN),
                header = false
            )
            val button = ButtonWidget()
            val canView = viewerCanViewDetail()
            // Keep enabled so hit-testing works even before ContainerData catches up; gate in onClick.
            button.isEnabled = true
            button.alpha = if (canView) 1f else 0.55f
            button.background = MisakaNetworkPanelScreen.actionBackground(false)
            button.onClickListener = OnClickListener {
                openOpsDetail(parsed)
            }
            button.addChild("cols", columns.apply {
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                    .gravity(Gravity.CENTER_VERTICAL)
                    .margin(2f, 0f, 2f, 0f)
            })
            button.layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(16f)
            column.addChild("sat_${parsed.index}", button)
        }
    }

    fun satelliteColumnsRow(
        index: String,
        dim: String,
        phase: String,
        power: String,
        kind: String,
        id: String,
        header: Boolean
    ): LinearLayoutWidget {
        val cols = satListColWidths()
        val row = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = COL_SPACING
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(if (header) 11f else 12f)
        }
        fun cell(text: String, width: Float): LabelWidget = LabelWidget(text).apply {
            scale = 1f
            baseFontSize = LIST_FONT_SIZE
            alpha = if (header) 0.62f else 0.92f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(width)
                .height(10f)
                .gravity(Gravity.CENTER_VERTICAL)
        }
        row.addChild("index", cell(index, cols.index))
        row.addChild("dim", cell(dim, cols.dim))
        row.addChild("phase", cell(phase, cols.phase))
        row.addChild("power", cell(power, cols.power))
        row.addChild("kind", cell(kind, cols.kind))
        row.addChild("id", cell(id, cols.id))
        return row
    }

    /**
     * Measure each column's content need, then share [LIST_ROW_BUDGET] by need ratio
     * (extra space spreads out when under budget; over budget shrinks proportionally).
     */
    fun satListColWidths(): SatColWidths {
        cachedSatColWidths?.let { return it }
        fun need(vararg samples: String): Float {
            var max = 0f
            for (sample in samples) {
                max = maxOf(max, LabelWidget.getTextWidth(sample, LIST_FONT_SIZE))
            }
            return kotlin.math.ceil(max.toDouble()).toFloat() + COL_PAD
        }
        fun t(key: String): String = Component.translatable(key).string
        val needs = floatArrayOf(
            need(
                t("gui.academy.aerospace_signal_cabin.ops_sat_col_index"),
                AerospaceSignalCabinMenu.BUTTON_SELECT_SAT_MAX.toString()
            ),
            need(
                t("gui.academy.aerospace_signal_cabin.ops_sat_col_dim"),
                t("gui.academy.aerospace_signal_cabin.ops_dim_short_overworld"),
                t("gui.academy.aerospace_signal_cabin.ops_dim_short_nether"),
                t("gui.academy.aerospace_signal_cabin.ops_dim_short_end")
            ),
            need(
                t("gui.academy.aerospace_signal_cabin.ops_sat_col_phase"),
                t("gui.academy.aerospace_signal_cabin.ops_list_phase_orbit"),
                t("gui.academy.aerospace_signal_cabin.ops_list_phase_launch"),
                t("gui.academy.aerospace_signal_cabin.ops_list_phase_crash")
            ),
            need(
                t("gui.academy.aerospace_signal_cabin.ops_sat_col_power"),
                t("gui.academy.aerospace_signal_cabin.ops_list_power_on"),
                t("gui.academy.aerospace_signal_cabin.ops_list_power_off")
            ),
            need(
                t("gui.academy.aerospace_signal_cabin.ops_sat_col_kind"),
                t("gui.academy.aerospace_signal_cabin.ops_list_kind_hyper"),
                t("gui.academy.aerospace_signal_cabin.ops_list_kind_normal")
            ),
            need(
                t("gui.academy.aerospace_signal_cabin.ops_sat_col_id"),
                "f".repeat(ID_DISPLAY_LEN)
            )
        )
        val gaps = COL_SPACING * (needs.size - 1)
        val available = (LIST_ROW_BUDGET - gaps).coerceAtLeast(needs.size.toFloat())
        val widths = allocateByNeed(needs, available)
        val fitted = SatColWidths(
            index = widths[0],
            dim = widths[1],
            phase = widths[2],
            power = widths[3],
            kind = widths[4],
            id = widths[5]
        )
        cachedSatColWidths = fitted
        return fitted
    }

    fun refreshNetworkListUi(force: Boolean = false) {
        val column = networkListColumn ?: return
        val rows = host.blockEntity.retargetNetworkList
        if (!force && rows == lastNetworkList) {
            return
        }
        lastNetworkList = rows
        column.clearChildren()
        if (rows.isEmpty()) {
            column.addChild("empty", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_no_networks").string).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(16f)
                scale = 0.75f
                alpha = 0.85f
            })
            return
        }
        rows.forEachIndexed { row, entry ->
            if (row >= AerospaceSignalCabinMenu.BUTTON_RETARGET_NET_MAX) {
                return@forEachIndexed
            }
            val name = entry.nodeName
            val current = entry.isCurrentSatNetwork
            val label = if (current) {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_network_row_current", name).string
            } else {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_network_row", name).string
            }
            column.addChild(
                "net_$row",
                createOpsListSelectButton(
                    label = label,
                    buttonId = AerospaceSignalCabinMenu.BUTTON_RETARGET_NET_BASE + row,
                    enabled = host.cabinMenu.viewerCanManageOps() && !current,
                    selected = current
                )
            )
        }
    }

    fun refreshLaserListUi(force: Boolean = false) {
        val column = laserListColumn ?: return
        val rows = host.blockEntity.rebindLaserList
        if (!force && rows == lastLaserList) {
            return
        }
        lastLaserList = rows
        column.clearChildren()
        if (rows.isEmpty()) {
            column.addChild("empty", LabelWidget(Component.translatable("gui.academy.aerospace_signal_cabin.ops_no_lasers").string).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(16f)
                scale = 0.75f
                alpha = 0.85f
            })
            return
        }
        rows.forEachIndexed { row, entry ->
            if (row >= AerospaceSignalCabinMenu.BUTTON_REBIND_LASER_MAX) {
                return@forEachIndexed
            }
            val pos = entry.pos
            val coords = "${pos.x},${pos.y},${pos.z}"
            val ready = entry.ready
            val label = if (ready) {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_laser_row_ready", coords).string
            } else {
                Component.translatable("gui.academy.aerospace_signal_cabin.ops_laser_row_unready", coords).string
            }
            column.addChild(
                "laser_$row",
                createOpsListSelectButton(
                    label = label,
                    buttonId = AerospaceSignalCabinMenu.BUTTON_REBIND_LASER_BASE + row,
                    enabled = host.cabinMenu.viewerCanManageOps() && ready
                )
            )
        }
    }

    fun selectedSatRow(): ManagedSatRow? {
        val selected = host.blockEntity.selectedSatelliteIndex
        val pending = pendingDetailRow
        val rows = host.blockEntity.managedSatelliteList
        if (pending != null) {
            val synced = rows.firstOrNull { it.index == pending.index }
            if (synced != null) {
                if (selected == pending.index) {
                    pendingDetailRow = null
                }
                return synced
            }
            return pending
        }
        return rows.firstOrNull { it.index == selected }
    }

    fun phaseLabel(phase: String): String {
        return when (phase) {
            "ORBIT" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_phase_orbit").string
            "LAUNCHING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_phase_launch").string
            "CRASHING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_phase_crash").string
            else -> phase
        }
    }

    fun phaseLabelShort(phase: String): String {
        return when (phase) {
            "ORBIT" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_phase_orbit").string
            "LAUNCHING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_phase_launch").string
            "CRASHING" -> Component.translatable("gui.academy.aerospace_signal_cabin.ops_list_phase_crash").string
            else -> phase
        }
    }

    fun formatDimPathShort(path: String): String {
        val key = when (path) {
            "overworld" -> "gui.academy.aerospace_signal_cabin.ops_dim_short_overworld"
            "the_nether" -> "gui.academy.aerospace_signal_cabin.ops_dim_short_nether"
            "the_end" -> "gui.academy.aerospace_signal_cabin.ops_dim_short_end"
            else -> null
        }
        return if (key != null) {
            Component.translatable(key).string
        } else {
            path.take(4)
        }
    }

    fun formatDimPathFull(path: String): String = Companion.formatDimPathFull(path)


    fun opsCountText(): String {
        val total = host.blockEntity.managedSatelliteCount
        return if (total <= 0) {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_none").string
        } else {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_sat_count", total).string
        }
    }

    fun detailTitleText(): String {
        val row = selectedSatRow()
        return if (row == null) {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_detail_missing").string
        } else {
            Component.translatable(
                "gui.academy.aerospace_signal_cabin.ops_detail_title",
                row.index,
                row.id8
            ).string
        }
    }

    fun detailNetworkText(): String {
        val name = host.blockEntity.selectedSatNetworkName
        return if (name.isNullOrBlank()) {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_network_unknown").string
        } else {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_network_current", name).string
        }
    }

    fun detailLaserText(): String {
        val row = selectedSatRow()
            ?: return Component.translatable("gui.academy.aerospace_signal_cabin.ops_detail_missing").string
        if (row.laserBound && row.powered) {
            if (row.unpoweredTicks > 0) {
                val debtSec = (row.unpoweredTicks + 19) / 20
                return Component.translatable(
                    "gui.academy.aerospace_signal_cabin.ops_laser_recovering",
                    debtSec
                ).string
            }
            return Component.translatable("gui.academy.aerospace_signal_cabin.ops_laser_bound").string
        }
        if (!row.laserBound) {
            val remainingTicks = (row.crashTimeout - row.unpoweredTicks).coerceAtLeast(0)
            val remainingSec = (remainingTicks + 19) / 20
            return Component.translatable(
                "gui.academy.aerospace_signal_cabin.ops_laser_unbound_countdown",
                remainingSec
            ).string
        }
        // Laser still bound but not feeding (sky/power failure) — same countdown.
        val remainingTicks = (row.crashTimeout - row.unpoweredTicks).coerceAtLeast(0)
        val remainingSec = (remainingTicks + 19) / 20
        return Component.translatable(
            "gui.academy.aerospace_signal_cabin.ops_laser_unpowered_countdown",
            remainingSec
        ).string
    }

    fun detailBodyText(): String {
        val row = selectedSatRow() ?: return Component.translatable("gui.academy.aerospace_signal_cabin.ops_detail_missing").string
        val power = if (row.powered) {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_power_on").string
        } else {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_power_off").string
        }
        val kind = if (row.hyper) {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_kind_hyper").string
        } else {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_kind_normal").string
        }
        return Component.translatable(
            "gui.academy.aerospace_signal_cabin.ops_detail_body",
            formatDimPathFull(row.dimPath),
            phaseLabel(row.phase),
            power,
            kind
        ).string
    }

    fun opsFeedbackText(): String {
        val key = host.blockEntity.opsFeedbackKey
        return if (key.isNullOrEmpty()) {
            Component.translatable("gui.academy.aerospace_signal_cabin.ops_detail_idle").string
        } else {
            Component.translatable(key).string
        }
    }

    /** Shared row for ops network / laser select lists (left-aligned label, inventory button id). */
    fun createOpsListSelectButton(
        label: String,
        buttonId: Int,
        enabled: Boolean = true,
        selected: Boolean = false
    ): ButtonWidget = MisakaMachineUi.menuListSelectButton(
        menu = host.cabinMenu,
        label = label,
        buttonId = buttonId,
        enabled = enabled,
        selected = selected,
        scale = SCALE_SECTION
    )


    companion object {
        const val SCALE_TITLE = 0.8f
        const val SCALE_BODY = 0.75f
        const val SCALE_SECTION = 0.7f
        const val DETAIL_SPACING = 2f
        const val LIST_FONT_SIZE = 7f
        const val COL_SPACING = 3f
        const val COL_PAD = 2f
        const val ID_DISPLAY_LEN = 4
        /** Guaranteed sat-list hit viewport (avoids weight/0 ScrollPanel dead clicks). */
        const val SAT_LIST_VIEWPORT_HEIGHT = 72f
        /** 176 face − 6×2 pane margin − 2×2 row inset. */
        const val LIST_ROW_BUDGET = 156f

        data class SatColWidths(
            val index: Float,
            val dim: Float,
            val phase: Float,
            val power: Float,
            val kind: Float,
            val id: Float
        )

        /** Share [available] across columns in proportion to each column's content need. */
        fun allocateByNeed(needs: FloatArray, available: Float): FloatArray {
            val sum = needs.sum().coerceAtLeast(0.001f)
            val widths = FloatArray(needs.size)
            var used = 0f
            for (i in needs.indices) {
                widths[i] = kotlin.math.floor((available * needs[i] / sum).toDouble()).toFloat()
                    .coerceAtLeast(1f)
                used += widths[i]
            }
            var rem = available - used
            if (rem == 0f) {
                return widths
            }
            val order = needs.indices.sortedByDescending { needs[it] }
            var step = 0
            while (rem != 0f && step < needs.size * 8) {
                val i = order[step % order.size]
                if (rem > 0f) {
                    widths[i] += 1f
                    rem -= 1f
                } else if (widths[i] > 1f) {
                    widths[i] -= 1f
                    rem += 1f
                }
                step++
            }
            return widths
        }

        fun formatDimPathFull(path: String): String {
            val key = when (path) {
                "overworld" -> "gui.academy.aerospace_signal_cabin.ops_dim_full_overworld"
                "the_nether" -> "gui.academy.aerospace_signal_cabin.ops_dim_full_nether"
                "the_end" -> "gui.academy.aerospace_signal_cabin.ops_dim_full_end"
                else -> null
            }
            return if (key != null) {
                Component.translatable(key).string
            } else {
                path
            }
        }
    }
}
