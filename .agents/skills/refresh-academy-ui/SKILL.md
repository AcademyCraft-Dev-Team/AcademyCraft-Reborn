---
name: refresh-academy-ui
description: Update, port, restyle, or review AcademyCraft Reborn GUI/UI/HUD/terminal surfaces using its line-led, high-contrast, translucent, and blur-masked technological visual language. Use for machine container screens, data-terminal apps, ability/developer screens, overlays, widgets, GUI textures, UI layout JSON, and client rendering code in this repository; trigger on requests to update or redo a GUI, match the legacy or supplied in-game examples, reduce decorative color, or create technology through rules, highlights, alpha, and backdrop blur. Do not use for unrelated world, entity, or VFX rendering.
---

# Refresh AcademyCraft UI

Preserve the legacy AcademyCraft identity while implementing with the current Kotlin widget and rendering stack.

## Load the right guidance

- Read [references/visual-language.md](references/visual-language.md) for every styling or layout task.
- Read [references/sample-derived-compositions.md](references/sample-derived-compositions.md) for every machine-screen or data-terminal task; it turns the supplied in-game examples into exact composition profiles and acceptance tests.
- Read [references/implementation-guide.md](references/implementation-guide.md) before changing Kotlin widgets, screens, HUD layouts, or GUI resources.
- Read [references/source-evidence.md](references/source-evidence.md) when exact legacy dimensions, colors, asset lineage, or behavior need verification.

## Workflow

1. Classify the target as a compact machine screen, developer/skill screen, terminal/app surface, or HUD/overlay.
2. Inspect the target class, its nearest same-archetype implementation, referenced `R` entries, textures, menu slot coordinates, and layout JSON. Preserve unrelated worktree changes.
3. Define a logical-coordinate layout before editing. Keep the archetype's canonical canvas, layer order, density, and alignment; do not size from raw texture pixels.
4. Design the hierarchy in grayscale first. Establish separation with blur, neutral alpha planes, rules, and foreground brightness; add semantic color only after the interface reads clearly without it.
5. Compose the surface in this order: world image, archetype-appropriate blur mask, neutral translucent structural plane, sparse white rule/texture overlay, content, then state/feedback layers. Keep blur out of foreground UI.
6. Reuse existing widgets, drawables, textures, and utilities. Implement legacy intent with the current API; never introduce LambdaLib/CGUI classes from the reference project.
7. Encode state through alpha, brightness, restrained color, and short motion. Keep normal, hover, selected, focused, disabled, empty, loading, and error states distinct where the backing behavior actually exposes those states. Do not invent protocol states.
8. Keep input, networking, menu slot handling, localization, and accessibility behavior intact while changing presentation.
9. Validate layout at representative GUI scales, long localized strings, empty and full lists, hover/focus/pressed states, transitions, live data updates, bright/dark worlds, and minimum/maximum blur. Run focused tests, then the repository build appropriate to the change.

## Non-negotiable style rules

- Create the technological character in this priority order: explicit geometry, line hierarchy, transparency hierarchy, local contrast, blur isolation, then at most one semantic accent. Color is never the primary structure.
- Favor monochrome translucent planes, fine white rules, technical icons, and one semantic accent over opaque card grids or colorful gradients.
- Keep foreground rules, icons, text, and authored line art crisp. Blur only the captured world behind a mask; never blur the foreground UI or stack multiple blur passes for decoration.
- Treat compact machine screens as dark, high-contrast instruments over a modal blurred/dimmed world. Treat expanded terminal apps as wide, low-contrast frosted workspaces with blur restricted to the terminal stencil while the outside world remains sharp.
- Use glow only as a small cursor, urgent indicator, or selected-state reinforcement. White-on-dark contrast should provide most of the perceived luminosity.
- Use `BlendQuadWidget` for the canonical machine/info-panel treatment unless the target archetype already provides its own structural background.
- Display legacy 2x textures at half their pixel dimensions with nearest sampling unless the current asset explicitly uses a different logical scale.
- Keep text compact: 8 logical px is the current base; use hierarchy through scale, alpha, spacing, and alignment rather than many font sizes.
- Keep icon-only actions dimmer at rest and white when hovered, selected, focused, or pressed. Do not communicate state by hover alone.
- Prefer `FrameLayoutWidget` for overlays and `LinearLayoutWidget` for rows/columns. Use absolute margins only for art-directed fixed canvases such as machine and developer screens.
- Treat animation as feedback, not decoration. Preserve the timing families in the visual-language reference and avoid perpetual motion except for meaningful status/breathing effects.
- Do not move inventory slots visually without checking the menu's slot coordinates and container hit handling.
- Do not hand-edit generated resources under `src/generated/resources`.

## Completion checklist

- Confirm the screen still belongs visibly to its archetype and shares spacing, alpha, icon, and motion rules with adjacent AcademyCraft UI.
- Confirm the hierarchy remains clear in grayscale and still looks technological after temporarily removing semantic accents.
- Confirm the world is recognizable but not readable beneath blurred areas, the world outside a terminal mask stays sharp, and every foreground line/text layer remains crisp.
- Confirm all states remain usable with keyboard/mouse focus and that hidden pages are both invisible and disabled.
- Confirm dynamic widgets invalidate or update correctly and that scroll panels clip and bound their content.
- Confirm textures use existing `R` identifiers when available; add resource identifiers consistently when new assets are unavoidable.
- Report files changed, behavior preserved, validation performed, and any in-game visual check still required.
