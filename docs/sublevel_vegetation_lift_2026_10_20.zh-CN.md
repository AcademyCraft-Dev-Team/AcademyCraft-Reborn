# 植被阻挡物理化抬升修复与验收

更新日期：2026-10-20。本文记录当前默认 `academy:sublevel` 路径的草、作物、树叶阻挡 H 问题，以及旧 `academy:native_level` 路径中原版空碰撞作物的地形准入补充。没有加载 Sable 的隔离客户端结果见下文；本报告不代替完整 Sable 性能对照。

## 已确认根因

### 选取与捕获把装饰方块当成障碍或无效内容

原 `BlockStructureManager.cropImmediatelyBlocked` 将向上相邻的非空气格视为阻挡，没有区分真实碰撞形状。草与小麦等空碰撞植物因此可以裁掉其下方本来能抬升的地面。后续 H 清单还统一调用 `cropNonFullBlocks`，会丢掉附属植物与非满格碰撞形状；`SubLevelCaptureTarget` 又拒绝空碰撞格，无法保留主体上的装饰。

修复后，外部阻挡依据真实 `getCollisionShape` 判断。默认 SubLevel 捕获保留附属草、作物及可准入的部分碰撞形状；纯空碰撞集合仍不能创建零质量刚体，必须在移除源方块前由整体几何验证拒绝。方块实体、流体、水含状态与计划 tick 等原有捕获边界继续生效。

### 树叶必须在本 tick 求解前处理

树叶有真实碰撞，不能像草一样忽略。如果仅在求解后根据实际位姿做扫掠，求解器会先阻止向树叶内部移动，留下近零运动，清障路径始终看不到跨入树叶的轨迹。

当前 `SubLevelRuntime.tick` 在控制冲量已经应用、地形碰撞更新与物理步进之前调用 `SubLevelTerrainBreaker.beforeStep`。预测采用真实质心、世界角速度和本 tick 时长；按当前线速度与角速度预测接触，再通过权限与事件决定是否破坏软方块。旧求解后扫掠保留为外部运动的补充，同结构同 tick 至多扫描一次。

时序已按代码复核：`SubLevelDirectControl.tick` 调用 `universe.impulse` 后立即刷新 `entry.motion`；因此这里读取的是施加控制冲量之后的速度，不是上一轮求解结束的旧速度。

### 保守包围球让扫描预算永远到不了叶盖

首轮修复后的真实烟测仍失败。`sublevel-vegetation-r2.log` 显示：

- tier 0 已捕获 79 格，其中 44 格为草或小麦，质量约 34.3125。
- `LIFTING / controlActive=true` 持续至初始期限结束，实际只上升约 0.996 格。
- `BreakBlockEvent` 计数始终为 0；出现的少量 `BlockDropsEvent` 来自叶盖自然凋零。
- 200 tick 后会话释放，700 tick 内没有连续悬浮记录。

发现 `SubLevelTerrainBreakPolicy.scan` 使用 `StructureImpactSweep.sweptBounds`，把结构局部原点周围的半径球也并入扫描范围。9×6 小平台的原点半径约 10.86，纯平移也产生上万候选格；固定从最小坐标扫描的 4096 格预算可能在到达实际结构与叶盖之前耗尽。下一 tick 又从同一位置开始，形成持续漏检。

现改为本地专用 `sweptContactBounds`：

1. 合并真实碰撞箱在起点、终点的世界 AABB。
2. 仅对旋转增加保守余量 `r × angle / 2`，覆盖线性原点与最短弧四元数插值之间的弧线偏差。
3. 纯平移不使用球半径扩张。回归用例中，9×1×6 平台上移 0.3 格只扫描 108 格。

没有修改全局 `StructureImpactSweep`，避免影响其他技能伤害扫掠。缩小候选范围后仍进行真实旋转箱与候选方块的 SAT 接触测试，不按外层 AABB 直接清除植物。合并碰撞箱超过局部索引单盒预算时走保留的线性检查，不能再静默丢弃该盒。

## 修复范围与权限

