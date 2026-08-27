package org.academy.internal.client.app.tutorial

import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.world.level.ItemLike
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.drawable.TextureDrawable
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.widget.*
import org.academy.api.client.resources.R
import org.academy.api.common.util.L10n
import java.util.function.Consumer
import net.minecraft.world.item.Items as VanillaItems
import org.academy.internal.common.world.item.Items as AcademyItems

object TutorialUi {
    const val WIDTH = 384f
    const val HEIGHT = 200f

    private const val NAV_WIDTH = 80f
    private const val PREVIEW_WIDTH = 122f
    private const val PROGRESSION_BLUE = 0xFF1177D6.toInt()
    private const val ROW_FILL = 0x28000000
    private const val ARTICLE_BODY_FONT_SIZE = 6f
    private const val ARTICLE_SUBTITLE_FONT_SIZE = 8f
    private const val RECIPE_SLOT_SIZE = 18f
    private const val RECIPE_SLOT_GAP = 2f

    private data class Recipe(
        val rows: List<List<ItemLike?>>,
        val result: ItemLike
    )

    private data class Page(
        val id: String,
        val navKey: String,
        val eyebrowKey: String,
        val titleKey: String,
        val briefKey: String,
        val bodyKeys: List<String>,
        val stage: Int? = null,
        val recipe: Recipe? = null,
        val previewKey: String? = null
    )

