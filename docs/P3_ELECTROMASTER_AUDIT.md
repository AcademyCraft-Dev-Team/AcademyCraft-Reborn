# P3 Electromaster Parity Audit

## Scope and Rules

This audit compares the 1.21.1 reference files under
`org/academy/internal/common/ability/builtin/electromaster/skills` with the 26.2 implementations under
`org/academy/internal/common/ability/electromaster/skills`. Existing 26.2-only modes remain available. Client packets carry input intent only; targeting, CP, damage, and state transitions are authoritative on the server.

Reference DuskMixin hooks are not copied. Use NeoForge events when they expose the same stable phase; otherwise use a focused standard Mixin declared in `academy.mixins.json`.

## Skill Decisions

| Reference skill | Reference behavior | Current 26.2 state | Port decision |
| --- | --- | --- | --- |
| `arc_generate` | Level1, 5,000 IF, 10 CP, 10-block thin ray, 4 base damage | Existing Level1 ray with current arc renderer; damage is based on CP cost | Keep renderer/input and normalize learning cost, damage, range, and server hit rules to the reference contract. |
| `magnet_manipulation` | Level3, 30,000 IF; hold-to-move toward a server ray target at 10 CP/s | Level2 instant pull of iron blocks/golems | Keep the iron interaction as an extra mode; add the held movement mode and update progression metadata to Level3. |
| `mine_detect` | Toggle, 20 permanent CP, 64-block ore outline/HUD; depends on magnet manipulation | Missing | Add the exact ID. Scan loaded client chunks incrementally on the client thread; never read `Level` from a background executor or request unloaded chunks. |
| `lightning_spear` | Level2, 10,000 IF, 40 CP; immediate 32-block, radius-2, 16-damage arc | `thunder_lance` is a 20-tick charged, narrower Level3 attack | Merge as a quick-cast mode into `thunder_lance`; preserve the charged mode and current ID instead of registering a duplicate concept. |
| `railgun` | Level4, 60,000 IF, 100 CP, 150 damage; thrown coin or held coin/iron ammunition | Existing Level4 charged coin shot, 200 CP, 150 damage | Preserve the current charge/render/entity pipeline; add safe reference ammunition fallbacks and make cost configurable before changing balance. |
| `electromagnetic_shield` | Level4 toggle, 40 permanent CP; stores up to `100 × power` absorbed damage and cools 10 points for 20 CP every 40 ticks | Missing | Implement exact ID with persistent `SkillData`, server damage event interception, authoritative cooling, and no DuskMixin. |
| `current_recharge` | Level3, 30,000 IF; hold H, spend 5 CP/tick to charge a looked-at block/entity within 5 blocks | `pulse_charge` only updates redstone neighbors | Keep `pulse_charge` distinct. Add the exact reference ID using NeoForge 26.2 transactional energy capabilities. |
| `current_symbiosis` | Level3, 30,000 IF; toggle, 30 permanent CP; charge worn/held equipment every 10 ticks | Missing | Add after the shared transactional energy helper used by `current_recharge`. Optional inventory integrations remain outside core P3. |
| `bioelectric_operation` | Level4, 60,000 IF; toggle, 40 permanent CP; attribute-based movement, attack, mining, jump, step, and fall bonuses | `bioelectric_surge` is a temporary potion-buff mode with shutdown penalties | Keep both because their mechanics differ. Add the exact reference ID with source-scoped transient attribute modifiers. |
| `thunder_clap` | Level5, 100,000 IF, 100 CP; 64-block target, radius 5, 20% max-health damage | `thunderclap` spends all available CP and deals 30 lightning damage in radius 6 | Preserve `thunderclap`; merge reference targeting and configurable percentage damage without importing true-health DuskMixin behavior. |

Current-only `electrical_contact`, `lightning_nova`, `magnet_moment_charge`, `magnetic_weapon`, `iron_sand_arsenal`, `lightning_storm`, and `ball_lightning` remain registered. Their tree nodes and progression metadata must be audited separately; they are not removed to make the reference tree fit.

## First Slice: Electromagnetic Shield

- Registry ID: `academy:electromagnetic_shield`.
- Progression: Level4, 60,000 IF, dependency `academy:magnet_manipulation`.
- Input: configurable Alt+K release toggle; empty C2S intent packet.
- Maintenance: 40 CP through the current permanent-occupation system.
- Capacity: `100 × ability power`; stored absorption is clamped when bonuses change.
- Damage: absorb incoming server-side damage until capacity is full, then pass the remainder through the normal Minecraft damage pipeline.
- Cooling: every 40 ticks, spend calculation-adjusted 20 CP only when stored absorption is nonzero, then remove `10 × ability power` stored damage.
- Persistence/sync: custom `SkillData`; mutations mark player data dirty and schedule normal skill-data sync.
- Lifecycle: category switching and overload use the shared toggle/occupation cleanup; no static per-player context is retained.
- Hook replacement: reference `MixinPlayerElectromagneticShield` modifies `Player.hurt`; 26.2 uses `LivingIncomingDamageEvent`, so no Mixin is required for this slice.

## Verification Gate

- Unit-test absorption boundaries and cooling clamps.
- Validate registry dependencies and both language JSON files.
- Confirm reference icon hashes after copying.
- Run `test -DisDev=true`, `build -DisDev=true`, and `build -DisDev=false`.
- Runtime gate: incoming damage, full shield overflow, insufficient cooling CP, save/rejoin, category switch, overload, and two-player dedicated-server behavior.

## Implementation Status

- Implemented and unit/build verified: `arc_generate`, held `magnet_manipulation`, `mine_detect`, `electromagnetic_shield`, `current_recharge`, `current_symbiosis`, `bioelectric_operation`, quick-cast `thunder_lance`, `railgun`, and `thunderclap`.
- `mine_detect` uses the 26.2 staged render buffer and a registered always-depth outline pipeline. Its 64-block scan runs in 8,192-position client-thread batches, reads only loaded chunks, updates the HUD progressively, and resets on movement, world change, disable, or periodic refresh.
- No DuskMixin hook was required for this group: the shield uses `LivingIncomingDamageEvent`, energy transfer uses NeoForge transactional capabilities, attributes use transient source-scoped modifiers, and mine rendering uses the existing standard `MixinLevelRenderer` event bridge.
- `thunder_lance` retains its 20-tick charged mode and adds the reference 40-CP, 32-block, radius-2 quick mode under a second configurable binding. Both modes derive aim, damage, and CP on the server and exclude the caster from path damage.
- `railgun` retains the current 200-CP charged balance until a shared server balance configuration exists. Its learning cost is corrected to 60,000 IF; legal ammunition is limited to the project coin, iron ingot, iron block, or a nearby owned thrown coin. Throw velocity, rotation, item consumption, cooldown, skill authorization, and CP are server-derived.
- `thunderclap` now spends the reference fixed 100 CP, resolves the nearest block/entity hit within 64 blocks on the server, uses a radius-5 sphere, and deals `20% × player damage multiplier` of each target's maximum health through the ordinary lightning damage pipeline. The reference true-health DuskMixin path is intentionally not recreated.
- Remaining Electromaster work is the separate audit of current-only skill progression/tree nodes and runtime acceptance testing; all ten reference rows now have an implemented target decision.
- All entries still require the runtime gate described below; build success alone is not gameplay verification.
