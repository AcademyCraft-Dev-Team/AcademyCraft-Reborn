---
name: refresh-academy-ui
description: Update, port, restyle, or review AcademyCraft Reborn GUI/UI/HUD/terminal surfaces using its line-led, high-contrast, translucent, and blur-masked technological visual language. Use for machine container screens, data-terminal apps, ability/developer screens, overlays, widgets, GUI textures, UI layout DSL, and client rendering code in this repository; trigger on requests to update or redo a GUI, match the legacy or supplied in-game examples, reduce decorative color, or create technology through rules, highlights, alpha, and backdrop blur. Do not use for unrelated world, entity, or VFX rendering.
---

# Refresh AcademyCraft UI

Preserve the legacy AcademyCraft identity while implementing with the current Kotlin widget and rendering stack.

## Load the right guidance

- Keep the non-negotiable rules below as the shared baseline. Read reference sections only when the target, change, or unresolved risk needs them; a local adjustment does not require every reference or archetype.
- Use [references/visual-language.md](references/visual-language.md) for the relevant design tokens, screen archetype, control, or motion guidance when styling or layout decisions need it.
- Use the matching machine, terminal, P.R.O.P.S, or Settings sections of [references/sample-derived-compositions.md](references/sample-derived-compositions.md) when matching those supplied examples.
- Use the relevant host, layout, state, blur, or resource sections of [references/implementation-guide.md](references/implementation-guide.md) for implementation questions. Its validation section is the single implementation acceptance checklist.
- Use [references/source-evidence.md](references/source-evidence.md) only when exact legacy dimensions, colors, asset lineage, or behavior need verification.
- For a complete screen redesign, read the shared visual grammar and the full guidance for that archetype, then apply all relevant acceptance checks.

## Choose the task mode

- **Read-only review:** Inspect the requested surface and only the dependencies needed to establish findings. Apply relevant style rules as review criteria; do not edit, redesign, build, or launch the client solely because this skill was selected. Finish with evidence, impact, minimal recommendations, and any unverified visual behavior; report when no actionable findings remain.
- **Implementation:** Follow the workflow below within the requested scope. Pure documentation or skill-maintenance changes use document/package validation rather than the UI implementation workflow.

## Implementation workflow

1. Classify the target as a compact machine screen, developer/skill screen, terminal/app surface, or HUD/overlay.
2. Inspect the target class. Consult a nearest same-archetype implementation only when a convention is unclear. Trace referenced `R` entries, textures, slots, input, networking, animation, or layout DSL factories only when the change or a concrete risk touches them. Preserve unrelated worktree changes.
3. Define a logical-coordinate layout before editing. Keep the archetype's canonical canvas, layer order, density, and alignment; do not size from raw texture pixels.
4. Design the hierarchy in grayscale first. Establish separation with blur, neutral alpha planes, rules, and foreground brightness; add semantic color only after the interface reads clearly without it.
5. Compose the surface in this order: world image, archetype-appropriate blur mask, neutral translucent structural plane, sparse white rule/texture overlay, content, then state/feedback layers. Keep blur out of foreground UI.
6. Reuse existing widgets, drawables, textures, and utilities. Implement legacy intent with the current API; never introduce LambdaLib/CGUI classes from the reference project.
7. Encode state through alpha, brightness, restrained color, and short motion. Keep normal, hover, selected, focused, disabled, empty, loading, and error states distinct where the backing behavior actually exposes those states. Do not invent protocol states.
8. Keep input, networking, menu slot handling, localization, and accessibility behavior intact while changing presentation.
9. Apply the relevant checks in [Validate the result](references/implementation-guide.md#validate-the-result), including the repository's required tests and builds. Reuse the same validation evidence across references.

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

## Completion

For implementation, finish when the requested change and applicable acceptance checks are complete. Report files changed, behavior preserved, and validation performed. If a required check cannot be performed, identify the blocker and mark it pending; do not claim full verification. Rerun successful checks only after relevant changes, failures, or unresolved concerns. Read-only reviews finish under their separate task-mode criteria above.