| 入口 | 当前行为 |
| --- | --- |
| `BlockStructureApi.cropImmediatelyBlocked` | 提供带软障碍准入谓词的重载；空碰撞格不会阻挡，谓词本身不执行破坏 |
| `VectorStructureService` H 捕获 | SubLevel 保留植物与部分形状；Sable 捕获桥仍保留自身既有准入约束 |
| `SubLevelCaptureTarget` | 按集合验证至少存在真实碰撞；不再逐格拒绝空碰撞装饰 |
| `SubLevelTerrainBreaker.beforeStep` | 有归属 actor、有效授权和实际运动才预测软接触 |
| `SubLevelTerrainBreakPolicy` | 默认低硬度植被及允许标签；保护标签、方块实体、流体、过硬或不可破坏格拒绝 |
| `SubLevelDetachedContents` | 最后碰撞格被挖后清理剩余装饰，沿原版掉落路径；被保护事件取消的余格仍可交互 |
| `NativeStructurePhysicsGeometry` | 旧路径仅增加已审计的原版空碰撞作物 exact class |

软障碍破坏始终检查 actor、维度、有效期、破坏设置及 `mayInteract`，逐格发布 `BreakBlockEvent`，事件后再次检查权限和状态，最终使用 `host.destroyBlock`。正常 `BlockDropsEvent` 沿原版路径发布，不另造直接生成物品的掉落链路。事件取消不会被清障逻辑绕过；实体墙仍由真实碰撞阻止。

挖完最后实心块时，未受保护的装饰由原版破坏和掉落回调处理。保护取消留下的装饰保留为可选择的 `SLEEPING` 投影，不创建零质量刚体、不留不可操作的 `INACTIVE` 残影；保存后恢复遵守同样规则。其掉落作用域与验收见 [挖掘回归报告](sublevel_mining_regression_2026_10_20.zh-CN.md)。

本轮只参考本地 Sable 源码中“按实际接触处理软方块”的设计与时序，未复制外部业务源码或二进制。Sable 本地许可检查结果沿用现有接续记录；本地实现保留 Academy 的保护与破坏设置约束。

## 真实 H 验收

探针：`src/gametest/java/org/academy/internal/client/validation/SubLevelLiftTiersSmoke.java`。使用真实 `KineticThrow.Server.handlePress/handleRelease`，分别蓄力 4 / 24 / 44 tick，对应三档。平台混合草方块与耕地，上方放短草与成熟小麦；捕获完成后再加 9×9 树叶盖，检验后续运动清障。

高度以“捕获源格最小坐标 + 实际局部质心”为原位基线，避免混合耕地与满方块时把平台几何中心误当质心。要求每档在目标 ±0.1 格内连续保持 200 tick，然后通过正常发射入口发射，20 tick 后质心位移必须超过 3 格。

### r3：修复扫描范围后的首个通过结果

日志：`build/sublevel-vegetation-r3.log`；隔离目录：`run/native-sublevel-direct-smoke-vegetation-r3`。2026-10-20 21:58:19 输出 `SUBLEVEL_LIFT357_SMOKE_PASSED tiers=3`。

| 档位 | 目标抬升 | 实测抬升 | 连续悬浮 | 保留草/小麦 | 叶盖 Break 事件 | 叶盖 Drop 事件 | 20 tick 发射位移 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 0 | 3 格 | 3.064378 格 | 200 tick | 44 | 35 | 46 | 41.810330 格 |
| 1 | 5 格 | 5.064378 格 | 200 tick | 62 | 72 | 72 | 51.356422 格 |
| 2 | 7 格 | 7.064378 格 | 200 tick | 64 | 69 | 71 | 51.356422 格 |

**事件计数解释：**夹具叶盖没有树干支撑且不是永久树叶，会自然凋零。`leaf_drop_events` 和 `cleared_leaves` 会包含自然凋零；不能把全部 Drop 事件或消失树叶都称为结构主动破坏。表中的 Break 事件是清障逻辑主动发出的事件计数。验收同时要求主动 Break、全局 Drop、实际清除均发生，不要求两种事件数量相等。

这组结果证明捕获附属植物、接触叶盖后继续抬升、200 tick 悬浮与发射可完成。约 +0.064 格离散控制偏差仍存在，落在既有 ±0.1 验收范围；本轮没有把它改写为“精确零误差”。

### r4：联合旧 Native 作物准入回归

r4 使用 `run/native-sublevel-direct-smoke-vegetation-r4`，日志为 `build/sublevel-vegetation-r4.log`。在同一客户端、同一隔离世界内，先运行下节旧 Native 全状态准入检查，再运行三档真实 H。2026-10-20 22:01:50 输出 `NATIVE_TERRAIN_ADMISSION_PASSED plants=29 plantStates=145 fluidStates=6 waterloggedSolids=3 unsafeBlocks=5 unsafeStates=536 emptyToSolid=true`；22:02:29 输出 `SUBLEVEL_LIFT357_SMOKE_PASSED tiers=3`。客户端随后正常退出，Gradle 成功结束。

