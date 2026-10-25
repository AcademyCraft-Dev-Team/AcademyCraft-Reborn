# 物理结构回归的 JVM 启动配置接续

日期：2026-10-26。承接[路径破坏接续](structure_path_damage_2026_10_25.zh-CN.md)。审查基线为 `860558da`，本记录与前批路径功能同批提交；既有 `native/academy_physics/src/lib.rs` 改动不属于本批。

## 结论与修正

本机 JBR `25.0.3+1-b329.124` 的原默认启动发生过 GC 与 C1 编译线程中的原生访问冲突。关闭增强类重定义的对照完成，但重新开启的一次交叉复跑同样完成，因此现有证据不足以证明 `AllowEnhancedClassRedefinition` 是唯一根因，也不足以宣称 JVM 内部缺陷已修复。

项目此前在所有 NeoForge run 任务中无条件加入 `-XX:+AllowEnhancedClassRedefinition`。本机 `java -XX:+PrintFlagsFinal -version` 确认 JBR 自身的该项默认值为 false。普通游戏验证无需强制开启这个调试功能，现改为 Gradle 属性 `academyEnhancedHotSwap`：默认 false；显式 `-PacademyEnhancedHotSwap=true` 时保留旧的增强热替换能力。属性仅接受 `true` / `false`，拼写错误直接失败。所有客户端、服务端、数据生成和编辑器 run 使用同一设置，生成参数中只有一条该选项。

本次调整作用于 Gradle 启动的 JVM。JBR 版本、G1、`-Xverify:none`、Sable runtime pin、Rust/DLL、原存档均保持本轮对照条件，真实结构碰撞继续启用。Gradle 启动参数不写入发布 JAR，普通玩家的启动器参数由其启动器决定。

## 证据与覆盖

既有失败报告见路径接续：独立退出 1 次、混装球形回归 3 次。其中混装既有前几项断言通过后崩溃，也有尚未进入夹具时崩溃，不能把这些运行算成完整通过。

| 运行 | 增强类重定义 | 源码/夹具条件 | 结果 |
| --- | --- | --- | --- |
| `path-regression-no-hotswap`（上一轮） | off，临时 init 覆盖 | 原有代码，fresh 球形混装夹具 | 22/22、606 tick，正常退出 |
| `jbr26-off-2` | off，同一 init 覆盖 | 与上一轮相同源码和夹具 | 22/22、606 tick，正常退出 |
| `jbr26-on-4` | on，旧默认配置 | 上一项完成后切回，fresh 同夹具 | 22/22、606 tick，正常退出；这是间歇性的重要反证 |

日志为 `build/structure-jbr26-off-2.log` / `build/structure-jbr26-on-4.log`，参数快照为同名前缀的 `-vmargs.txt`。结果在 `run/native-sublevel-direct-smoke-<名称>/structure-damage-result.json`。这里的成功既不撤销旧失败，也不构成无限时间稳定性证明。

