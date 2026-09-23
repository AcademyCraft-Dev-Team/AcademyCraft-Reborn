# 能力技能通用方法梳理

以 `Skills.java` 中的 99 个实际注册技能为范围；兼容别名不重复计数。技能效果还会经由能力系运行时、程序节点和受控实体执行，因此公共方法放在 `org.academy.api.server`，调用方提供阵营、权限、目标点和掉落去向等策略。

| 能力系 | 技能数 | 共性与现有入口 |
| --- | ---: | --- |
| 气流操作 Aeromanip | 15 | 范围风场、持续伤害、位移、软方块破坏；`AeromanipTargeting`、`AreaEffectTargets`、`AbilityBlockDrops` |
| 矢量操作 Accelerator | 14 | 碰撞箱扫描、射线/圆柱命中、反射伤害和方块位移；`ViewTargetScanner`、`LinearAttackExecutor`、`AreaEffectTargets` |
| 电击使 Electromaster | 17 | 电弧连锁、范围落雷、带电荷点的伤害；`ElectromasterArcTargeting`、`AreaEffectTargets`、`AbilityDamageService` |
| 原子崩坏 Meltdowner | 10 | 光束扫描、反射伤害和路径采矿；`MeltdownerBeamActions`、`LevelUtil.destroyBlocksAlongPath` |
| 空间移动 Teleport | 15 | 空间选点、实体/方块转移、空间伤害；`TeleportTargeting`、`AbilityDamageService` |
| 暗物质 Darkmatter | 9 | 网络目标排除、真实伤害、定制工具掉落；`DarkmatterTargeting`、`AbilityBlockDrops` |
| 心理掌握 Mentalout | 11 | 受控实体选目标、挖掘及农作物收获；`MentaloutTargeting`、`AbilityBlockDrops` |
| 通用 Level 0 | 8 | 被动属性和能力资源；沿用属性与技能调节入口，无需额外空间/破坏方法 |

## 本次收敛

- **AABB 粗筛加精确球形判定**：`AreaEffectTargets.inSphere` 保留实体位置语义；新增可选目标点和 `inSphereByBoundsCenter`，供真空领域、绝热压缩与动能冲击使用。筛选器仍由技能决定敌我、宠物和观战规则。圆环、射线和只要求 AABB 相交的技能不能直接替换成球形查询。
- **技能来源伤害**：`AbilityDamageService.applySource` 接收已经构造好的 `SkillDamageSource`，保留伤害类型、直接实体、电荷点等元数据，并返回实际生命值/吸收值损失。落雷和真空领域改用它处理最大生命值伤害部分；具体技能仍负责倍率、冷却和附加状态。
- **定制工具破坏**：`AbilityBlockDrops.harvestBlock` 统一“按操作者感知计算掉落 → 无原版掉落破坏 → 成功后分发”的顺序。暗物质解构和受控实体采矿/收获改用它；背包、空间储物、缓冲区、重种等去向仍由调用方决定。普通破坏继续用 `destroyBlock`，路径采矿继续用 `LevelUtil`，无声破坏继续用 `destroyBlockSilently`。

## 后续扩展边界

新的范围技能先选明确的几何语义：实体位置、碰撞箱中心、圆柱/射线、圆环或单纯 AABB 相交。新的伤害技能优先使用 `AbilityDamageService`；需要 CTA/VEC 真实伤害、反射或网络免疫时，保留专门的结算路径。新的方块效果先选普通、带工具掉落、路径、无声或搬运中的一种；搬运不是破坏，不应触发掉落归属。这样公共入口可被技能、程序和非玩家施术者复用，而能力系规则留在各自的策略层。
