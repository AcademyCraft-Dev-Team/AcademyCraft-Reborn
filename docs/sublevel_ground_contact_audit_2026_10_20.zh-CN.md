# 贴地物理结构步行接触复查（2026-10-20）

## 已定位的问题

### 毫米级结构沉降反复触发玩家传送握手

旧 `SubLevelContactResolver.carried` 对承载中的根实体与乘客调用 `teleportTo(double, double, double)`。26.2 的 `ServerPlayer` 覆写直接调用 `connection.teleport`；`ServerGamePacketListenerImpl` 在 `awaitingPositionFromClient` 非空时等待客户端确认，再处理普通移动。贴地物体即使只发生很小的刚体求解修正，也可能每 tick 再发起传送，打断行走输入。

本地官方映射源码核对位置：

- `ServerPlayer.teleportTo(double, double, double)` 调用连接的传送方法。
- `ServerGamePacketListenerImpl.handleMovePlayer` 的传送等待门控。
- `ServerGamePacketListenerImpl.resetPosition` 维护 `firstGood`、`lastGood` 移动验证基准。

修复将同一维度的承载改为已裁剪的 `setPos`，服务端同步维护移动验证基准，客户端在每个实际游戏 tick 开头根据同一对结构姿态预测承载。每个实体每 tick 最多由一个结构承载；宿主墙壁与顶板仍限制承载位移。离地且向上运动的跳跃不继续黏住结构；支撑容差从 0.35 格收紧至 0.08 格。

### 倾斜大表面不能按玩家附近的实际高度迈步

旧的阶梯候选只枚举 OBB 的八个角。大块贴地结构略微倾斜时，玩家附近的表面高度与远处角点高度不同；上坡水平移动会被截断，候选阶高却可能全部无效，或把玩家直接抬至远处角点高度。

修复保留宿主与结构候选阶高，并补充最大可迈步高度；越过障碍后再次沿竖直方向与真实 OBB 和宿主碰撞体求交，让脚落回新位置的实际表面。不会把玩家留在虚空中的最大阶高位置。

## 验证

纯数学测试新增：

1. 5°、40 格长倾斜表面连续行走 100 tick，保持水平速度并只升高该步应有的坡度高度。
2. 宿主地面与物理结构存在 ±0.0015 格沉降差时，连续越过接缝。
3. 从宿主地面迈上物理半砖，脚落在真实半格高度。
4. 越过窄凸起后不悬空在最大阶高。

`SubLevelContactResolverTest` 与 `SubLevelOrientedCollisionTest` 已通过，日志：`build/ground-contact-tests-r1.log`。

真实客户端探针 `SubLevelGroundWalkingSmoke` 使用 `ClientInput` 经正常客户端移动及网络处理，场地在实际地形高度，结构底面直接贴合宿主地板。路线包含宿主地面、物理石平台、物理半砖、宿主半阶，双向行走，并人为加入每 tick 3 毫米的结构沉降变化，专门验证旧的重复传送问题。

探针命令：

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat runClientDev -DisDev=true @physicsNativeArgs `
  -I validation/native_structure/sublevel_ground_walking.init.gradle `
  -PacademyGroundWalkDirectory=run/native-sublevel-ground-walk-r1
