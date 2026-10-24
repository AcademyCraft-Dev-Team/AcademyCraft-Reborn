# docs 索引与留存约定

本目录只跟踪**手写规范**：说明、契约、设计计划与决策依据。测试/复现脚本、运行产物（截图、录像、画册）和**验收记录**都算本地证据，留在本地，不进入版本库。

## 留存约定

| 类别 | 是否跟踪 | 说明 |
| --- | --- | --- |
| 说明、API/契约、设计计划、决策依据 | ✅ | 需要随代码同步更新；改动实现时一并更新对应文档 |
| Blockbench 源模型 `*.bbmodel`、构建锁文件 `sable_compat_artifacts.json` | ✅ | 模型源与构建输入，不是产物 |
| 测试 / 复现脚本：`*.init.gradle`、`*.mjs`、`*.cjs`、辅助 `*.java`/`*.kt` | ❌ | 本地留存；换机器时按文档描述重建 |
| 验收记录：`*_validation*.md`、`*_result.md`、性能/像素 JSON、`performance.txt`、`runtime/`、`*/performance/` | ❌ | 本地证据，不是规范 |
| 截图 `*.png`、动图 `*.gif`、录像 `*.mp4`、索引这些产物的 `*.html` 画册、模型预览 `*.jpg` | ❌ | 见下方说明 |
| 带日期的故障/审计/已被取代的设计记录 | ❌ | 标注为历史归档后本地留存，见下表 |

**关于本地证据**：`.gitignore` 忽略了 `docs/**/*.png|gif|mp4|webp|html|jpg`、`*.init.gradle`、`*.cjs`、`*.mjs`、`*.java`、`*.kt`、`*/performance/`、`vfx/darkmatter_concept_v2/runtime/`，以及若干验收记录文件。因此在新克隆的仓库里：

- 文档中的**图片引用**与画册**不可解析**，这是预期行为；
- 文中提到的 `*.init.gradle` 等复现脚本、`VALIDATION.md` 一类验收记录也**不在仓库里**，需要在本机按文档描述重建或重跑。

需要长期随仓库分发的内容请放进 `src/main/resources` 等正式资源目录，不要放在 `docs/`。

## 当前实现文档

改动对应代码时，下列文档需要同步更新。

### 技能与能力

| 文档 | 内容 |
| --- | --- |
| [`ABILITY_SKILL_REFERENCE.zh-CN.md`](ABILITY_SKILL_REFERENCE.zh-CN.md) | **技能单一事实来源**：分类/等级/ID/中文名/前置/默认按键/CP 与资源消耗/迭代/堆叠/效果摘要。原 `ABILITY_SKILL_USAGE_GUIDE.md`、`SKILL_IMPLEMENTATION_CONTROL_MATRIX.md`、`SKILL_EFFECT_COST_ITERATION_STACK_MATRIX.md`、`ability_skill_dependencies.md` 已合并至此 |
| [`ability-skill-architecture.md`](ability-skill-architecture.md) | 代码布局、包职责、可复用的公共方法边界（原 `ability-skill-layout.md` + `ability-skill-common-methods.md`） |
| [`NON_COMMON_SKILL_PROFICIENCY_PLAN.md`](NON_COMMON_SKILL_PROFICIENCY_PLAN.md) | 1000/2000/3000 熟练度里程碑方案与实现入口 |
| [`ABILITY_PROGRAM_NODE_CATALOG.zh-CN.md`](ABILITY_PROGRAM_NODE_CATALOG.zh-CN.md) | 精密操作/能力程序节点总表（含结构操作节点族） |
| [`precision_operation_text_nodes.md`](precision_operation_text_nodes.md) | 文本分割与坐标解析节点 |
| [`MENTALOUT_DEVELOPMENT.md`](MENTALOUT_DEVELOPMENT.md) | 心理掌握开发记录与技能合同 |

### 命令

| 文档 | 内容 |
| --- | --- |
| [`COMMANDS.zh-CN.md`](COMMANDS.zh-CN.md) | `/academy` 服务端与客户端命令全集 |

### 伤害、生命与资源

| 文档 | 内容 |
| --- | --- |
| [`category_damage_effects.md`](category_damage_effects.md) | 各能力分类的穿防、结算入口与命中附效 |
| [`max_health_damage_balance.md`](max_health_damage_balance.md) | 最大生命百分比伤害的统一折算 |
| [`true_health_offset.md`](true_health_offset.md) | 真实伤害上限缺口与衰减 |
| [`custom_damage_entry_compatibility.md`](custom_damage_entry_compatibility.md) | 直伤替换入口的兼容 ABI |
| [`hostility_and_pain_suppression.md`](hostility_and_pain_suppression.md) | 临时敌对标记与痛觉消减 |
| [`native_cp_recovery.md`](native_cp_recovery.md) | 本体分类的 CP 上限与恢复保护 |
| [`air_output_cost.md`](air_output_cost.md) | 压缩空气与出力的费用曲线 |
| [`props.md`](props.md) | P.R.O.P.S 获取率与因子加成 |
| [`temporal_external_control.md`](temporal_external_control.md) | 时停期间的外部输入兼容 |

