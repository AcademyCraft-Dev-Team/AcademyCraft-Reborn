# test 世界定点铁块崩溃排查

日期：2026-10-20。审阅基线：`28d07b53`。用户后续确认不再崩溃，当前修复目标转为该铁块无法破坏、无法推动、持续悬空。下面分开记录观察崩溃的诊断证据与冻结结构的修复；不把未复现原生退出当作已经定位其原因。原 test 存档保持只读。

## 报告与目标确认

用户确认：未打开精密操作编辑器，每次转向原玩家位置上方的倾斜悬空单个铁块时退出。

用户最初提供的 `run/crash-reports/crash-2026-09-24_00.33.14-client.txt` 是旧的 `ModularProgramScreen` 鼠标缩放坐标非有限值异常：`Cannot round NaN value`。它不对应此次世界观察场景。

与本次最新运行对应的文件是 `run/hs_err_pid53656.log`：

- 2026-10-20 16:30:42，JBR 25.0.3+1-b329.124，进程已运行约 336 秒。
- `EXCEPTION_ACCESS_VIOLATION`，故障线程为 `G1 Conc#3`。
- `G1ConcurrentMark::mark_in_bitmap` 扫描对象数组时收到非法对象地址 `0x2000`。
- `0xfffff7fa` 是由该地址计算出的越界 region 索引，不能把它误写成原始压缩对象引用。
- 没有 minidump。`Object[257]` 与 fastutil iterator 的栈信息不能确定是哪张 map，更不能定位最早写坏内存的代码。
- `run/logs/latest.log` 在 16:30:40 记录玩家进入 test，22 个 SubLevel 恢复；随后进程退出。UBO 扩容发生在退出之前，不构成其导致崩溃的证据。

原运行依赖不含 ImGui，使用 release coremod；初轮 `isDev=true` 探针与之存在依赖差异。因此另外安排 `isDev=false`、开启 F3+B 的对照，不能只用开发环境的成功覆盖原问题。

## 已闭合：铁块异常悬空的来源

只读解码 `run/saves/test/data/academy/native_structures/metadata.dat`：

| 项目 | 值 |
| --- | --- |
| 结构 ID | `860aa2e6-7a95-4f90-ac1a-98cdc3ba5b00` |
| 后端 / slot | `academy:native_level` / `0` |
| 原物理区域 | `7dd86e8b-fc20-4995-a544-fe52955b89aa` |
| 持久状态 | `active=1`、`quarantined=0`、`resume_allowed=0`、`sleeping=0` |
| 原点 | `(-202.75947605184783, 65.2125447057951, 153.36720681638613)` |
| 四元数 x/y/z/w | `(0.19901663214979898, -0.01677001932440189, -0.3475717773639707, 0.9161359103100704)` |

这不是新 SubLevel 的睡眠状态。存档仍保存非零线速度和角速度，但所属旧区域已经失败，拒绝自动恢复。

`run/logs/2026-10-20-2.log.gz` 解压后 274–277 行还原了失败顺序：

1. 07:33:22.080，旧区域接管该铁块，状态 READY。
2. 07:33:22.294，H 投掷区域跟随重定位失败，记录 `ending flight safely`。
3. 07:33:22.397，区域抛出 `Structure left the bounded physics domain; last published pose retained`。

`VectorStructureService.followRegion` 重定位失败后调用 `terminate`，后者只释放技能控制，继续保留物理惯性。随后的物理步越界，`NativeStructurePhysics.Region.fail` 销毁区域物理世界并保留最后发布姿态；`NativeStructureRuntime.markPhysicsFailed` 持久化禁止恢复标记。该区域还包含另一旧结构，因此不应直接清除整组数据来消除铁块。

这条链解释冻结，**尚不能解释观察它时发生的 JVM 崩溃**。证据：

- `build/inspect-native-metadata.ps1`：只读 NBT 解码。
- `build/native-metadata-target-audit.json`：原始目标状态。
- `build/native-metadata-target-failure-evidence.log`：旧日志摘录。

## 渲染与原生边界审阅

