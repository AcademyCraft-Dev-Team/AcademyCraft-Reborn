# Sable 拆分来源与永久删除接续

从 `a6da5d18` 接续。范围是既有物理结构的生命周期补齐，不启用 S4 受控碎裂、发射奖励或非玩家破坏授权。

## 行为与边界

- 包装固定版本 Sable 的实际 `SubLevelAssemblyHelper.assembleBlocks`：调用前保存锚点所在 plot 的同宿主、有效父对象及已提交来源，调用成功后验证新子对象与父来源未变，创建 `SKILL_FRAGMENT`。普通新增通知发生得太早，不能用它推断拆分；`setSplitFrom` 在父体最后一块被搬走时会被 Sable 跳过，字段又会被清除，因此不能作为唯一继承证据。此作用域不使用全局待匹配队列，异常调用不会遗留下一次组装可误用的父来源。
- 子结构获得独立创建操作，继承根 lineage、拥有者、技能、源网格旋转和原静态化策略；直接 parent 指向本次父结构。连续拆分保持同一根来源，重复通知不重写。父结构退休不递归删除后代标记。
- 航空学等外部船只、无标记捕获结构及其拆分体不补技能来源。控制、旋转、投掷仍不授予创建来源。
- 未到周期保存的新拆分体可以先静止。自动归块检查点在确认 `SKILL_FRAGMENT` 后，通过 Sable 自己的保存/分配器取得存储指针，再按既有严格检查点强制持久化；不会手工分配槽位，也不会为普通无标记船只触发这条补偿。
- `UNLOADED` 保留来源。`REMOVED` 在后端删除前记录永久删除意图，待源存储清理与占用图持久化确认后，按原创建操作精确删除来源标记。已有自动归块事务继续由自己的日志负责，不重复登记。
- 待删除来源若在重启时重新激活，复用既有隔离：不进入结构查询、物理更新或自动归块，再按日志完成原删除决定。

来源账本只记录已观察到的创建事实，不搬运方块。Sable 自己的父/子内容保存没有被本轮改造成跨文件原子拆分事务。若进程在子结构保存前中断，可能留下无法解析到活体的来源条目；不能把“当前没加载”当成永久删除证据自动清除。来源写入失败会记录错误且不猜测/补造子标记，完整磁盘故障重放矩阵尚未完成。

## 文件与恢复约束

实现入口：

- `LiveStructureProvenance.commitFragment`：来源继承与幂等。
- `MixinSableProvenanceSplit` / `MixinSableProvenanceRemoval`：固定版本后端事件。
- `SableProvenanceRetirements`：每体有界、原子写入的永久删除意图，位置为世界 `data/academy/sable_provenance_retirements/*.dat`。
- `SableProvenanceLifecycle`：每 20 tick 最多观察 4 条、执行 1 次持久清理；不强载世界区块。
- `SableStructureTransactions.retireRemoved`：复用归块的源存储核对，但不再清除已释放、可能复用的 plot。存储槽已归其他 UUID 时，仅在旧 UUID 不在 live/holding 索引中时退休旧标记，不改写新体的槽、索引、删除队列或占用位。

空存储槽不能证明当前索引归谁：若旧指针仍存在于 holding 缓存或磁盘索引，保留意图等待，不能删除可能被其他体新认领的索引。源指针冲突、未知记录、损坏记录和 I/O 失败均保留证据；日志不健康时隔离已标记的 Sable 体。未保存体若重启后出现无法解释的活体，同样保留等待。不要通过删除 journal、清空全部标记或释放所有 occupancy 来“恢复”。

两条新 mixin 复用 `SableContainerMixinGate`：只对已核验的 `2.0.6-port.26.2.0.70` / SHA `21a22bcd6b0dd5bc742472e332f7e8a17dd83cbbd471ebe97087e153acc206ca` 生效。没有新增硬链接、改动外部 fork 或更新依赖 pin。默认仍关闭增强类重定义。

## 验证与复跑

使用仓库规定的 JBR 25。所有客户端均使用 `prepare_default_skill_smoke.ps1 -Fresh` 生成的隔离存档，不操作原 test/test2。

```powershell
$env:JAVA_HOME='C:/Users/Dusk/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name provenance-new -Fresh
./gradlew.bat runClientSable -DisDev=true -PacademySableRuntime=suite `
  -I validation/native_structure/sable_provenance.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-provenance-new --console=plain
