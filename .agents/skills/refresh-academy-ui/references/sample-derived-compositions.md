# In-game Sample-derived Compositions

## Contents

- Authority and scale
- Shared visual grammar
- Machine profile
- Data-terminal profile
- P.R.O.P.S dashboard
- P.R.O.P.S console
- Settings app
- Do not hybridize the profiles
- Acceptance tests

## Authority and scale

Five supplied in-game screenshots define the desired result:

- Samples 1-2: compact wind/solar machine screens.
- Samples 3-5: expanded P.R.O.P.S dashboard, P.R.O.P.S console, and Settings terminal apps.

The screenshot geometry matches current code at GUI scale 4:

- A machine face appears about 704 px wide because its logical width is 176 px.
- An expanded terminal appears about 1536 x 800 px because its logical canvas is 384 x 200 px.

Use the logical sizes from current code. Do not encode screenshot pixels directly.

## Shared visual grammar

The two profiles share a contrast-first language even though their density differs:

1. Preserve the game world as context.
2. Remove background detail with archetype-appropriate blur.
3. Establish a neutral translucent plane.
4. Draw the information structure with explicit white lines and alignment.
5. Use brightness/alpha to express state.
6. Add only a small semantic accent where data requires it.

The interface should still read as technological after full desaturation. If removing cyan/blue destroys the hierarchy, the composition relies too heavily on color.

## Machine profile

### Composition

Use the canonical 176 x 187 canvas and 16 px left page rail:

```text
page rail | compact machine face | optional 128 px telemetry
          | equipment diagram    | channel label/value
          | port/control + brand | secondary information
          | inventory grid       |
```

The supplied machine examples demonstrate this order:

- A bright top rail and bottom rail frame the dark instrument without a conventional filled title bar.
- Two 16 x 16 page actions float to the left. The active inventory action is white; the inactive wireless action is gray.
- A large authored equipment diagram occupies the upper center. Pipes and ports connect it to the control region below.
- Incomplete or unavailable equipment segments remain visible at a lower luminance tier rather than disappearing.
- The output/control block and AcademyCraft mark form a middle bridge between the diagnostic diagram and inventory.
- Three inventory rows plus a separated hotbar form the lower high-density grid. Each slot receives a crisp white technical outline because the grid is directly interactive.
- Optional telemetry grows to the right and aligns with the machine's top rail. It uses a 128 px information surface rather than widening the machine face itself.

### Layer recipe

Use this order:

1. Modal screen background blur/dimming owned by `ContainerUiScreen`.
2. `BlendQuadWidget` at about 50% alpha black.
3. `ui_inventory` and the page-specific authored machine texture at 176 x 187 logical px.
4. Equipment/status sprite layers with 20%, 60%, or 100% alpha as applicable.
5. Item stacks, live values, and focused states.
6. Optional `InfoAreaUtil` panel to the right.

Keep the authored line textures on nearest sampling. Never blur or smooth the machine face together with the world.

### Luminance and accent

- Primary rails, active page icon, connected machine outline, slot contours, and primary text: 80-100% white.
- Inactive page icon and secondary labels: 60-80% white.
- Missing machine components: about 20% white.
- Present but non-working component: about 60% white.
- Main structural plane: about 50% black.
- Semantic telemetry marker: one small cyan square; the rest of the information area remains neutral.

The screenshots look luminous because white line art is surrounded by dark translucent space. Do not add broad glow behind the inventory grid or machine diagram.

## Data-terminal profile

### Shared shell

The expanded terminal uses the current 384 x 200 canvas:

```text
back action | centered app title
--------------------------------  1 px primary separator
wide app content over masked blur
```

Use the current shell values:

- Terminal root plane: `TerminalHud.COLOR`, currently `0x40000000`.
- Expanded size: 384 x 200.
- Back action: 16 x 16 with 2 px top/side margins.
- Header separator: 1 px white with 2 px horizontal padding.
- Terminal blur radius: configurable from 0 to 20, default 20.
- Blur ownership: `HudManager` plus the terminal stencil/depth mask; apps do not run another blur.

The blur region follows the projected terminal quad. The world outside this region remains sharp. Within the region, large snow/sky/building shapes remain visible but detailed blocks no longer compete with labels.

