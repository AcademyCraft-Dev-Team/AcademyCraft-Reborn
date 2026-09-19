# AcademyCraft UI Implementation Guide

## Contents

- Choose the current host
- Inspect before editing
- Map legacy concepts to current widgets
- Implement backdrop blur and masking
- Compose and lay out the screen
- Implement state and controls
- Manage resources
- Validate the result

## Choose the current host

| Target | Current host or entry point |
| --- | --- |
| Machine menu/container | `ContainerUiScreen` and its page/content widgets |
| Standalone full screen | `UiScreen` |
| Terminal launcher | `TerminalHud.Context` |
| Terminal application | Terminal `App` plus its `WidgetContext` |
| HUD/overlay | `HudManager` and neighboring layout DSL factories |
| Repeated screen fragment | A shared widget or utility, not copied screen-local drawing |

Use the host already selected by the target's nearest current implementation. Changing frameworks is not part of a visual refresh.

## Inspect before editing

Read the target. Consult an adjacent implementation of the same archetype when a convention is unclear. Trace only the dependencies touched by the requested change or a concrete risk:

- the screen or HUD registration point;
- its menu and slot coordinates;
- referenced `R` identifiers and texture dimensions;
- animation and visibility state;
- mouse, keyboard, focus, IME, and tooltip behavior;
- packets or state mutations triggered by controls;
- layout DSL factories under `org.academy.internal.client.gui.layout` when present.

Useful current implementations include:

- `ContainerUiScreen.kt` for the machine canvas, page rail, reveal, and inventory treatment;
- `InfoAreaUtil.kt` for compact telemetry rows;
- `WirelessPanelUtil.kt` for list rows, text input, and action states;
- `AbilityDeveloperScreen.kt` for the fixed developer canvas;
- `HudManager.kt` and `TerminalHud.kt` for the terminal stencil, blur ownership, neutral root plane, launcher, and app shell;
- `PropsApp.kt`, `RadarChartWidget.kt`, and `SettingsApp.kt` for the supplied expanded-terminal target compositions;
- `AnimationUtil.kt`, `BlendQuadWidget.kt`, `TextBoxWidget.kt`, `ProgressBarWidget.kt`, `ScrollBarWidget.kt`, and `ScrollPanelWidget.kt` for reusable behavior.

Use `rg --files src/main | rg '<name>'` when paths have moved. Follow repository code style and preserve unrelated dirty-worktree changes.

## Map legacy concepts to current widgets

The legacy reference project uses LambdaLib/CGUI. Preserve its visual and interaction intent, not its API.

| Legacy concept | Current implementation direction |
| --- | --- |
| `Widget` and `Transform` | Current widget plus `LayoutParams` |
| Overlapping child widgets | `FrameLayoutWidget` |
| Vertical/horizontal element list | `LinearLayoutWidget`, with `ScrollPanelWidget` when needed |
| `DrawTexture` | `ImageWidget` and `TextureDrawable` |
| Solid/translucent rectangle | `FillWidget` or `ColorDrawable` |
| Tint or hover alpha | `StateListDrawable` and/or current animator |
| Read-only `TextBox` | `LabelWidget` |
| Editable `TextBox` | Current `TextBoxWidget` |
| Legacy `ProgressBar` | Current `ProgressBarWidget` |
| `DragBar` | Current `ScrollBarWidget` |
| Per-frame listener | Current screen/widget tick or bound state update |
| `doesDraw = false` page switch | Visibility plus enabled/focus state, preferably through `AnimationUtil` |
| CGUI XML | Kotlin widget composition, or the shared layout DSL factories under `org.academy.internal.client.gui.layout` for that subsystem |

Never add a LambdaLib or CGUI dependency merely to reuse reference code.

## Implement backdrop blur and masking

Keep exactly one blur owner per surface.

### Machine/container screen

`ContainerUiScreen.extractBackground` already calls the screen host's blurred and transparent background extraction. Preserve that path. Build the machine face above it with `BlendQuadWidget` and authored textures. Do not add another Gaussian blur to the machine widget tree.

### Data terminal

The terminal blur pipeline is shared infrastructure:

1. `TerminalHud` renders the projected terminal UI and writes its stencil/depth coverage.
2. `HudManager` checks that coverage and calls `BlurEffect.apply` on the main world color.
3. The blur result is composited only through the terminal mask.
4. The crisp premultiplied-alpha terminal UI is composited afterward.

