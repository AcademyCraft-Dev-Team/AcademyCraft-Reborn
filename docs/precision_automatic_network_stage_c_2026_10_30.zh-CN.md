# 精密操作 C 阶段续批：多人自动触发与网络

从 `c701bd9a` 接续。用户要求先测量多人自动触发和网络开销，再按瓶颈优化。本轮已完成测量、优化与必要验证，用户已明确确认本批 34 个相关文件的本地提交，提交信息为“优化精密自动触发解码并完善网络测量”，以包含本记录的提交为准。[机器报告](../validation/precision_nodes/precision_batch_c2_validation_2026_10_30.json)保存检查结果、文件清单与哈希。

## 测量边界

- 自动负载使用实际注册进 `PlayerList` 的 `ServerPlayer`、自然玩家 tick、生产触发分派、准入、执行桥、查询、共享变量事务和挂起调度。连接使用 Netty `EmbeddedChannel`；这是服务端负载基准，不是 32 个完整 Minecraft 客户端或广域网测试。
- 普通矩阵为 1 / 8 / 32 玩家 × 每人 1 / 4 个图 × 即时完成 / WAIT 4 tick。图实际查询周围实体，并在事务中写入查询结果和执行次数。另用 32 × 9 个长等待图验证全服 256 挂起会话边界。测试不冒充物理技能、Sable 混装或付费动作的压力测试。
- 每普通场景预热 32 tick、采样 128 tick，再单独开启 profiler 16 tick。耗时采样虽关闭 profiler，但仍开启专用计量器，包含线程 CPU / 分配采样的扰动；不是生产默认关闭计量时的零观测成本。CPU、墙钟和 Java 线程分配分开记录，嵌套区段不能相加。
- 应用 CODEC 字节、Misaka 注册 ID 封装字节分别记录。后者不包含 Minecraft packet ID、长度帧、压缩、加密或 TCP/IP。真实单客户端使用集成服的本地传输，验证实际收发、生产 handler 与客户端状态，不据此推算网络带宽上限。
- 首轮注册模拟玩家缺少 NeoForge 配置同步登记，服务端在有效采样前退出；已作为测试夹具初始化问题处理，失败轮不纳入性能对比。正式采样使用新隔离目录。

## 必须保持的执行规则

每玩家每世界 tick 最多四次自动执行尝试，移动、循环、生命值等事件共用该预算；成功消费预算后、调用执行桥之前推进槽位轮转，执行桥拒绝也不会撤回这次推进。挂起中的同槽图不重复启动；预算耗尽时不消费生命值阈值边沿；即时失败循环保持 100 tick 冷却。会话上限为每服 256，拒绝新会话不得挤掉已有会话。世界数据、随机数、共享变量和行动费用不能跨动作缓存。

以下是现有边界，不能把本轮测试通过写成已经修复：LOOP 先于 HEALTH 分派，四个每 tick 循环可能持续耗尽预算；健康锁存早于编译和挂起会话检查；自动延迟失败与即时失败的冷却不同；VM 完成轨迹先于事务最终提交，取消和到期也不保证有相同的终态轨迹；256 数量上限不等于全服 tick 燃料公平预算。

## 基线与优化决策

有效自动基线为 `r4`，13 场全部通过，见 [原始报告](../validation/precision_nodes/precision_c2_automatic_baseline_2026_10_30.json)。前几轮只修测试初始化与观测假设，没有把失败轮当作基线。GameTest 服会加速推进 tick，这里的“每 tick”不是实时 20 TPS 的吞吐证明。每场不同玩家安装相同的程序书，后续缓存收益包含相同内容的复用；不能外推任意不同大图的缓存命中率。

32 玩家 × 每人 4 图的基线：

| 路径 | 自动分派区段 P95 | 程序书区段 P95 | 自动分派平均 Java 分配 / tick | 128 tick 结果包 / 应用字节 |
| --- | ---: | ---: | ---: | ---: |
| 即时完成 | 13.8858 ms | 3.1994 ms | 27,196,216 B | 128 / 10,240 B |
| WAIT 4 | 4.7038 ms | 3.6496 ms | 14,986,648 B | 4,224 / 372,480 B |