### 结构系统

| 文档 | 内容 |
| --- | --- |
| [`block_structure_entity_system.md`](block_structure_entity_system.md) | 独立物理结构的实现说明（分批记录中的历史批次已标注） |
| [`block_structure_physics_and_sable_compat_plan.zh-CN.md`](block_structure_physics_and_sable_compat_plan.zh-CN.md) | Sable 共存、后端选择与事务目标（计划 + 已实施部分） |
| [`structure_skill_migration_handoff.zh-CN.md`](structure_skill_migration_handoff.zh-CN.md) | 结构技能与精密节点的接续状态 |

### 子系统

| 文档 | 内容 |
| --- | --- |
| [`spatial_storage_unit.md`](spatial_storage_unit.md) | 空间收纳单元的收纳、卸货与存档 |
| [`server_dimension_effects.md`](server_dimension_effects.md) | 按维度强制开关 PVP / 方块破坏 |
| [`wide_area_interference_work_modes.md`](wide_area_interference_work_modes.md) | 广域干涉的 RTS 相机与工作系统 |
| [`wing_flight.md`](wing_flight.md) | 四种翼的飞行控制与网络时序 |
| [`CHUNK_LEAP_DESIGN.zh-CN.md`](CHUNK_LEAP_DESIGN.zh-CN.md) | 区块跃迁设计、配置与常量 |
| [`neoforge_coremod_migration.zh-CN.md`](neoforge_coremod_migration.zh-CN.md) | NeoForge CoreMod 字节码处理迁移 |

### 能力系专项方案

这几篇是单个能力系的重做/优化方案，属于设计依据；其中的“将/应”段落已标注为实施前基线。对应的实现数值已经并回技能总表，验收结果留在本地。

| 文档 | 内容 |
| --- | --- |
| [`electromaster_magnetic_field_iron_sand_rework_plan.zh-CN.md`](electromaster_magnetic_field_iron_sand_rework_plan.zh-CN.md) | 磁力场与铁砂重做方案（已实施，保留设计依据与 API 形态对照） |
| [`electromaster_magnetic_field_iron_sand_implementation.zh-CN.md`](electromaster_magnetic_field_iron_sand_implementation.zh-CN.md) | 该重做的行为、数值与公共接口表 |
| [`electromaster_magnetic_levitation_terrain_following_plan.zh-CN.md`](electromaster_magnetic_levitation_terrain_following_plan.zh-CN.md) | 磁悬浮地形跟随方案与 §10 实施结果 |
| [`electromaster_mine_detect_redesign.zh-CN.md`](electromaster_mine_detect_redesign.zh-CN.md) | 矿物探测重做设计 |
| [`aeromanip_vacuum_flow_sense_optimization.zh-CN.md`](aeromanip_vacuum_flow_sense_optimization.zh-CN.md) | 真空领域与流场感知优化方案（已实施） |
| [`mental_control_work_task_optimization.zh-CN.md`](mental_control_work_task_optimization.zh-CN.md) | 受控实体工作任务优化方案（§2 为优化前基线） |

### 扩展 API

| 文档 | 内容 |
| --- | --- |
| [`addon_api_guide.md`](addon_api_guide.md) | 附属模组接入指南（注册、伤害档案、程序节点、心理控制） |
| [`addon_registration_api_design.md`](addon_registration_api_design.md) | 注册 API 的设计依据与初始化顺序 |

### VFX 与模型

这些是特效/模型的**参数与设计说明**，改动对应资源时更新；它们的验收记录、截图与复现脚本都在本地。

| 文档 | 内容 |
| --- | --- |
| [`vfx/railgun/README.md`](vfx/railgun/README.md) | 超电磁炮特效与弹药数据 |
| [`vfx/sky_strike/README.md`](vfx/sky_strike/README.md) | 落雷与雷暴特效 |
| [`vfx/electromaster_arcs/README.md`](vfx/electromaster_arcs/README.md) | 电弧族特效（射程/弹药数据的权威口径） |
| [`vfx/iron_sand.md`](vfx/iron_sand.md) | 铁砂操作 VFXGraph 重制 |
| [`vfx/black_wings/README.md`](vfx/black_wings/README.md) | 黑翼弯曲墨流风暴 |
| [`vfx/black_wings/multiplayer_optimization_plan.md`](vfx/black_wings/multiplayer_optimization_plan.md) | 黑翼多人性能方案（含 LOD 预算与历史基线） |
| [`vfx/darkmatter_interference_field/README.md`](vfx/darkmatter_interference_field/README.md) | 未元干涉范围着色器 |
| [`vfx/darkmatter_concept_v2/README.md`](vfx/darkmatter_concept_v2/README.md) | 未元物质特效方案（当前版本） |
| [`vfx/ability_update/README.md`](vfx/ability_update/README.md) | 顺序 4–6 的技能 VFXGraph 更新 |
| [`skill_vfx_network_optimization.md`](skill_vfx_network_optimization.md) | 多人技能特效同步优化方案 |
| [`models/darkmatter_creature/README.md`](models/darkmatter_creature/README.md) | 独角仙 Blockbench 模型（源模型与导出规则） |