    private val pages = listOf(
        Page(
            "project", "app.academy.tutorial.nav.project", "app.academy.tutorial.eyebrow.project",
            "app.academy.tutorial.page.project.title", "app.academy.tutorial.page.project.brief",
            listOf("app.academy.tutorial.page.project.body.1", "app.academy.tutorial.page.project.body.2"),
            previewKey = "app.academy.tutorial.page.project.preview"
        ),
        Page(
            "route", "app.academy.tutorial.nav.route", "app.academy.tutorial.eyebrow.route",
            "app.academy.tutorial.page.route.title", "app.academy.tutorial.page.route.brief",
            listOf("app.academy.tutorial.page.route.body.1", "app.academy.tutorial.page.route.body.2"),
            stage = 0, previewKey = "app.academy.tutorial.page.route.preview"
        ),
        Page(
            "step_1", "app.academy.tutorial.nav.step.1", "app.academy.tutorial.eyebrow.tutorial",
            "app.academy.tutorial.page.step.1.title", "app.academy.tutorial.page.step.1.brief",
            listOf("app.academy.tutorial.page.step.1.body.1", "app.academy.tutorial.page.step.1.body.2"),
            stage = 1, previewKey = "app.academy.tutorial.page.step.1.preview"
        ),
        Page(
            "step_2", "app.academy.tutorial.nav.step.2", "app.academy.tutorial.eyebrow.tutorial",
            "app.academy.tutorial.page.step.2.title", "app.academy.tutorial.page.step.2.brief",
            listOf("app.academy.tutorial.page.step.2.body.1", "app.academy.tutorial.page.step.2.body.2"),
            stage = 2, previewKey = "app.academy.tutorial.page.step.2.preview"
        ),
        Page(
            "step_3", "app.academy.tutorial.nav.step.3", "app.academy.tutorial.eyebrow.tutorial",
            "app.academy.tutorial.page.step.3.title", "app.academy.tutorial.page.step.3.brief",
            listOf("app.academy.tutorial.page.step.3.body.1", "app.academy.tutorial.page.step.3.body.2"),
            stage = 3, previewKey = "app.academy.tutorial.page.step.3.preview"
        ),
        Page(
            "step_4", "app.academy.tutorial.nav.step.4", "app.academy.tutorial.eyebrow.tutorial",
            "app.academy.tutorial.page.step.4.title", "app.academy.tutorial.page.step.4.brief",
            listOf("app.academy.tutorial.page.step.4.body.1", "app.academy.tutorial.page.step.4.body.2"),
            stage = 4, previewKey = "app.academy.tutorial.page.step.4.preview"
        ),
        Page(
            "step_5", "app.academy.tutorial.nav.step.5", "app.academy.tutorial.eyebrow.tutorial",
            "app.academy.tutorial.page.step.5.title", "app.academy.tutorial.page.step.5.brief",
            listOf("app.academy.tutorial.page.step.5.body.1", "app.academy.tutorial.page.step.5.body.2"),
            stage = 5, previewKey = "app.academy.tutorial.page.step.5.preview"
        ),
        recipePage(
            "probe", AcademyItems.IMAG_PHASE_DOWSING_ROD.get(),
            listOf(
                recipeRow(null, VanillaItems.COMPARATOR, null),
                recipeRow(
                    VanillaItems.LIGHTNING_ROD.weathering().unaffected(),
                    VanillaItems.COMPASS,
                    VanillaItems.IRON_INGOT
                ),
                recipeRow(null, null, VanillaItems.IRON_INGOT)
            )
        ),
        recipePage(
            "solar", AcademyItems.SOLAR_GEN.get(),
            listOf(
                recipeRow(
                    VanillaItems.STAINED_GLASS_PANE.gray(),
                    VanillaItems.STAINED_GLASS_PANE.gray(),
                    VanillaItems.STAINED_GLASS_PANE.gray()
                ),
                recipeRow(
                    AcademyItems.IMAG_PHASE_INGOT.get(),
                    VanillaItems.DAYLIGHT_DETECTOR,
                    AcademyItems.IMAG_PHASE_INGOT.get()
                ),
                recipeRow(
                    AcademyItems.IMAG_PHASE_POLYMER.get(),
                    VanillaItems.REDSTONE,
                    AcademyItems.IMAG_PHASE_POLYMER.get()
                )
            )
        ),
        recipePage(
            "tablet", AcademyItems.ABILITY_CONTROL_TABLET.get(),
            listOf(
                recipeRow(
                    AcademyItems.IMAG_PHASE_PLATE.get(),
                    VanillaItems.COMPARATOR,
                    AcademyItems.WIND_GEN_BASE_SCREEN.get()
                ),
                recipeRow(
                    AcademyItems.IMAG_PHASE_PLATE.get(),
                    AcademyItems.IMAG_PHASE_CIRCUIT.get(),
                    VanillaItems.COMPARATOR
                ),
                recipeRow(
                    AcademyItems.IMAG_PHASE_INGOT.get(),
                    AcademyItems.IMAG_PHASE_PLATE.get(),
                    AcademyItems.IMAG_PHASE_PLATE.get()
                )
            )
        ),
        recipePage(
            "terminal", AcademyItems.DATA_TERMINAL.get(),
            listOf(
                recipeRow(VanillaItems.IRON_INGOT, VanillaItems.REDSTONE, VanillaItems.IRON_INGOT),
                recipeRow(VanillaItems.REDSTONE, VanillaItems.GLASS_PANE, VanillaItems.REDSTONE),
                recipeRow(VanillaItems.IRON_INGOT, VanillaItems.IRON_INGOT, VanillaItems.IRON_INGOT)
            )
        ),
        recipePage(
            "cloud", AcademyItems.TUTORIAL.get(),
            listOf(
                recipeRow(null, VanillaItems.AMETHYST_SHARD, null),
                recipeRow(VanillaItems.REDSTONE, VanillaItems.BOOK, VanillaItems.REDSTONE),
                recipeRow(null, VanillaItems.IRON_INGOT, null)
            )
        ),
        Page(
            "fusion", "app.academy.tutorial.nav.fusion", "app.academy.tutorial.eyebrow.recipe",
            "app.academy.tutorial.page.fusion.title", "app.academy.tutorial.page.fusion.brief",
            listOf("app.academy.tutorial.page.fusion.body.1", "app.academy.tutorial.page.fusion.body.2"),
            previewKey = "app.academy.tutorial.page.fusion.preview"
        )
    )

