# 能力技能前置关系清单

本清单按当前源码中的 `Skill.Builder.dependsOn(...)` 整理，共 94 个技能，其中 86 个技能具有直接前置。

- “等级”是技能的推荐能力等级，不是熟练度推导出的技能效果等级。
- 表中只列直接前置；学习时会通过前置技能继续形成完整依赖链。
- 服务端学习条件以 `dependsOn(...)` 为准。
- 开发机所有连线会在客户端技能注册完成后统一从 `dependsOn(...)` 重建；手写 `SkillInfo` 只保留纹理和坐标，缺失节点使用自动回退。
- 通用被动技能会在每个非 Level 0 类别中分别显示，但共享同一组技能数据和前置链。

## 通用被动 (`academy:level0`)

| 等级 | 技能 | 直接前置 |
|---:|---|---|
| 1 | 脑域开发 (`academy:brain_domain_development`) | 无 |
| 1 | 耐力训练 (`academy:endurance_training`) | `academy:brain_domain_development` |
| 1 | 体术训练 (`academy:physical_training`) | `academy:brain_domain_development` |
| 2 | 多重脑域分割 (`academy:multiple_brain_domain_segmentation`) | `academy:brain_domain_development` |
| 3 | 思考并列演算 (`academy:parallel_thought_computation`) | `academy:multiple_brain_domain_segmentation` |
| 4 | 意识完全解析 (`academy:complete_consciousness_analysis`) | `academy:parallel_thought_computation` |
| 5 | 自我绝对控制 (`academy:absolute_self_control`) | `academy:complete_consciousness_analysis` |

## 电气使 (`academy:electromaster`)

| 等级 | 技能 | 直接前置 |
|---:|---|---|
| 1 | 电弧生成 (`academy:arc_generate`) | 无 |
| 1 | 接触电击 (`academy:electrical_contact`) | `academy:arc_generate` |
| 3 | 矿物探测 (`academy:mine_detect`) | `academy:magnet_manipulation` |
| 2 | 闪电新星 (`academy:lightning_nova`) | `academy:thunder_lance` |
| 2 | 雷击之枪 (`academy:thunder_lance`) | `academy:arc_generate` |
| 3 | 电流共生 (`academy:current_symbiosis`) | `academy:current_recharge` |
| 3 | 磁力操纵 (`academy:magnet_manipulation`) | `academy:arc_generate` |
| 3 | 磁悬武装 (`academy:magnetic_weapon`) | `academy:magnet_manipulation` |
| 3 | 电流充能 (`academy:current_recharge`) | `academy:magnet_manipulation` |
| 4 | 生物电操作 (`academy:bioelectric_operation`) | `academy:electrical_contact` |
| 4 | 电磁护盾 (`academy:electromagnetic_shield`) | `academy:magnet_manipulation` |
| 4 | 铁砂操作 (`academy:iron_sand_arsenal`) | `academy:magnetic_weapon` |
| 5 | 闪电风暴 (`academy:lightning_storm`) | `academy:ball_lightning` |
| 4 | 电磁炮 (`academy:railgun`) | `academy:thunder_lance` |
| 5 | 球状闪电 (`academy:ball_lightning`) | `academy:lightning_nova` |
| 5 | 雷鸣 (`academy:thunderclap`) | `academy:ball_lightning` |

## 空间能力 (`academy:teleport`)

| 等级 | 技能 | 直接前置 |
|---:|---|---|
| 1 | 空间折叠理论 (`academy:space_folding_theorem`) | `academy:threatening_teleport` |
| 1 | 威胁传送 (`academy:threatening_teleport`) | 无 |
| 2 | 穿透传送 (`academy:piercing_teleportation`) | `academy:self_teleport` |
| 2 | 缴械传送 (`academy:disarm`) | `academy:self_teleport` |
| 2 | 自身传送 (`academy:self_teleport`) | `academy:threatening_teleport` |
| 2 | 空间协同 (`academy:spatial_synergy`) | `academy:self_teleport` |
| 3 | 肉体撕裂 (`academy:flesh_ripping`) | `academy:piercing_teleportation` |
| 3 | 位置传送 (`academy:location_teleport`) | `academy:piercing_teleportation` |
| 3 | 禁锢传送 (`academy:shackle`) | `academy:self_teleport` |
| 4 | 区域传送·选择 (`academy:area_teleport_select`) | `academy:location_teleport` |
| 4 | 区域传送·设置 (`academy:area_teleport_setup`) | `academy:area_teleport_select` |
| 4 | 区域传送·启动 (`academy:area_teleport_start`) | `academy:area_teleport_setup` |
| 4 | 快速位置传送 (`academy:quick_location_teleport`) | `academy:location_teleport` |
| 5 | 防御传送 (`academy:defensive_teleport`) | `academy:quick_location_teleport` |
| 5 | 高速闪现 (`academy:flashing`) | `academy:location_teleport` |
| 5 | 空间切除（未完成） (`academy:spacial_excision`) | `academy:area_teleport_start` |

## 矢量操控 (`academy:accelerator`)