## 验收记录与复现脚本（仅本地留存）

下列内容不随仓库分发，文件仍在本地 `docs/` 下。需要复现时按对应文档的“复现/验证”章节重建。

| 本地路径 | 内容 | 对应规范文档 |
| --- | --- | --- |
| `aeromanip_vacuum_flow_sense_validation.zh-CN.md` | 真空领域/流场感知验收 | `aeromanip_vacuum_flow_sense_optimization.zh-CN.md` |
| `electromaster_mine_detect_validation.zh-CN.md` | 矿物探测验收 | `electromaster_mine_detect_redesign.zh-CN.md` |
| `mental_control_work_task_validation.zh-CN.md` | 工作任务优化验收 | `mental_control_work_task_optimization.zh-CN.md` |
| `vfx/darkmatter_concept_v2/VALIDATION.md`、`vfx/darkmatter_concept_v2/runtime/` | 未元物质九技能验收、GPU 捕获、性能对照与全部 `*.init.gradle` | `vfx/darkmatter_concept_v2/README.md` |
| `vfx/black_wings/multiplayer_optimization_result.md`、`multiplayer_baseline.json`、`multiplayer_optimized_comparison.json` | 黑翼多人优化结果与采样数据 | `vfx/black_wings/multiplayer_optimization_plan.md` |
| `vfx/darkmatter_interference_field/performance.txt` | 干涉场性能摘要 | `vfx/darkmatter_interference_field/README.md` |
| `models/darkmatter_creature/performance/` | 独角仙性能场景、JSON 基线与 `report.cjs` | `models/darkmatter_creature/README.md` |
| `models/darkmatter_creature/perf.init.gradle`、`*.cjs`、`blockbench_native_export.java` | 模型导出与性能基准脚本 | 同上 |

## 历史归档（仅本地留存）

下列文档记录的是**特定日期的故障排查、审计或已被取代的设计**，内容保持原样，不随仓库分发，也不应作为当前实现阅读。

| 本地文件 | 记录内容 | 当前参考 |
| --- | --- | --- |
| `server_damage_log_analysis_2026_09_12.md` | 服务端伤害日志排查 | `category_damage_effects.md` |
| `server_watchdog_refresh_audit_2026_09_12.md` | 看门狗崩溃与周期刷新审计 | `props.md`、`server_dimension_effects.md` |
| `worldgen_c2me_hang_fix_2026_09_12.md` | 创建世界卡死修复 | `neoforge_coremod_migration.zh-CN.md` |
| `chunk_leap_end_crash.md` | 区块跃迁退出崩溃 | `CHUNK_LEAP_DESIGN.zh-CN.md` |
| `structure_control_audit_20261016.zh-CN.md` | 结构操作核查（基线 `df036f5a`） | `structure_skill_migration_handoff.zh-CN.md` |
| `skill_vfx_multiplayer_validation.md` | 多人特效验收记录 | `skill_vfx_network_optimization.md` |
| `vfx/darkmatter_interference_field_plan.md` | 未元干涉设计依据 | `vfx/darkmatter_interference_field/README.md` |
| `vfx/darkmatter_redesign_plan.md`、`vfx/darkmatter_redesign/` | 未元物质重做方案与验收 | `vfx/darkmatter_concept_v2/` |
| `models/darkmatter_creature/performance/AUDIT.md` | 优化前性能审计快照 | `models/darkmatter_creature/README.md` |
| `vfx/darkmatter_concept_v2/references.md` | 原作视觉来源与设计边界 | `vfx/darkmatter_concept_v2/README.md` |
| `vfx-graph/blender-reference/texts.md` | Blender 资产内嵌文本导出 | 无 |

另有 `docs/AcademyCraft_Reborn_能力技能效果手册.docx`：由 Markdown 派生的本地 Word 排版版本，含锁文件 `~$…docx`，两者都只作本地交付物，已被 `.gitignore` 忽略。

## 维护约定

- 技能、节点、命令、按键这类**枚举型信息只在单一文档里维护**，不要在别处复制第二份表格。
- 文档里出现的类名、路径、常量、命令都应可在仓库中检索到；改动即更新，避免再次出现整表漂移。
- 验收记录只写结论与关键数字，**不要写进规范文档**；规范文档引用它们时用“本地归档”措辞，不要写成 Markdown 链接。
- 带日期的记录一旦被取代，就移入上面的表并从版本库移除，不再逐版维护。