WAIT 的自动分派区段不包含另计的 scheduler 续跑区段；不能凭这两行宣称等待图的总执行成本更低。即时场程序书读取总分配为 1,556,480,000 B / 128 tick，约占分派路径 45%；其中 CODEC 解码占 1,447,165,952 B，迁移只有 9,371,648 B。因此优先缓存纯解码结果，保留每次类别、版本、槽位检查及图迁移。

网络 [独立基线](../validation/precision_nodes/precision_c2_network_baseline_2026_10_30.json)覆盖 9 类消息 × 应用 CODEC / Misaka 封装两层。代表图有 128 节点、256 边，编码 21,360 B；十槽程序书 213,465 B。这是节点和边数量边界，不是所有可能配置下的字节上限。等待反馈为 56 B，应用 CODEC 往返 P50 为 1.8 μs；十槽同步应用负载为 213,554 B，Misaka 封装往返 P50 为 95.4 μs，分配 P50 为 1,166,928 B。微基准包含编码、解码与夹具调用，不是单向真实网络时延。

本轮不改变轨迹发送、节流、协议或完整书同步：变化中的 WAIT 图每次都有新的计数输入，严格去重应保留全部结果；大书同步发生在请求/保存时，没有证据表明它是本轮每 tick 的主瓶颈。稳定且完全相同的自动成功轨迹去重留作后续单独自然负载测试，不能拿本次动态轨迹的数据替它宣称收益。

原始自动报告的 `network_scope` 文字仍沿用早期“re-encoded”表述；有效 `r4` 的实际捕获已改为读取真实发送包装体的只读副本，额外观察解码不进入计时/网络计数，Misaka 注册 ID 长度另计。该文字勘误不改写原始测量值。

## 实施与复测

`ProgramStoredBookCache` 仅保存成功的 Base64 / `ProgramBookCodec.decode` 原始结果，以完整存储字符串精确匹配，最多 64 项、4 MiB 保守估算权重，LRU 淘汰。超预算及失败解码旁路。命中后仍执行类别、schema、槽位检查和当前节点 lookup 下的迁移；能力权限、旧心理技能迁移副作用、触发预算、随机数和世界查询均不缓存。缓存没有玩家、世界、会话引用，随现有服务端停止清理。经 codec 解析的配置数值不可变，节点的构造和访问继续深复制配置容器。

独立单测验证 32 份 UUID / 名称不同、各四图的程序书可同时留存，权重估计为 3,827,392 B；这不是实测堆大小，也不是主世界矩阵已测试 32 份不同作品。更大的书或更多不同作品可能淘汰或旁路，不保证任何负载下都命中。

固定区段与计数默认关闭。自动计量正确恢复嵌套触发上下文；采样不可用时保留业务结果。网络计量包装原 CODEC，一次真实编码计一次字节，观察解码明确排除；同一嵌套 send / encode 耗时不相加。

[优化第一轮](../validation/precision_nodes/precision_c2_automatic_optimized_2026_10_30.json)与[第二轮](../validation/precision_nodes/precision_c2_automatic_optimized_r2_2026_10_30.json)均通过全部 13 场。追加第二轮是因为第一轮单人小图 P95 出现反向波动，没有选择性覆盖第一轮报告。

| 32 玩家 × 4 图 | 基线 | 优化第一轮 | 优化第二轮 |
| --- | ---: | ---: | ---: |
| 即时分派 P95 | 13.8858 ms | 9.6378 ms | 11.1559 ms |
| 即时分派平均 Java 分配 / tick | 27,196,216 B | 14,890,297 B | 15,099,192 B |
| WAIT 分派 P95，不含 scheduler | 4.7038 ms | 2.0622 ms | 1.9573 ms |
| WAIT 分派平均 Java 分配 / tick，不含 scheduler | 14,986,648 B | 835,224 B | 832,920 B |
| 程序书区段 P95，即时 / WAIT | 3.1994 / 3.6496 ms | 0.2139 / 0.1948 ms | 0.2076 / 0.1828 ms |

