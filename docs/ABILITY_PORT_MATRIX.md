# Ability Port Matrix

Status values: `existing` means a 26.2 implementation already exists but still requires parity review; `planned` means a new implementation is required; `candidate` means behavior must be compared before deciding whether to merge; `blocked-late` means deliberately deferred until its supporting subsystem exists.

## Framework and Common Skills

| Priority | Reference ID | 26.2 target | Decision | Status |
| --- | --- | --- | --- | --- |
| P0 | Ability registration validation | Current synchronized registries | Identity, membership, dependency, and cycle validation added | implemented; runtime boot pending |
| P0 | Learning authorization | `LearningHelper`/server handlers | Selected-category ownership enforced on server and client paths | implemented; unit/build verified |
| P1 | `brain_development_lv1` | `level0_passive_lv1` | Preserved ID; common scope; +20 max CP/+1 recovery | implemented; runtime smoke test pending |
| P1 | `brain_development_lv2` | `level0_passive_lv2` | Preserved ID; Lv1 dependency; +0.05 efficiency | implemented; runtime smoke test pending |
| P1 | `brain_development_lv3` | `level0_passive_lv3` | Preserved ID; Lv2 dependency; +100 max CP/+5 recovery | implemented; runtime smoke test pending |
| P1 | `brain_development_lv4` | `level0_passive_lv4` | Preserved ID; Lv3 dependency; +0.15 efficiency | implemented; runtime smoke test pending |
| P1 | `brain_development_lv5` | `level0_passive_lv5` | Preserved ID; Lv4 dependency; +500 max CP/+25 recovery/+0.30 efficiency | implemented; runtime smoke test pending |

## Aeromanip

| Priority | Reference ID | 26.2 target | Decision | Status |
| --- | --- | --- | --- | --- |
| P2 | `airflow_jet` | same ID | First complete vertical slice | implemented; runtime smoke test pending |
| P2 | `breathing_film` | same ID | Always-on oxygen passive; depends on shield; automatic CP reservation | implemented; runtime smoke test pending |
| P2 | `atmosphere_shield` | same ID | Toggleable attack/knockback enhancement; reference behavior corrected | implemented; runtime smoke test pending |
| P2 | `atmosphere_blast_gun` | same ID | Server-derived frontal area attack; reference is instant, not charged | implemented; runtime smoke test pending |
| P2 | `flight` | same ID | NeoForge creative-flight attribute lease; no transformer or raw ability-field access | implemented; runtime smoke test pending |
| P2 | `vacuum_domain` | same ID | Server-targeted, bounded maintained hostile-area effect | implemented; runtime smoke test pending |

## Electromaster

| Priority | Reference ID | 26.2 target | Decision | Status |
| --- | --- | --- | --- | --- |
| P3 | `railgun` | same ID | Server-authorized charged mode; coin/iron/owned thrown-coin ammunition | implemented; unit/build verified |
| P3 | `arc_generate` | same ID | Reference damage/range normalized; current renderer retained | implemented; unit/build verified |
| P3 | `magnet_manipulation` | same ID | Held movement added; current iron-pull modes retained | implemented; unit/build verified |
| P3 | `mine_detect` | same ID | Main-thread incremental loaded-chunk scan, x-ray outline, and HUD | implemented; unit/build verified |
| P3 | `lightning_spear` | `thunder_lance` | Reference quick-cast merged into current charged skill; current ID retained | implemented; unit/build verified |
| P3 | `bioelectric_operation` | same ID | Attribute mode added; current `bioelectric_surge` retained as a distinct skill | implemented; unit/build verified |
| P3 | `electromagnetic_shield` | same ID | Maintained damage store/cooling through NeoForge damage event | implemented; unit/build verified |
| P3 | `thunder_clap` | `thunderclap` | Current ID retained; 64-block server target and 20% max-health mode merged | implemented; unit/build verified |
| P3 | `current_symbiosis` | same ID | Transactional equipment charging through 26.2 energy capability | implemented; unit/build verified |
| P3 | `current_recharge` | same ID | Exact held target-charging skill added; `pulse_charge` retained as distinct redstone mode | implemented; unit/build verified |

Current-only skills such as `lightning_nova`, `magnet_moment_charge`, `magnetic_weapon`, `iron_sand_arsenal`, `lightning_storm`, and `ball_lightning` remain in scope and must continue to work.

## Meltdowner

| Priority | Reference ID | 26.2 target | Decision | Status |
| --- | --- | --- | --- | --- |
| P3 | `single_high_speed_electron_beam` | same ID | Server-authoritative intent, ownership, protected block path, and reference damage formula | implemented; unit/build verified |
| P3 | `mining_beam` | same ID | Held 48-block mining/damage beam with authoritative recurring CP | implemented; unit/build verified |
| P3 | `radiation_intensify` | same ID | Exact passive; shared 200-tick mark and 1.5x compatible follow-up beam damage | implemented; unit/build verified |
| P3 | `light_shield` | same ID | Added exact held defensive pulse; current `electron_barrier` remains distinct | implemented; unit/build verified |
| P3 | `scatter_bomb` | `spreading_blast` | Charged seven-beam reference mode merged; current ID and level growth retained | implemented; unit/build verified |
| P3 | `particle_wave_cannon` | same ID | Exact charged/maintained wide beam using current render-state pipeline | implemented; unit/build verified |
| P3 | `assault_jet` | `jet_strike` | Current ID retained; server aim, safe endpoint, cost, radius, and damage merged | implemented; unit/build verified |
| P3 | `auto_cruise_beam_cannon` | same ID | Added exact maintained hostile scanner; current `homing_blast` remains distinct | implemented; unit/build verified |

