package org.academy.internal.client.app.tutorial

import com.mojang.renderpearl.api.textures.FilterMode
import net.minecraft.world.level.ItemLike
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*
import org.academy.api.client.resources.R
import org.academy.api.common.util.L10n
import java.util.function.Consumer
import net.minecraft.world.item.Items as VanillaItems
import org.academy.internal.common.world.item.Items as AcademyItems

object TutorialUi {
    private data class Recipe(
        val rows: List<List<ItemLike?>>,
        val result: ItemLike
    )

    private data class Page(
        val id: String,
        val keyNav: String,
        val keyEyebrow: String,
        val keyTitle: String,
        val keyBrief: String,
        val keysBody: List<String>,
        val stage: Int? = null,
        val recipe: Recipe? = null,
        val keyPreview: String? = null
    )

    private val pages = listOf(
        Page(
            "project", "app.academy.tutorial.nav.project", "app.academy.tutorial.eyebrow.project",
            "app.academy.tutorial.page.project.title", "app.academy.tutorial.page.project.brief",
            listOf("app.academy.tutorial.page.project.body.1", "app.academy.tutorial.page.project.body.2"),
            keyPreview = "app.academy.tutorial.page.project.preview"
        ),
        Page(
            "route", "app.academy.tutorial.nav.route", "app.academy.tutorial.eyebrow.route",
            "app.academy.tutorial.page.route.title", "app.academy.tutorial.page.route.brief",
            listOf("app.academy.tutorial.page.route.body.1", "app.academy.tutorial.page.route.body.2"),
            stage = 0, keyPreview = "app.academy.tutorial.page.route.preview"
        ),
        Page(
            "step_1", "app.academy.tutorial.nav.step.1", "app.academy.tutorial.eyebrow.tutorial",
            "app.academy.tutorial.page.step.1.title", "app.academy.tutorial.page.step.1.brief",
            listOf("app.academy.tutorial.page.step.1.body.1", "app.academy.tutorial.page.step.1.body.2"),
            stage = 1, keyPreview = "app.academy.tutorial.page.step.1.preview"
        ),
        Page(
            "step_2", "app.academy.tutorial.nav.step.2", "app.academy.tutorial.eyebrow.tutorial",
            "app.academy.tutorial.page.step.2.title", "app.academy.tutorial.page.step.2.brief",
            listOf("app.academy.tutorial.page.step.2.body.1", "app.academy.tutorial.page.step.2.body.2"),
            stage = 2, keyPreview = "app.academy.tutorial.page.step.2.preview"
        ),
        Page(
            "step_3", "app.academy.tutorial.nav.step.3", "app.academy.tutorial.eyebrow.tutorial",
            "app.academy.tutorial.page.step.3.title", "app.academy.tutorial.page.step.3.brief",
            listOf("app.academy.tutorial.page.step.3.body.1", "app.academy.tutorial.page.step.3.body.2"),
            stage = 3, keyPreview = "app.academy.tutorial.page.step.3.preview"
        ),
        Page(
            "step_4", "app.academy.tutorial.nav.step.4", "app.academy.tutorial.eyebrow.tutorial",
            "app.academy.tutorial.page.step.4.title", "app.academy.tutorial.page.step.4.brief",
            listOf("app.academy.tutorial.page.step.4.body.1", "app.academy.tutorial.page.step.4.body.2"),
            stage = 4, keyPreview = "app.academy.tutorial.page.step.4.preview"
        ),
        Page(
            "step_5", "app.academy.tutorial.nav.step.5", "app.academy.tutorial.eyebrow.tutorial",
            "app.academy.tutorial.page.step.5.title", "app.academy.tutorial.page.step.5.brief",
            listOf("app.academy.tutorial.page.step.5.body.1", "app.academy.tutorial.page.step.5.body.2"),
            stage = 5, keyPreview = "app.academy.tutorial.page.step.5.preview"
        ),
        pageRecipe(
            "probe", AcademyItems.IMAG_PHASE_DOWSING_ROD.get(),
            listOf(
                rowRecipe(null, VanillaItems.COMPARATOR, null),
                rowRecipe(
                    VanillaItems.LIGHTNING_ROD.weathering().unaffected(),
                    VanillaItems.COMPASS,
                    VanillaItems.IRON_INGOT
                ),
                rowRecipe(null, null, VanillaItems.IRON_INGOT)
            )
        ),
        pageRecipe(
            "solar", AcademyItems.SOLAR_GEN.get(),
            listOf(
                rowRecipe(
                    VanillaItems.STAINED_GLASS_PANE.gray(),
                    VanillaItems.STAINED_GLASS_PANE.gray(),
                    VanillaItems.STAINED_GLASS_PANE.gray()
                ),
                rowRecipe(
                    AcademyItems.IMAG_PHASE_INGOT.get(),
                    VanillaItems.DAYLIGHT_DETECTOR,
                    AcademyItems.IMAG_PHASE_INGOT.get()
                ),
                rowRecipe(
                    AcademyItems.IMAG_PHASE_POLYMER.get(),
                    VanillaItems.REDSTONE,
                    AcademyItems.IMAG_PHASE_POLYMER.get()
                )
            )
        ),
        pageRecipe(
            "tablet", AcademyItems.ABILITY_CONTROL_TABLET.get(),
            listOf(
                rowRecipe(
                    AcademyItems.IMAG_PHASE_PLATE.get(),
                    VanillaItems.COMPARATOR,
                    AcademyItems.WIND_GEN_BASE_SCREEN.get()
                ),
                rowRecipe(
                    AcademyItems.IMAG_PHASE_PLATE.get(),
                    AcademyItems.IMAG_PHASE_CIRCUIT.get(),
                    VanillaItems.COMPARATOR
                ),
                rowRecipe(
                    AcademyItems.IMAG_PHASE_INGOT.get(),
                    AcademyItems.IMAG_PHASE_PLATE.get(),
                    AcademyItems.IMAG_PHASE_PLATE.get()
                )
            )
        ),
        pageRecipe(
            "terminal", AcademyItems.DATA_TERMINAL.get(),
            listOf(
                rowRecipe(VanillaItems.IRON_INGOT, VanillaItems.REDSTONE, VanillaItems.IRON_INGOT),
                rowRecipe(VanillaItems.REDSTONE, VanillaItems.GLASS_PANE, VanillaItems.REDSTONE),
                rowRecipe(VanillaItems.IRON_INGOT, VanillaItems.IRON_INGOT, VanillaItems.IRON_INGOT)
            )
        ),
        pageRecipe(
            "cloud", AcademyItems.TUTORIAL.get(),
            listOf(
                rowRecipe(null, VanillaItems.AMETHYST_SHARD, null),
                rowRecipe(VanillaItems.REDSTONE, VanillaItems.BOOK, VanillaItems.REDSTONE),
                rowRecipe(null, VanillaItems.IRON_INGOT, null)
            )
        ),
        Page(
            "fusion", "app.academy.tutorial.nav.fusion", "app.academy.tutorial.eyebrow.recipe",
            "app.academy.tutorial.page.fusion.title", "app.academy.tutorial.page.fusion.brief",
            listOf("app.academy.tutorial.page.fusion.body.1", "app.academy.tutorial.page.fusion.body.2"),
            keyPreview = "app.academy.tutorial.page.fusion.preview"
        )
    )

