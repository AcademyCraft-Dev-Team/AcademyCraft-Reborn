# P4 Teleport Parity Audit

## Scope and Porting Rules

This audit compares the twelve reference Teleport skills in
`org/academy/internal/common/ability/builtin/teleport/skills` with the 26.2 Teleport category. Reference gameplay, levels, learning energy, CP costs, and dependencies are the parity target; the current registry, Misaka packets, skill data, render-state pipeline, and NeoForge lifecycle remain authoritative implementation infrastructure.

C2S packets carry input intent, selected indices, or entity IDs only. The server rechecks skill ownership, CP, dimension, range, collision, targets, chunks, and all world mutations. Existing 26.2-only skills remain registered when their mechanics are distinct.

## Skill Decisions

| Reference skill | Reference contract | 26.2 decision |
| --- | --- | --- |
| `threatening_teleport` | Level1, 5,000 IF, 10 CP; Alt+left-click teleports one held item into a target within 24 blocks and deals `4 + weapon bonus` damage | Add exact ID. `matter_warp` is a distinct point attack and remains. |
| `space_folding_theorem` | Level1, 5,000 IF passive; depends on threatening teleport and multiplies compatible damage by 1.25 | Add exact ID. `spatial_synergy` teleports nearby players and remains distinct. |
| `self_teleport` | Level2, 10,000 IF; depends on threatening teleport; held preview selects a collision-safe point within 20 blocks | Harden the existing ID with server range/collision validation and exact progression. |
| `penetrate_teleport` | Level2, 10,000 IF, 20 CP; held preview reaches 36 blocks through intervening blocks | Merge into `cut_through`, retaining the current ID but adopting reference progression, fixed cost, safe destination checks, and server-derived direction. |
| `flesh_ripping` | Level3, 30,000 IF, 30 CP; 14-block held target; `12 + 5% max health` space damage | Add exact ID after the single-target helper is verified. |
| `location_teleport` | Level3, 30,000 IF, 30 CP; persistent named marks, selection UI, cross-dimension self teleport, maximum 32 marks | Add exact ID and typed data. Keep the simpler `coordinate_teleport` as a current-only skill. |
| `quick_location_teleport` | Level4, 60,000 IF, 30 CP; sends self or the server-picked target to the selected mark | Add exact ID using location data and temporary destination chunk leases. |
| `area_teleport_select` | Level4, 60,000 IF; server raycast selects a clamped 32³ region and synchronizes its preview | Add exact ID and transient server state. |
| `area_teleport_setup` | Level4, 60,000 IF; records the destination anchor | Add exact ID; depends on area select. |
| `area_teleport_start` | Level4, 60,000 IF, 50 CP; buffered same-dimension block/block-entity/entity move | Add an overlap-safe transaction with bounded chunk leases, rollback, and entity freeze lifecycle. |
| `flashing` | Level5, 100,000 IF; 30 CP reserved while enabled, 10 CP per safe eight-block directional dash | Add exact ID. `flash_back` is a damage-cancel passive and remains distinct. |
| `defensive_teleport` | Level5, 100,000 IF; 30 CP reserved, 10 CP per hostile/projectile repelled 16 blocks | Add exact ID with bounded hostile scans and safe destinations. |

Current-only `clip_through`, `matter_warp`, `spatial_synergy`, `visual_teleport`, `disarm`, `shackle`, `coordinate_teleport`, `flash_back`, `phantom_falling`, `spacial_replace`, and `spacial_excision` must keep working.

## State, Assets, and Mixin Audit

- Persistent state: typed location marks and selected index; migration must tolerate missing or legacy data.
- Transient state: held previews, area selection/setup, active dashes/defence, chunk leases, frozen entities, and in-flight transactions. Clear these on logout, death, category switch, world unload, and server stop.
- Copy exact reference icons and sounds. Add English/Chinese skill text, bindings, status/error messages, and developer-tree dependencies.
- No Teleport skill directly imports a DuskMixin hook. Phase-protection and Vector Reflection DuskMixins found in the reference are unrelated. If area entity stabilization cannot be implemented through current tick/events, add focused standard Sponge Mixins to `academy.mixins.json`; do not port the DuskMixin runtime.

## Implementation Order and Gates

1. Add `threatening_teleport` plus `space_folding_theorem`, with a shared space-damage helper and formula tests.
2. Harden `self_teleport`, then merge `penetrate_teleport` into `cut_through`; reject non-finite, over-range, obstructed-destination, and stale packets.
3. Add `flesh_ripping`, then persistent `location_teleport` and `quick_location_teleport`.
4. Add area select/setup preview and the buffered move transaction. Test overlap, block entities, protected blocks, unloaded chunks, disconnects, and rollback.
5. Add `flashing` and `defensive_teleport`, then audit every current-only Teleport skill.

Each slice must pass focused unit tests, `test -DisDev=true`, both build variants, and two-player dedicated-server acceptance. Runtime acceptance remains a separate gate from compilation.

## Implementation Record

All twelve reference contracts are now represented in 26.2. `penetrate_teleport` is merged into `cut_through`; the other eleven use their reference IDs. Location marks use typed persistent skill data, while area selection and chunk leases are bounded transient server state. The area move buffers source and destination blocks and block entities, prechecks protection events, freezes affected non-player entities, and rolls back world state on an unexpected failure.

`flashing` and `defensive_teleport` use the current skill toggle and CP-occupation system instead of legacy entity NBT. Dash directions, targets, ranges, destinations, collision, world borders, and loaded chunks are all server-derived or revalidated. The exact reference icons and available sounds were imported.

No Teleport implementation required a Mixin: public 26.2/NeoForge APIs cover movement, block entities, protection events, rendering, input, networking, and chunk leases. Focused tests plus `test -DisDev=true` and both build variants pass (53 tests on 2026-08-01). Client and two-player dedicated-server acceptance remains pending.
