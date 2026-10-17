# 能力与技能架构

本文合并了原 `ability-skill-layout.md`（代码布局）与 `ability-skill-common-methods.md`（通用方法梳理），两者描述的是同一套分层约定。

核对基线：2026-10-18，HEAD `782d935b`，`Skills.java` 注册 **99** 个技能。

## 1. 包布局与职责

能力系以 `org.academy.internal.common.ability` 为实现根包。新增技能前，先检查本能力系及公开 API 中是否已有可复用的范围查询、伤害、方块处理和资源结算方法。

| 目录 | 职责 |
| --- | --- |
| `api/common/ability`、`api/server/ability` | 稳定的能力模型及服务端扩展入口，供技能、程序图和非玩家实体调用。 |
| `internal/common/ability/{development,proficiency,effect}` | 跨能力系的开发配置、熟练度策略、计时效果。 |
| `internal/common/ability/shared/linear` | 多能力系共用的线性攻击与反射计算。 |
| `internal/common/ability/program/{registry,compile,editor,migration}` | 程序图的节点注册、编译、编辑和旧格式迁移。`program` 根包保留运行时、调度器及其需要共享包内状态的执行器。 |
| `internal/common/ability/<category>/skills/lvN` | 技能入口。多个入口共用、但仅属于同一技能组的类再放到该组子目录（例如 `accelerator/skills/lv5/wing`）。 |
| `internal/common/ability/<category>/{targeting,damage,beam,field,chunk,map,control,resource,...}` | 该能力系的支撑逻辑；目录名按实际职责选择，不为每个能力系强行创建空目录。 |
| `internal/common/ability/<category>/{program,network,config,migration}` | 该能力系的程序图适配、同步、配置及迁移。 |

技能入口负责触发、冷却和参数选择。目标筛选、连续碰撞、伤害计算、方块收获等规则分别交给相应支撑类；跨能力系复用时再提升到 `api/server/ability` 或 `shared`，避免从另一个能力系的技能包直接取工具类。比如圆形范围先用 `AreaEffectTargets` 进行 AABB 粗筛与球体精筛；特殊方块破坏走 `AbilityBlockDrops`，使掉落归属、战利品计算和维度效果策略保持一致。非玩家调用者的接口应显式接收施术实体或上下文，只有实际需要玩家背包、能量或身份时才要求 `ServerPlayer`。

## 2. 公开入口与兼容约束

`api/server/ability` 根包中的 `AreaEffectTargets`、`HostileTargets`、`HostileProjectiles`、`AbilityBlockDrops`、`AbilityEffectPolicy`、`WorldBlockTargets` 等是已发布的调用入口，保留现有包名以维护外部模组的源码和二进制兼容。以后若要调整其公开包名，应先添加委托兼容层并单独评估。`api/common/ability/pakcet` 的历史拼写也属于兼容迁移，不能在普通目录整理中直接改名。

## 3. 各能力系的技能数与共性入口

技能效果还会经由能力系运行时、程序节点和受控实体执行，因此公共方法放在 `org.academy.api.server`（`ability`、`damage`、`vfx` 子包），调用方提供阵营、权限、目标点和掉落去向等策略。

| 能力系 | 技能数 | 共性与现有入口 |
| --- | ---: | --- |
| 气流操作 Aeromanip | 15 | 范围风场、持续伤害、位移、软方块破坏；`AeromanipTargeting`、`AreaEffectTargets`、`AbilityBlockDrops` |
| 矢量操作 Accelerator | 15 | 碰撞箱扫描、射线/圆柱命中、反射伤害和方块位移；`ViewTargetScanner`、`LinearAttackExecutor`、`AreaEffectTargets`、`VectorStructureService` |
| 电击使 Electromaster | 16 | 电弧连锁、范围落雷、带电荷点的伤害、磁场结构操控；`ElectromasterArcTargeting`、`AreaEffectTargets`、`AbilityDamageService`、`MagneticStructureControl` |
| 原子崩坏 Meltdowner | 10 | 光束扫描、反射伤害和路径采矿；`MeltdownerBeamActions`、`LevelUtil.destroyBlocksAlongPath` |
| 空间移动 Teleport | 15 | 空间选点、实体/方块转移、空间伤害；`TeleportTargeting`、`AbilityDamageService` |
| 未元物质 Darkmatter | 9 | 网络目标排除、真实伤害、定制工具掉落；`DarkmatterTargeting`、`AbilityBlockDrops` |
| 心理掌握 Mentalout | 11 | 受控实体选目标、挖掘及农作物收获、工作调度；`MentaloutTargeting`、`AbilityBlockDrops`、`GroupControlApi` |
| 通用 Level 0 | 8 | 被动属性和能力资源；沿用属性与技能调节入口，无需额外空间/破坏方法 |

逐技能的 ID、等级、前置、按键与数值见 [技能总表](ABILITY_SKILL_REFERENCE.zh-CN.md)。

## 4. 已收敛的三类通用语义

- **AABB 粗筛加精确球形判定**：`AreaEffectTargets.inSphere` 保留实体位置语义；另有可选目标点和 `inSphereByBoundsCenter`，供真空领域、绝热压缩与动能冲击使用。筛选器仍由技能决定敌我、宠物和观战规则。圆环、射线和只要求 AABB 相交的技能不能直接替换成球形查询。
- **技能来源伤害**：`AbilityDamageService.applySource` 接收已经构造好的 `SkillDamageSource`，保留伤害类型、直接实体、电荷点等元数据，并返回实际生命值/吸收值损失。落雷和真空领域改用它处理最大生命值伤害部分；具体技能仍负责倍率、冷却和附加状态。非玩家施术者走接收 `LivingEntity` 的 `apply` 重载。
- **定制工具破坏**：`AbilityBlockDrops.harvestBlock` 统一“按操作者感知计算掉落 → 无原版掉落破坏 → 成功后分发”的顺序。暗物质解构和受控实体采矿/收获改用它；背包、空间储物、缓冲区、重种等去向仍由调用方决定。普通破坏继续用 `destroyBlock`，路径采矿继续用 `LevelUtil`，无声破坏继续用 `destroyBlockSilently`。

## 5. 后续扩展边界

新的范围技能先选明确的几何语义：实体位置、碰撞箱中心、圆柱/射线、圆环或单纯 AABB 相交。新的伤害技能优先使用 `AbilityDamageService`；需要 CTA/VEC 真实伤害、反射或网络免疫时，保留专门的结算路径。新的方块效果先选普通、带工具掉落、路径、无声或搬运中的一种；搬运不是破坏，不应触发掉落归属。这样公共入口可被技能、程序和非玩家施术者复用，而能力系规则留在各自的策略层。

## 6. 测试与验证约定

测试包应与被测实现包一致，尤其是测试包内可见方法时。技能新增或支撑类迁移后，检查 Java、Kotlin、编辑器预览、数据资源中的引用，并完成测试、开发构建、发布构建；涉及游戏行为时再做客户端冒烟验证。