| 等级 | 技能 | 直接前置 |
|---:|---|---|
| 1 | 矢量加速 (`academy:vector_accel`) | 无 |
| 1 | 矢量冲击 (`academy:vector_blast`) | `academy:vector_accel` |
| 2 | 导向踏击 (`academy:dir_strike`) | `academy:vector_blast` |
| 2 | 动能加持 (`academy:kinetic_energy_applied`) | `academy:vector_accel` |
| 3 | 矢量偏移 (`academy:vector_deviation`) | `academy:kinetic_energy_applied` |
| 4 | 矢量反射 (`academy:vector_reflection`) | `academy:vector_deviation` |
| 4 | 过滤网 (`academy:reflection_filter`) | `academy:vector_reflection` |
| 4 | 风暴之翼 (`academy:storm_wing`) | `academy:vector_reflection` |
| 5 | 黑翼 (`academy:black_wing`) | `academy:storm_wing` |
| 5 | 血流逆流 (`academy:bloodflow_reverse`) | `academy:vector_reflection` |
| 5 | 跨越深渊 (`academy:crossing_the_abyss`) | `academy:white_wing` |
| 5 | 等离子体 (`academy:plasma_generation`) | `academy:storm_wing` |
| 5 | 白金翼 (`academy:platinum_wing`) | `academy:white_wing` |
| 5 | 白翼 (`academy:white_wing`) | `academy:black_wing` |

## 原子崩坏 (`academy:meltdowner`)

| 等级 | 技能 | 直接前置 |
|---:|---|---|
| 1 | 镭射强化 (`academy:radiation_intensify`) | `academy:single_high_speed_electron_beam` |
| 1 | 粒机波形高速炮 (`academy:single_high_speed_electron_beam`) | 无 |
| 2 | 采矿光束 (`academy:mining_beam`) | `academy:single_high_speed_electron_beam` |
| 2 | 电子弹散射 (`academy:scatter_bomb`) | `academy:single_high_speed_electron_beam` |
| 3 | 粒子云室 (`academy:cloudroom`) | `academy:light_shield` |
| 3 | 光盾 (`academy:light_shield`) | `academy:single_high_speed_electron_beam` |
| 4 | 突击喷射 (`academy:jet_strike`) | `academy:light_shield` |
| 4 | 波形粒子炮 (`academy:particle_wave_cannon`) | `academy:scatter_bomb` |
| 5 | 自动巡航光束炮 (`academy:auto_cruise_beam_cannon`) | `academy:scatter_bomb` |
| 5 | 解离射线 (`academy:disintegrate`) | `academy:particle_wave_cannon` |

## 气动操控 (`academy:aeromanip`)

| 等级 | 技能 | 直接前置 |
|---:|---|---|
| 1 | 气垫缓冲 (`academy:air_cushion`) | `academy:airflow_jet` |
| 1 | 气流喷射 (`academy:airflow_jet`) | 无 |
| 1 | 流场感知 (`academy:flow_sense`) | `academy:airflow_jet` |
| 2 | 呼吸薄膜 (`academy:breathing_film`) | `academy:flow_sense` |
| 2 | 气动牵引 (`academy:pneumatic_grasp`) | `academy:flow_sense` |
| 2 | 顺风场 (`academy:tailwind_field`) | `academy:air_cushion` |
| 3 | 大气护盾 (`academy:atmosphere_shield`) | `academy:breathing_film` |
| 3 | 层流切割 (`academy:laminar_cutter`) | `academy:pneumatic_grasp` |
| 3 | 涡流牵引 (`academy:vortex_pull`) | `academy:pneumatic_grasp` |
| 4 | 大气爆枪 (`academy:atmosphere_blast_gun`) | `academy:atmosphere_shield` |
| 4 | 压力禁锢 (`academy:pressure_lock`) | `academy:vortex_pull` |
| 4 | 定向风道 (`academy:wind_corridor`) | `academy:tailwind_field` |
| 5 | 大气支配 (`academy:atmospheric_dominion`) | `academy:atmosphere_blast_gun`<br>`academy:vortex_pull` |
| 5 | 飞行 (`academy:flight`) | `academy:wind_corridor` |
| 5 | 真空领域 (`academy:vacuum_domain`) | `academy:atmospheric_dominion` |

## 未元物质 (`academy:darkmatter`)

| 等级 | 技能 | 直接前置 |
|---:|---|---|
| 1 | 未元物质分解 (`academy:darkmatter_disassemble`) | `academy:darkmatter_shaping` |
| 1 | 未元物质塑型 (`academy:darkmatter_shaping`) | 无 |
| 2 | 未元物质切割 (`academy:darkmatter_cut`) | `academy:darkmatter_disassemble` |
| 3 | 未元物质照射 (`academy:darkmatter_radiation`) | `academy:darkmatter_cut` |
| 4 | 未元物质创生 (`academy:darkmatter_creation`) | `academy:darkmatter_repair` |
| 4 | 未元物质修补 (`academy:darkmatter_repair`) | `academy:darkmatter_shaping` |
| 5 | 未元物质六翼 (`academy:darkmatter_six_wings`) | `academy:darkmatter_shaping` |

## 心理掌握 (`academy:mentalout`)

| 等级 | 技能 | 直接前置 |
|---:|---|---|
| 1 | 心灵介入 (`academy:mental_intervention`) | 无 |
| 1 | 心灵潜入 (`academy:mental_intrusion`) | `academy:mental_intervention` |
| 1 | 目标误认 (`academy:target_misidentification`) | `academy:mental_intervention` |
| 2 | 呆然自失 (`academy:mental_stupor`) | `academy:target_misidentification` |
| 2 | 感官扭曲 (`academy:sensory_distortion`) | `academy:mental_intrusion` |
| 3 | 指挥定位 (`academy:command_positioning`) | `academy:mental_intervention` |
| 3 | 印象操作 (`academy:impression_manipulation`) | `academy:mental_intervention`<br>`academy:target_misidentification` |
| 4 | 意识接管 (`academy:mental_takeover`) | `academy:mental_intrusion`<br>`academy:command_positioning`<br>`academy:mental_stupor` |
| 5 | 精密操作 (`academy:precision_operation`) | `academy:impression_manipulation`<br>`academy:mental_stupor` |