- 空闲转头主要新增的是视锥命中后的模型提取/提交。结构精确射线通常由点击、挖掘或特定技能预览调用，不是持续的准星 tooltip 路径。
- 单个铁块走原版 MovingBlock 模型提交，不进入要求至少 512 格的批处理路径。旧实体与 SubLevel 共用投影 renderer。
- `StructurePose` 已检查非有限位置/旋转和零四元数；`NativeStructureView` 已限制方块数量、坐标和状态。
- Java FFM 输入复制到 confined arena；Rust 同步读取后复制输入，不保留 Java 堆数组指针；输出容量与 ABI 长度一致。此次未找到可直接解释 JVM 堆损坏的越界写入或生命周期缺陷。
- 原 `run/.mixin.out` 与 r4 共同的 126 个 class，在归一化 Mixin session UUID/方法名随机后缀后，二进制相同。原目录额外导出 `EntityHitboxDebugRenderer`，故补测 F3+B。未导出的主体实现类不在该比较范围内，不能据此宣称所有历史编译产物完全一致。
- 本机历史报告中还有 G1 对象搬移、C2 编译器等不同位置的 JVM 崩溃；这些历史记录不能单独证明运行时或硬件故障。

## 隔离复测记录

每轮从原 test 重新复制，使用 `prepare_default_skill_smoke.ps1` 记录源文件 SHA-256；所有运行均在 `run/native-sublevel-direct-smoke-look-crash-r*`。

| 轮次 | 条件与覆盖 | 结果 / 限制 |
| --- | --- | --- |
| r1 | dev、GC 前后堆验证、原位置和部分结构巡视 | 未出现新 JVM 崩溃；验证开销很大，正常关闭后换普通参数；未完成全量巡视 |
| r2 | 普通参数 | 探针对相对目录的判断错误导致主动拒绝；无效测试，不计游戏回归 |
| r3 | dev、4 个旧实体及 1 个 SubLevel，15 张观察截图 | 无观察崩溃；SubLevel 的空 runtime UUID 导致探针去重覆盖不足，后续改用 SubLevel ID |
| r4 | dev、13 个实际跟踪目标，三角度巡视，784 client ticks | 无观察崩溃；不是全部 22 个保存 SubLevel 的验收 |
| r5 | dev、已确认铁块，原位置仰视及六方向，480 client ticks | 无观察崩溃，精确射线命中同一结构 ID；20 张截图 |
| r6 | dev、`-Xverify:all -Xcheck:jni` | 进入世界前被独立 ImGui 字形 JNI 错误中断，未覆盖目标 |
| r7 | release 依赖、F3+B、请求 3440×1351、原位置与六方向反复观察 | 7,200 目标观察 ticks，7,360 总 client ticks；正常结束，未出现观察崩溃；Gradle 成功，耗时 6 分 30 秒 |

普通参数沿用仓库的 `-XX:+AllowEnhancedClassRedefinition -Xverify:none`。3440×1351 来自用户提供的旧 GUI 报告，10 月 20 日原会话的窗口尺寸未证实，不能称为完全一致。没有永久换 GC、关闭碰撞或删除异常结构。探针会改变副本玩家的飞行/重力和朝向，观察测试不等于逐输入复刻用户的完整会话。

临时探针源码：`build/structure-look-src/org/academy/internal/client/validation/StructureLookProbe.java`；启动配置：`build/structure-look.init.gradle`。它们在忽略目录，属于诊断工具，不进入发布包。每轮日志为 `build/structure-look-crash-rN.log`，截图在对应副本的 `screenshots/`。

复测命令（先用新的唯一名称准备副本；不得指向原 `run`）：

```powershell
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name look-crash-next
. ./build/native-physics-p16-env.ps1
./gradlew.bat runClientDev -DisDev=false -I build/structure-look.init.gradle `
    -PacademyLookDirectory=run/native-sublevel-direct-smoke-look-crash-next `
    -PacademyLookIron=true -PacademyLookHitboxes=true -PacademyLookIronTicks=7200 `
    -PacademyLookWidth=3440 -PacademyLookHeight=1351 @physicsNativeArgs --console=plain