    private fun recipePage(id: String, result: ItemLike, rows: List<List<ItemLike?>>) = Page(
        "recipe_$id",
        "app.academy.tutorial.nav.recipe.$id",
        "app.academy.tutorial.eyebrow.recipe",
        result.asItem().descriptionId,
        "app.academy.tutorial.page.recipe.$id.brief",
        listOf("app.academy.tutorial.page.recipe.$id.body"),
        recipe = Recipe(rows, result)
    )

    private fun recipeRow(vararg items: ItemLike?): List<ItemLike?> = items.toList()

    fun create(onBack: () -> Unit): Widget = Context(onBack).root

    private class Context(private val onBack: () -> Unit) {
        val root = object : LinearLayoutWidget() {
            override fun render(context: RenderContext) {
                if (width < WIDTH) return
                super.render(context)
            }
        }.apply {
            orientation = Orientation.VERTICAL
            spacing = 1f
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }

        private val articleContainer = FrameLayoutWidget()
        private val previewContainer = FrameLayoutWidget()
        private val navGroup = RadioGroupWidget()
        private val navButtons = mutableListOf<RadioButtonWidget>()
        private var selectedPage = 0

        init {
            root.addChild("header", createHeader())
            root.addChild("header_rule", FillWidget(0xBFFFFFFF.toInt()).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(1f)
                    .padding(2f, 0f)
            })
            root.addChild("body", createBody())
            navGroup.onSelectionChanged = Consumer { button -> showPage(button.id) }
            navGroup.selectButton(navButtons.first())
        }