```

运行目录必须是独立的 `run/native-sublevel-ground-walk-*`，其中世界副本名为 `test`。探针会修改这个副本的场地，不允许使用原 `run`。

首次真实客户端运行在步行前触发源格 AIR 断言，位置为 `(-142, 62, 96)`，日志：`build/ground-walking-r1.log`。后续 r3 同时记录失败时的两端状态，证明客户端与服务端实际都是 `water[level=4]`，并非旧的石头：夹具位于近水地形，清场后周围水流灌入了捕获空位。因此 r1/r2 不能作为源区块不同步或幽灵块的确认依据。夹具需要补全防水边界后保留原 AIR 断言重新执行；真实源格同步问题仍需独立证据，不能把测试场地灌水误归因于生产代码。

补齐场地防水边界后的 r4 完整实机验收通过，日志：`build/ground-walking-r4.log`：

| 检查项 | 结果 |
| --- | --- |
| 捕获后源格 | 服务端及客户端均为 AIR |
| 行走路线 | 双向连续通过，含两个半格台阶 |
| 毫米级结构沉降修正 | 79 次 |
| 承载期间原版传送等待门控 | 0 次 |
| 最大反向回拉 | 0 格 |
| 平台中部最大垂直误差 | 0.002827 格 |
| 平台中部 grounded 样本 | 54 / 54 |

包含最终 native 接触面修复后的随包复测 r5 再次通过，日志：`build/ground-walking-r5.log`。仍为源格两端 AIR、双向通过两个半格台阶、79 次承载修正、0 次传送等待、0 格反向回拉、54/54 grounded；平台中部最大垂直误差为 **0.001676 格**。

此结果验证了贴地承载与连续步行修复，没有重新定义或绕过碰撞检查；本探针不把灌水失败当作幽灵源块证据。

## 不可见阻挡的独立排查

用户随后明确：幽灵方块指草地上看不到方块却阻挡行走、跳跃，并进一步怀疑结构仍在运动时，其原位置留下不可见阻挡，连宿主方块交互也受影响。此现象不能等同于源格显示未消失，必须验证结构移动后的旧姿态是否仍参与接触或拾取。

空域负例增加三项纯数学检查：同一结构两侧墙之间的空走廊可行走及跳跃；旋转 OBB 外层 AABB 的空角不产生墙壁/顶板；脚部与支撑面存在 1.5 毫米重叠时可正常起跳离开。对应的实际客户端探针 `SubLevelEmptySpaceSmoke` 已在 `build/empty-space-r1.log` 通过：

| 检查项 | 结果 |
| --- | --- |
| 实际输入穿过同一结构的空走廊 | 通过 |
| 位于结构外层 AABB 内的 tick 数 | 28 |
| 起跳最大高度 | 1.252203 格 |
| 离地 tick 数 | 11 |

探针通过说明当前精确 OBB 路径不会一概填实结构的外框；它不等于确认用户原场景的不可见阻挡已经解决。命令使用 `validation/native_structure/sublevel_empty_space.init.gradle`，目录参数为 `-PacademyEmptySpaceDirectory=run/native-sublevel-empty-space-r1`，仍需独立世界副本。

最终 native 随包复测 `build/empty-space-r2.log` 同样通过：外框内 28 tick、跳高 1.252203 格、离地 11 tick，空走廊正常通行。

`build/ground-final-tests-r2.log` 对应的 JUnit 结果显示 `SubLevelContactResolverTest` 共 11 项全部通过，包含上述三项空域/起跳负例。

### 已保存现场的只读检查

以 `run/native-ground-repair-source/saves/test2` 的 22:37 快照为准，玩家保存位置为 `(84.464888, 63, -54.499043)`，处于地面；该快照有 26 个 SubLevel。当前位置周围宿主的 `y=62` 为草方块，头部空间为空气或无碰撞草。

结构 `fe8cf85e-e582-4b67-a964-4e3a4ccad4d5` 的 manifest 外框覆盖玩家：该结构接近翻转 180°，外框约为 `(76.085627, 61.999946, -55.270560)` 至 `(86.711027, 65.000040, -44.781436)`。但玩家逆变换后的局部坐标为 `(3.523845, 2.000007, 8.902332)`，超过本地最大 `z=7`，实际处于旋转外框的空角。plot 中只剩 3 个草方块和 4 个草植被；3 个实心单元的世界联合范围约为 `(81.080492, 62.999957, -51.456493)` 至 `(86.711020, 64.999994, -49.004332)`，与保存玩家脚/头均不相交。因此 manifest 外框本身不能作为存在实体接触的证据。当前 `SubLevelCollisionIndex` 依据实际局部碰撞单元联合范围建立索引，未采用这份较大的 manifest 范围。

只读代码检查还确认：普通实体接触不消费路径导航用的外层 AABB；client-only 投影的旧实体碰撞入口关闭；碰撞和渲染都推进同一组客户端 tick 姿态。尚无证据表明这里保留了旧姿态，仍需运动期间的实机负例与现场坐标补充。

用户关闭后再次只读检查原 `run/saves/test2` 的 23:11 保存结果：清单已增至 36 个结构，玩家位置改为 `(15.300000, 94, -65.699999)`，附近 16 格没有结构 manifest 外框。旧 `native_structures` 清单为空，保存实体中也没有 `academy:*` 实体。此位置头顶 `(15, 96, -66)`、`(15, 96, -65)` 存在宿主橡树叶，相邻地形有一格高差；这些宿主方块会正常约束起跳，但用户没有指定它就是问题发生点，因此不能拿该位置的普通碰撞解释或否定运动中的不可见阻挡。

### 运动中原位置回归

新增 `SubLevelMovingOriginSmoke`，真实调用 native launch，物体离开起始位置后仍在运动时，通过正常客户端输入穿越其原占据体积并起跳，同时检查旧位置索引、结构射线，以及后方宿主拉杆的正常交互并在服务端确认开关状态。测试要求至少 15 个服务器 tick 保持运动，防止只验证停稳后的场景。

命令使用 `validation/native_structure/sublevel_moving_origin.init.gradle`，目录参数为 `-PacademyMovingOriginDirectory=run/native-sublevel-moving-origin-r1`。

首轮 `build/moving-origin-r1.log` 已通过运动后旧位置索引与结构射线的检查，随后宿主拉杆命中断言失败。检查原版 `LeverBlock.makeShapes` 发现壁挂拉杆占据单元靠墙的一侧，夹具使用 `Vec3.atCenterOf(lever)` 把射线终点放在形状外的空气里；这不是结构残留命中的证据。探针改为该拉杆实际 outline bounds 的世界中心，并在失败时完整输出 hit、眼睛、目标点、方块状态及形状。可达距离、真实 `useItemOn` 和服务端 powered 断言均保留，随后重新执行 r2。

修正瞄准点后，`build/moving-origin-r2.log` 完整通过：

| 检查项 | 结果 |
| --- | --- |
| 原生物理发射及正常客户端输入 | 通过 |
| 玩家穿过结构原占据体积 | 14 tick |
| 起跳最大高度 / 离地时间 | 1.252203 格 / 11 tick |
| 验证过程中服务端结构仍在运动 | 28 tick |
| 原位置遗留碰撞索引 / 结构射线 | 均无 |
| 穿过原位置操作后方宿主拉杆 | 正常；服务端确认 powered |

这是当前新版本在标准夹具中的运动原位置回归通过，覆盖物体尚未停下时的移动、跳跃和宿主交互；没有确切复现旧 `test2` 中用户遇到的原点现场，因此不能宣称已确认该现场的唯一根因。新 native 随包的贴地步行 r5 与空域 r2 复测均已通过。