    private fun pageRecipe(id: String, result: ItemLike, rows: List<List<ItemLike?>>) = Page(
        "recipe_$id",
        "app.academy.tutorial.nav.recipe.$id",
        "app.academy.tutorial.eyebrow.recipe",
        result.asItem().descriptionId,
        "app.academy.tutorial.page.recipe.$id.brief",
        listOf("app.academy.tutorial.page.recipe.$id.body"),
        recipe = Recipe(rows, result)
    )

    private fun rowRecipe(vararg items: ItemLike?): List<ItemLike?> = items.toList()

    fun create(onBack: () -> Unit): Widget = standaloneColumn(spacing = 1f) {
        lp {
            matchParent()
        }

        lateinit var containerArticle: FrameLayoutWidget
        lateinit var containerPreview: FrameLayoutWidget
        lateinit var groupNav: RadioGroupWidget

        val buttonsNav = mutableListOf<RadioButtonWidget>()
        var pageSelected = 0

        fun WidgetContainer.labelArticle(
            value: String,
            name: String,
            fontSize: Float,
            alpha: Float,
            height: Float,
            gravity: Int = Gravity.CENTER_LEFT
        ): TextWidget = text(value, name) {
            lp {
                widthMode(SizeMode.MATCH_PARENT)
                height(height)
                gravity(gravity)
            }

            textSize = fontSize
            this.alpha = alpha
            this.gravity = gravity
        }

        fun WidgetContainer.textPreview(key: String?, name: String): TextWidget =
            text(key?.let(L10n::get) ?: "", name) {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    heightMode(SizeMode.WRAP_CONTENT)
                }

                textSize = R.ui.tutorial.font_body
                singleLine = false
                alpha = 0.78f
            }

