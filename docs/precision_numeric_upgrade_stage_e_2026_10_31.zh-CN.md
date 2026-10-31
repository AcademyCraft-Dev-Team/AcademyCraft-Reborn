# 精密操作 E：数值节点的可选等价升级

基线 `195ed8c6`。用户明确要求进入 E 阶段，本批从已核对的旧数值算术、比较节点开始；D 的复杂多体正向验收、紧凑布局和多人压力仍保留，不能据此关闭。

## 范围与等价边界

支持 29 个旧节点一对一替换成既有统一节点：三种数值类型的 15 个比较，以及 14 个算术。节点 ID、程序 UUID、名称、布局、边及边的顺序保持；不折叠数据子图，不合并动作，不减少 VM fuel，也不改变每动作数据缓存、随机数或世界查询时点。旧运行 ID 和旧图执行器继续存在，程序打开、同步、编译和导入均不自动触发本次升级。

| 旧路径（`academy:program/core/`） | 新路径和配置 | 本批处理 |
| --- | --- | --- |
| `logic/{integer,big_integer,float}/{equal,less,less_equal,greater,greater_equal}` | `logic/numeric/compare`，原数值域为 `type`，原运算为 `operator` | 15 个候选 |
| `logic/{integer,big_integer,float}/{add,subtract,multiply,divide,modulo}` | `logic/numeric/arithmetic`，同上 | 14 个候选，排除旧整数除法 |
| `logic/integer/divide` | 无无条件等价替代 | 保留：`MIN_VALUE / -1` 在旧节点回绕，统一节点拒绝溢出 |
| 旧常量、心理筛选/集合、随机节点、物理控制动作 | 不在本批映射 | 保留原行为，不从名称推断等价 |

比较使用同一数值比较语义，浮点 `-0.0` 与 `+0.0` 不被悄悄合并。浮点除零/对零取模沿旧规则产生非有限结果错误；不改成新的除零诊断。整数加减乘保留精确溢出检查，大整数取模仍为 `remainder`，不换成只接受正模数的 `mod`。

只有原版本、空配置、内建类型和内建执行器一致、解析后端口/作用域/纯度一致的节点可升级。带未知字段、未知 schema 或被扩展改变含义的节点保留；整图编译有效才允许应用。已保留的未知节点不会为使升级通过而被删除。

## 用户操作与恢复

编辑器右上角新增 `U` 工具，点击“升级兼容数值节点”先显示逐节点替换和保留原因。主行使用本地化数值类型/运算名，不要求理解原始类型 ID。确认后先在当前游戏目录保存带原图和候选图的本地备份，再应用到草稿；文件失败不应用，不静默覆盖旧备份。草稿支持一次撤销，用户继续通过原保存操作和修订冲突检查写回服务端。取消预览保留旧图，不发送保存请求。预览只缓存可重建的文本，按方案、错误、语言、宽度和扩展版本失效，不逐帧重建所有说明或保留绘制资源。

备份原图是当前编辑器草稿，不冒称为此前打开图时尚未经过旧迁移/默认配置整理的服务端原始字节。应用前重检当前草稿、槽位、修订、扩展及候选，且检验编辑器重新构造不会额外改写候选。

备份位于当前游戏目录 `academy/program-upgrade-backups/program-upgrade-<随机UUID>.json`，单文件上限 512 KiB、目录最多 128 项/32 MiB，达到上限时拒绝新升级并保留旧图，由用户整理；不会自动删除旧备份。恢复时打开 JSON，复制 `original_clipboard` 的完整文本值，通过原导入工具 `I` 放回草稿，再手动保存。新文件以 `CREATE_NEW` 写入并强制落盘；字面剪贴文本不转义 Base64 尾部 `=`。撤销压入冻结的原图快照，精确 codec 字节比较避免大整数和 Gson 数值相等规则掩盖改写。

## 测量与验证