        private fun createHeader(): LinearLayoutWidget = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            layoutParams = LinearLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
            addChild("back", ButtonWidget().apply {
                layoutParams = LinearLayoutWidget.LayoutParams().margin(2f, 2f, 2f, 0f).size(16f, 16f)
                onClickListener = { onBack() }
                background = iconDrawable(R.textures.gui.icon.arrow_back)
            })
            addChild("title", LabelWidget(L10n["app.academy.tutorial.title"]).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).height(0f).gravity(Gravity.CENTER)
            })
        }

        private fun createBody(): LinearLayoutWidget = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 1f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
            addChild("navigation", createNavigation())
            addChild("navigation_rule", FillWidget(0x70FFFFFF).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().width(1f).heightMode(SizeMode.MATCH_PARENT)
            })
            articleContainer.layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .heightMode(SizeMode.MATCH_PARENT)
            addChild("article", articleContainer)
            addChild("preview_rule", FillWidget(0x70FFFFFF).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().width(1f).heightMode(SizeMode.MATCH_PARENT)
            })
            previewContainer.background = ColorDrawable(0x18000000)
            previewContainer.layoutParams = LinearLayoutWidget.LayoutParams()
                .width(PREVIEW_WIDTH)
                .heightMode(SizeMode.MATCH_PARENT)
            addChild("preview", previewContainer)
        }

        private fun createNavigation(): LinearLayoutWidget = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 2f
            background = ColorDrawable(ROW_FILL)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(NAV_WIDTH)
                .heightMode(SizeMode.MATCH_PARENT)
                .padding(4f, 4f, 3f, 4f)
            addChild("label", LabelWidget(L10n["app.academy.tutorial.index"]).apply {
                baseFontSize = 7.5f
                alpha = 0.65f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(10f)
                    .gravity(Gravity.CENTER_LEFT)
            })
            addChild("rule", FillWidget(0x60FFFFFF).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().widthMode(SizeMode.MATCH_PARENT).height(1f)
            })
            addChild("entries_area", FrameLayoutWidget().apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .widthMode(SizeMode.MATCH_PARENT)
                val panel = ScrollPanelWidget().apply {
                    layoutParams = FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT)
                        .paddingRight(5f)
                    setScrollSpeed(15f)
                }
                addChild("scroll", panel)
                navGroup.orientation = Orientation.VERTICAL
                navGroup.spacing = 2f
                navGroup.layoutParams = FrameLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .heightMode(SizeMode.WRAP_CONTENT)
                pages.forEachIndexed { index, page ->
                    val button = createNavigationButton(index, page)
                    navButtons.add(button)
                    navGroup.addChild(page.id, button)
                }
                panel.addChild("entries", navGroup)
                addChild("scrollbar", ScrollBarWidget(panel, Orientation.VERTICAL).apply {
                    setTrackColor(0x20000000)
                    setThumbColor(0x90FFFFFF.toInt())
                    layoutParams = FrameLayoutWidget.LayoutParams()
                        .width(3f)
                        .heightMode(SizeMode.MATCH_PARENT)
                        .gravity(Gravity.RIGHT)
                })
            })
        }

        private fun createNavigationButton(index: Int, page: Page): RadioButtonWidget = RadioButtonWidget().apply {
            setId(index)
            layoutParams = LinearLayoutWidget.LayoutParams().widthMode(SizeMode.MATCH_PARENT).height(13f)
            background = StateListDrawable().apply {
                addState(Widget.SELECTED, ColorDrawable(0x68FFFFFF))
                addState(Widget.PRESSED, ColorDrawable(0x4FFFFFFF))
                addState(Widget.FOCUSED, ColorDrawable(0x38FFFFFF))
                addState(Widget.HOVERED, ColorDrawable(0x28FFFFFF))
                setDefault(ColorDrawable(0x08000000))
            }
            addChild("text", LabelWidget(L10n[page.navKey]).apply {
                baseFontSize = 7.5f
                alpha = 0.82f
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                    .padding(3f, 1f)
                    .gravity(Gravity.CENTER_LEFT)
            })
        }

        private fun showPage(index: Int) {
            if (index !in pages.indices) return
            selectedPage = index
            articleContainer.clearChildren()
            previewContainer.clearChildren()
            articleContainer.addChild("page", createArticle(pages[index]))
            previewContainer.addChild("page", createPreview(pages[index]))
        }

        private fun createArticle(page: Page): FrameLayoutWidget = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            val panel = ScrollPanelWidget().apply {
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                    .padding(7f, 6f, 10f, 6f)
                setScrollSpeed(18f)
            }
            addChild("scroll", panel)
            panel.addChild("content", LinearLayoutWidget().apply {
                orientation = Orientation.VERTICAL
                spacing = 4f
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .heightMode(SizeMode.WRAP_CONTENT)
                addChild(
                    "eyebrow", articleLabel(
                        L10n[page.eyebrowKey], ARTICLE_BODY_FONT_SIZE, 0.68f, 10f
                    )
                )
                addChild(
                    "title", articleLabel(
                        L10n[page.titleKey], ARTICLE_SUBTITLE_FONT_SIZE, 1f, 14f
                    )
                )
                addChild("rule", FillWidget(0xA0FFFFFF.toInt()).apply {
                    layoutParams = LinearLayoutWidget.LayoutParams().widthMode(SizeMode.MATCH_PARENT).height(1f)
                })
                addChild("brief", LabelWidget(L10n[page.briefKey]).apply {
                    baseFontSize = ARTICLE_BODY_FONT_SIZE
                    wrapText = true
                    alpha = 0.88f
                    background = ColorDrawable(ROW_FILL)
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .heightMode(SizeMode.WRAP_CONTENT)
                        .padding(4f, 3f)
                })
                page.bodyKeys.forEachIndexed { bodyIndex, key ->
                    addChild("body_$bodyIndex", LabelWidget(L10n[key]).apply {
                        baseFontSize = ARTICLE_BODY_FONT_SIZE
                        wrapText = true
                        alpha = 0.82f
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .widthMode(SizeMode.MATCH_PARENT)
                            .heightMode(SizeMode.WRAP_CONTENT)
                    })
                }
            })
            addChild("scrollbar", ScrollBarWidget(panel, Orientation.VERTICAL).apply {
                setTrackColor(0x20000000)
                setThumbColor(0xA0FFFFFF.toInt())
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .width(3f)
                    .heightMode(SizeMode.MATCH_PARENT)
                    .gravity(Gravity.RIGHT)
                    .margin(0f, 6f, 3f, 6f)
            })
        }

        private fun createPreview(page: Page): LinearLayoutWidget = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 3f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(8f, 6f)
            addChild(
                "preview_label", articleLabel(
                    L10n[if (page.recipe == null) "app.academy.tutorial.preview" else "app.academy.tutorial.recipe"],
                    ARTICLE_BODY_FONT_SIZE, 0.68f, 10f
                )
            )
            when {
                page.recipe != null -> addRecipePreview(page.recipe)
                page.stage != null -> addStagePreview(page)
                page.id == "project" -> addProjectPreview(page)
                else -> addTextPreview(page)
            }
            addChild("spacer", EmptyWidget().apply {
                layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).widthMode(SizeMode.MATCH_PARENT)
            })
            addChild("navigation", createPreviewNavigation())
        }

        private fun LinearLayoutWidget.addProjectPreview(page: Page) {
            addChild("icon", ImageWidget(R.textures.gui.app.tutorial.icon).apply {
                setSampler(FilterMode.NEAREST, false)
                layoutParams = LinearLayoutWidget.LayoutParams().size(32f, 32f).gravity(Gravity.CENTER)
            })
            addChild("brand", articleLabel("MISAKA CLOUD", 8f, 0.95f, 12f, Gravity.CENTER))
            addChild("rule", FillWidget(PROGRESSION_BLUE).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().width(52f).height(1.5f).gravity(Gravity.CENTER)
            })
            addChild("description", previewText(page.previewKey))
        }

        private fun LinearLayoutWidget.addStagePreview(page: Page) {
            val stage = page.stage ?: 0
            addChild(
                "stage", articleLabel(
                    stage.toString().padStart(2, '0') + " / 05", 13f, 1f, 20f, Gravity.CENTER
                )
            )
            addChild("progress", FrameLayoutWidget().apply {
                background = ColorDrawable(0x30000000)
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(2f)
                addChild("value", FillWidget(PROGRESSION_BLUE).apply {
                    layoutParams = FrameLayoutWidget.LayoutParams()
                        .width(((PREVIEW_WIDTH - 16f) * stage / 5f).coerceAtLeast(2f))
                        .heightMode(SizeMode.MATCH_PARENT)
                })
            })
            addChild("description", previewText(page.previewKey))
        }

        private fun LinearLayoutWidget.addTextPreview(page: Page) {
            addChild("mark", articleLabel("DATA / NOTE", 10f, 0.95f, 16f, Gravity.CENTER))
            addChild("rule", FillWidget(PROGRESSION_BLUE).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().width(52f).height(1.5f).gravity(Gravity.CENTER)
            })
            addChild("description", previewText(page.previewKey))
        }

        private fun LinearLayoutWidget.addRecipePreview(recipe: Recipe) {
            addChild("grid", LinearLayoutWidget().apply {
                orientation = Orientation.VERTICAL
                spacing = RECIPE_SLOT_GAP
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .size(
                        RECIPE_SLOT_SIZE * 3f + RECIPE_SLOT_GAP * 2f,
                        RECIPE_SLOT_SIZE * 3f + RECIPE_SLOT_GAP * 2f
                    )
                    .gravity(Gravity.CENTER)
                recipe.rows.forEachIndexed { rowIndex, rowValue ->
                    addChild("row_$rowIndex", LinearLayoutWidget().apply {
                        orientation = Orientation.HORIZONTAL
                        spacing = RECIPE_SLOT_GAP
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .widthMode(SizeMode.MATCH_PARENT)
                            .height(RECIPE_SLOT_SIZE)
                        rowValue.forEachIndexed { columnIndex, item ->
                            addChild("cell_$columnIndex", FrameLayoutWidget().apply {
                                background = ColorDrawable(if (item == null) 0x10000000 else ROW_FILL)
                                layoutParams = LinearLayoutWidget.LayoutParams()
                                    .size(RECIPE_SLOT_SIZE, RECIPE_SLOT_SIZE)
                                if (item != null) addChild(
                                    "item",
                                    ItemStackWidget(item.asItem().defaultInstance).apply {
                                        tooltipText = L10n[item.asItem().descriptionId]
                                        layoutParams = FrameLayoutWidget.LayoutParams()
                                            .size(ItemStackWidget.ITEM_SIZE, ItemStackWidget.ITEM_SIZE)
                                            .gravity(Gravity.CENTER)
                                    })
                            })
                        }
                    })
                }
            })
            addChild("result", LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                spacing = 4f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .width(38f)
                    .height(RECIPE_SLOT_SIZE)
                    .gravity(Gravity.CENTER)
                addChild("arrow", LabelWidget("→").apply {
                    baseFontSize = ARTICLE_BODY_FONT_SIZE
                    alpha = 0.78f
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .size(RECIPE_SLOT_SIZE, RECIPE_SLOT_SIZE)
                        .gravity(Gravity.CENTER)
                })
                addChild("item", ItemStackWidget(recipe.result.asItem().defaultInstance).apply {
                    tooltipText = L10n[recipe.result.asItem().descriptionId]
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .size(ItemStackWidget.ITEM_SIZE, ItemStackWidget.ITEM_SIZE)
                        .gravity(Gravity.CENTER)
                })
            })
            addChild("recipe_rule", FillWidget(0x60FFFFFF).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().widthMode(SizeMode.MATCH_PARENT).height(1f)
            })
        }

        private fun createPreviewNavigation(): LinearLayoutWidget = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 4f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(14f)
            addChild("previous", ButtonWidget().apply {
                layoutParams = LinearLayoutWidget.LayoutParams().size(14f, 14f)
                background = iconDrawable(R.textures.gui.icon.arrow_back)
                tooltipText = L10n["app.academy.tutorial.previous"]
                onClickListener = {
                    val nextIndex = if (selectedPage == 0) pages.lastIndex else selectedPage - 1
                    navGroup.selectButton(navButtons[nextIndex])
                }
            })
            addChild("position", LabelWidget("${selectedPage + 1} / ${pages.size}").apply {
                baseFontSize = 7f
                alpha = 0.6f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .heightMode(SizeMode.MATCH_PARENT)
                    .gravity(Gravity.CENTER)
            })
            addChild("next", ButtonWidget().apply {
                layoutParams = LinearLayoutWidget.LayoutParams().size(14f, 14f)
                background = iconDrawable(R.textures.gui.icon.arrow_foward)
                tooltipText = L10n["app.academy.tutorial.next"]
                onClickListener = {
                    val nextIndex = if (selectedPage == pages.lastIndex) 0 else selectedPage + 1
                    navGroup.selectButton(navButtons[nextIndex])
                }
            })
        }

        private fun previewText(key: String?): LabelWidget = LabelWidget(key?.let(L10n::get) ?: "").apply {
            baseFontSize = ARTICLE_BODY_FONT_SIZE
            wrapText = true
            alpha = 0.78f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .heightMode(SizeMode.WRAP_CONTENT)
        }

        private fun articleLabel(
            text: String,
            fontSize: Float,
            alpha: Float,
            height: Float,
            gravity: Int = Gravity.CENTER_LEFT
        ): LabelWidget = LabelWidget(text).apply {
            baseFontSize = fontSize
            this.alpha = alpha
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(height)
                .gravity(gravity)
        }

        private fun iconDrawable(icon: net.minecraft.resources.Identifier): StateListDrawable {
            val resting = TextureDrawable(icon).apply { tintColor = 0xFFBBBBBB.toInt() }
            val active = TextureDrawable(icon).apply { tintColor = 0xFFFFFFFF.toInt() }
            return StateListDrawable().apply {
                addState(Widget.PRESSED, active)
                addState(Widget.FOCUSED, active)
                addState(Widget.SELECTED, active)
                addState(Widget.HOVERED, active)
                setDefault(resting)
            }
        }
    }
}
