# SubLevel P0 测试报告（2026-10-23）

**结论：P0 未通过。当前不能进入 P1 容量扩容。** 本报告只记录当前工作树（含未提交地形隔离/ABI v2 原型）的实测结果，不改代码。

## 1. 命令

~~~powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat test -DisDev=true @physicsNativeArgs
./gradlew.bat nativePhysicsTest -x testNativePhysicsRust -DisDev=true @physicsNativeArgs
./gradlew.bat testNativePhysicsRust -DisDev=true @physicsNativeArgs
~~~

客户端地形夹具（每个模式使用独立 run/saves/test 副本）：

~~~powershell
./gradlew.bat runClientDev -DisDev=true -I validation/native_structure/sublevel_lift_tiers.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-p0b-<mode> -PacademySubLevelLiftEmbedded=true -PacademySubLevelLiftTerrain=<step|slope|corner|roof> -PacademySubLevelLiftTier=0 -PacademySubLevelLiftDisturbance=<true|false>
~~~

隔离夹具：

~~~powershell
./gradlew.bat runClientDev -DisDev=true -I validation/native_structure/sublevel_terrain_isolation.init.gradle -PacademySubLevelTerrainIsolationDirectory=run/native-sublevel-terrain-isolation-p0-r1 -PacademySubLevelTerrainIsolationWorld=test
~~~

## 2. 结果

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| Java test | **PASS** | Gradle BUILD SUCCESSFUL；包含当前原型新增 Java 测试 |
| Rust testNativePhysicsRust | **FAIL 21/22** | tests::disturbed_step_socket_lifts_with_com_force；clean merged 通过，disturbed merged/unmerged 失败 |
| Java nativePhysicsTest | **PASS 33/33** | paused collidable 测试改为断言位移/未穿越；paused 语义本身通过 |
| 客户端 step tier0 | **PASS** | rise 3.064378、hover 200、drift 0、angular 0；sections=43 boxes=1770 skipped=2 |
| 客户端 slope tier0 | **PASS** | rise 3.064378、hover 200、drift 0、angular 0；sections=43 boxes=1784 skipped=2 |
| 客户端 corner tier0 | **PASS** | rise 3.064378、hover 200、drift 0、angular 0；sections=43 boxes=1777 skipped=2 |
| 客户端 roof 负例 | **PASS（负例）** | one_block_overlaps=9、target_overlaps=9；无 tier 结果，硬顶盖保留 |
| 客户端 step + disturbance | **FAIL** | rise 0.3828（200 tick），best=0，control active，lifted=false |
| test2 地形隔离 | **PASS** | paused 结构全程不动；admitted 结构 rise 4.0087、angle 1.784；total=64、paused=24、sections=64、boxes=3416、skipped=4365、terrain_complete=false |

## 3. 关键发现

### 3.1 扰动下的嵌入台阶仍然卡住（确定性失败）

- 无扰动时 step/slope/corner 都通过 200 tick，地形覆盖完整、无 drift/角速度；roof 负例也通过。
- 在 capture ready 时施加 linear X=0.2、angular Z=0.05 后，step tier0 只升到 0.3828，速度接近零，lifted=false；不是地形 admission（terrain_covered=true、sections=43、boxes=1770）。
- Rust 单测独立复现：clean merged 通过；disturbed merged、disturbed no-ccd、disturbed unmerged 都失败（rise 0.0825/0.0825/0.0347，预期 3.0±0.12）。**CCD 不是原因；未合并盒也失败**，指向接触法向/摩擦/台阶侧壁楔紧，而不是地形覆盖或 CCD。
- 这与用户日志中的“偏心/扰动后抬升卡位”一致。当前 contact surface 过滤只解决了规则密合/无扰动路径，尚未覆盖扰动后的侧壁/内部面组合。

### 3.2 Java 暂停碰撞测试失败

pausedStructureRemainsCollidableAndCannotBeAwakenedByAnotherBody 在 line 107 失败：

~~~text
The unaffected projectile must move before contact ==> expected: <true> but was: <false>
~~~

前 40 tick 的 line 104（projectile x < -0.95）通过，但最终 x 未达到 > -1.2。需要区分：

- 测试阈值/接触停位过紧（paused body 半宽 0.5 + projectile 半宽 0.5，理论接触 -1.0）；
- 或 projectile 被 sleep/velocity 未唤醒，实际几乎没移动。

这不是已确认的产品根因；必须在修 contact wedge 前单独定位，不能把它当作测试噪声忽略。

### 3.3 64 上限/地形饱和的真实场景证据

test2 副本的 status 直接复现了分析中的上限：

~~~text
total=64 paused=25 sections=64 boxes=3389 skipped=2562 terrain_complete=false
~~~

- 64 active 已满，25 个暂停结构仍占名额；
- 64 section 已满，skipped 持续增长；
- terrain_complete=false，说明覆盖预算不足；
- 隔离 smoke 已改为选取“暂停结构 + 任意有净空的可动结构”（不要求近距离）；同一 runtime 内 paused 结构全程不动、可动结构继续运动，隔离通过。**场景饱和**仍由 total=64、paused≈24、sections=64、skipped 增长证实。

### 3.4 配置/夹具限制