```

## 分开记录的其他故障

1. **dev ImGui 字形指针生命周期**：本地 `imgui-java-binding-1.90.0-sources.jar` 的 `ImFontAtlas.java` 当前重载将 `GetPrimitiveArrayCritical(short[])` 指针交给 `AddFontFromFileTTF`，随后立即 release；下一次调用构建 atlas 才读取。`ImGuiBackend.loadLocalizedFont` 的 `reachabilityFence` 不能维持地址固定。r6 在 `src_range[0] <= src_range[1]` 断言处退出。原崩溃进程无 ImGui，因此不能拿这个独立缺陷替代原问题。正确修复需要绑定使用稳定的 native 字形范围内存，不能仅把数组改成字段。
2. **退出时 TemporalRuntime 生命周期**：部分探针退出出现 `Temporal runtime has stopped`，调用链经矢量反射的 `maintainTemporalImmunity`。它发生在主动退出后的清理阶段，不是用户转头触发的故障。后续 20:56 新世界崩溃确认了这一独立缺陷会残留跨世界句柄；修复与同 JVM 回归见 [世界切换生命周期报告](reflection_world_switch_crash_2026_10_20.zh-CN.md)。
3. **旧编辑器 NaN 坐标**：用户给出的 9 月报告仍有对应未防护路径，未在此轮混入 GUI 修复。

## 尚未完成与后续判据

- UNVERIFIED：原报告中非法对象引用的写入者；不得仅根据 G1 栈指定为 Rapier、UBO、某张 fastutil map、JBR 或硬件缺陷。
- UNVERIFIED：用户完整启动会话的稳定触发条件；无复现不等于已修复。
- 若再次捕获原生退出，先保全新的 `hs_err`、启动参数、运行产物哈希和发生前日志；为下一轮独立诊断运行开启 minidump，再做按模块的对照，不能用捕获 Java 异常来掩盖 native 退出。
- 冻结结构的恢复事务与故障收尾见下节；通用区域迁移仍为后续工作，不能把旧后端有界恢复当作无限世界运动支持。
- 崩溃修复完成判据：取得重复触发与针对性修复证据；同一铁块、原位置、F3+B 开/关、原依赖环境均通过，且原存档未被删除或暗中重建。

观察探针结束后的源存档核验：164 个文件的 SHA-256 全部与初始快照一致（不计 `session.lock`）。诊断客户端只证明表中列出的场景。

## 冻结与不可破坏的修复

用户确认不再退出后，进一步确认两项独立根因：

1. 旧区域 FAILED checkpoint 的 `resume_allowed=0` 使正常启动恢复拒绝整组结构。它解释物理停止与持续悬空。
2. `NativeStructureMiningPolicy` 的受审计方块白名单遗漏 `IRON_BLOCK`。失败物理状态本身不禁止对最后发布姿态进行静态挖掘验证；不能把此处不可破坏归结为同一失败标记。

实现：

- `StructurePhysics.recoverFailedRegion(host, regionId)` 暴露整组恢复入口，返回 RECOVERED / WAITING / REJECTED。所有已保存成员必须完成正常加载，且存储没有隔离或待恢复事务。
- 在原最后发布姿态构建未发布的新求解器，校验全组几何、地形、时间倍率、区域容量和邻区占用，清零线速度及角速度。首选纵向 24 格、水平按整组实际范围每侧预留 3 格（各轴不超过 24）的区域，保留下落和旋转空间，同时避免无谓占满邻区可用空间；完整立方体和旧边界作为兜底。结构 ID、region ID、slot、姿态、方块和物品不变。
- 候选完整构建后才严格原子替换 checkpoint，再发布新区域。原子替换失败时保留原文件和内存记录，不使用删除原文件再移动的后备路径。
- 启动时只对启动前已失败的区域排队恢复：前 30 次每 20 tick 重试等待状态，之后降低至每 200 tick，保留玩家较晚走近时的恢复机会，不强制加载区块。拒绝会记录原因并退出队列；运行中发生的新失败不会进入无限重建循环，公开入口保留显式重试能力。
- H 的两次区域跟随重定位均失败时，释放当前租约并清除线速度、角速度，再结束会话；普通结束路径继续保持原惯性契约。
- 仅将铁块补入旧后端受审计破坏白名单；仍保留工具、原版正确工具掉落判定、挖掘进度、耐久、方块实体与流体限制。

验证已完成：

| 检查 | 结果与证据 |
| --- | --- |
| `test build -DisDev=true` | 成功；2,830 项主测试、104 项 editorTest、16 项 sableCompatTest 均无失败；`build/frozen-iron-final-dev.log` |
| `build -DisDev=false` | 成功，发布包 `build/libs/academy-26.2.0-0.0.4-alpha-release.jar`；`build/frozen-iron-final-release-motion.log` |
| `nativePhysicsTest -DisDev=true` | 27 项真实 FFM/Rapier JVM 测试、13 项 Rust 测试通过；`build/frozen-iron-native-test.log` |
| `runGameTestServer -DisDev=true -PacademyGameTests=academy:native_structure_mining_policy` | 真实注册表环境下的铁块挖掘策略检查通过；`build/frozen-iron-dev-checks.log` |
| r3 新副本首次进入，仅运动与保存 | 两组旧区域均自动重建；目标实际自然下落 4.723 格并落稳，冲量推动 0.770 格；保留铁块、保存 checkpoint、正常退出；`build/frozen-iron-final-release-motion.log` |
| r3 同副本重进并挖掘 | 启动正常 `Resumed 2 saved physics region(s)`；目标再次受冲量移动 0.340 格；真实生存左键长按完成挖掘，服务端进度达到 1.0，恰好一个铁块掉落，铁镐耐久消耗 1，末格投影消失；邻区未再次失败；`build/frozen-iron-final-reload-mining.log` |

客户端入口均为 `runClientDev -DisDev=false`，使用与原运行一致的 release 依赖选择，未加载 Sable。`LegacyFrozenIronClientSmoke` 只允许在 `legacy-frozen-iron-smoke-*` 世界副本内运行；会改变副本玩家位置、飞行与游戏模式，借用快捷栏槽位时将原整栈物品移到背包空位，不删除它们。测试夹具不进入发布包。原 test 的 164 个数据文件仍与诊断前 SHA-256 一致。

本轮实际发现并修正的验证问题：

- 新提交事务测试最初将 NBT 再次归一化产生的四元数 1 ULP 差异判为失败；磁盘重读旋转改用 `1e-15` 容差，内存记录、回滚文件字节、其余字段继续严格比较。
- r1 客户端在运动检查后因为快捷栏全满而主动失败；修正的是测试夹具，没有绕过生产挖掘流程。
- r1/r2 最初使用完整 24³ 首选边界，邻区 `9060…` 的合欢树旋转到 `maxZ≈144.016` 时越界。其整组实际范围远小于 24，但边界被铁块区域的保留范围锁住。紧凑水平规划后，r3 两阶段均未再出现该失败；增加了实际双区域规划回归测试及客户端邻区状态断言。

复测启动（目录必须是重新准备的原 test 副本，先运动保存，再对同副本执行完整检查）：

```powershell
./gradlew.bat runClientDev -DisDev=false -I validation/native_structure/legacy_frozen_iron.init.gradle `
    -PacademyLegacyFrozenIronDirectory=run/legacy-frozen-iron-smoke-next `
    -PacademyLegacyFrozenIronMotionOnly=true
./gradlew.bat runClientDev -DisDev=false -I validation/native_structure/legacy_frozen_iron.init.gradle `
    -PacademyLegacyFrozenIronDirectory=run/legacy-frozen-iron-smoke-next
```

边界与尚未覆盖项：

- 旧后端仍是有界区域，不等于已实现无限世界迁移。通用无约束运动越界仍需独立扩展。
- 恢复尊重尚未调整的邻区保留范围，不重排其他区域；不同恢复顺序保证布局合法，但不保证获得相同的运动余量。
- 本轮未重新执行所有 H/R/喷射与精密节点组合，也未完成 H 双次重定位失败分支的专项游戏内故障注入。当前实机证据覆盖已确认旧铁块的启动恢复、自然下落、公共冲量控制、存档重载与实际挖掘。
- 本次不能据此宣称新 SubLevel 与 Sable 全面的性能或碰撞等价；原 G1 非法对象引用的写入者仍未定位。
