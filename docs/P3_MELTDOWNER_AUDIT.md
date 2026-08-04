# P3 Meltdowner Parity Audit

## Scope and Rules

This audit compares the eight reference skills under
`org/academy/internal/common/ability/builtin/meltdowner/skills` with the 26.2 implementations under
`org/academy/internal/common/ability/meltdowner/skills`. The reference project defines gameplay and balance; the target keeps the current registry, Misaka networking, skill execution, rendering, and lifecycle systems.

Client packets carry input intent only. The server owns CP checks, player scaling, aim, targets, block changes, damage, effects, and entity creation. Existing 26.2-only skills remain registered when their mechanics are distinct.

No reference DuskMixin hook is needed for the audited mechanics. Use NeoForge/Minecraft APIs for damage, attributes, effects, movement, and block interaction. Add a focused standard Mixin only if a later runtime audit proves that no public hook can preserve an invariant.

## Skill Decisions

| Reference skill | Reference contract | Current 26.2 state | Port decision |
| --- | --- | --- | --- |
| `single_high_speed_electron_beam` | Level1, 5,000 IF, 20 CP; delayed 20 + 1% max-health beam | Same ID, but 30 CP, no learning cost/server execution check, no caster attribution, fixed 100 damage | Repair as the first vertical slice. Keep the current beam renderer/entity, but make firing and damage server-authoritative. |
| `radiation_intensify` | Level1 passive, 5,000 IF; depends on the single beam; successful Meltdowner beam hits mark for 200 ticks, apply Weakness/Slowness, and later hits deal 1.5x | Missing | Add exact ID and integrate a shared mark helper into compatible single, scatter, particle-wave, and auto-cruise paths. Reference mining-beam damage does not opt into radiation. |
| `mining_beam` | Level2, 10,000 IF; held beam, 5 CP/tick, block breaking, 12 damage every 20 ticks | Same ID exists as a toggle/context with different cost, tier, range, and no damage | Enhance the existing context after extracting a reusable continuous-beam helper. Preserve the current renderer and intent packet. |
| `scatter_bomb` | Level2, 10,000 IF, 40 CP; multiple randomized 20 + 1% max-health rays; depends on the single beam | `spreading_blast` is a configurable multi-ray instant skill with the same dependency | Merge reference damage, cost, radiation, and block-interaction rules into `spreading_blast`; retain the current ID and adjustable beam count. |
| `light_shield` | Level3, 30,000 IF; held shield granting Resistance II and pulsing damage/knockback against hostile mobs | `electron_barrier` is a Level2 maintained frontal damage/knockback field | Mechanics are complementary. Preserve `electron_barrier` and add the exact `light_shield` ID. |
| `particle_wave_cannon` | Level4, 60,000 IF; held 10 CP/tick, 40 + 1% max-health damage every 10 ticks, wide block-breaking beam | Missing | Add exact ID after the mining-beam helper is verified. Reuse maintained-context cleanup and radiation marking. |
| `assault_jet` | Level4, 60,000 IF, 20 CP; forward impulse and radius-3.25 impact attack; depends on light shield | `jet_strike` is a higher-cost forward movement context | Retain `jet_strike` as the target ID and merge reference collision/damage semantics after lifecycle review. |
| `auto_cruise_beam_cannon` | Level5, 100,000 IF; maintained 50 CP, 16-block scan, delayed automatic 10 + 1% max-health beams costing 10 CP/shot | `homing_blast` launches five maintained homing orbs and is not an automatic defensive cannon | Add the exact reference ID; keep `homing_blast` as a distinct current-only skill. |

Current-only `hell_flare`, `trace_ring`, `electron_barrier`, `beta_particle_stream`, `cloudroom`, `homing_blast`, `chain_fusion`, and `disintegrate` remain in scope. Their progression, damage attribution, category cleanup, and developer-tree nodes require a separate preservation audit.

## First Slice: Single Beam and Radiation

