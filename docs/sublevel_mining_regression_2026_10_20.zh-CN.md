# 本地结构挖掘、点击反馈与全局掉落事件修复

日期：2026-10-20。范围：默认 `academy:sublevel` 后端与保留的 `academy:native_level` 后端。本文不代替多结构性能、植被抬升的独立验证。

## 后续修正：手持无耐久物品

用户继续游戏后的 `test2` 日志出现大量 `SubLevel mining target changed during tool callbacks`。
原因是工具回调后的比较副本无条件调用 `setDamageValue(0)`；NeoForge 的该方法会给种子、方块等无耐久物品添加 `DAMAGE` 组件，使未发生变化的实际手持物品反而无法通过 `ItemStack.matches`。
现仅对原本可损耗的工具归一化耐久，保持其他组件及实际工具身份的校验。

新增实际客户端输入验证均通过：

| 模式 | 目标与手持物品 | 结果 | 日志 |
| --- | --- | --- | --- |
| 创造 | 手持小麦种子破坏铁块 | 挥手、成功删除；无掉落，种子不被修改 | `build/mining-creative-seeds-r1.log` |
| 生存 | 手持小麦种子破坏泥土 | 挥手、裂纹、全局事件一次、泥土掉落一个；种子无新增耐久组件 | `build/mining-survival-seeds-r1.log` |
| 生存 | 铁镐破坏铁块 | 挥手、裂纹、全局事件一次、铁块掉落一个、消耗一点耐久 | `build/mining-pickaxe-ground-r1.log` |

这项修复解决普通手持物品被误拒绝，不能据此单独宣称已定位所有“幽灵方块”现象。

## 已确认的问题

1. `SubLevelClientInteraction` 在 Minecraft 的攻击入口取消原版处理后，仅发送挖掘请求，没有执行点击输入事件、玩家挥手、命中粒子和敲击声音。因此服务器即使收到请求，玩家也看不到正常左键反馈。
2. `SubLevelInteractionController` 使用 `Block.getDrops` 后直接向宿主生成物品，并自行计算经验，没有进入全局 `BlockDropsEvent`。取消事件、修改物品列表、追加掉落和修改经验的订阅者都无法接入。
3. 服务端对 `toolBefore` 副本调用 `mineBlock`，没有消耗玩家实际持有工具的耐久。
4. SubLevel 客户端收到 `REJECTED`、`ABORTED` 或 `COMPLETED` 后没有结束本地会话。继续按住同一目标时一直发送旧请求的 `CONTINUE`，而服务端该会话已经不存在。这是一次拒绝后同一个铁块持续无法挖掘的一条实际代码路径。
5. legacy Native 路径仍有材料、工具类型和工具组件白名单。此前只添加铁块的补丁无法覆盖其他普通方块，也无法覆盖默认 SubLevel 路径的会话问题。
6. 最后一个有碰撞形状的方块消失后，若结构中仍有远离该格的草等内容，原来的 `INACTIVE` 分支会保留投影，却使这些余格无法继续通过结构查询和挖掘验证。

## 修复后的处理路径

客户端仍只提交意图，破坏进度和方块删除仍由服务器决定：

- 两种本地后端共用 `StructureMiningFeedback`，触发 NeoForge 点击输入事件，遵循取消和挥手标志，并在真实命中点产生结构方块的粒子与声音。SubLevel 的持续命中声音按四个游戏 tick 的间隔执行。
- SubLevel 在一个游戏 tick 内合并重复输入；收到服务端终态后清理会话，以短冷却开始下一次请求。切换客户端世界或连接会清除旧会话。
- 客户端检查宿主方块射线遮挡，避免墙后的结构抢占正常方块的点击。
- 服务端保留工具、槽位、模式、玩家存活状态、容器状态、目标身份、范围和宿主权限检查；保护、采集、工具等回调之后再次验证。正常耐久变化被允许，回调替换目标或实际持有工具会中止提交。
- `onDestroyedByPlayer` 返回成功后，还必须确认原格已经变成空气或液体。仅返回成功但保留实体方块的异常回调不能重复生成掉落。

矿物与工具不再由固定名称决定是否可以挖掘。已允许进入结构的普通方块使用原版硬度、工具能力、采集资格和权限；对应的真实方块实体必须存在、未移除并与方块状态兼容。生存模式仍不能破坏负硬度方块，创造模式可以；水、岩浆本身仍不作为挖掘目标，含水方块可以在破坏后恢复其液体。

默认 SubLevel 的完成路径现在调用真实工具的 `mineBlock`、方块的 `onDestroyedByPlayer`、`destroy` 和 `playerDestroy`。统计、消耗、全局战利品修饰、全局掉落事件与经验走同一条路径，不再额外计算第二份物品或经验。

## 与 Sable 对照及事件坐标约定

参考的是本机已有的 Sable fork：

- `D:/mcmodtest/create-fork/.porting/sources/Sable/common/src/main/java/dev/ryanhcode/sable/mixin/punching/MultiPlayerGameModeMixin.java`
- `D:/mcmodtest/create-fork/.porting/sources/Sable/common/src/main/java/dev/ryanhcode/sable/mixin/entity/entity_kicking/ServerLevelMixin.java`