```

真正中断恢复使用另一份新隔离存档，先以 `-PacademySableProvenanceMode=crash` 运行，预期 `Runtime.halt(86)`，Gradle 因退出码 86 报失败；再以同目录、`-PacademySableProvenanceMode=verify` 重启。不要重新复制该目录，否则不再是恢复测试。报告为该运行目录下 `sable-provenance-<mode>-result.json`，必须同时检查报告和正常退出；崩溃注入那次检查明确的退出码 86。

最终结果见 [结构化验证汇总](../validation/native_structure/sable_provenance_validation_2026_10_26.json)：

| 检查 | 结果 | 日志 |
| --- | --- | --- |
| Sable 生命周期最终矩阵 | 14/14，182 tick，正常退出；包含真实 plot 复用、旧槽内容核对、自动拆分、搬空父体与未保存子体自动归块 | `build/provenance-client-final-d.log` |
| 删除中断准备 | 6 项前置断言，14 tick；按预期 halt(86) | `build/provenance-final-crash.log` |
| 同存档重启恢复 | 2/2，19 tick，正常退出；父体不复活、嵌套子体来源保留 | `build/provenance-final-recover.log` |
| 独立后端归块回归 | 6/6，2084 tick，正常退出 | `build/provenance-rest-native.log` |
| 四模组混装归块回归 | 6/6，2084 tick，正常退出 | `build/provenance-rest-sable.log` |
| `test` / `sableCompatTest` / `editorTest` | 分别 3262 / 44 / 104，均 0 failure/error/skipped；专项可能重复执行部分测试，不合计为独立用例数 | `build/provenance-final-dev-build2.log` |
| `build -DisDev=true` | 通过，52 秒 | `build/provenance-final-dev-build2.log` |
| `build -DisDev=false` | 通过，40 秒 | `build/provenance-final-release-build.log` |

同修订、同配置的成功任务按仓库规范复用；构建含 UP-TO-DATE / 缓存结果，不代表每次重新运行全部检查。测试基线存档的 148 个文件哈希未变。发行包包含新生命周期类与 mixin、不含强制退出夹具或外部 Sable 类；既有 Rust 文件 SHA 保持 `0B172501A5DA088A1CDA8AD13A41C88005D64B8D62D5347103DB566F84AC3C70`。

产物：

- `build/libs/academy-26.2.0-0.0.4-alpha-release.jar`：24,799,191 字节，SHA-256 `86F13EA01FB982F74535EB2D23B14D23D9428879C46D76BE4D4FC9D1BB288C97`。
- `build/libs/academy-26.2.0-0.0.4-alpha-dev.jar`：32,270,292 字节，SHA-256 `F482863569B4DACA75CC18FD6B6E1BC4255800A43AB556E66807DF0AE6E18D73`。

本轮早期 a1 的卸载夹具会连带卸载 Sable 依赖链，随后错误地继续使用已卸载父对象；已将卸载验证移到永久删除之后。a2 通过 10 项，a3 通过 12 项，均是阶段证据。加入真实 plot 复用、存储槽核对和未保存子体归块后，final-b/c 暴露整父体搬空时跳过 `setSplitFrom` 的继承缺口，改为完整组装作用域；final-d 首次编译因旧版 `ChunkPos` 构造写法失败，改用 26.2 的 `ChunkPos.containing`。这些早期结果不替代最终矩阵。

JVM 新测试覆盖连续拆分的精确继承、日志重启、创建/指针不可替换、NBT 防变异、原子发布失败、损坏记录保留，以及未保存体证据要求。它们不等于完整 Sable 存储故障矩阵。

## 后续顺序

1. 在玩家混装存档完成键盘/蓄力/行走与三系精密节点人工验收，记录操控体验。自动夹具不是真人测试。
2. 对当前保守等待分支补故障注入，尤其是空槽仍有旧索引、holding/live 交错激活、写盘失败与长期大量结构；建立诊断入口后再考虑人工修复流程，不能凭缺席猜删标记。
3. 服务端持久发射账本和非玩家明确授权；再逐项迁移卫星/轨道等复杂来源破坏入口，随后开展 S4 接触汇聚、首撞一次和有限碎裂。
4. 原生密集接触压力红测与 JBR 进程崩溃根因继续独立处理。本批不修改既有 `native/academy_physics/src/lib.rs`、原生 DLL、GC 或 JBR。