Keep app roots transparent. Let `TerminalHud.COLOR` (`0x40000000`) provide the shared neutral plane, and add only local `0x28000000`-class row/input fills. Never invoke `BlurEffect` from `PropsApp`, `SettingsApp`, or another child app.

Use `TerminalHud.getBlurRadius()`/configuration instead of a hard-coded child blur. Test radius 0 and 20. At radius 0, alpha planes must still separate text from the world.

### Sampling and order

- Keep world blur smooth and UI compositing premultiplied-alpha correct.
- Keep machine textures and pixel/technical line art on nearest sampling.
- Use linear sampling only where the existing projected/large smooth icon treatment calls for it, such as the expanded terminal back arrow.
- Never blur the foreground render target containing text, rules, item icons, chart lines, or cursor.

## Compose and lay out the screen

For a canonical machine surface, the composition should resemble:

```kotlin
val root = FrameLayoutWidget()
root.addChild(
    BlendQuadWidget(),
    LayoutParams(width = 176f, height = 187f)
)
root.addChild(
    ImageWidget(TextureDrawable(R.someMachineTexture)),
    LayoutParams(width = 176f, height = 187f)
)
root.addChild(content, LayoutParams.matchParent())
```

Adapt constructor and layout syntax to the current definitions; the important part is the layer order and 176 x 187 logical canvas. On a multi-page machine, apply page-specific authored textures only to their owning page; for example, do not put the node-face texture behind the wireless list.

Use these layout rules:

- Use `FrameLayoutWidget` for background/content/state overlays.
- Use `LinearLayoutWidget` for fluid rows and columns.
- Use explicit art-directed margins only inside fixed legacy canvases such as machine and developer screens.
- Keep list widths flexible so names can absorb remaining space.
- Align numeric live values to a stable right edge.
- Tie decorative inventory cells to actual menu slot geometry.
- Convert 2x bitmap dimensions to half-size logical dimensions and preserve nearest sampling.

For an expanded terminal app, use the existing 384 x 200 parent instead of creating a second panel. Follow the shared shell used by `PropsApp` and `SettingsApp`: 16 x 16 back action, centered title, 1 px white separator, then flexible content. Preserve substantial transparent space and use low-alpha grouping fills rather than a grid of bordered cards.

Do not import `DataTerminalTheme` only because of its name. The supplied P.R.O.P.S and Settings targets are implemented without it and use a neutral black/white palette. Inspect the target screen family before adopting that class's warmer multi-accent tokens.

## Implement state and controls

### Stateful icon

Build state drawables with a dim resting texture and white active texture. Include keyboard focus and selected state, not only mouse hover:

```kotlin
val normal = TextureDrawable(icon).apply {
    tintColor = 0xFFBBBBBB.toInt()
}
val active = TextureDrawable(icon).apply {
    tintColor = 0xFFFFFFFF.toInt()
}
val drawable = StateListDrawable().apply {
    setDefault(normal)
    addState(Widget.FOCUSED, active)
    addState(Widget.SELECTED, active)
    addState(Widget.HOVERED, active)
    addState(Widget.PRESSED, active)
}
button.background = drawable
```

`StateListDrawable` evaluates the state of the widget it draws for. Attach it to the interaction-owning widget, such as the `ButtonWidget`, rather than to a passive child `ImageWidget` that does not own the parent's focused or pressed state. Add compound/specific masks before broader masks when their visuals differ.

### Editable value

Use `TextBoxWidget` for real input. Preserve validation, length constraints, packet timing, focus loss, selection, caret, and IME behavior. Use `0x5F1F1F1F`-class resting background and `0x5F5A5A5A`-class focused background when the archetype does not use legacy brackets.

For a sparse terminal command console, prefer a `0x28000000` input plane and separate `>` label as shown by `PropsApp`; do not add a bright border unless focus remains ambiguous against the tested background.

### Keyboard and asynchronous behavior

Confirm the current widget framework's shared focus traversal and keyboard activation behavior before promising keyboard support. Improve the shared widget when that is the correct scope; do not add a screen-local key simulation that bypasses normal focus or button semantics.

For asynchronously refreshed lists, distinguish only the states the API actually exposes. Guard against stale or out-of-order responses when requests can overlap. Treat user-supplied names as data, not translation keys; give the name flexible width, define truncation/ellipsis behavior, and expose the full value in a tooltip when it does not fit. Preserve fixed input/action widths.

