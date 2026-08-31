# AcademyCraft UI Visual Language

## Contents

- Core character
- Contrast-first grammar
- Design tokens
- Blur and masking
- Screen archetypes
- Controls and state
- Motion
- Anti-patterns
- Review checklist

## Core character

AcademyCraft UI should feel like a restrained scientific instrument projected over the game world. Its identity comes from translucent monochrome planes, crisp white line work, deliberate contrast, compact technical information, and sparse semantic color. It is not a conventional desktop window or a collection of opaque cards.

Build layers in this order:

1. Keep the world visible enough to preserve spatial context.
2. Blur only the world region owned by the archetype's mask.
3. Establish structure with neutral translucent planes at roughly 10-50% opacity.
4. Add sparse, explicit white rules and authored technical line art.
5. Render primary text and controls in white, with secondary content at 60-80% brightness/opacity.
6. Reserve saturated color for live data, focus, progression, capacity, warnings, or another explicit meaning.

## Contrast-first grammar

Build the screen in grayscale before choosing accents. A successful AcademyCraft surface remains legible and recognizably technological when desaturated.

Use this luminance ladder:

| Tier | Treatment | Purpose |
| --- | --- | --- |
| L0 context | Sharp or blurred world | Establish place without competing with content |
| L1 structure | Neutral black/white plane at 10-50% alpha | Separate the UI from the world |
| L2 grouping | Secondary neutral fill around 10-25% alpha | Form rows, input fields, selected tabs, and zones |
| L3 guidance | White/gray at 40-75% | Inactive diagrams, grid lines, labels, and secondary actions |
| L4 focus | Crisp white at 80-100% | Primary rules, active icons, current values, and selected controls |
| L5 semantic | One small cyan/blue/orange/red accent | Identify a data channel or exceptional state |

Create focus by moving an element upward through this ladder. Do not reach for a new hue when alpha, brightness, a 1 px rule, or a neutral local fill can communicate the state.

### Line hierarchy

- Use 1 logical px for separators and ordinary rules.
- Use authored 2x line textures at half size for machine frames and diagrams.
- Let one or two primary horizontal rails anchor a surface. Avoid drawing a complete bright border around every terminal row.
- Use full white for the strongest frame or selected contour; use 20-70% white for construction lines, disabled machine parts, chart grids, and secondary separators.
- Keep corners square. Lines should terminate or connect with intent, especially around machine diagrams, ports, pipes, slots, and data channels.
- Keep bloom subordinate. A crisp white edge against a dark plane should create most of the apparent glow.

## Design tokens

### Color and alpha

Use ARGB integers in rendering code. Treat these values as a small semantic palette, not as decoration.

| Role | Typical value | Use |
| --- | --- | --- |
| Structural machine fill | `#80000000` | Main translucent dark surface |
| Terminal root plane | `#40000000` | Shared frosted workspace over the terminal blur mask |
| Terminal row/input fill | `#28000000` | Low-contrast grouping without borders |
| Selected terminal tab | white up to 50% alpha | Current section, animated by brightness rather than hue |
| Light row/card fill | `#3DFFFFFF` | List row or selection surface |
| Very light dark fill | `#28000000`-`#40000000` | HUD, progress background, subtle grouping |
| Primary foreground | `#FFFFFFFF` | Active icons, selected state, primary text |
| Resting foreground | white at 60-80% | Secondary text and inactive controls |
| Disabled foreground | white at about 20% | Unavailable actions or incomplete structure |
| Edit/focus blue | `#FF2180D8` | Editable value or focused field |
| Energy cyan | `#FF25C4FF` | Energy generation or flow |
| Buffer cyan | `#FF25F7FF` | Energy buffer or stored charge |
| Phase violet | `#FF7680DE` | Phase or specialized machine state |
| Capacity orange | `#FFFF6C00` | Capacity, saturation, overload-adjacent state |
| Progression blue | `#FF1177D6` | Developer/ability progression |

Do not use several saturated accents on one surface unless each has a stable data meaning. Avoid arbitrary gradients and broad color washes.

The neutral target shown by the machine and P.R.O.P.S/settings examples takes precedence over classes whose names suggest a terminal theme but whose palette belongs to another screen family. Do not import gold, green, or red merely because `DataTerminalTheme` exists.

### Typography

- Use 8 logical pixels as the current base size.
- Use roughly 0.65-0.75 scale for compact metadata when legibility remains acceptable.
- Legacy fixed canvases commonly use 9-12 logical-pixel labels; preserve those proportions when matching existing authored textures.
- Create hierarchy with alpha, spacing, alignment, and weight before introducing more font sizes.
- Keep strings localizable. Test English and Chinese labels, especially right-aligned values and narrow action rows.
- Avoid decorative drop shadows and oversized headings. Larger text is appropriate only for deliberate terminal/app title moments.

### Spacing and geometry

Prefer the established spacing rhythm:

- Micro gaps: 2 px.
- Control gaps: 4 px.
- Compact row padding or markers: 6.5-8 px.
- Panel inset: 10-12 px.
- Separator: 1 px.
- Thin progress line: about 1.5 px.
- Compact scrollbar: 5 px.
- Common icons: 12, 14, 16, 24, or 32 px.
- Compact rows: 16-18 px.
- Terminal launcher tile: 48 x 62 px with a 48 x 48 px icon region.

Use square or nearly square geometry. Rounded Material-style cards, pills, thick borders, and large soft shadows conflict with the visual language.

## Blur and masking

Blur is a spatial separation tool, not a surface decoration.

- Blur the captured world before compositing neutral planes, rules, icons, and text.
- Preserve large world shapes and overall light direction while removing block-edge detail and readable background patterns.
- Add a neutral translucent plane over the blurred image so white text remains stable on snow, sky, interiors, and night scenes.
- Never blur text, icons, authored machine textures, rules, the radar chart, or the cursor.
- Never stack an app-local blur on top of the terminal blur. One owner performs the blur; child widgets remain transparent or use simple alpha fills.
- Test blur at its minimum and maximum configured values. At zero blur, the neutral plane must still provide adequate contrast.

Use the archetype-specific ownership model:

| Archetype | Blur scope | Structural plane |
| --- | --- | --- |
| Modal machine/container screen | Screen background through the screen host | Compact machine `BlendQuadWidget` at about 50% black plus authored line art |
| Data terminal HUD/app | Only pixels covered by the terminal stencil/depth mask | Shared terminal root plane `0x40000000`; child rows/inputs commonly `0x28000000` |
| Standalone full screen | Screen host's blurred/transparent background | A centered neutral panel appropriate to that screen |

### Authored texture scale

Many legacy textures are authored at 2x resolution and displayed at half size. Keep nearest sampling and map texture pixels to logical coordinates deliberately:

- 352 x 374 machine background -> 176 x 187 logical px.
- 217 x 374 developer left panel -> 108.5 x 187 logical px.
- 556 x 374 developer right panel -> 278 x 187 logical px.
- 300 x 32 list row -> 150 x 16 logical px.

Do not use the source bitmap dimensions directly as layout dimensions.

## Screen archetypes

### Compact machine screen

- Use a 176 x 187 logical-pixel authored canvas when the surrounding menu follows the canonical machine format.
- Keep the page rail approximately 16 px wide to the left of the main panel.
- Layer `BlendQuadWidget`, the machine texture, live content, and state feedback.
- Use bright top/bottom rails and the authored equipment/pipe/slot line work as the main geometry. Do not replace this with filled cards.
- Keep the equipment/status diagram in the upper portion and the inventory grid in the lower portion. Let darker incomplete components sit behind bright connected parts.
- Keep page controls detached on the left. Use bright white for the current page and a lower luminance tier for inactive pages.
- Preserve inventory slot coordinates and hit behavior from the menu. Visual inventory cells must agree with server-side/menu positions.
- Put extended telemetry in a compact adjacent info area rather than expanding the central machine face arbitrarily.

### Info area

- Use a translucent `BlendQuadWidget` background.
- A typical property row is 128 px wide with about 6.5 px vertical padding and 2 px inter-row spacing.
- Use a 6.5 px colored marker to identify data series.
- Left-align the label and right-align the live value.
- Bracketed values are appropriate for legacy-compatible compact editors; focused/editable values use the focus blue.

### Wireless node panel

- Keep the canonical 176 x 187 panel with roughly 12 px horizontal and 10 px vertical inset.
- Use 8 px major and 4 px minor spacing.
- Current list rows are 18 px high, with a 14 px state icon, flexible device name, 46 x 10 px input, 14 px action icon, and 5 px scrollbar.
- Compose the shared wireless page from `BlendQuadWidget` plus rows and scrolling content. The `ui_node` texture belongs to the node/inventory face, not the wireless list page.
- Use a white fill around 25% opacity for row structure.
- Render inactive icons as `0xFFBBBBBB` and brighten them to white for hover, focus, press, or selection.

### Developer/ability screen

- Preserve the art-directed 400 x 187 fixed canvas.
- The canonical left panel is 108.5 x 187; the right panel is 278 x 187, with an internal work area near 257 x 139.
- Treat nodes, prerequisites, learned state, and progression as layers rather than conventional form controls.
- Unlearned or unavailable content generally sits around 40-60% opacity. Use progression blue consistently.

### Terminal and terminal app

