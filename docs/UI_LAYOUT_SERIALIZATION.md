# Ability UI Layout Serialization

The following ability interfaces load their static geometry through the upstream
`WidgetSerializer` system:

- `mental_control_hud.json`
- `location_teleport.json`
- `reflection_filter_wide.json` / `reflection_filter_compact.json`
- `precision_operation_wide.json` / `precision_operation_medium.json` /
  `precision_operation_compact.json`

Bundled defaults live in `assets/academy/ui/layout`. A file with the same name
under `<gameDir>/academy/ui` overrides the bundled layout. Invalid overrides
fall back to the bundled resource; invalid or missing bundled resources fall
back to a programmatic layout.

Widget names listed in `SerializedAbilityUiLayoutTest` are binding contracts.
They may be moved or resized in the UI layout editor, but must not be renamed or
removed. Runtime behavior, callbacks, dynamic rows, graph nodes, and network
state remain code-owned and are rebound after deserialization.