### Progress and live values

Use `ProgressBarWidget` with a translucent background and a single semantic foreground. Smooth noisy values only when it improves reading; never delay an action or warning state. Clamp calculated progress and handle zero capacity safely.

### Terminal tabs, rows, and toggles

- Reuse the selected-tab animation pattern from `SettingsApp`: neutral white fill from 0 to about 50% alpha over 100 ms.
- Reuse `ToggleButtonWidget` for the screenshot target's white track, black thumb, and 150 ms movement. Do not encode ordinary boolean state with red/green.
- Use `ColorDrawable(0x28000000)` for terminal dashboard rows and command inputs when matching P.R.O.P.S.
- Separate dashboard rows with 2 px gaps. Avoid adding a bright outline to each row.
- Keep simple setting rows transparent; align labels left and toggles/actions right.
- Reuse `RadarChartWidget` when the target is P.R.O.P.S data. Preserve an unfilled low-alpha grid and at most one brighter data outline.

### Page transition

Use `AnimationUtil` or the local animation system. During a hide transition, stop input at the point the page should no longer be interactive. During show, enable focus deliberately. Clear or reverse stale animations when the user changes pages quickly.

## Manage resources

- Reuse resource identifiers in `src/main/java/org/academy/api/client/resources/R.java` when available.
- Keep new GUI identifiers and filenames lowercase `snake_case`.
- Put hand-authored textures under `src/main/resources/assets/academy/textures/gui` or the established subsystem folder.
- Do not edit `src/generated/resources` by hand.
- Prefer an existing AcademyCraft icon over inventing a new glyph.
- If a new identifier is unavoidable, add it consistently with surrounding `R` entries and use the repository's `AcademyCraft.academy(...)` convention when appropriate.
- Preserve third-party license metadata for imported assets. Do not copy an asset from the reference project until its existing license and current-project lineage are understood.
- Keep GUI texture filtering crisp; do not apply smooth resampling to technical line art.

## Validate the result

This is the single implementation acceptance checklist. The repository `AGENTS.md` owns required tests, both build variants, and in-game verification; this guide does not relax them. Reuse successful results for the same revision and configuration across references. Select the visual checks below by the changed behavior and its risks; a full redesign needs all applicable checks. Read-only reviews use these as evidence criteria, not instructions to modify files or launch validation workflows.

1. Review the task's diff and preserve unrelated changes. Run the repository-required tests and builds; add focused state/layout tests when they provide meaningful coverage beyond those checks.
2. Perform the required in-game smoke check on the affected content. Use the UI debugger/F12 support where available when bounds or hit targets need inspection.
3. For layout, typography, or texture changes, check representative GUI scales, English/Chinese and long localized strings, clipping, alignment, crisp 1 px rules, and the archetype's spacing, alpha, and icon conventions. Reuse existing `R` identifiers where available.
4. For hierarchy, color, or background changes, check bright and dark worlds and a grayscale view. Neutralize semantic accents temporarily when needed to verify that geometry/brightness carries hierarchy; preserve sparse outlines and consistent accent meanings without unnecessary gradients, rounded cards, broad bloom, or opaque planes.
5. For blur or composition changes, verify that the world remains recognizable without detail competing with text and that foreground lines, text, icons, and cursor edges remain crisp. For terminal work, check radius 0 and 20, readability at both limits, and sharp pixels outside the projected stencil without rectangular leakage. For machine work, check that host blur/dimming occurs once and authored 2x textures remain sharp at half size.
6. For interaction, pages, or animation changes, exercise the exposed control states with keyboard/mouse focus; verify hidden pages cannot receive input and transitions can reverse or restart without stale alpha, scale, or enabled state.
7. For lists, scrolling, or live data changes, check empty/full content, clipping and scroll bounds, invalidation, and updates. For moved inventory slots, verify menu coordinates and hit handling.
8. Report the checks performed and any required check still pending, with its blocker. Once applicable checks pass, stop validation unless a new change, failure, or unresolved concern warrants more work; do not claim full verification while required checks remain pending.

Current menu slot coordinates are the sole implementation authority. Legacy slot measurements are useful only for understanding the texture's visual rhythm.

For documentation-only changes to this skill, validate the skill package rather than running Gradle.
