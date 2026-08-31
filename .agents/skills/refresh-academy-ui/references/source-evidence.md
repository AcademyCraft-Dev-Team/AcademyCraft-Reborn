# Legacy UI Source Evidence

## Contents

- Reference scope
- Architectural evidence
- Measured layouts
- Palette observations
- Asset lineage
- Behavior evidence
- How to use this evidence

## Reference scope

The source used to derive this skill is:

`D:\mcmodtest\AcademyCraft-neoforge-1.21.1\AcademyCraft\dependencies\AcademyCraft-master`

The reference implementation is predominantly Scala plus CGUI XML for these screens. The current repository uses a different Kotlin widget stack. Treat all legacy paths below as design evidence, not as code to import.

This file is intentionally self-contained for everyday use. Re-open the legacy source only when an exact animation formula, authored coordinate, texture layer, or interaction edge case is material to the current task.

## Architectural evidence

### Shared machine UI

- `src/main/scala/cn/academy/core/client/ui/TechUI.scala` defines the translucent `BlendQuad`, shared 172-176 px-class machine host, left page buttons, animated info area, property rows, edit color, and semantic telemetry colors.
- Machine-specific composition appears under `src/main/scala/cn/academy/energy/client/ui`, including `GuiWindGenBase.scala`, `GuiSolarGen.scala`, `GuiNode.scala`, and `GuiMatrix.scala`.
- Crafting machines appear under `src/main/scala/cn/academy/crafting/client/ui`, including `GuiMetalFormer.scala`, `GuiImagFusor.scala`, and `GuiAbilityInterferer.scala`.
- `src/main/java/cn/academy/core/client/ui/TechUIContainer.java` supplies the inventory slot rhythm used by the authored machine background.

### Wireless node

- `src/main/resources/assets/academy/guis/page_wireless.xml` defines the legacy 176 x 187 panel, 150 x 16 rows, 12 px icons, 9 px labels, 48 x 9 input, and 12 px connect action.
- The current implementation intentionally increases the row and control hit areas slightly; use current `WirelessPanelUtil` values for new work.

### Developer screen

- `src/main/resources/assets/academy/guis/page_developer.xml` defines the 400 x 187 fixed screen, left/right authored panels, ability icon, thin progression line, and node work area.
- The current `AbilityDeveloperScreen` preserves this fixed-canvas lineage and should be the implementation authority.

### Terminal

- `src/main/java/cn/academy/terminal/client/TerminalUI.java` and `src/main/resources/assets/academy/guis/terminal.xml` define the projected/floating terminal, parallax, custom cursor, launcher tiles, staggered entrance, selected texture, and selection sound.
- The legacy settings app XML uses a dense translucent settings sheet with full-width compact rows and a thin scrollbar.
- The current `TerminalHud` changes the physical launcher scale but retains the projected, icon-forward, brightness-and-motion-driven identity.

### HUD

- The legacy CP bar implementation uses a wide edge-aligned low-profile texture, smoothed CP/overload values, a short blend-in, semantic white/orange/red states, and overload-only glow.
- Current serialized HUD layouts under `src/main/resources/assets/academy/ui/layout` are the implementation authority for placement.

## Measured layouts

| Surface or asset | Legacy authored size | Logical/display size or current equivalent |
| --- | ---: | ---: |
| Machine background | 352 x 374 | 176 x 187 |
| Developer left panel | 217 x 374 | 108.5 x 187 |
| Developer right panel | 556 x 374 | 278 x 187 |
| Developer full canvas | - | 400 x 187 |
| Developer internal work area | - | about 257 x 139 |
| Legacy list-row texture | 300 x 32 | 150 x 16 |
| Legacy wireless panel | - | 176 x 187 |
| Legacy wireless row | - | 150 x 16 |
| Legacy terminal root | - | 640 x 785 |
| Legacy terminal app tile | - | 151 x 151 with 110 px icon |
| Legacy settings root | - | 742 x 923 at 0.2 scale |
| Legacy settings content | - | 614 x 720; rows 611 x 60; scrollbar 9 x 96 |
| Current terminal collapsed | - | 150 x 200, 32 px right margin |
| Current terminal unfolded | - | 384 x 200 |
| Current terminal tile | - | 48 x 62, 3 columns, 4 px gap |

The legacy machine inventory uses an 18 px pitch: hotbar near y = 163 and main rows near y = 141, 123, and 105, with x near `6 + 18 * column`. Confirm the current menu before using these values.

## Palette observations

Direct inspection of representative legacy textures produced these dominant colors:

| Texture role | Dominant or notable value | Observation |
| --- | --- | --- |
| Parent machine background | `#80000000` | Approximately half-opacity black over most of the surface |
| Wind-base technical lines | white at alphas near `0x18`, `0x47`, `0x77`, and `0xFF` | Several deliberate information-strength layers |
| Generic element row | `#3DFFFFFF` | Light translucent row structure |
| Terminal background | near `#33848383` | Pale translucent neutral projection plane |
| Settings sheet | near `#99252525` and `#336C6C6C` | Dark dense surface plus lighter separators/rows |

Shared semantic colors in `TechUI.scala` include energy `0xFF25C4FF`, buffer `0xFF25F7FF`, phase `0xFF7680DE`, capacity `0xFFFF6C00`, and edit/focus `0xFF2180D8`.

## Asset lineage

SHA-256 comparison found these legacy/current assets byte-identical even where their paths changed:

| Legacy asset | Current asset |
| --- | --- |
| `textures/ui/ui_windbase.png` | `textures/gui/element/ui_gen.png` |
| `textures/ui/ui_node.png` | `textures/gui/node/ui_node.png` |
| `textures/element/element_background300x32.png` | Current element-row texture of the same name |
| Developer parent backgrounds | Current developer parent backgrounds |
| Developer left/right authored panels | Current developer left/right UI textures |
| Developer buttons and node textures | Current developer button/node assets |

This establishes direct visual continuity and supports reusing the current assets rather than redrawing the legacy art. Recheck paths with `rg --files src/main/resources` because resource folders can be reorganized.

## Behavior evidence

- Machine page buttons rest around 80% opacity and become fully bright when hovered or current.
- Edit mode uses blue while idle/read-only information remains white.
- Wind-generator structure completeness is communicated by approximately 20%, 60%, and 100% alpha stages.
- The legacy terminal staggers app entrance by about 0.1 seconds per tile over a roughly 0.4 second fade; normal icons sit around 60% opacity and selected icons around 80%, with a separate selected background.
- Developer nodes use restrained breathing opacity only when the node state benefits from visible activity.
- The CP bar blends in over roughly 200 ms and confines glow or shader emphasis to overload/urgent presentation.
- The current implementation standardizes machine reveal around 600 ms, page changes around 350 ms, terminal unfold up to 400 ms, app hover around 100 ms, and toggle movement around 150 ms.

## How to use this evidence

Use the evidence to answer design questions such as "why is this panel translucent?", "what scale should this texture use?", or "which accent is appropriate?". For implementation details, always prefer the current repository's widget API, utilities, resource map, and neighboring screens. When the legacy and current implementations differ, preserve current input correctness and architecture while carrying forward the legacy visual intent. Current menu slot coordinates are always authoritative; legacy measurements only explain the authored texture's visual rhythm.