Current-only skills including `hell_flare`, `trace_ring`, `cloudroom`, `beta_particle_stream`, `chain_fusion`, and `disintegrate` remain in scope.

Detailed behavior and merge decisions are recorded in `P3_MELTDOWNER_AUDIT.md` before implementation.

## Teleport

| Priority | Reference ID | 26.2 target | Decision | Status |
| --- | --- | --- | --- | --- |
| P4 | `self_teleport` | same ID | Harden existing held preview with exact progression and server validation | implemented; unit/build verified; runtime pending |
| P4 | `threatening_teleport` | same ID | Add exact held-item single-target attack; preserve distinct `matter_warp` | implemented; unit/build verified; runtime pending |
| P4 | `space_folding_theorem` | same ID | Add exact 1.25x compatible-damage passive; preserve distinct `spatial_synergy` | implemented; unit/build verified; runtime pending |
| P4 | `penetrate_teleport` | `cut_through` | Merge reference progression/preview/cost into current ID | implemented; unit/build verified; runtime pending |
| P4 | `location_teleport` | same ID | Add exact persistent named-mark UI; preserve `coordinate_teleport` | implemented; unit/build verified; runtime pending |
| P4 | `flesh_ripping` | same ID | New held offensive teleport | implemented; unit/build verified; runtime pending |
| P4 | `flashing` | same ID | Add exact directional-dash toggle; preserve distinct `flash_back` | implemented; unit/build verified; runtime pending |
| P4 | `defensive_teleport` | same ID | New maintained defensive context | implemented; unit/build verified; runtime pending |
| P4 | `area_teleport_select` | same ID | New transactional selection state | implemented; unit/build verified; runtime pending |
| P4 | `area_teleport_setup` | same ID | New destination state and preview | implemented; unit/build verified; runtime pending |
| P4 | `area_teleport_start` | same ID | New server transaction/chunk-ticket execution | implemented; unit/build verified; runtime pending |
| P4 | `quick_location_teleport` | same ID | New shortcut using location data | implemented; unit/build verified; runtime pending |

Current-only `clip_through`, `visual_teleport`, `disarm`, `shackle`, `phantom_falling`, `spacial_replace`, and `spacial_excision` remain in scope.

`matter_warp`, `spatial_synergy`, `coordinate_teleport`, and `flash_back` were also confirmed as mechanically distinct current-only skills and remain in scope. Detailed contracts, state, assets, Mixin decisions, and gates are recorded in `P4_TELEPORT_AUDIT.md`.

## Darkmatter

| Priority | Reference ID | 26.2 target | Decision | Status |
| --- | --- | --- | --- | --- |
| P5 | `darkmatter_shaping` | same ID | Material creation, repair/enchantment toggle, and equipment rules | implemented; unit/build/datagen verified; runtime pending |
| P5 | `darkmatter_disassemble` | same ID | Permission-safe server entity/block ray; Six Wings area enhancement | implemented; unit/build/datagen verified; runtime pending |
| P5 | `darkmatter_cut` | same ID | Server fan attack plus synchronized slash entity/renderer | implemented; unit/build/datagen verified; runtime pending |
| P5 | `darkmatter_repair` | same ID | Maintained public-API healing without raw-health DuskMixin access | implemented; unit/build/datagen verified; runtime pending |
| P5 | `darkmatter_creation` | same ID | Typed owned-beetle data, reservation, limit, dismissal, and cleanup | implemented; unit/build/datagen verified; runtime pending |
| P5 | `darkmatter_six_wings` | same ID | Creative-flight lease, synchronized effect, enhancement, and cleanup | implemented; unit/build/datagen verified; runtime pending |
| P5 | `darkmatter_radiation` | same ID | Bounded maintained hostile scan and single attributed damage hit | implemented; unit/build/datagen verified; runtime pending |

## Accelerator

| Priority | Reference ID | 26.2 target | Decision | Status |
| --- | --- | --- | --- | --- |
| P6 | `vector_reflection` | same ID | Rewrite against current events/damage APIs; no transformer port | existing |
| P6 | `reflection_filter` | same ID | New filter UI/data model | planned |
| P6 | `bloodflow_reverse` | same ID | Enhance existing implementation | existing |
| P6 | `storm_wing` | same ID | Enhance existing implementation | existing |
| P6 | `black_wing` | same ID | New wing tier and attack | planned |
| P6 | `white_wing` | same ID | New wing tier and attack | planned |
| P6 | `platinum_wing` | same ID | Re-specify safely; no entity purge/true-health transformer chain | blocked-late |
| P6 | `plasma_generation` | same ID | Enhance existing implementation | existing |
| P6 | `kinetic_energy_applied` | same ID | Merge `kinetic_superposition` and `directed_shock`; add reference impact-wave state/effect | implemented; unit/compile verified; runtime pending |
| P6 | `crossing_the_abyss` | same ID | New maintained state/HUD | planned |
| P6 | `dir_strike` | same ID | Enhance existing implementation | existing |
| P6 | `vector_accel` | same ID | Enhance existing implementation | existing |
| P6 | `vector_blast` | same ID | Merge `flow_control`; add click push, held pull, and safe push | implemented; unit/build verified; runtime pending |

Current-only `flow_control`, `kinetic_superposition`, and `directed_shock` are merged and removed. `hyper_accelerate` and `vector_reduction` remain in scope.

## Per-Skill Tracking Fields

When work starts on a row, extend it or its implementation PR with:

- reference behavior and source files;
- target level, CP formula, learning energy, iteration/cooldown, and dependencies;
- packet names and direction;
- persistent, synchronized, and transient state;
- required icons, sounds, particles, entities, shaders, and translations;
- required events or standard Mixins;
- unit, GameTest, dedicated-server, and manual acceptance cases;
- final status: `implemented`, `verified`, or `deferred` with reason.