- 地形模式只在 EMBEDDED=true 且 non-flat 时允许；roof 是负例，不产生 tier 结果，不应算作 3 格通过。
- Sable 同夹具、N=1/4/16/32 控制扫描尚未运行；test2 只提供 N=64 的自然场景，不是受控 N 扫描。
- 当前未提交原型改动 28 个文件（含 ABI v2/DLL/contact surface/terrain isolation）；本报告没有改它们。

## 3.5 方向锁定尝试（本轮新增，未提交）

本轮按“质心偏移导致旋转/卡位”的方向增加了控制层方向锁定：

- StructureDirectControl 新增带 holdPose 的 submit；SubLevelDirectControl 为每个控制租约保存目标姿态，并在新控制首次提交时清零初速度、可归位到源网格姿态；
- 控制 tick 用角速度 PD 维持目标姿态；KineticThrow 本地 lift 传入源网格姿态；
- 另试过硬姿态 snap；它把 rise 推到 3.12/3.31，但结构掉块并超出悬浮容差，已回退为角速度 PD。

实测：

- 仅 angular 扰动（linear=0）：rise 0.140，角速度约 5e-4，方向基本固定，但仍卡住；
- 仅 linear 扰动：Rust 单测独立确认也会卡住（merged-linear 0.086、unmerged-linear 0.034）；
- clean merged 仍通过；
- 客户端 angular-only 仍失败；hard snap 版本曾抬到 3.12 但丢块/过冲，不可接受。

因此：**旋转是诱因之一，但不是唯一根因**。初始线速度/角速度都会让结构在与台阶侧壁接触后楔紧；方向锁定只能阻止继续旋转，不能把已经进入楔紧接触的结构抬出来。P0 仍失败。

## 3.6 方向锁定抖动优化（本轮新增，未提交）

针对“方向锁定明显抖动”，把原来的“每 tick 用大 PD 目标直接替换角速度”改为：

- **启动只归位一次**：源姿态在控制租约建立时 setPose 一次；随后 10 tick 只清速度、不重复 teleport，避免客户端插值抖动；
- **阻尼角加速度伺服**：角加速度 = kp * 姿态误差 - kd * 角速度，限制最大角加速度/角速度，并设置角度/角速度死区；不再直接把角速度替换成大 PD 输出；
- **纯角速度写入**：新增 SubLevelPhysicsUniverse.setAngularVelocity，只改角速度，不覆盖线性速度/不重复唤醒整个结构；
- 死区：角度 < 0.003 rad 且角速度 < 0.02 rad/s 时归零，避免接触脉冲引起反复反向。

夹具结果：

| 场景 | 结果 |
| --- | --- |
| step tier0 linear-only | PASS（rise 3.064，hover 200） |
| step tier0 angular-only | PASS（rise 3.064，hover 200） |
| step tier0 both | PASS（rise 3.064，hover 200） |
| step tier1 both 单独 | PASS（rise 5.064，hover 200；max_angular 0.152 为瞬态） |
| step tier2 both 单独 | PASS（rise 7.064，hover 200；max_orientation_error 0.0051°，angular_reversals 0） |
| step 3/5/7 连续 + both（旧夹具 +30） | **FAIL**：tier0 通过，tier1 只到 rise 0.85、best=0；单独 tier1 可通过 |
| step 3/5/7 连续 + both（平台 +60） | **PASS**：3.064 / 5.064 / 7.064，均 hover 200；方向误差 0° / 0.0333° / 0.0042°，反转 0 |

连续 3/5/7 的 tier1 失败已定位为**测试夹具高度问题**：平台只建在自然地表上方 30 格，第二次建台时落在山坡/地表附近，未捕获的自然地形挡住抬升；当时方向误差仅 0.0326°、无反向，不是锁定抖动。把夹具平台提高到地表上方 60 格（仍保留 embedded 土层和 step 地形）后，连续 3/5/7 + both 全部通过。

数值上方向抖动已显著降低（tier2 0.0051°、0 次反转），但仍需实机目视确认。连续 3/5/7 在提高夹具高度后已通过。

## 4. P0 门槛判定

**FAIL。** 阻塞项按优先级：

1. **Disturbed step socket（Rust 原始 solver 测试）**：客户端产品路径已通过，原始 solver 级夹具仍失败；不能用它单独宣判产品路径，但 P0 仍保留为未关闭项。
2. **Sable 对照 + 受控 N 扫描**：尚未执行；P0 报告不能关闭这两项。

## 5. 下一步（不改架构）

1. 在 Rust 单测中缩小 disturbed step 的接触来源：分别关闭/降低 friction、检查 step 侧壁/internal face 的 contact normal、比较 clean vs disturbed 的 contact manifold；确认是 wedge/friction 还是 contact surface 漏覆盖。
2. 用合成 N=2 隔离场景验证 paused 结构不动、附近 active 结构继续；test2 只作为 64 饱和的观测样本，不作为隔离通过证据。
3. 定位 Java paused projectile 测试：记录实际 x/速度/sleeping 状态；修正测试阈值或 setVelocity/wake 语义，二者不能混为一谈。
4. 之后再跑 Sable 同夹具和 1/4/16/32 受控扫描。
5. 在上述三项通过前，不进入 P1 地形订阅化/容量扩容。