主测试 **3494**、Sable 专项 **49**、编辑器专项 **104**，合计 **3647**，全部无失败/错误/跳过。最终双语客户端与付费动作服务端专项通过；双构建产物和最终完整性证据见[机器报告](../validation/precision_nodes/precision_batch_e_validation_2026_10_31.json)。

基线揭示统一算术/比较的配置只有两个不可变枚举、实际配置输入为空，但每次求值仍编码配置、反射记录字段并重建同一 schema。本批仅为这两个精确类型显式声明固定配置，编译照常校验，执行直接使用已解码配置；其他类型、插件及动态配置端口保留原路径。29 个旧别名的运行注册委托同一数值实现，绑定配置只在注册时构造；旧整数除法仍独立。

使用 JBR `25.0.3+1-b329.124`，每场 128 次预热、128 个样本，每样本 4 次真实 VM 会话；2 节点无算术对照，32/128 节点分别测整数、大整数、浮点加法 DAG，旧/统一图共 14 场。包含 `ProgramVm`、配置解析、实际注册执行器和会话创建，编译、正确性断言、签名及 JSON 写盘在计时外。测量是 `nanoTime` 墙钟和当前线程 Java 分配，非 CPU 耗时。

| 128 节点统一图 | 基线 P95（µs） | 优化首轮 / 复测 P95（µs） | 基线分配 P50（B） | 优化首轮 / 复测分配 P50（B） |
| --- | ---: | ---: | ---: | ---: |
| 整数 | 329.625 | 81.300 / 63.425 | 416,256 | 213,568 / 213,672 |
| 大整数 | 262.450 | 53.825 / 48.525 | 423,312 | 220,824 / 220,760 |
| 浮点 | 356.475 | 65.825 / 54.825 | 418,384 | 215,896 / 215,768 |

两轮优化图与旧图的分配量接近；旧专用图尾延迟和小图数据有波动，尤其第一个 2 节点对照仍受进程预热影响，不宣称所有旧图加速、稳定 TPS 或多人吞吐提升。首轮优化为 `r1`，正式复测为只运行同一 benchmark 的 `r3`；与核心测试同进程的 `r2` 留在 build 辅助记录，不作为表内同配置复测。三份正式原始报告以 `precision_e_numeric_{baseline,optimized,optimized_repeat}_2026_10_31.json` 保留，不覆盖基线。

8 项迁移合同测试包含全部 29 映射、39 种统一算术/比较配置 schema、原图保真与拒迁反例；6 项独立 VM 测试对照固定预期输出，另覆盖溢出、除零、非有限值、正负零、大整数与负余数、真实时钟跨 WAIT 重读、真实随机数锁存及错误源节点。未以两个共享实现互相比相同替代独立预期。benchmark 额外检查精确 fuel 边界、少 1 fuel 后续跑、无付费动作和结果签名。

8 项备份测试覆盖精确 codec/导入恢复、未知配置、大数、节点/边/布局、创建不覆盖、I/O 失败、目录容量、身份与大小限制。真实服务端 `paid r2` 的四案分别让旧/新浮点乘法输出实际驱动未元分解：均毁掉同一石头、CP 消耗均为 `7.142853`（当前技能标定/浮点存储下实测），零 CP 均在动作节点 `5` 以 `ACTION_CONDITION_FAILED` 拒绝，方块和 CP 不变。只证明这个真实付费动作对照，不冒称全部 29 运算都是世界动作。

最终 `zh/en r2` 各六项实机检查：请求旧图后仍原样且执行得 `18`；预览/保留/备份应用只改草稿，保存包数仍为 0；一次撤销/重做恢复完整图；手动保存修订加 1 且执行仍得 `18`；备份 `original_clipboard` 经普通导入恢复；恢复旧图再保存后服务端保持原图。共六张预览/应用/恢复截图已审查。只使用副本世界，148 个源测试世界文件哈希核验；没有用单个集成客户端代替多人 WAN 测试。