- Register `academy:radiation_intensify`, Level1, 5,000 IF, depending on `academy:single_high_speed_electron_beam`.
- Normalize the single beam to Level1, 5,000 IF, 20 CP, with an empty C2S shoot intent.
- Run the packet through `Skill.executeActive`; derive position, rotation, damage scale, ownership, and block interaction on the server.
- Attribute damage through `SkillDamageSource`, exclude the caster, and calculate `(20 + target max health x 0.01) x player damage multiplier`.
- Store the reference 200-tick radiation expiry on the target's persistent entity data. Only a caster who has learned the passive can amplify and refresh the mark.
- Preserve the existing 40-tick charge visual and beam renderer. Add developer-tree nodes, exact reference icons, descriptions, bindings, and focused formula/mark tests.

## Verification Gate

- Unit-test base/max-health scaling, negative/non-finite input clamping, mark expiry, and 1.5x amplification.
- Validate the new registry dependency, packet registration, icon paths, and both language JSON files.
- Run `test -DisDev=true`, `build -DisDev=true`, and `build -DisDev=false`.
- Runtime gate: learned/unlearned packet rejection, exact CP deduction, delayed damage attribution, mark refresh/expiry, block permissions, category switch, death/logout during charge, and two-player dedicated-server behavior.

## Implementation Status

- Implemented the first slice: normalized the single beam to Level1, 5,000 IF and 20 CP, registered its developer-tree node, and added the exact `radiation_intensify` passive and dependency.
- The existing beam renderer and 40-tick charge visual are retained. Its entity now stores the server-selected caster, cancels when that caster becomes invalid, and applies skill-attributed `(20 + 1% max health) x player multiplier` damage instead of fixed unattributed damage.
- Radiation marks persist on hit targets for 200 ticks, add the reference Weakness/Slowness effects for 100 ticks, amplify valid follow-up beam damage by 1.5x, and refresh only after a successful hit by a caster who has learned the passive.
- Added a pure shared beam-damage helper for the remaining Meltdowner rows. Ability block destruction now has an optional player-authorized path that posts NeoForge `BreakBlockEvent`; the single beam uses it so protection integrations can cancel edits.
- Added exact reference icons, English/Chinese text, formula and expiry tests. The first slice passed all tests and both development/release builds. The runtime gate above remains pending.
- No standard Mixin or DuskMixin hook was needed for this slice.

## Parity Completion Status

- `mining_beam` is now a press/release Level2 context with 10,000 IF learning cost. It authoritatively validates 5 CP every two ticks, mines at tier 3 every three ticks through `BreakBlockEvent`, deals 12 scaled skill damage every 20 ticks, and drives a synchronized continuous-beam visual.
- `spreading_blast` keeps the current ID and skill-level growth while merging `scatter_bomb`: 20–80 server charge ticks, 40 CP, seven delayed beams at base levels and eight at level 3, reference health damage, radiation, caster ownership, and protected block paths.
- Added exact `light_shield`: Level3, 30,000 IF, held 5-CP validation, Resistance II, and a four-tick hostile-only radius-3.5 damage/knockback pulse. The existing `electron_barrier` is unchanged.
- Added exact `particle_wave_cannon`: Level4, 60,000 IF, 25-tick server charge, recurring 10 CP, 85-block protected bore, ten-tick `40 + 1% max health` radiation-compatible damage, and a current render-state beam entity.
- Merged `assault_jet` into `jet_strike` without trusting client motion. The server resolves an eight-block collision-safe endpoint, spends 20 CP, applies radius-3.25 scaled damage, synchronizes motion, and retains the current smoke trail.
- Added exact `auto_cruise_beam_cannon`: Level5, 100,000 IF, toggleable 50-CP maintenance, bounded 16-block hostile scan, recurring server-authorized 10-CP shots, 40-tick synchronized visual/damage delay, pet/player/team filtering, and radiation support. `homing_blast` remains distinct.
- All reference Meltdowner rows now have implemented targets. The complete suite has 45 passing tests; both `build -DisDev=true` and `build -DisDev=false` pass. Runtime acceptance and the current-only skill preservation audit remain pending.
- No new Mixin was required. Public NeoForge events, skill contexts, synchronized entity data, vanilla effects/particles, and the existing 26.2 render-state pipeline cover this group.