| 档位 | 实测抬升 | 连续悬浮 | 保留草/小麦 | 叶盖 Break 事件 | 叶盖 Drop 事件 | 20 tick 发射位移 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 0 | 3.064378 格 | 200 tick | 44 | 35 | 42 | 41.897981 格 |
| 1 | 5.064378 格 | 200 tick | 62 | 72 | 74 | 51.356422 格 |
| 2 | 7.064378 格 | 200 tick | 64 | 68 | 71 | 51.356422 格 |

r4 同样包含自然凋零，Drop 与 Break 的差额不能计作主动清障。两轮相同的高度、悬浮与发射断言均通过，主动清障数量会受叶盖自然变化及接触姿态影响。

## 旧 Native 全状态准入验收

旧路径原有 `EMPTY_TERRAIN_PLANTS` 漏掉如下 10 个原版类；patched Minecraft 26.2 源码确认其注册无碰撞，且没有覆盖碰撞形状：

`CropBlock`、`CarrotBlock`、`PotatoBlock`、`BeetrootBlock`、`TorchflowerCropBlock`、`StemBlock`、`AttachedStemBlock`、`NetherWartBlock`、`SugarCaneBlock`、`SweetBerryBushBlock`。

新增仍使用精确类匹配，并要求原版 namespace、没有方块实体或流体、不是动态形状，最后确认当前真实碰撞为空。没有使用 `instanceof CropBlock` 等方式放行任意模组子类。

`PitcherCropBlock` 下半部存在真实碰撞，不能按“作物”整体放行；`CocoaBlock` 也不是空碰撞。这两类全部状态在当前旧路径中保持 `UNSUPPORTED_CONTENT`，包括猪笼草上半部的空碰撞状态。这里的拒绝是旧路径准入边界，不表示默认 SubLevel 也拒绝所有这类方块。

入口：`NativeStructureTerrainAdmissionSmoke.run`。植被 H 开关启用时，tier 0 在已清空隔离平台的边缘空区调用一次，测试后 `finally` 恢复测试格并清空缓存；不另启客户端。它检查：

- 29 种原版植物的全部状态；新增 10 类对应小麦、胡萝卜、马铃薯、甜菜、火把花苗、南瓜/西瓜两种茎与两种连接茎、下界疣、甘蔗、甜浆果丛共 12 种方块。
- 空碰撞植物不增加碰撞箱，但仍记录在地形状态收据中。
- 植物替换为石头会使缓存失效并真实添加碰撞箱。
- 水/岩浆的 6 种测试状态不制造假碰撞；3 种水含实体方块保留真实形状。
- 细雪、火、蜘蛛网、猪笼草作物、可可全部状态保持安全拒绝。

成功标记为 `NATIVE_TERRAIN_ADMISSION_PASSED`，同时记录植物状态数和拒绝状态数。仅出现 Gradle `BUILD SUCCESSFUL` 不能替代这些游戏断言。

## 复测命令与边界

使用 JetBrains Runtime 25，先准备 `run/native-sublevel-direct-smoke-*` 下的隔离游戏目录，内含 `saves/test` 副本并且不安装 Sable；不要对原 `run/saves/test` 直接运行本夹具。入口会在副本内构建和破坏测试平台。

```powershell
.\gradlew.bat runClientDev -DisDev=true `
  -I validation/native_structure/sublevel_lift_tiers.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-vegetation-r4 `
  -PacademySubLevelLiftVegetation=true
```

相关单元回归位于 `SubLevelCaptureTargetTest`、`SubLevelTerrainPredictionTest`、`SubLevelTerrainContactGridTest`。覆盖空碰撞内容准入、质心预测、旋转预测、108 格候选范围、旋转中间角覆盖、合并大盒和中空结构接触。完整主测试及 dev/release 构建由主任务统一执行，本文不把尚未汇总的构建记录当作已通过结果。

本次 H 证据来自常见植被平台与叶盖，未覆盖全部模组植物、所有保护插件、所有地形或精密操作节点组合；不代表与 Sable 的性能、每帧运动或碰撞效果完全相同。重现旧失效症状时应保留 `SUBLEVEL_LIFT357_PROGRESS` 中的实际质心、速度、会话状态和 Break/Drop 分项计数，不能只依据最终外观或自然凋零判断清障成功。