- Keep the terminal as a floating/projected system, including its transform, parallax, and world-aware presentation when present.
- The current launcher rests as a 150 x 200 px strip at the right with a 32 px margin and unfolds toward a 384 x 200 px centered surface.
- Use three apps per row, 4 px gaps, and 48 x 62 px launcher tiles.
- A terminal app shell uses a 16 x 16 px back action, centered compact title, and 1 px white separator.
- Let the shared `0x40000000` root plane and stencil blur carry the workspace. App roots should not obscure that glass effect with opaque backgrounds.
- Use low-alpha black rows and inputs (`0x28000000`) without bright borders; reserve the primary rule for the header and selected/interactive edges.
- Use large quiet zones. The terminal gains scale from whitespace, alignment, and blur, not from more decoration.
- Use a high-contrast monochrome toggle: white track and black thumb, with position carrying the boolean state.
- Use a small white cursor with a dark core and restrained local glow. Do not turn every control into a glowing object.
- Preserve icon-forward selection, brightness lift, and short motion. Do not add opaque desktop chrome or mobile-style navigation bars.

### HUD and overlay

- Align HUD information to screen edges and keep bars low-profile.
- Smooth live values rather than snapping noisy telemetry every tick.
- Reserve glow and pulsing for overload, urgency, selection, or another meaningful state.
- Prefer serialized layout when the neighboring HUD already uses `assets/academy/ui/layout` so placement remains consistent and adjustable.

## Controls and state

### Icon actions

- Default: `0xFFBBBBBB` or 60-80% white.
- Hover/focus/pressed: white.
- Selected: persistently white or backed by the appropriate authored selected texture.
- Disabled: around 20% white and non-interactive.
- Keep hit targets usable even when the visible glyph is only 12-16 px.

### Text input

Use the current `TextBoxWidget` so focus, selection, caret, keyboard handling, and IME behavior remain correct. Integrate it into a dark translucent field or the legacy bracket treatment. Do not replace an editable field with a painted string.

For a terminal command line, use a separate `>` prompt marker, a broad `0x28000000` input plane, white text/caret, and no decorative border. Focus may add a narrow rule or brightness lift if needed, but should not introduce a new accent by default.

### Tabs and rows

- Animate a selected terminal tab from transparent to white at no more than about 50% alpha over roughly 100 ms.
- Keep inactive tabs transparent with white text; avoid colored tab bars.
- Use low-alpha row planes for dense dashboard entries. Separate rows with 2 px gaps instead of bright outlines.
- Leave simple settings rows unboxed when alignment alone is sufficient.

### Progress and status

Use a thin progress bar with a `0x40000000`-class background and white or one semantic accent for the foreground. Clamp values and distinguish empty, partial, full, unavailable, and error states.

### Scrolling

Use the current scroll panel and scrollbar widgets. Clip content, keep the 5 px compact scrollbar where space is constrained, and test zero, one, and many rows.

### Tooltip

Use a black surface near 50% opacity, compact 5 px horizontal and 2 px vertical padding, and white text near 80% opacity. A tooltip supplements an icon; it does not replace visible state feedback.

## Motion

Use a small family of predictable timings:

| Interaction | Typical timing/easing |
| --- | --- |
| Machine reveal | 600 ms, ease-out exponential |
| Side info entrance | 600 ms, ease-out cubic, about 20 px vertical travel |
| Page show/hide | 350 ms; show ease-out-back, hide ease-in-cubic |
| Terminal unfold | Up to 400 ms, cubic |
| Terminal content crossfade | 75-100 ms |
| App hover | 100 ms, sine, scale up to about 1.2 |
| Toggle thumb | 150 ms, ease-out cubic |

Breathing opacity around 0.675-0.85 is acceptable only for meaningful active nodes or status indicators. Make animations interruptible and ensure hidden pages are disabled as well as transparent.

## Anti-patterns

- Opaque full-screen panels that erase the game-world context.
- Full-screen blur for a terminal app when the terminal stencil already defines the frosted region.
- Blurring the UI foreground, text, line art, or cursor together with the world.
- Using saturated color as the main source of hierarchy or adding multiple accents to make the screen feel technological.
- Rounded card dashboards, pill buttons, heavy borders, broad shadows, or colorful gradients.
- Bright borders around every terminal row, input, and setting.
- Glow on every icon, rule, or label.
- Replacing established technical icons with text-only actions.
- Communicating selection or availability only on hover.
- Scaling a 2x authored texture at full bitmap size.
- Absolute positioning inside a fluid list or terminal content area.
- Porting LambdaLib/CGUI types into the current codebase.
- Continuous decorative motion with no information value.

## Review checklist

- Inspect the surface at representative GUI scales and on bright and dark world backgrounds.
- Inspect a desaturated screenshot: layout, state, and hierarchy must remain clear without semantic color.
- Confirm blurred world detail cannot interfere with text while major world shapes remain recognizable.
- For terminal apps, confirm pixels outside the stencil remain sharp and child content does not receive the blur.
- Check normal, hover, focused, pressed, selected, disabled, empty, loading, and error states as applicable.
- Verify that each saturated accent has one consistent meaning.
- Confirm 1 px rules and authored textures remain crisp.
- Test English and Chinese text for clipping or collision.
- Verify hidden content is not focusable or clickable.
- Confirm transitions can reverse or restart without leaving stale alpha, scale, or enabled state.
