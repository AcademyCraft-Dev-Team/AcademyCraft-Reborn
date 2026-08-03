# P6 Accelerator Parity Audit

## Scope and Safety Boundary

P6 ports the 1.21.1 Accelerator gameplay contract onto the 26.2 `Skill.Builder`, Misaka packet, attachment, sync-key, render-state, and NeoForge event systems. The reference source is a behavior and localization specification, not reusable runtime architecture. Current-only `flow_control`, `kinetic_superposition`, and `directed_shock` are merged into retained skills and removed; `hyper_accelerate` and `vector_reduction` remain registered.

The reference `VectorReflection` and Platinum subsystems contain deep reflection, foreign object-graph mutation, entity purge/tombstones, true-health replacement, Java instrumentation, and DuskMixin hooks. None of those mechanisms will be ported. Standard Sponge Mixins are allowed only for a small vanilla invariant that cannot be expressed by a public NeoForge event; every such Mixin must be listed in `academy.mixins.json` and fail closed.

## Skill Contracts and Merge Decisions

| Skill | Reference contract | 26.2 decision |
| --- | --- | --- |
| `vector_blast` | Level1, 5,000 IF, 10 CP; Alt+left-click; 64-block line, 10 base damage | Add a server-raycast instant skill. Reuse current target geometry and `SkillDamageSource`; cap particles and stop at the first blocking hit. |
| `vector_accel` | Level1, 5,000 IF; 10 CP movement cast | Retain the richer current dash/preview implementation and normalize its level, learning cost, authorization, and reference minimum cost where compatible. |
| `kinetic_energy_applied` | Level2, 10,000 IF; depends on `vector_accel`; maintained body enhancement, projectile acceleration, impact level 1-6, and attack wave | Merge the current projectile, haste, melee, and directed-knockback behavior. Use synchronized attachments, server raycasts, normal `SkillDamageSource`, a high-version distortion entity, and the reference sound. |
| `dir_strike` | Level2, 10,000 IF, 20 CP; depends on `vector_blast` | Keep current visuals and block effect, but align dependency, cost, target authorization, and damage attribution. |
| `vector_reflection` | Level3, 30,000 IF; depends on `kinetic_energy_applied`; reflects eligible damage/projectiles/effects | Keep the bounded current NeoForge incoming-damage implementation. Align tree metadata; never copy entity tracking, teleport, classloader, or foreign-state mutation code. |
| `reflection_filter` | Level4, 60,000 IF; depends on reflection; configurable effect mode plus whitelist/blacklist | Add bounded typed `SkillData`, validated effect IDs, a client screen, and C2S request/update packets. Filter with `MobEffectEvent.Applicable`; no `LivingEntity.addEffect` Mixin unless the event proves insufficient. |
| `storm_wing` | Level4, 60,000 IF; depends on reflection; toggle flight with 20 CP reservation | Enhance current wing movement, add authorization and lifecycle cleanup, align dependency/metadata, and retain the current tornado renderer. |
| `bloodflow_reverse` | Level5, 100,000 IF, 100 CP; depends on reflection; short-range biological attack | Preserve current server-selected target and debuffs, align metadata/range/damage, and protect players/allies through normal events. |
| `plasma_generation` | Level5, 100,000 IF; depends on storm wing; high-cost plasma attack | Preserve the current safe projectile/area behavior and exact-ID assets while aligning tree metadata and server validation. |
| `black_wing` | Level5, 100,000 IF; depends on storm wing; flight, 20 CP reservation, 32-block fan strike | Add a mutually-exclusive wing state, shared server flight controller, normal-pipeline fan damage, and synchronized render effect. |
| `white_wing` | Level5, 100,000 IF; depends on black wing; upgraded flight/fan strike | Add as the next mutually-exclusive tier using the same bounded server controller and a distinct render state/asset. |
| `crossing_the_abyss` | Level5, 100,000 IF; depends on white wing; toggle, 50 CP reservation, enhances outgoing attacks | Re-specify as a maintained damage-domain buff: ordinary Academy damage, bounded bonus damage and healing-reduction effect. Do not replace health storage or synthesize deaths. |
| `platinum_wing` | Level5, 100,000 IF; depends on white wing; flight/fan attack plus execution | Add only the safe wing tier. Sneak-strike becomes a server-raycast high-damage/cooldown action against non-player targets; no entity purge, controller scanning, true-health overwrite, boss-overlay reflection, or instrumentation. |

## State, Networking, and Lifecycle

- Clients send toggle/cast/control intent only. The server derives look vectors, targets, costs, damage, and active state.
- Wing states are mutually exclusive and stored in synchronized boolean attachments. A shared helper owns CP occupation, motion, fan geometry, creative-flight lease, and deactivation.
- Every toggle revalidates selected category, learned/enabled state, dependency, and available CP. Logout, death, category switch, dimension change, and skill loss release attachments, flight, and CP exactly once.
- Reflection-filter lists are deduplicated, limited to 256 entries, and restricted to registered mob-effect identifiers. Blacklist wins over whitelist.
- Packets use bounded `StreamCodec` values. Movement control uses an enum codec and ignores input unless its wing is active.

## Resources and Localization

Import only assets consumed by implemented effects: new skill icons; black/white tornado rings; and the Platinum starfield with its adjacent license. Register paths through `R` and preserve license notices in `thirdparty/` when required.

English and Chinese keys and wording are copied from the 1.21.1 `en_us.json` and `zh_cn.json`: `skill.academy.*`, `key.academy.*`, `hud.academy.*`, `screen.academy.reflection_filter.*`, and `ability.academy.accelerator`. Current 26.2 `.desc` keys remain and receive concise descriptions rather than being removed.

## Implementation Order and Gates

1. Add metadata, localization, icons, and `vector_blast`; normalize existing exact-ID skills.
2. Add `reflection_filter` data/screen/event path and verify reflection interaction.
3. Harden `storm_wing`, then implement shared wing support, Black Wing, and White Wing.
4. Add safe `crossing_the_abyss`, then the reduced-scope Platinum Wing.
5. Run focused unit/codec tests, the full test suite, both build variants, client smoke tests, and two-player dedicated-server lifecycle tests.

Runtime acceptance must cover effect filtering, projectile/melee/environmental reflection, repeated toggle packets, insufficient CP, all wing transitions, attack permissions, logout/death/dimension cleanup, and a dedicated server with no client-class loading.
