# AcademyCraft Ability Port Plan

## 1. Objective

Port the completed ability gameplay from the Minecraft 1.21.1 reference project at
`D:\mcmodtest\AcademyCraft-neoforge-1.21.1\AcademyCraft` into this Minecraft/NeoForge 26.2 project while preserving the current architecture.

The reference project is a behavioral and asset specification. Its string registries, packet layer, player NBT model, render hooks, coremods, Java agents, and DuskMixin transformers are not target architecture.

## 2. Current Baseline

The 26.2 project already provides:

- synchronized custom registries for `AbilityCategory` and `Skill`;
- `Skill.Builder`, dependency resolution, development conditions, CP occupation, experience, and per-skill runtime data;
- client/server contexts, configurable input, the ability developer UI, and Misaka `PacketType`/`StreamCodec` networking;
- five registered categories (`level0`, `electromaster`, `teleport`, `accelerator`, and `meltdowner`) and 56 registered skill IDs.

The reference project contains seven categories, 56 category skills, and five common brain-development skills. `aeromanip` and `darkmatter` are entirely new categories. Existing category names overlap, but their skill trees and behavior do not match one-to-one.

The first sandboxed build attempt on 2026-08-01 could not download the Gradle distribution. After using the approved external Gradle cache, `test -DisDev=true`, `build -DisDev=true`, and `build -DisDev=false` all completed successfully. Game launch and dedicated-server runtime behavior remain unverified.

## 3. Architectural Rules

### 3.1 Registration and IDs

- Register categories and skills through the current `DeferredRegister` and synchronized registries.
- Keep existing `academy:<id>` values unless the migration matrix explicitly introduces a new ID.
- Do not rename an existing ID to match the reference project. Add a data migration/alias only if importing 1.21.1 saves becomes a supported requirement.
- Validate category membership, registered dependencies, dependency cycles, and orphaned registry entries during common setup.

### 3.2 Server Authority

- Clients send intent only: key state, selected mode, requested position, or aim vector.
- The server checks current category, learned state, level, dependencies, development conditions, CP, cooldown/iteration limits, target reach, line of sight, chunk state, and permissions.
- Damage, entity creation, teleportation, inventory mutation, and block changes run only on the logical server.
- Packet payloads are bounded and contain no client-supplied damage, CP cost, or trusted hit result.

### 3.3 Runtime State

- Persist long-lived state with the current `SkillData` serialization system.
- Use attachments, `DataTypes`, or `SyncKeys` for state observed by other systems or clients.
- Use `ClientContext`/`ServerContext` for transient casts and ensure cleanup on release, death, logout, category change, dimension change, and server shutdown.
- Centralize toggles and CP reservations so repeated packets cannot duplicate occupation or release.

### 3.4 DuskMixin Replacement Policy

- Do not port DuskMixin transformers, Java agents, ASM rewrites, or custom class-loader logic.
- Prefer public Minecraft/NeoForge APIs and events first.
- When access is impossible, add a standard Mixin under `org.academy.mixin.client` or `org.academy.mixin.common` and declare it in `academy.mixins.json`.
- Keep each Mixin focused on one invariant. Prefer `@Accessor`/`@Invoker`; use `@Inject`/`@Modify*` only when an event or accessor cannot express the behavior.
- Document target method, injection point, expected call count, and failure behavior. Avoid optional foreign-mod targets in the core mixin config.
- Re-specify reference behavior that depends on invasive true-health replacement, arbitrary foreign-entity deletion, or bytecode instrumentation instead of recreating the transformer chain.

## 4. Milestones

| Milestone | Scope | Exit gate |
| --- | --- | --- |
| P0 | Framework authorization, registration validator, tests | Invalid cross-category learning is rejected; registry graph validates; both build variants pass |
| P1 | Common brain-development branch | Available to non-Level0 categories, hidden from Level0, saved/synced correctly |
| P2 | `aeromanip` plus six skills | Complete playable tree on client and dedicated server |
| P3 | Electromaster and Meltdowner parity | Matrix entries resolved without duplicate semantics |
| P4 | Teleport parity | Safe unloaded-chunk, collision, logout, and dimension behavior |
| P5 | `darkmatter` plus supporting content | Items, enchantments, entities, recipes, rendering, and cleanup complete |
| P6 | Advanced Accelerator skills | Wings/filter/reflection work without DuskMixin/coremod dependencies |
| P7 | Balance, compatibility, and release audit | Full regression matrix and resource/license audit pass |

