# 紧密地形中的 H 抬升修复记录

本文记录 2026-10-20 本轮“创建成功但只抬不足半格”的定位、实现与专门回归。贴地行走及不可见阻挡另见 `sublevel_ground_contact_audit_2026_10_20.zh-CN.md`；区块卸载回正仍仅设计，本修复不实现自动回正。

## 为什么原来的测试没有覆盖故障

先前 9×9 单层悬空平台周围没有坑壁。新增 `SubLevelLiftTiersSmoke` 的 `academy.sublevelLiftEmbedded` 模式，在副本中构建 21×21×9 土体，通过真实 H 按下/释放流程挖出选区，保留紧贴结构侧面的地形。三档仍要求按来源格质心升高 3/5/7 格，在 ±0.1 格范围连续保持 200 tick，随后执行显式投掷。

修复前 tier 0 捕获 128 格，残留顶盖数量为 0，40 tick 实升约 0.00194 格，200 tick 不到 0.005 格。另两项隔离试验也失败：原位向上偏移 0.25 格后只停在 0.257 格附近；所有动态碰撞子盒每面内缩 0.005 格后仍只升约 0.012 格。这两个试验参数与改变几何的探针代码已删除，未作为修复手段保留。

本地日志：`build/ground-embedded-before-r1.log`、`ground-embedded-clearance-r1.log`、`ground-embedded-inset-r1.log`。这些文件是本机验证产物，不纳入版本控制。

## 本地求解器根因

在纯 Rust 中复现相同 128 格选区及其外围地形，不依赖 Minecraft、网络或地形缓存：

- 单个立方体可从零间隙坑中正常抬起。
- 多个子盒拼成的不规则选区卡死；关闭 CCD、取消所有摩擦、把最大力从 2000 增至 10000，均未解除。
- 精确合并为 12 个子盒仍然卡死。因此不能只通过减盒数、加力或禁用碰撞解决。
- 接触记录显示，坑壁子盒下角与结构子盒上角相切时产生 `(±0.707, 0.707, 0)` 一类 45° 法线。该法线包含本应由相邻子盒遮住的内部面分量。四周地形同时施加这种约束，使几何上合法的向上滑动被锁住。

`native/academy_physics/src/contact_surface.rs` 在几何改变时缓存每个碰撞子盒的被遮挡面矩形；接触钩子仅移除接触锚点所在内部面的法线分量，保留真实外侧墙、地面和顶板法线。某接触完全属于内部面时才删除该接触。仅在角或边相碰、没有共享面积的邻盒不能遮挡一个面。

缓存具有以下边界：

- 动态体使用自身局部几何，随刚体旋转；固定关节连接的多刚体装配也建立跨体邻接。
- 固定地形使用 Parry 的增量 BVH 查找相邻候选，覆盖不同 section、不同批次刚体之间的内部面。
- 创建、删除、显式位姿改变与固定关节拓扑改变更新缓存；删除连接中间体会重建受影响的装配，防止已经分离的物体继续遮挡彼此表面。
- 物理 step 不扫描整个世界或全部刚体建立邻接。没有按大包围盒展开体素网格。
- 对任意非正交相对旋转的局部子盒保留原求解器处理；接触面投影不是忽略硬地形或强制传送。

目前大装配的显式位姿 API 仍逐分片调用 native，每次会刷新该固定连接组，因此多分片旋转或传送可能重复刷新。普通 step 和投掷不走此路径。本轮未改变该 API 批量语义，不能将本修复当作大装配所有操作的性能结论。

`PlotGeometry` 同时复用地形的精确相邻盒合并算法，不再只为达到 512 格且完整填满外框的结构启用合并。合并保持真实形状的并集，包括凹陷、孔洞和半方块，不用外层 AABB 替代实体几何。

## H 选区与控制

`VectorStructureService` 的手动 H 预算裁剪保留每个已选低格上方的已选格，防止预算裁掉顶盖后把顶盖留在世界中。Sable 的后端内容过滤先于遮挡检查，避免先认定上方格已选、随后又过滤该格。Sable 手动 H 的普通一质量格数量先受对应档位质量预算约束，实际后端质量检查仍保留。精密操作的独立提取保留原距离预算裁剪，不套用手动抬升的依赖裁剪。

Sable 手动 H 改用与本地一致的质心力控制租约和重力补偿。目标来自原始选区格坐标及对应局部质心，保留原 X/Z，向上增加 3/5/7 格；不再用视线命中的偏心表面作为抬升拉力点。Sable 桥创建阶段的原点修正由本轮桥接修复独立处理，不能只靠这一控制改动解释解决。

悬浮计时在质心进入目标 0.1 格内、线速度不超过 0.15 格/秒且角速度不超过 0.1 弧度/秒后开始。首次快速经过目标高度不再消耗后续稳定瞄准时间。完整保持期为 200 server tick；到期释放控制，不因玩家仍看着结构而自动发射。发射必须由 H 或对应显式控制命令触发。初始抬升仍有独立的 200 tick 超时，防止被真实障碍挡住的结构永久持有控制租约。

