# P5 Darkmatter Parity Audit

## Scope and Architecture

Darkmatter is a new 26.2 category, not a group of independent skills. Port it in dependency order with its material item, enchantments, recipes, summoned entity, renderers, flight lease, and cleanup rules. The reference category probability is `0.1` and its seven skills form three branches rooted at `darkmatter_shaping`.

Client packets carry cast, hold, or toggle intent only. The server owns CP charging, target selection, protection events, block mutation, entity ownership, summon limits, and all persistent state. Current `SkillData`, Misaka packets, NeoForge events, render states, and registries replace legacy `PlayerSyncData` and custom network classes.

## Skill Contracts

| Skill | Reference contract | 26.2 adaptation |
| --- | --- | --- |
| `darkmatter_shaping` | Level1, 5,000 IF, 50 CP; U creates material in an empty hand or repairs and toggles the darkmatter enchantment on the held item | First vertical slice. Use the current enchantment component API and exact material/icon assets. |
| `darkmatter_disassemble` | Level1, 5,000 IF, 30 CP; Alt+left-click attacks or destroys a server-raycast block within 32 blocks | Require NeoForge break permission, unbreakable/protected-block checks, loaded chunks, and server-selected targets. Six Wings expands the effect to radius 3. |
| `darkmatter_cut` | Level2, 10,000 IF, 40 CP; R damages a forward 8-block cone for 12 damage | Add a short-lived slash entity/render state. Six Wings expands the cone to 24 blocks and 16 damage. |
| `darkmatter_radiation` | Level3, 30,000 IF; hold C, 10 CP per two ticks, front-hemisphere hostile scan to 32 blocks | Use a bounded maintained server context. Combine the reference double hit into one `SkillDamageSource` hit instead of resetting invulnerability frames or rotating arbitrary vanilla damage types. |
| `darkmatter_repair` | Level4, 60,000 IF; Alt+U toggle, 10 CP per healing tick, 4 health times ability strength | Use `LivingEntity.heal` and normal health events; do not port the raw-health DuskMixin accessor. Charge CP only when healing succeeds. |
| `darkmatter_creation` | Level4, 60,000 IF, 80 CP; G summons up to eight owned beetles and reserves 20 CP each | Store owned UUIDs in typed skill data, validate dismiss targeting, clean up reservations on removal/logout/category change, and use a 26.2 mob renderer. |
| `darkmatter_six_wings` | Level5, 100,000 IF; Alt+R toggle and 70 CP reservation; grants flight and enhances other Darkmatter skills | Reuse the NeoForge creative-flight attribute lease pattern, synchronize a render-state effect, and release flight/CP on every exit path. |

## Content and Mixin Decisions

- Register `academy:darkmatter`, four armor items, an armor material, two data-driven enchantments, the duplication recipe, slash entity, and beetle entity before dependent skills.
- Import the reference category/skill/item/entity/effect textures. Generate item models and recipes through `runClientData`; do not hand-edit generated resources.
- The two reference DuskMixins affect `ItemStack.getMaxDamage` and `ItemEntity.fireImmune`. Port them as focused standard Sponge Mixins in `academy.mixins.json`, guarded by a shared enchantment helper. No DuskMixin runtime is introduced.
- Do not initially port optional PSI radial bindings. They are integration features rather than core Darkmatter behavior and remain deferred until the category passes standalone acceptance.

## Implementation Order and Gates

1. Category, material, enchantments, standard Mixins, and `darkmatter_shaping`.
2. Permission-safe `darkmatter_disassemble`, then slash entity and `darkmatter_cut`.
3. Maintained `darkmatter_radiation` and public-API `darkmatter_repair`.
4. Owned beetle entity plus `darkmatter_creation`.
5. Flight/effect lifecycle for `darkmatter_six_wings`, armor content, and duplication recipe.

Every slice requires focused tests, `test -DisDev=true`, development and release builds, then client and two-player dedicated-server acceptance. Runtime acceptance remains separate from compilation.

## Implementation Record

The complete seven-skill category, material, four-piece armor set, two enchantments, duplication recipe, standard ItemStack/ItemEntity Mixins, slash entity, owned beetle, Six Wings flight/effect state, equipment rules, exact reference assets, and 1.21.1 localization keys are now implemented. Optional PSI radial integration remains deliberately deferred.

Focused tests and the full test suite pass. Data generation completed all providers and emitted the four equipment item/model definitions plus `darkmatter_duplication.json`; NeoForge left cleanup threads alive after provider completion, so the command wrapper timed out even though the data log recorded successful completion. Both `build -DisDev=true` and `build -DisDev=false` pass after the final equipment-event changes. Client and two-player dedicated-server runtime acceptance remains pending.