## 5. Implementation Sequence

### P0 — Framework Guardrails

1. Add `AbilityRegistrationValidator` and run it after `AbilitySystemFinalizedEvent` resolves dependency holders.
2. Reject skills whose category does not match the player's selected category in both instant learning and timed development handlers.
3. Introduce an explicit skill availability scope before common skills are registered: category-only, non-Level0 common, and internal/hidden if required.
4. Make category switching deactivate toggles, unregister contexts, and release permanent CP occupation.
5. Add JUnit 5 and contract tests for registry integrity, dependency cycles, category authorization, codecs, and cleanup policy.

### P1 — Common Brain Development

Preserve the current `level0_passive_lv1` through `level0_passive_lv5` IDs, but move them to an explicit common branch visible to developed non-Level0 players. Reconcile their existing passive attribute effects with the reference max-CP, CP-recovery, and calculation-efficiency bonuses through server configuration. Do not duplicate them as `brain_development_lv*` unless save import is implemented.

### P2 — Aeromanip Vertical Slices

Create the category shell, icon resources, translations, registry entries, and developer layout. Implement in this order:

1. `airflow_jet`: instant movement cast; proves the complete registration/input/network/CP/effect path.
2. `atmosphere_shield`: maintained attack/knockback enhancement; establishes toggle and cleanup behavior.
3. `breathing_film`: always-on maintained passive depending on the shield; establishes idempotent automatic CP reservation.
4. `atmosphere_blast_gun`: instant frontal area attack.
5. `flight`: server-authorized movement state with logout/dimension cleanup.
6. `vacuum_domain`: bounded area scan, periodic cost, and multi-entity effects.

### P3 — Existing Categories

For every row in `ABILITY_PORT_MATRIX.md`, write a short behavior comparison before coding. Exact-ID overlaps enhance the current implementation. Semantic candidates retain the current ID unless tests prove they are distinct abilities. Existing 26.2-only skills remain available and are not deleted merely because the reference tree lacks them.

### P4 — Teleport

Implement single-entity same-dimension operations first. Location and area teleport require:

- server-side collision and border validation;
- bounded chunk tickets with guaranteed release;
- transactional selection/setup/execution state;
- entity passenger/leash policy;
- cancellation on death, logout, dimension change, or world unload;
- no forced loading from untrusted client coordinates.

### P5 — Darkmatter

Treat Darkmatter as a content subsystem, not seven isolated skill files. Port in dependency order: category/assets, material item and recipes, shaping/enchantments, disassembly/cut, repair, summoned entity, six-wings rendering/flight, then radiation. Use ordinary Mixins only where an accessor is unavoidable.

### P6 — Accelerator Advanced Features

Port `reflection_filter`, `vector_blast`, and `crossing_the_abyss` before wing tiers. Implement black/white/platinum wings on current damage, attachment, renderer, and entity APIs. The reference `VectorReflection` and platinum support code must be decomposed into documented gameplay rules; entity compatibility is best-effort and must never corrupt foreign entity state.

## 6. High-Version Adaptation Checklist

- Convert `ResourceLocation` usage to current `Identifier` APIs.
- Replace reference `IPacket`/`C2SPacket`/`S2CPacket` with Misaka packet types and `StreamCodec`.
- Replace `PlayerSyncData` and arbitrary persistent tags with `SkillData`/attachments/sync keys.
- Register new entity types, data serializers, particles, sounds, menus, and renderers through current registries.
- Adapt entity rendering to the 26.2 render-state APIs; do not copy 1.21.1 renderer event code blindly.
- Route damage through `SkillDamageSource` and current damage tags.
- Respect `mobGriefing`, protection hooks, world borders, build height, and server permissions for block-affecting skills.
- Bound ray traces, entity searches, particle counts, packet frequency, and per-tick work.
- Keep Tensura, Touhou Little Maid, ManaScore, and other optional integrations outside the core port until P7.