The shared neutral plane must preserve readability when blur is configured to zero. Do not substitute a cool opaque rectangle; the cool appearance in bright outdoor scenes is the blurred world showing through neutral black.

### Foreground behavior

- Keep title, back arrow, separator, labels, and cursor crisp.
- Use a small white cursor ring with a dark core and restrained glow as the local high point.
- Use broad whitespace and alignment rather than extra borders.
- Let content rows use low-alpha black grouping surfaces.
- Keep major geometry square and flat.

## P.R.O.P.S dashboard

The dashboard in sample 3 maps directly to current `PropsApp` and `RadarChartWidget`:

- Main content uses 5 px horizontal and 4 px vertical padding.
- Overview and factor rows form two columns with a 4 px gap.
- The right factor column is 178 px wide.
- Five factor rows use `0x28000000` backgrounds and 2 px vertical gaps.
- Each row uses a larger primary value and a smaller secondary effect label.
- Toggles are 18 x 10, using a white track and black thumb. State is communicated by thumb position, not hue.
- The left radar chart is unfilled line work: muted blue-gray grid at low alpha, with a slightly brighter cyan data outline.
- Footer information is centered below the chart and remains secondary.

The radar accent is acceptable because it represents one live data series. Do not color each factor row differently.

## P.R.O.P.S console

The console in sample 4 demonstrates a sparse terminal state:

- Use 18 px horizontal and 16 px vertical content padding.
- Use 5 px vertical spacing.
- Give the heading a modest 1.15 scale; do not turn it into a large hero title.
- Keep the instructional copy below at reduced scale/brightness.
- Use a separate 8 px-wide `>` prompt marker.
- Give the input the remaining width and an 18 px row height.
- Use `0x28000000` as the input plane with no bright box border.
- Keep status output below the command row at a lower luminance tier.

The empty space is intentional. Do not fill it with decorative charts, grids, or accent panels.

## Settings app

The settings view in sample 5 demonstrates flat navigation and simple aligned controls:

- Keep the shared back/title/separator shell.
- Use three equal-width tabs, each 14 px high with 2 px gaps.
- Animate the selected tab to white at up to 50% alpha over about 100 ms.
- Keep inactive tabs transparent with white labels.
- Use 18 px setting rows and 3 px vertical spacing.
- Keep simple rows unboxed; alignment and whitespace provide structure.
- Use 20 x 10 monochrome toggles aligned to the right.
- Use a text action for HUD layout only when the action name carries more meaning than an icon; keep it secondary and right-aligned.

Do not add a colored tab underline, rounded switches, or separate opaque cards for every setting.

## Do not hybridize the profiles

Both profiles are AcademyCraft, but their contrast budgets are different:

| Machine instrument | Data-terminal workspace |
| --- | --- |
| Compact and dense | Wide and spacious |
| Near-black 50% structural plane | Neutral black 25% root plane over local blur |
| Bright authored line frames | One primary header rule plus low-alpha grouping fills |
| Nearest-sampled pixel/technical art | Smooth projected composite with crisp widget foreground |
| Full outlines around interactive slots and ports | Avoid borders around every row/input |
| Screen-host modal blur/dim | Terminal-stencil local blur |

Do not apply the terminal's frosted row dashboard to a machine inventory face. Do not enlarge a machine's bright slot/frame vocabulary across an expanded terminal app.

## Acceptance tests

For every implementation based on these samples:

1. Capture it against bright snow/sky and a dark interior.
2. Desaturate or inspect the screenshot in grayscale; content hierarchy and selected state must remain clear.
3. Remove or neutralize semantic accents temporarily; the result must still look technological.
4. Verify background detail under the mask is softened enough that it cannot be mistaken for UI text or rules.
5. Verify terminal pixels outside the stencil are sharp and no rectangular blur leaks beyond the projected quad.
6. Verify all foreground lines, text, item icons, radar lines, and cursor edges remain crisp.
7. Test terminal blur at 0 and 20; text must remain readable at both limits.
8. Check 1 px rules and half-scale machine textures at representative GUI scales.
9. Confirm bright outlines are scarce enough to preserve a clear focus hierarchy.
10. Confirm the layout uses no unnecessary hue, gradient, rounded card, broad bloom, or opaque panel.