前者保留原版 `startDestroyBlock`，只附加结构推动处理；后者在实体进入世界时，把所属 plot 的输出转换到可见位置。本次沿用其“真实存储方块执行原版逻辑，输出转换坐标”的设计，不复制其代码或引入运行依赖。

`BlockDropsEvent` 的 `level` 和 `pos` 对应真实存储维度及 plot 坐标，`state`、方块实体、破坏者和工具均是此次破坏的真实上下文。事件不能被伪装成宿主空气格的破坏。玩家始终留在宿主维度，回调执行期间提供 `StructureContexts.current()`，供扩展读取结构引用、姿态与局部世界。

`NativeStructureBreakDrops` 的作用域现在支持 plot 原点偏移：事件接受后的物品和经验先被作用域持有，随后减去 plot 原点，再应用结构姿态，发布到可见宿主。保留事件替换的物品实体身份、堆叠及运动信息，不再构造一份重复物品。事件取消时不发布该次方块战利品、后续掉落回调或经验。

检索本仓库 `src/main` 时没有发现其他 `BlockDropsEvent` 订阅者；第三方效果若需要玩家世界距离，应使用上述结构上下文转换坐标。此次验证不意味着所有外部模组都已经支持独立存储维度。

## 无碰撞余格和失败输出

`SubLevelDetachedContents` 在同一个掉落作用域内检查剩余格。若仍有任意碰撞形状，不清理；若已经全部失去碰撞形状，仅遍历该结构边界内的余格，经过保护事件和权限复核后调用原版 `destroyBlock`。邻居更新已删除的格会被跳过，避免重复掉落。

保护事件保留的内容维持可查询、可交互的 `SLEEPING` 状态，不制造空碰撞刚体。重新加载也保留该状态，保护解除后可以继续挖掘，或放置新的支撑方块。

若方块回调、事件或输出发布抛异常，不能将已经分离的容器内容或已发布的物品当作“从未发生”：

- `SubLevelMiningRecovery` 保存作用域的输出收据、原格观察和失败原因。
- 文件位于世界目录 `data/academy/sublevel-mining-recovery/<UUID>.nbt`；先写临时文件并强制刷新，再原子重命名。
- 序列化或磁盘写入失败时，服务端仍强持有该作用域，停服前重试保存。
- 记录明确为人工恢复证据。**不自动重放掉落，不自动恢复原方块或容器库存**，以免把已经发布或结果不确定的输出复制一遍。
- plot 输出证据含 `storage_origin`；分析记录中的输出位置时必须结合该偏移及结构姿态。

这不是整段挖掘流程的磁盘断电事务保证。若证据持续不能序列化或写入磁盘，日志会明确报告仍只保留在内存中的收据。

## 验证记录

已由主 Agent 统一运行：

- 最终 `test -DisDev=true` 及两种构建中的主测试：2,840 项通过。
- 隔离目录中的真实客户端输入回归：`build/sublevel-mining-r2.log`，出现 `SUBLEVEL_MINING_INTERACTION_SMOKE_PASSED`。
- 该回归通过 Minecraft 的 `startAttack` / `continueAttack` 实际入口和网络处理，验证铁块有挥手、服务端裂纹进度、恰好一次全局掉落事件、恰好一个宿主铁块掉落，以及实际持有铁镐恰好消耗一点耐久。
- `academy:sublevel_mining_drops` GameTest 通过，日志为 `build/sublevel-mining-gametests-r2.log`。覆盖普通材料挖掘规则、生存与创造负硬度区别、真实 plot 中铁块的默认/取消/替换实体/修改经验事件、输出坐标及没有存储维度遗留物品，以及最后实心格消失后远处草的掉落、保护取消保留及再次清理。首次运行只因测试预设未声明 plot 维度失败，补齐 `src/test/gametest_packs` 的真实维度 fixture 后通过。
- `academy:native_structure_mining_policy` GameTest 单独运行通过，日志为 `build/native-mining-policy-r2.log`。legacy 路径取消材料与工具白名单后，原版采集资格检查仍成立。

最终开发与发布 `build` 均通过（各 37 秒），日志 `build/structure-fixes-build-dev.log` 与 `build/structure-fixes-build-release.log`。其余场景与测试计数统一记录于 [接续记录](structure_skill_migration_handoff.zh-CN.md)。

验证边界：异常收据的磁盘写入失败、序列化超限与第三方方块回调抛异常尚未做故障注入；本次也没有扩大任意模组方块实体或含水格的客户端投影支持范围。上述挖掘结论适用于当前已支持捕获与呈现的内容。

构建使用仓库已有 JBR 25 与 Gradle wrapper。带本地物理库参数的统一验证可使用：

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat test -DisDev=true @physicsNativeArgs --console=plain
./gradlew.bat build -DisDev=true @physicsNativeArgs --console=plain
./gradlew.bat build -DisDev=false @physicsNativeArgs --console=plain
```

输入探针位于 `src/gametest/java/org/academy/internal/client/validation/SubLevelMiningInteractionSmoke.java`，仅在 `academy.sublevelMiningSmoke=true` 且运行目录名称为 `sublevel-mining-smoke-*` 时启用。它会创建验证用方块和工具，必须使用隔离副本。