## 7. Definition of Done per Skill

A skill is complete only when all items are true:

- stable registry ID, category, level, dependencies, development conditions, and configurable balance values;
- developer-tree node, icon, description, Chinese/English names, and non-conflicting configurable input;
- server-authoritative CP, hit, range, permission, and learned/category checks;
- packet codecs round-trip and reject malformed/out-of-range input;
- runtime state survives or clears according to the documented lifecycle;
- client effect/audio starts and stops correctly without dedicated-server client class loading;
- single-player and two-player dedicated-server behavior is verified;
- save/reload, death, logout, dimension change, and category change are tested;
- `test`, development build, and release build pass.

## 8. Change and Review Strategy

- Commit framework changes separately from individual category ports.
- Prefer one simple skill or one tightly coupled skill cluster per change.
- Keep resource-only changes adjacent to the skill that consumes them.
- Record current status and deviations in the migration matrix in the same change.
- Preserve GPL-3.0 notices and copy any third-party asset license into `thirdparty/` before importing the asset.

## 9. Progress Log

### 2026-08-01 — P0 and first vertical slice

- Added startup validation for category membership, registered dependencies, cross-category dependency rejection, and dependency cycles.
- Added server-side selected-category authorization to instant learning and timed development requests; malformed skill IDs are rejected.
- Category changes now disable unavailable toggles, release their permanent CP occupation, and unregister active server contexts.
- Client and shared skill checks now suppress use outside the selected category.
- Added JUnit 5 and contract tests for dependency graphs and category availability.
- Registered `aeromanip` and implemented the first `airflow_jet` vertical slice with input, packet, CP cost, server movement, particles, skill-tree data, translations, and imported GPL project icons.
- Verification: tests, development build, and release build passed. In-game and dedicated-server smoke tests are still required.

### 2026-08-01 — P1 common brain development

- Added explicit `CATEGORY` and `COMMON` skill scopes. Common skills are authorized for every category that opts in and are unavailable while the player is Level0.
- Migrated `level0_passive_lv1` through `level0_passive_lv5` to one shared developer-tree branch while preserving registry IDs and adding an enforced Lv1→Lv5 dependency chain.
- Matched the reference learning-energy values and configurable cumulative bonuses: +620 maximum CP, +31 CP recovery, and +0.50 calculation efficiency at the complete branch.
- Adapted recovery to the current CP-occupation model by shortening non-permanent occupation time. Calculation efficiency reduces authoritative CP costs and contributes to the shared ability-power multiplier.
- Effective maximum CP is derived from saved base CP and learned skills. A persisted applied-bonus marker adjusts available CP exactly once across learning, removal, category changes, configuration changes, and old-save migration.
- Updated the developer screen, HUD wheel, commands, client checks, server checks, registry validation, sync payload, and contract tests. Tests and both build variants pass; runtime smoke testing remains pending.

### 2026-08-01 — P2 maintained Aeromanip skills

- Implemented `atmosphere_shield` after confirming the reference behavior is an attack-damage/knockback enhancement rather than incoming-damage reduction. It uses an initially-disabled toggle, adjusted permanent CP occupation, transient attribute modifiers, and server-side cleanup.
- Implemented `breathing_film` with its reference dependency on `atmosphere_shield`. It is an always-on learned passive that idempotently reserves CP and restores air every ten server ticks while Aeromanip is selected.
- Added exact reference icons, English/Chinese text, skill-tree nodes, the shield toggle packet, safe initial toggle state, and a server check preventing unlearned toggle packets from reserving CP.
- Verification: JSON resources, icon hashes, tests, and development/release builds pass. In-game and dedicated-server smoke tests remain pending.

### 2026-08-01 — P2 Atmosphere Blast Gun

- Implemented `atmosphere_blast_gun` with the reference 8-block frontal volume, 8 base damage, strong forward/upward knockback, 40 CP cost, Level4 requirement, and `atmosphere_shield` dependency.
- The client sends an empty cast intent. The server derives the eye/look vectors, bounds the entity query, rejects allies and occluded targets, applies `SkillDamageSource`, and computes damage through the current CP/ability-power multiplier.
- Added the exact icon, localized text, configurable Alt+left-click binding, packet registration, and geometry contract tests. Tests and both build variants pass; runtime smoke testing remains pending.