上游资料只作背景：[JetBrainsRuntime PR #588](https://github.com/JetBrains/JetBrainsRuntime/pull/588) 记录 JBR 25 中另一项增强类重定义相关能力修复的回移提案，其复现与本机 GC/C1 栈并不相同，不能据此给本机崩溃指定官方缺陷编号。决定本次默认配置调整的依据是本地启动参数与对照结果。

修改默认配置后的客户端验收：五轮均完整通过并正常保存退出，没有附加诊断 init 或显式覆盖属性。每次实际 VM 参数均核对为唯一的 `-XX:-AllowEnhancedClassRedefinition`。

| 运行 | 后端与夹具 | 场景数 | tick |
| --- | --- | --- | --- |
| `launch26-sphere-suite-a` | Sable + Create + 航空学混装，球形范围 | 22/22 | 606 |
| `launch26-path-native` | 独立本地 SubLevel，路径 | 28/28 | 763 |
| `launch26-path-suite` | 四模组混装，路径 | 28/28 | 763 |
| `launch26-sphere-native` | 独立本地 SubLevel，球形范围 | 22/22 | 606 |
| `launch26-sphere-suite-b` | 四模组混装，球形额外复跑 | 22/22 | 606 |

日志为 `build/structure-<运行名>.log`，启动参数为 `build/structure-<运行名>-vmargs.txt`，结果位于 `run/native-sublevel-direct-smoke-<运行名>/` 对应的 `structure-damage-result.json` 或 `structure-path-damage-result.json`。汇总 `build/structure-launch26-smokes.json` 同时检查退出码、case 数、JSON 通过标记及实际默认参数；其中 `DefaultFlag=true` 表示参数核对通过，增强类重定义本身为关闭状态。

客户端 `prepareClientDevRun -PacademyEnhancedHotSwap=true` 已确认生成唯一的启用参数，快照为 `build/structure-jbr26-optin-vmargs.txt`。这不包含人工 IDE 字段/方法热替换验收。

收尾检查已完成：

| 检查 | 结果与证据 |
| --- | --- |
| `test sableCompatTest -DisDev=true` 及两种构建中的编辑器测试 | 主测试 3,250、Sable 兼容 38、编辑器 104，共 3,392 项；失败、错误、跳过均为 0。Java 源码未再改变，部分任务复用 Gradle 缓存或已有结果，并非本轮全部重新执行。日志 `build/structure-launch26-tests.log`，XML 位于 `build/test-results/` |
| `build -DisDev=true` | 成功，14 秒；`build/structure-launch26-build-dev.log` |
| `build -DisDev=false` | 成功，34 秒；`build/structure-launch26-build-release.log` |
| 服务端、图编辑器、数据生成的默认参数 | `prepareServerCompatRun prepareGraphEditorRun prepareClientDataRun -DisDev=true` 成功；三个生成文件均只有一条关闭参数。日志 `build/structure-launch26-prepare-defaults.log`，快照 `build/structure-launch26-<runName>-vmargs.txt`。这里只验证生成参数，不代表实际启动这些程序 |
| 属性拼写反例 | `help -DisDev=true -PacademyEnhancedHotSwap=treu` 按预期拒绝配置，提示字符串不是布尔值；`build/structure-launch26-invalid-option.log` |

产物已核对包含本批 `StructurePathDamage`、`DamagePathRegion`、`LiveStructureDamage` 及 Sable domain 类。清单为 `build/structure-launch26-artifacts.json`：

| 产物 | 大小（字节） | SHA-256 |
| --- | --- | --- |
| [开发 JAR](../build/libs/academy-26.2.0-0.0.4-alpha-dev.jar) | 32,246,828 | `789F7B60BDB3E73A13F1B856DCED6BBD636CB33E960DF48EBF18ECAA41342A79` |
| [发布 JAR](../build/libs/academy-26.2.0-0.0.4-alpha-release.jar) | 24,775,727 | `C9910F2799A9F214244393D6CE082D4BE63D5B03E6A9AA39A9F16E3117C70E46` |

本轮只调整 Gradle 启动配置和文档，因此上述 JAR 与上一轮路径实现的产物哈希一致。五轮客户端总计执行 122 个场景，其中混装球形 22 项是额外复跑，不应计作新增独立用例。当前阶段已完成这些限定范围的验收；JVM 根因、人工 IDE 热替换、S4 首撞碎裂、发射账本及原生密集接触压力红测仍未完成。

用户已明确确认创建本批本地提交，范围为前轮路径功能及本轮启动配置、测试和文档，共 24 个相关文件；既有 Rust 改动和忽略目录中的运行产物不纳入。提交信息为“接入物理结构路径破坏并调整启动配置”，以包含本条记录的提交为准。

## 复跑

使用仓库 JBR 25 和 fresh 隔离副本，默认启动不再需要 `build/structure-path-no-enhanced-hotswap.init.gradle`：

```powershell
$env:JAVA_HOME='C:/Users/Dusk/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name launch-next-suite -Fresh
./gradlew.bat runClientSable -DisDev=true -PacademySableRuntime=suite `
  -I validation/native_structure/structure_damage.init.gradle -PacademyDamageRun=clientSable `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-launch-next-suite --console=plain
```

路径夹具换为 `structure_path_damage.init.gradle`，预期 28 项；球形预期 22 项。独立后端使用 `runClientDev`，去掉 `academySableRuntime` 和 `academyDamageRun` 两项。每次必须使用新目录，同时检查 JSON 与正常退出。

需要复现旧模式或 IDE 结构性热替换时追加 `-PacademyEnhancedHotSwap=true`。普通方块交互、技能节点及编辑器资源/图数据重载不需要这一 JVM 功能。后续若复现 JVM 崩溃，应保留 `hs_err`、实际启动参数、模组 pin、是否开始夹具及退出阶段；不要通过不断重试直到成功来注销失败。
