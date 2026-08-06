# RunClientDev UI Debug Workflow

The visual UI debugger is available only in `runClientDev`, where `IS_DEV=true`
and the ImGui runtime is present.

The browser, HUD preview, layout editor, validation messages, and live ImGui
inspector follow the active Minecraft language. The development ImGui atlas
loads the bundled WenQuanYi font so Simplified Chinese labels render directly.

- `/academy debug ui` opens the registered GUI layout browser.
- `/academy debug ui <layout>` opens a registered layout directly.
- `/academy debug hud` opens the four-region ability HUD preview.
- `/academy debug save` publishes every dirty layout.
- `/academy uieditor [layout]` remains as a compatibility alias.

Registered layouts lock their widget names, types, and structure. Layout,
common, and codec properties remain editable. `Attach live` arms the selected
GUI layout; the next matching screen exposes its serialized subtree in the
ImGui inspector without capturing code-owned graph, list, input, or network
state.

Publishing writes working copies to `<gameDir>/academy/ui`. When the project
root can be located, it also updates
`src/main/resources/assets/academy/ui/layout`. Existing files are backed up
under `<gameDir>/academy/ui/backup/<timestamp>` before replacement. Set
`academy.ui.debug.project_root` to override automatic project-root discovery.

HUD default anchors live in `hud_layout_defaults.json`. Player HUD settings are
still stored separately and are applied as offsets and scale multipliers on top
of these version defaults.