即时分派分配量下降约 44–45%，WAIT 分派下降约 94%；实时执行和 scheduler 的开销仍然存在。单人单图即时 P95 为基线 0.7102 ms、优化 0.8823 / 0.5713 ms，说明小负载尾延迟存在 JIT / 系统噪声，不能承诺所有场景延迟均下降，也不能把区段耗时换算成 FPS / TPS 增幅。全部普通场景的准入、挂起、完成次数、事务结果与查询结果签名保持一致；网络包数和字节保持一致。

网络[复测](../validation/precision_nodes/precision_c2_network_optimized_2026_10_30.json)的 18 个样本保持原 wire 字节合同；这轮没有网络编码优化，不能将重复测量的时延变化记作收益。自动/网络/客户端报告的对照脚本为 `validation/precision_nodes/verify_automatic_network_reports.py`，不使用不稳定的性能数值作为正确性断言。

## 验证与接续

主测试 3428、Sable 专项 49、编辑器专项 104 项，均无失败/错误/跳过；开发、发布两种构建通过。独立服务端语义测试通过十槽轮转、四次共享预算、健康锁存、100 tick 即时失败冷却及退出清理。最初的即时收包断言失败来自 Minecraft 本 tick 的 suspendFlushing；改用正常下一 tick 观察，没有强制改变业务发送时序。

真实客户端基线与最终轮均通过请求同步、保存、旧版本冲突、手动等待续跑、重复序号拦截、自动循环启停和打开真实编辑器九项检查，最终版本为 5；分别见 [基线](../validation/precision_nodes/precision_c2_client_baseline_2026_10_30.json)和[最终轮](../validation/precision_nodes/precision_c2_client_optimized_2026_10_30.json)。两张实际截图中的四节点、连线和文字已审查。本轮未再次验证十分钟大图或原存档漏绘，也未修改可见文本/节点目录。

复跑入口如下，使用 JBR 25，在仓库根目录运行；每次替换 `run_id`，禁止复用非空 GameTest 目录。机器报告保留本轮实际命令/日志。网络复测的 `optimized` 仅表示整批源码版本，不代表编码协议有优化。

```powershell
./gradlew.bat test sableCompatTest editorTest -DisDev=true
./gradlew.bat build -DisDev=true
./gradlew.bat build -DisDev=false
./gradlew.bat runGameTestServer -DisDev=true -I validation/precision_nodes/precision_automatic_performance.init.gradle -PacademyGameTests=academy:precision_automatic_benchmark -PacademyAutomaticStage=optimized -PacademyAutomaticRun=run_id
./gradlew.bat runGameTestServer -DisDev=true -I validation/precision_nodes/precision_network_performance.init.gradle -PacademyGameTests=academy:precision_network_benchmark -PacademyNetworkStage=optimized -PacademyNetworkRun=run_id
./gradlew.bat runGameTestServer -DisDev=true -I validation/precision_nodes/precision_automatic_semantics.init.gradle -PacademyGameTests=academy:precision_automatic_semantics -PacademyAutomaticSemanticsRun=run_id
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name precision-automatic-network-run_id -Fresh
./gradlew.bat runClientDev -DisDev=true -I validation/precision_nodes/precision_automatic_network_client.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-precision-automatic-network-run_id -PacademyPrecisionAutomaticNetworkStage=optimized
python -B validation/precision_nodes/verify_automatic_network_reports.py
```

本机 Gradle 进程沿用 `-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=512m -XX:TieredStopAtLevel=1`、`-Pkotlin.compiler.execution.strategy=in-process` 避免此前 JBR 构建进程问题；这不是修改游戏默认 JVM 配置。语义 `baseline-r3` 目录命名早于缓存集成通知，实际上已包含缓存，应归为首次优化后回归，不能作为未优化源码的性能基线。

下一轮先分解执行桥与实时查询的剩余分配，再补 32 份不同作品/多类别/真实客户端压缩网络及混装压力；有新数据再决定稳定轨迹去重。原节点与物理系统遗留事项保持在总接续记录，不因本轮通过而关闭。

本批不包含既有 `native/academy_physics/src/lib.rs` 改动及构建/运行产物；本地提交已获用户明确授权。