### 2026-08-01 — P2 Flight and Vacuum Domain

- Implemented `flight` as an initially-disabled maintained skill with the reference Level5 requirement, `breathing_film` dependency, 50 CP reservation, Alt+F input, localized tree node, and exact icon.
- Adapted flight to NeoForge 26.2 through an independent transient `CREATIVE_FLIGHT` attribute modifier. Removing this skill's lease preserves flight granted by creative mode or other mods and clears active flight only when no source remains; no DuskMixin or raw `mayfly` access is used.
- Implemented `vacuum_domain` with the reference 16-block targeting range, 12-block radius, 200-tick lifetime, ten-tick pulse interval, 100 CP cast, and five-percent maximum-health damage. The client sends no position or damage values; the server performs the ray trace, hostile filtering, spherical bounds check, and damage calculation.
- Added context-removal lifecycle hooks so casts clean up their per-player active marker on expiry, logout, category change, death, or dimension change. Domain scans use a bounded AABB plus sphere filter and never force-load chunks.
- Added the exact vacuum-domain icon, English/Chinese text, configurable Y-key input, packet registration, and boundary tests. Unit tests and both development/release builds pass; in-game and dedicated-server smoke tests remain pending.

### 2026-08-01 — P3 Electromaster first group

- Audited the ten reference Electromaster skills before implementation and recorded merge/retain decisions in `P3_ELECTROMASTER_AUDIT.md`.
- Normalized `arc_generate`; expanded `magnet_manipulation` with its held movement mode; and added exact-ID implementations for `electromagnetic_shield`, `current_recharge`, `current_symbiosis`, and `bioelectric_operation` while preserving distinct current-only modes.
- Added a shared NeoForge 26.2 transactional energy-charging helper and generic synchronized custom-skill-data mutation for shield persistence.
- Implemented `mine_detect` with a client-thread incremental scan of loaded chunks, an always-depth 26.2 line pipeline, progressive HUD state, periodic refresh, and no background world access.
- Added exact reference icons, configurable bindings, Misaka packet types, English/Chinese text, tree nodes, and focused unit tests. Tests and both development/release builds pass; runtime smoke tests remain pending for this group.

### 2026-08-01 — P3 Electromaster parity completion

- Merged the reference `lightning_spear` as a second quick-cast mode of `thunder_lance`, retaining the current charged mode and registry ID. Both paths now use skill-attributed damage and explicitly exclude the caster.
- Corrected Railgun authorization: the server now validates the selected category, learned/enabled skill, CP, cooldown, and ammunition before consuming anything. It accepts coins, iron ingots, iron blocks, and nearby thrown coins owned by the caster; the throw packet carries intent only.
- Preserved Railgun's current 200-CP balance pending a shared server balance configuration, while correcting its learning cost to 60,000 IF and scaling its 150 base damage through the current player multiplier.
- Reworked `thunderclap` to a fixed 100-CP cast with a 64-block server ray, nearest block/entity selection, radius-5 sphere, visual lightning, arc effects, and normal-pipeline damage equal to 20% of target maximum health times player scaling.
- Added the merged Thunder Lance icon, tree dependencies, English/Chinese descriptions and bindings, focused damage/target/ammunition tests, and lifecycle cleanup for charged contexts. Runtime smoke testing remains pending.

### 2026-08-01 — P3 Meltdowner first slice

- Audited all eight reference Meltdowner skills and recorded exact-ID, merge, preserve, and new-skill decisions in `P3_MELTDOWNER_AUDIT.md` before coding.
- Repaired `single_high_speed_electron_beam`: the empty client intent now runs through authoritative skill/CP validation, the delayed entity retains and revalidates its caster, and damage uses the reference `20 + 1% target maximum health` formula with current player scaling and `SkillDamageSource` attribution.
- Added the exact `radiation_intensify` Level1 passive with its 5,000-IF cost and single-beam dependency. Successful compatible beam hits apply a 200-tick mark plus Weakness/Slowness; marked follow-up hits receive the reference 1.5x multiplier.
- Extracted a pure Meltdowner beam-damage helper for later continuous and multi-beam skills. Added a player-authorized block-path overload that posts NeoForge `BreakBlockEvent`, allowing protection integrations to cancel beam destruction without a Mixin.
- Added both reference icons, developer-tree nodes, English/Chinese text, and focused damage/mark tests. All 33 tests and both build variants pass; gameplay and dedicated-server runtime checks remain pending.

