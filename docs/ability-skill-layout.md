# 能力与技能代码布局

能力系以 `org.academy.internal.common.ability` 为实现根包。新增技能前，先检查本能力系及公开 API 中是否已有可复用的范围查询、伤害、方块处理和资源结算方法。

| 目录 | 职责 |
| --- | --- |
| `api/common/ability`、`api/server/ability` | 稳定的能力模型及服务端扩展入口，供技能、程序图和非玩家实体调用。 |
| `internal/common/ability/{development,proficiency,effect}` | 跨能力系的开发配置、熟练度策略、计时效果。 |
| `internal/common/ability/shared/linear` | 多能力系共用的线性攻击与反射计算。 |
| `internal/common/ability/program/{registry,compile,editor,migration}` | 程序图的节点注册、编译、编辑和旧格式迁移。`program` 根包保留运行时、调度器及其需要共享包内状态的执行器。 |
| `internal/common/ability/<category>/skills/lvN` | 技能入口。多个入口共用、但仅属于同一技能组的类再放到该组子目录。 |
| `internal/common/ability/<category>/{targeting,damage,beam,field,chunk,map,control,resource,...}` | 该能力系的支撑逻辑；目录名按实际职责选择，不为每个能力系强行创建空目录。 |
| `internal/common/ability/<category>/{program,network,config,migration}` | 该能力系的程序图适配、同步、配置及迁移。 |

技能入口负责触发、冷却和参数选择。目标筛选、连续碰撞、伤害计算、方块收获等规则分别交给相应支撑类；跨能力系复用时再提升到 `api/server/ability` 或 `shared`，避免从另一个能力系的技能包直接取工具类。比如圆形范围先用 `AreaEffectTargets` 进行 AABB 粗筛与球体精筛；特殊方块破坏走 `AbilityBlockDrops`，使掉落归属、战利品计算和维度效果策略保持一致。非玩家调用者的接口应显式接收施术实体或上下文，只有实际需要玩家背包、能量或身份时才要求 `ServerPlayer`。

`api/server/ability` 根包中的 `AreaEffectTargets`、`HostileTargets`、`HostileProjectiles`、`AbilityBlockDrops`、`AbilityEffectPolicy` 等是已发布的调用入口，保留现有包名以维护外部模组的源码和二进制兼容。以后若要调整其公开包名，应先添加委托兼容层并单独评估。`api/common/ability/pakcet` 的历史拼写也属于兼容迁移，不能在普通目录整理中直接改名。

测试包应与被测实现包一致，尤其是测试包内可见方法时。技能新增或支撑类迁移后，检查 Java、Kotlin、编辑器预览、数据资源中的引用，并完成测试、开发构建、发布构建；涉及游戏行为时再做客户端冒烟验证。