早期失败已保留：`core-optimized-r2` 使用了不在 init 脚本白名单内的阶段名，改正确参数后 `r2b` 通过；UI 初编译两轮修正 MC GUI accessor 和旧诊断枚举名；`full-paid-r1` 的唯一主测试失败是新增翻译键未排序，另有新 GameTest 复用相同 Runnable 作为延迟任务键后被原版清理逻辑移除，导致超时且无 raw。翻译排序及每轮独立回调修正后，主测试与 `paid r2` 均通过，没有延长原超时或降低断言。首轮双语实机通过后，又把整数除法保留理由改为准确的特殊溢出说明，并完成最终双语复跑。

复跑入口（JBR 25；使用新的唯一输出名/隔离目录，不覆盖基线）：

- 单测及专项：`gradlew.bat test sableCompatTest editorTest -DisDev=true`；双构建：`build -DisDev=true`、`build -DisDev=false`。
- VM 测量：`test -DisDev=true --tests org.academy.internal.common.ability.program.ProgramNumericUpgradeBenchmark -I validation/precision_nodes/precision_numeric_vm_benchmark.init.gradle -PacademyNumericUpgradeStage=optimized -PacademyNumericUpgradeReport=build/<新文件名>.json`。重跑当前代码不能伪标为旧基线。
- 真实付费动作：`runGameTestServer -DisDev=true -I validation/precision_nodes/precision_numeric_upgrade_gametest.init.gradle -PacademyGameTests=academy:precision_numeric_upgrade_equivalence -PacademyNumericUpgradeRun=<唯一名>`。
- 真实客户端：先 `validation/native_structure/prepare_default_skill_smoke.ps1 -Name precision-numeric-upgrade-<唯一名> -Fresh`；仅改副本 options 的 `lang:zh_cn` 或 `lang:en_us`、`guiScale:2`、`tutorialStep:none`。再 `runClientDev -DisDev=true -I validation/precision_nodes/precision_numeric_upgrade_client.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-precision-numeric-upgrade-<唯一名>`。
- 证据复验：`python -B validation/precision_nodes/verify_numeric_upgrade_reports.py`。该脚本受既有 validation Python 忽略规则覆盖，提交必须显式包含；构建、运行目录和截图不提交。

开发/发布两种构建均成功；最终 JAR 为 `build/libs/academy-26.2.0-0.0.4-alpha-{dev,release}.jar`。开发包 SHA-256 为 `39223A4F9D77985C4A4F54B27F32DFFC4123C137D9A27E4F73A8F368C2D3658C`，发布包为 `EF85FC2D30AD7412DD04E0A79C42F79201F2ED48349AC495AAECC4BA0B6A26E8`；包内关键类/语言与冻结输出一致、实机夹具未入包的检查记录在机器报告中。

## 接续限制

本批不自动升级旧整数除法，不合并带副作用的能力动作，不扩大类型转换或取消权限检查。D 后续和既有物理化、混装、完整 I/O 故障矩阵维持原待办。既有 `native/academy_physics/src/lib.rs` 改动排除。

下一步如继续 E，应先审计其他候选的真正差异并记录反例：旧布尔常量的动态 `value` 端口与统一布尔常量不同；三种数值常量虽可推断数值端口，编码和解析开销仍须单独证明。心理旧筛选的对象域、血量比例、顺序/随机与权限尚未证明等价，不沿本批 29 白名单推断可迁移。原 342 节点库存和 D 新增取样节点均不变，本批没有删除运行 ID，也没有减少图的节点数或必需操作。

本轮只完成 E 的首批数值家族，不宣称 E 全部合并完毕。复杂物理模板正向矩阵、缺块/重载/成员失效组合、真实多人、世界预检与费用估计、旧漏绘和紧凑图布局继续按 D/C 专项处理；本批截图保留当前初始缩放下的视口裁剪，不把它视为旧漏绘已解决。用户已明确确认本批 29 个相关文件的本地 Git 提交，提交信息为“新增精密数值节点等价升级与运行优化”，以包含本记录的提交为准；既有 Rust 改动及构建、运行产物排除。
