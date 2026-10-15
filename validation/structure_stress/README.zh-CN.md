# 结构系统性能压力测试工具（validation/structure_stress）

本目录提供结构系统（原生后端 / Sable 后端 / 客户端）的可复用性能压力测试与基线采集脚本。
所有探针都是 opt-in：只有带 `-I validation/structure_stress/<场景>.init.gradle` 启动时才会被编译并注册，
普通开发/发布构建完全不受影响（`validation/structure_stress/java` 由 init 脚本临时加入 `sourceSets.main`）。

## 目录内容

| 文件 | 作用 |
| --- | --- |
| `close-startup-dialogs.ps1` | 可复用模块：等待客户端窗口、自动关闭启动警告弹窗（"MC 未来废弃方法提示"/NeoForge `LoadingErrorScreen`），直到 `latest.log` 出现世界继续标志；记录 `dialog dismissed` 判定 |
| `run-client-stress.ps1` | 客户端基线：隔离存档副本 + quickPlay + 弹窗自动关闭 + 帧时间/载入/内存采样 |
| `run-server-native-stress.ps1` | 原生后端专服基线：捕获/抬升/发射/释放/槽位回收循环（含区域上限探测） |
| `run-server-sable-stress.ps1` | Sable 后端专服基线：多子级 + 直控租约循环 + 子级保存耗时 |
| `client_stress.init.gradle` | 客户端 run 注入（quickPlay、隔离目录校验、探针属性） |
| `server_native_stress.init.gradle` | 原生专服 run 注入（EULA/server.properties/新世界校验、原生库参数、探针属性） |
| `server_sable_stress.init.gradle` | Sable 专服 run 注入（同前，Sable runtime 由命令行固定） |
| `java/org/academy/validation/structure/` | 三个探针类（原生专服 / Sable 专服 / 客户端），由 init 脚本注入源集 |

## 前置条件

- 仓库根目录执行；JetBrains Runtime 25（脚本默认 `-PacademyStressJavaLauncher=jbr`，`shell` 可改用默认 JVM；Sable 专服必须 JBR）。
- 原生专服需要本机 Rust 工具链与既有产物：先 `. .\build\native-physics-p16-env.ps1`（脚本内部自动加载）。
- 客户端场景需要一份**存档副本**，先按下列方式复制（不要指向用户实际游玩存档）：

```powershell
New-Item -ItemType Directory -Path 'run\sable-compat\stress-client-rN\client\saves' -Force
Copy-Item -Recurse 'run\sable-compat\repro-crash-suite-r2\client\saves\新的世界 (32)' `
    'run\sable-compat\stress-client-rN\client\saves\新的世界 (32)'
```

## 快速开始

```powershell
# 客户端（弹窗自动关闭 + 1 个结构在场，采样 60 秒）
powershell -ExecutionPolicy Bypass -File validation/structure_stress/run-client-stress.ps1 `
    -RunSuffix r1 -SampleSeconds 60 -Structures 1

# 原生专服（30 次操作、2 个并发区域、至少采样 60 秒）
powershell -ExecutionPolicy Bypass -File validation/structure_stress/run-server-native-stress.ps1 `
    -RunSuffix r1 -Operations 30 -Regions 2 -Seconds 60

# Sable 专服（10 子级、30 次直控/发射循环、至少采样 60 秒）
powershell -ExecutionPolicy Bypass -File validation/structure_stress/run-server-sable-stress.ps1 `
    -RunSuffix r1 -Children 10 -Operations 30 -Seconds 60
```

每次运行必须使用**全新隔离目录**（脚本会拒绝已有结果/世界的目录）：
`run/stress-server-native-rN`、`run/sable-compat/stress-server-sable-rN`、`run/sable-compat/stress-client-rN`。
失败保留：所有原始日志与结果文件都不会被覆盖。

## 输出

| 路径 | 内容 |
| --- | --- |
| `build/stress-client-rN.log` | Gradle/客户端原始日志 |
| `build/stress-client-rN-driver.log` | 弹窗处理驱动日志（`DIALOG_SCAN/DIALOG_ACTION/DIALOG_RESULT`） |
| `build/stress-client-rN-summary.txt` | 结构化摘要 + `stress-client-result.txt` 全文 |
| `build/stress-server-native-rN.log` / `-summary.txt` | 原生专服日志与摘要（含 `stress-server-native-result.txt`） |
| `build/stress-server-sable-rN.log` / `-summary.txt` | Sable 专服日志与摘要（含 `stress-server-sable-result.txt`） |
| `run/.../stress-*-result.txt` | 探针原始结果（指标全集，保留在隔离目录） |

指标口径：