        fun LinearLayoutWidget.addPreviewProject(page: Page) {
            image(R.textures.gui.app.tutorial.icon, "icon") {
                lp {
                    size(32f, 32f)
                    gravity(Gravity.CENTER)
                }

                sampler(FilterMode.NEAREST, false)
            }

            labelArticle("MISAKA CLOUD", "text_brand", 8f, 0.95f, 12f, Gravity.CENTER)

            fill(R.ui.tutorial.progression_blue, "rule") {
                lp {
                    width(52f)
                    height(1.5f)
                    gravity(Gravity.CENTER)
                }
            }

            textPreview(page.keyPreview, "text_description")
        }

        fun LinearLayoutWidget.addPreviewStage(page: Page) {
            val stage = page.stage ?: 0

            labelArticle("${stage.toString().padStart(2, '0')} / 05", "text_stage", 13f, 1f, 20f, Gravity.CENTER)

            frame("progress") {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(2f)
                }

                background = ColorDrawable(0x30000000)

                fill(R.ui.tutorial.progression_blue, "value") {
                    lp {
                        width((((R.ui.tutorial.preview_width - 16f) * stage) / 5f).coerceAtLeast(2f))
                        heightMode(SizeMode.MATCH_PARENT)
                    }
                }
            }

            textPreview(page.keyPreview, "text_description")
        }

        fun LinearLayoutWidget.addPreviewText(page: Page) {
            labelArticle("DATA / NOTE", "text_mark", 10f, 0.95f, 16f, Gravity.CENTER)

            fill(R.ui.tutorial.progression_blue, "rule") {
                lp {
                    width(52f)
                    height(1.5f)
                    gravity(Gravity.CENTER)
                }
            }

            textPreview(page.keyPreview, "text_description")
        }

        fun LinearLayoutWidget.addPreviewRecipe(recipe: Recipe) {
            val slotSize = R.ui.tutorial.recipe_slot_size
            val slotGap = R.ui.tutorial.recipe_slot_gap

            row("row_recipe", spacing = 4f) {
                lp {
                    widthMode(SizeMode.WRAP_CONTENT)
                    heightMode(SizeMode.WRAP_CONTENT)
                    gravity(Gravity.CENTER)
                }

                column("grid", spacing = slotGap) {
                    lp {
                        size(slotSize * 3f + slotGap * 2f, slotSize * 3f + slotGap * 2f)
                        gravity(Gravity.CENTER)
                    }

                    recipe.rows.forEachIndexed { rowIndex, rowValue ->
                        row("row_$rowIndex", spacing = slotGap) {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                                height(slotSize)
                            }

                            rowValue.forEachIndexed { columnIndex, item ->
                                frame("cell_$columnIndex") {
                                    lp {
                                        size(slotSize, slotSize)
                                    }

                                    background =
                                        ColorDrawable(if (item == null) 0x10000000 else R.ui.tutorial.row_fill)

                                    if (item != null) {
                                        add("item", ItemStackWidget(item.asItem().defaultInstance)) {
                                            lp {
                                                size(ItemStackWidget.ITEM_SIZE, ItemStackWidget.ITEM_SIZE)
                                                gravity(Gravity.CENTER)
                                            }

                                            tooltipText = L10n[item.asItem().descriptionId]
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                text("→", "text_arrow") {
                    lp {
                        size(slotSize, slotSize)
                        gravity(Gravity.CENTER)
                    }

                    textSize = R.ui.tutorial.font_body
                    alpha = 0.78f
                    gravity = Gravity.CENTER
                }

                frame("cell_result") {
                    lp {
                        size(slotSize, slotSize)
                        gravity(Gravity.CENTER)
                    }

                    background = ColorDrawable(R.ui.tutorial.row_fill)

                    add("item", ItemStackWidget(recipe.result.asItem().defaultInstance)) {
                        lp {
                            size(ItemStackWidget.ITEM_SIZE, ItemStackWidget.ITEM_SIZE)
                            gravity(Gravity.CENTER)
                        }

                        tooltipText = L10n[recipe.result.asItem().descriptionId]
                    }
                }
            }

            fill(R.ui.tutorial.rule_soft, "rule_recipe") {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(1f)
                }
            }
        }

        fun createNavigationPreview(): LinearLayoutWidget = standaloneRow(spacing = 4f) {
            lp {
                widthMode(SizeMode.MATCH_PARENT)
                height(16f)
            }

            button("button_previous") {
                lp {
                    size(16f, 16f)
                }

                tooltipText = L10n["app.academy.tutorial.previous"]

                image(R.textures.gui.icon.arrow, "icon")

                onClick {
                    val indexNext = if (pageSelected == 0) pages.lastIndex else pageSelected - 1
                    groupNav.selectButton(buttonsNav[indexNext])
                }
            }

            text("${pageSelected + 1} / ${pages.size}", "text_position") {
                lp {
                    heightMode(SizeMode.MATCH_PARENT)
                    gravity(Gravity.CENTER)
                }

                weight(1f)

                textSize = 7f
                alpha = 0.6f
                gravity = Gravity.CENTER
            }

            button("button_next") {
                lp {
                    size(16f, 16f)
                }

                tooltipText = L10n["app.academy.tutorial.next"]

                image(R.textures.gui.icon.arrow, "icon") {
                    flipUvU()
                }

                onClick {
                    val indexNext = if (pageSelected == pages.lastIndex) 0 else pageSelected + 1
                    groupNav.selectButton(buttonsNav[indexNext])
                }
            }
        }

        fun createArticle(page: Page): FrameLayoutWidget = standaloneFrame {
            lp {
                sizeMode(SizeMode.MATCH_PARENT)
            }

            val panel = scrollPanel(name = "scroll") {
                lp {
                    matchParent()
                    padding(7f, 6f, 10f, 6f)
                }

                scrollSpeed(18f)

                column("content", spacing = 4f) {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        heightMode(SizeMode.WRAP_CONTENT)
                    }

                    labelArticle(
                        L10n[page.keyEyebrow], "label_eyebrow",
                        R.ui.tutorial.font_body, 0.68f, 10f
                    )

                    labelArticle(
                        L10n[page.keyTitle], "text_title",
                        R.ui.tutorial.font_subtitle, 1f, 14f
                    )

                    fill(R.ui.tutorial.rule_medium, "rule") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(1f)
                        }
                    }

                    text(L10n[page.keyBrief], "text_brief") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            heightMode(SizeMode.WRAP_CONTENT)
                            padding(4f, 3f)
                        }

                        textSize = R.ui.tutorial.font_body
                        singleLine = false
                        alpha = 0.88f
                        background = ColorDrawable(R.ui.tutorial.row_fill)
                    }

                    page.keysBody.forEachIndexed { bodyIndex, key ->
                        text(L10n[key], "text_body_$bodyIndex") {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                                heightMode(SizeMode.WRAP_CONTENT)
                            }

                            textSize = R.ui.tutorial.font_body
                            singleLine = false
                            alpha = 0.82f
                        }
                    }
                }
            }

            scrollBar(panel, Orientation.VERTICAL, "scroll_bar") {
                lp {
                    width(3f)
                    heightMode(SizeMode.MATCH_PARENT)
                    gravity(Gravity.RIGHT)
                    margin(0f, 6f, 3f, 6f)
                }

                trackColor(0x20000000)
                thumbColor(0xA0FFFFFF.toInt())
            }
        }

        fun createPreview(page: Page): LinearLayoutWidget = standaloneColumn(spacing = 3f) {
            lp {
                sizeMode(SizeMode.MATCH_PARENT)
                padding(8f, 6f)
            }

            labelArticle(
                L10n[if (page.recipe == null) "app.academy.tutorial.preview" else "app.academy.tutorial.recipe"],
                "label_preview", R.ui.tutorial.font_body, 0.68f, 10f
            )

            when {
                page.recipe != null -> addPreviewRecipe(page.recipe)
                page.stage != null -> addPreviewStage(page)
                page.id == "project" -> addPreviewProject(page)
                else -> addPreviewText(page)
            }

            empty("spacer") {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                }

                weight(1f)
            }

            add("navigation", createNavigationPreview())
        }

        fun showPage(index: Int) {
            if (index !in pages.indices) return
            pageSelected = index
            containerArticle.clearChildren()
            containerPreview.clearChildren()
            containerArticle.addChild("page", createArticle(pages[index]))
            containerPreview.addChild("page", createPreview(pages[index]))
        }

        row("header") {
            lp {
                sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
            }

            button("button_back") {
                lp {
                    margin(2f, 2f, 2f, 0f)
                    size(16f, 16f)
                }

                onClick { onBack() }

                image(R.textures.gui.icon.arrow, "icon")
            }

            text(L10n["app.academy.tutorial.title"], "text_title") {
                lp {
                    height(0f)
                    gravity(Gravity.CENTER)
                }

                weight(1f)

                gravity = Gravity.CENTER
            }
        }

        fill(R.ui.tutorial.rule_strong, "rule_header") {
            lp {
                widthMode(SizeMode.MATCH_PARENT)
                height(1f)
                padding(2f, 0f)
            }
        }

        row("body", spacing = 1f) {
            lp {
                widthMode(SizeMode.MATCH_PARENT)
            }

            weight(1f)

            column("navigation", spacing = 2f) {
                lp {
                    width(R.ui.tutorial.nav_width)
                    heightMode(SizeMode.MATCH_PARENT)
                    padding(4f, 4f, 3f, 4f)
                }

                background = ColorDrawable(R.ui.tutorial.row_fill)

                text(L10n["app.academy.tutorial.index"], "label_index") {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(10f)
                        gravity(Gravity.CENTER_LEFT)
                    }

                    textSize = 7.5f
                    alpha = 0.65f
                    gravity = Gravity.CENTER_LEFT
                }

                fill(R.ui.tutorial.rule_soft, "rule") {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(1f)
                    }
                }

                frame("area_entries") {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                    }

                    weight(1f)

                    val panel = scrollPanel(name = "scroll") {
                        lp {
                            matchParent()
                            paddingRight(5f)
                        }

                        scrollSpeed(15f)

                        groupNav = radioGroup("group_nav") {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                                heightMode(SizeMode.WRAP_CONTENT)
                            }

                            orientation = Orientation.VERTICAL
                            spacing = 2f

                            pages.forEach { page ->
                                buttonsNav.add(radio(page.id) {
                                    lp {
                                        widthMode(SizeMode.MATCH_PARENT)
                                        height(13f)
                                    }

                                    background = StateListDrawable().apply {
                                        addState(Widget.SELECTED, ColorDrawable(0x68FFFFFF))
                                        addState(Widget.PRESSED, ColorDrawable(0x4FFFFFFF))
                                        addState(Widget.FOCUSED, ColorDrawable(0x38FFFFFF))
                                        addState(Widget.HOVERED, ColorDrawable(0x28FFFFFF))
                                        setDefault(ColorDrawable(0x08000000))
                                    }

                                    text(L10n[page.keyNav], "text") {
                                        lp {
                                            sizeMode(SizeMode.MATCH_PARENT)
                                            padding(3f, 1f)
                                            gravity(Gravity.CENTER_LEFT)
                                        }

                                        textSize = 7.5f
                                        alpha = 0.82f
                                        gravity = Gravity.CENTER_LEFT
                                    }
                                })
                            }
                        }
                    }

                    scrollBar(panel, Orientation.VERTICAL, "scroll_bar") {
                        lp {
                            width(3f)
                            heightMode(SizeMode.MATCH_PARENT)
                            gravity(Gravity.RIGHT)
                        }

                        trackColor(0x20000000)
                        thumbColor(0x90FFFFFF.toInt())
                    }
                }
            }

            fill(R.ui.tutorial.rule_faint, "rule_navigation") {
                lp {
                    width(1f)
                    heightMode(SizeMode.MATCH_PARENT)
                    paddingBottom(4f)
                }
            }

            containerArticle = frame("article") {
                lp {
                    heightMode(SizeMode.MATCH_PARENT)
                }

                weight(1f)
            }

            fill(R.ui.tutorial.rule_faint, "rule_preview") {
                lp {
                    width(1f)
                    heightMode(SizeMode.MATCH_PARENT)
                    paddingBottom(4f)
                }
            }

            containerPreview = frame("preview") {
                lp {
                    width(R.ui.tutorial.preview_width)
                    heightMode(SizeMode.MATCH_PARENT)
                }

                background = ColorDrawable(R.ui.tutorial.plane_preview)
            }
        }

        groupNav.onSelectionChanged = Consumer { button -> showPage(button.id) }

        groupNav.selectButton(buttonsNav.first())
    }
}