### 2026-08-01 — P3 Meltdowner reference parity

- Completed the remaining reference decisions: held `mining_beam`, charged `spreading_blast`/`scatter_bomb`, exact `light_shield`, exact `particle_wave_cannon`, merged `jet_strike`/`assault_jet`, and exact `auto_cruise_beam_cannon`.
- Added shared beam damage/radiation operations, player-authorized block destruction through NeoForge events, generic skill ownership on delayed beam entities, and synchronized beam length/block-interaction state.
- All C2S packets now carry empty input intent for this group. Charge duration, look direction, targets, movement, CP, damage, block edits, hostile filtering, delayed settlement, and toggle state are server-owned.
- Kept `electron_barrier` and `homing_blast` because their mechanics differ from the new exact reference skills. Other current-only Meltdowner skills also remain registered for a separate preservation audit.
- Added exact icons, tree nodes, English/Chinese text, and focused tests for costs, timing, beam counts, damage formulas, ranges, and direction validation. The complete suite has 45 passing tests and both build variants pass; runtime acceptance remains pending.

### 2026-08-01 — P5 Darkmatter subsystem

- Added the `darkmatter` category and all seven reference skills in dependency order, including server-authoritative shaping, disassembly, cut, radiation, repair, owned-beetle creation, and Six Wings flight/enhancement behavior.
- Added the material, armor/equipment assets, enchantments, equipment events, duplication recipe, slash/beetle entities and renderers, synchronized wing effect, and focused standard Mixins replacing the two reference DuskMixins.
- Copied the consumed 1.21.1 assets and aligned English/Chinese localization keys and terminology to that project while retaining 26.2 `.desc` keys. Optional PSI radial integration remains deferred.
- Focused tests, the full suite, data providers, and both development/release builds pass after the final changes. Client and two-player dedicated-server runtime acceptance remains pending.

### 2026-08-01 — P6 Accelerator audit

- Audited all 13 reference Accelerator skills and recorded merge, preserve, and safe re-specification decisions in `P6_ACCELERATOR_AUDIT.md` before implementation.
- Explicitly excluded the reference true-health replacement, foreign object-graph mutation, entity purge/tombstone, Java instrumentation, and DuskMixin runtime chains. Platinum Wing will use ordinary server-authoritative damage and bounded target rules.
- Fixed the implementation order as Vector Blast, metadata normalization, Reflection Filter, wing lifecycle, Crossing the Abyss, and finally the reduced-scope Platinum Wing. Localization keys and wording follow the 1.21.1 English and Chinese files.

### 2026-08-01 — Data Terminal keybindings and skill HUD

- Registered the Settings app in the current data terminal and ported the 1.21.1 Settings/Keybindings localization contract.
- Added a live InputSystem binding registry with keyboard/mouse capture, modifier support, reset-to-default behavior, immediate client-config persistence, and compatibility with existing per-skill defaults.
- The keybinding list shows global controls plus the selected category's skill controls. The V-key skill wheel now displays each learned skill's current binding, including distinct press/release bindings.
- Development compilation, the complete test suite, and both development/release JAR builds pass. In-game interaction and persistence smoke testing remain pending.

### 2026-08-01 — Skill implementation control matrix

- Added `SKILL_IMPLEMENTATION_CONTROL_MATRIX.md`, covering all 89 currently registered skills with port status, progression metadata, implementation/effect summaries, source defaults, dependencies, and implementation classes.
- Recorded global and same-category default-key conflicts, zero-IF progression gaps, and current-only implementations that need server-authority or damage-pipeline review before unified balance work.
- Proposed a per-skill `SkillBalanceConfig` as the next step for changing damage, range, duration, CP, maintenance, and cooldown values without rebuilding the mod.