## 验证记录

Rust 已通过 18 项测试，见 `build/ground-lift-native-full-r2.log`；本次新增的有效断言包括：

| 场景 | 结果 |
| --- | --- |
| 128 格选区精确合并为 12 盒 | 60 tick 实升 3.06725 格，水平偏移为 0 |
| 128 盒不合并，固定地形每 7 盒分一个刚体 | 同上 |
| 128 盒分成 2 个动态刚体并固定连接 | 同上 |
| 50 格/秒射向实体墙或顶板 | 被阻挡，无穿透 |
| 整体旋转 0.35 弧度的墙与投射体 | 仍被阻挡 |
| 旋转后的邻接面、删除固定邻体 | 覆盖法线正确，并在删除后恢复真实外露面 |
| 仅角点相接的方块 | 不错误遮挡真实表面 |

上述测试使用最终生产创建、关节、删除、step 路径。`VectorStructureServiceTest` 另增加预算裁剪确定性及保留顶部依赖的单元测试。

最终随包 Windows native 已在真实本地客户端通过紧密土体三档 H：

| 档位 | 实际抬升 | 连续保持 |
| --- | --- | --- |
| 0 | 3.064378 格 | 200 tick |
| 1 | 5.064378 格 | 200 tick |
| 2 | 7.064378 格 | 200 tick |

日志为 `build/ground-native-final-r1.log`，包含三档通过及最终 `SUBLEVEL_LIFT357_SMOKE_PASSED`。此运行使用随包库，没有通过 external library 属性替换。库 SHA-256 为 `d4e9e43737708a397dc05b1b9af11279ce2906ae54d97320997c19da8d4a80ee`。三档均受当前本地配置限制为 128 格，因此真实 512 格上限仍标记 **UNVERIFIED**，不能用这里的 7 格高度通过代替 512 格容量验证。

统一主测试和 Java 25 FFM 原生适配专项 `test` / `nativePhysicsTest` 已通过，见 `build/ground-final-tests-r2.log`。

Sable 最终 `build/ground-sable-r4.log` 同样通过三档：

| 档位 | 方块数 | 实际抬升 | 连续保持 |
| --- | --- | --- | --- |
| 0 | 128 | 3.016769 格 | 200 tick |
| 1 | 256 | 5.022888 格 | 200 tick |
| 2 | 256 | 7.022888 格 | 200 tick |

日志包含最终 `SUBLEVEL_LIFT357_SMOKE_PASSED`。H probe 开关下的 `SUBLEVEL_LIFT_COMMAND` 调用源诊断确认，本轮三次发射都由夹具完成 200 tick 后显式调用；默认游戏不开启该诊断。早期 r3 在 tier 1 发生过额外发射及新的会话，本轮未复现，无法从没有调用栈的旧日志确认其触发源，不据此推断为玩家按键。

## 实机复测方式与验证边界

使用 JBR 25，在 `run/native-sublevel-direct-smoke-*` 内准备含 `saves/test` 的独立副本。探针会修改副本地形，禁止直接使用用户原世界目录。

```powershell
.\gradlew.bat runClientDev -DisDev=true `
  -I validation/native_structure/sublevel_lift_tiers.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-ground-local `
  -PacademySubLevelLiftEmbedded=true

.\gradlew.bat runClientSable -DisDev=true `
  -PacademySableRuntime=sable `
  -PacademySableRunDirectory=run/sable-compat/ground-lift-r4 `
  -I validation/native_structure/sublevel_lift_tiers.init.gradle `
  -PacademySubLevelLiftRun=clientSable `
  -PacademySubLevelSmokeDirectory=run/sable-compat/ground-lift-r4/native-sublevel-direct-smoke-ground-sable-r4 `
  -PacademySubLevelLiftEmbedded=true
```

成功需要三档 `SUBLEVEL_LIFT357_TIER_PASSED` 和整体完成标记；仅 `BUILD SUCCESSFUL` 不证明实机断言通过。源格、向上 0.1 格后的选区与重建地形存在正体积重叠时，夹具直接失败。

最终开发/发布构建结果统一见接续记录最新节。真实 512 格选择、全部第三方方块以及用户原 `test2` 故障瞬间仍不能仅凭这组有限回归宣称完全验证。

新增接触钩子后的 16×128 格持续运动复测见 `build/ground-final-moving-perf-r2.log`。沿用此前基准 options（VSync=false、maxFps=260、视距16、模拟距离12）、1280×720 窗口，240 tick 得到平均 805.71 FPS，帧 P50/P95 为 1.0626/1.9816 ms，服务器 tick P50/P95 为 5.2712/12.3281 ms。未见该夹具的明显整体回退；单次结果不证明大装配、显式 setPose 或 Sable 对照的性能等价。r1 误用了 VSync=true、maxFps=120，平均 116.98 FPS，不作为同设置比较。