- 服务端：`tick_ms_avg/p50/p95/p99/max`、`mspt_avg`、`tps_avg`、各阶段耗时分布（原生 `capture_ms_*`/`recycle_ms_*`；Sable `capture_ms_*`、`lease_ms_*`、`release_ms_*`、`launch_ms_*`、`save_ms_*`，其中 lease/release/launch 是轻量 API 调用本身、不含阶段等待）、`heap_used_mb_avg/max`、区域/槽位/子级计数、`capture_retries`（仅 journal 类失败的有界重试计数）。
- 客户端：`load_ms`（JVM 启动→世界可用）、`operable_ms`（→可操作）、`frame_ms_*`（相邻 `RenderFrameEvent.Pre` 间隔，真实帧节奏）、`render_ms_*`（渲染段事件耗时）、`fps_avg`、`tick_ms_*`、`heap_used_mb_avg/max`。
- 早期运行（客户端 r1、Sable r3）使用旧口径：`frame_ms_*` 为渲染段耗时、Sable `lease_ms_*`/`launch_ms_*` 含阶段等待；新运行以 r3/r4 修订后的口径为准。

## 启动弹窗自动关闭（可单独使用）

NeoForge 在启动时会显示一个列出废弃/未来变更警告的 `LoadingErrorScreen`（用户提示为"MC 未来废弃方法提示"），它会阻塞 quickPlay 世界创建/载入。手动复现与处理：

1. 正常启动客户端（例如 `runClientSable ... -I validation/structure_stress/client_stress.init.gradle`），quickPlay 会停在警告屏幕；日志中世界创建不再前进。
2. 点击屏幕中下部的继续按钮（或按 Escape/Enter）即可继续；脚本等价地自动执行该步骤。

自动处理实现（`close-startup-dialogs.ps1`）：

- 轮询 Minecraft 主窗口，先用 `PostMessage` 发送 Escape/Enter 试探；随后按窗口客户区尺寸计算“继续（Proceed）”按钮候选点（`width/4 + 偏移, height - 偏移`），以真实系统输入点击（`SetForegroundWindow` + `SetCursorPos` + `mouse_event`），因为 GLFW 不响应 `PostMessage` 的鼠标消息；每轮动作都写入驱动日志。
- 一旦 `latest.log` 出现 `Preparing spawn area` / `logged in with entity id` / `joined the game` / `Saving chunks for level` 之一，即写入
  `DIALOG_RESULT dialog dismissed=true attempts=N evidence='<命中的日志行>'`；超时则 `dialog dismissed=false`。
- 单独使用：`Invoke-StartupDialogDismissal -ClientDir <客户端目录> -DriverLog <日志路径> -JoinWaitSeconds 600`（dot-source 本模块），
  或用 `-NoDialogAutomation` 做对照运行。
- 注意（2026-10-15 修复）：候选点表达式中 `$size.Height - 28` 必须写成 `[int]($size.Height - 28)`，否则 PowerShell 的逗号优先级会把数组字面量解析为“数组减 28”，导致点击坐标为空、弹窗永不关闭（客户端 r2 的 `NO_WORLD` 即由此产生）。

## 已知边界

- 弹窗定位基于窗口尺寸与键盘试探，不依赖窗口控件树（GLFW 窗口没有可枚举的子控件）；若屏幕布局未来改变，日志中的 `DIALOG_ACTION` 与截图可复核。
- 客户端探针在集成服务器上用生产捕获事务创建真实结构（提交后由后端持有，未在视野内物理化/渲染）；不发送合成键盘输入。
- Sable 专服探针的 Rapier 内部步进无法从外部单独计时，`save_ms_*` 是唯一可分的 Sable 原生库接口耗时（容器子级保存）。
- 服务端“程序图运行”（结构节点族）相位未纳入本探针：真实 `extract` 在专服走跨 tick PENDING 且不发布结构引用，跑通需要喷口/技能能量与玩家夹具；程序侧覆盖见 P30–P33 单测图验收与专服程序入口烟测。
- 已知生产问题（2026-10-15 已修复，根因/对照见 `docs/structure_skill_migration_handoff.zh-CN.md`《修复：事务日志原子替换偶发 AccessDeniedException》）：`StructureTransactionJournal` 与 `NativeStructureManifest` 的原子替换在 Windows 上偶发 `AccessDeniedException`（修复前连续捕获压力下约 2/108 次；Sable r2、原生 r5 及修复中间轮原生 r7 的日志保留）。生产侧现为 `DurableFile` 的 5 次有界重试 + 非原子兜底；修复后原生 r8（120 次操作）与 Sable r5 均通过且 `capture_retries=0`。探针仍保留“仅 journal 类捕获失败、每操作 ≤3 次”的有界重试与 `capture_retries` 计数作为观测项。
- 原始日志与失败运行目录一律保留，复核时可对照 `docs/structure_skill_migration_handoff.zh-CN.md` 的《性能压力测试工具与基线（2026-10-15）》章节。
