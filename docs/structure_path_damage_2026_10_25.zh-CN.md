# 物理结构路径破坏与射线截断接续

日期：2026-10-25。基线提交：`860558da 接入物理结构范围破坏并修复 Sable 分裂卸载`。本轮承接 S2 / C 线，新增路径选择与技能接线，复用上一轮的宿主保护、真实内容修改和掉落投射。当前为 EXPERIMENTAL，不代表 S4 首撞、碎裂或非玩家发射授权已完成。

2026-10-26 后续：增强类重定义已改为显式启动选项，当前配置的验收见[启动配置接续](structure_runtime_launch_2026_10_26.zh-CN.md)。下方保留 10-25 初验与崩溃证据，不能以配置调整后的成功删除旧失败。

## 行为与接入范围

`LevelUtil.destroyBlocksAlongPath*` 的服务端活动玩家路径统一进入 `StructureDamages.service().path(...)`。同一次攻击按距离合并已加载的宿主方块、本地 SubLevel 和 Sable 结构内容；旋转结构在其局部空间检查真实碰撞形状，空碰撞内容回退选择形状。空洞与台阶的空白区域不会仅因整体包围盒相交而被击中。返回距离始终属于宿主世界。

| 调用方 | 本轮行为 | 验证边界 |
| --- | --- | --- |
| 采矿光束 `MiningBeam` | 持续技能、公共采矿入口和精密节点共用路径服务；保留精准、时运、熔炼模式与采集者 | 双后端真实矿石输出；精密 `fireMiningBeam` 验证实际 runtime、电子束执行及基岩后矿石保留 |
| 高速电子束 `HighSpeedElectronBeam` | 消费前程和反射回程的实际破坏距离，实体伤害与视觉同步截断 | 真实精密采矿电子束；其他电子技能沿公共入口接入，未逐个模拟蓄力/按键 |
| 超电磁炮 `Railgun` | 保留 `canBlock=false` 的原穿透规则；预算或覆盖不足时截断实际攻击与显示 | 公共路径和截断逻辑验证；硬币/弹药、蓄力、CP 全流程待人工验收 |
| 波形粒子炮 `ParticleWaveCannon` | 前后程采用实际破坏距离，再更新视觉 | 公共路径、阻挡与反射截断验证；持续输入全过程待人工验收 |
| 电浆 `Plasma` | 原有 24 条爆破路径自动覆盖结构内容，保留无掉落/静默相邻处理 | 实际实体发射与爆破执行通过 |
| 矢量反射相关路径调用 | 通过原 `LevelUtil` 公共入口获得结构选格和同一输出作用域 | 公共路径和 `ResolvedLinearAttack` 前后程截断验证；未声称真实玩家反射输入验收 |

`ResolvedLinearAttack.limitOutboundLength` 在前程尚未到达反射点便受阻时撤销该回程；浮点长度舍入容差避免一条畅通射线意外丢失有效反射。`limitReturnLength` 只缩短回程。破坏距离在实体伤害执行之前生效。

## 公共接口与实现约束

- 新接口 `StructurePathDamage.Request/Result`，入口为 `StructureDamage.path`。DRAFT 默认仍返回 UNSUPPORTED；LIVE 才提供实现。调用必须位于服务端线程，主体必须是同一宿主中的在线存活 `ServerPlayer`，不使用假玩家。
- 这是一次同步攻击。查询、复核、修改及返回阻挡距离在本次调用完成；没有延迟射线、后续自动续跑或可重复应用的客户端计划。下一 tick 的持续光束属于新攻击。
- 形状保留旧路径语义：线段与按半径膨胀的选择盒求交；并非严格欧氏胶囊。线长上限 2,048 格，半径上限 32 格。零长路径检查原点。
- `DamagePathRegion.Cursor` 按进入距离优先二分整数格范围，每个工作单元至多拆成两个节点；斜长射线不遍历整块立方包围区。候选发现留一格裕量，覆盖栅栏等伸出所属格的原版形状；精确相交仍使用技能原半径，不扩大实际攻击。任意模组超过这一裕量的超大形状尚无完整索引保证。宿主和结构游标按同一进入比例合并，矿级不足的前方阻挡限制后方选择。
- 每次最多 32,768 个扫描工作单元、1,024 次修改尝试；同一玩家同一 tick 合计 65,536 / 2,048。最多发现 256 个相交结构，适配器另有限制。修改预算包含被保护拒绝的尝试；扫描预算包含游标内节点。形状依赖读取另限 128 次，且不能加载未知区块。
- 模拟查询不触发破坏/掉落事件。覆盖缺失、事务锁、查询期间内容失效、预算耗尽都返回保守的未完成边界；不能当作全程畅通。已知矿级阻挡可返回 COMPLETED 和短距离；BLOCKED/STALE 等代表未完整完成。
- 真正写入前后复核内容、方块实体身份、激活、姿态与修订。事件重入被拒绝。原版邻居更新已移除的空格可跳过，避免“草丛已经掉落，却把光束判作过期阻挡”。Sable 在最后一格清空时提前 `markRemoved`，因此新增 `DamageDomain.retiringEmptyCell` 只读证据：必须仍是容器中同一对象、相同原点和姿态、空包围区、已加载格确为空且无 BE。域已释放、被替换或非空时均不能用旧地址作畅通证据；原生实现默认拒绝该特例。
- 共用 `LevelUtil.destroyBlockForAbility`、`AbilityBlockDrops` 与 `NativeStructureBreakDrops`。自定义采矿处理器收到真实 storage level/格坐标，玩家始终留在宿主；箱子物品、事件修改掉落、经验按现有作用域投射一次。变更后的结构几何在批次末合并刷新，宿主粒子使用世界坐标。
- 预算不能约束其他模组回调或原版邻居更新自行触发的任意级联工作。回调已有副作用不回滚；异常沿原恢复记录保留输出证据，不能自动重放。客户端及无玩家的旧路径没有由本轮授予新的结构破坏权限。

## 夹具与复跑

`StructurePathDamageSmoke` 在 fresh 隔离的 test 副本启动真实客户端与集成服务器。28 项：铁块、无掉落、模拟、空洞、台阶空区、旋转台阶、旋转箱子、结构矿级阻挡、宿主阻挡、混合内容、保护取消、保护回调替换、掉落替换与经验、回调重入、载荷锁定、精准、时运、熔炼、电浆、零长路径、长宽路径覆盖/预算截断、反射前程/回程截断、1,024 次修改上限、静默清流体、真实精密采矿节点、支撑移除后的植物、同一射线命中宿主/结构栅栏高于所属格的碰撞部分。

Sable 捕获白名单不包含矿石。夹具先捕获允许的普通铁块，再通过真实内容写入与变更刷新在结构内放矿石；这用于测试后续放置/外部结构内容，没有放宽生产捕获白名单。旋转测试只在夹具中设置姿态，不把测试注入方式用于技能。

```powershell
$env:JAVA_HOME='C:/Users/Dusk/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name path-next-native -Fresh
./gradlew.bat runClientDev -DisDev=true `
  -I validation/native_structure/structure_path_damage.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-path-next-native --console=plain

./validation/native_structure/prepare_default_skill_smoke.ps1 -Name path-next-suite -Fresh
./gradlew.bat runClientSable -DisDev=true -PacademySableRuntime=suite `
  -I validation/native_structure/structure_path_damage.init.gradle -PacademyDamageRun=clientSable `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-path-next-suite --console=plain
```

必须检查 `structure-path-damage-result.json` 的 `passed` 和具体 case，不能只看 Gradle 退出码。`scan_budget` 证明未知覆盖或扫描上限不会报全程畅通，不单独证明每次都恰好耗尽扫描数；`change_budget` 则精确检查 1,331 格中破坏 1,024 格、保留 307 格及路径截断。

最终结果（2026-10-25，未创建本批 Git 提交）：

| 检查 | 结果 | 本地证据 |
| --- | --- | --- |
| 独立路径 `runClientDev` | 28/28，763 tick，正常保存退出 | `run/native-sublevel-direct-smoke-path-native-final3/structure-path-damage-result.json`、`build/structure-path-native-final3.log` |
| 四模组混装路径 `runClientSable` | 28/28，763 tick，正常保存退出，使用原启动配置 | `run/native-sublevel-direct-smoke-path-suite-final3/structure-path-damage-result.json`、`build/structure-path-suite-final3.log` |
| 独立球形回归 `runClientDev` | 22/22，606 tick，正常保存退出 | `run/native-sublevel-direct-smoke-path-regression-native/structure-damage-result.json`、`build/structure-path-regression-native.log` |
| 混装球形回归，原启动配置 | **未完成：三次 JVM 原生崩溃，无最终通过回执** | `build/structure-path-regression-suite.log`、`suite2.log`、`suite3.log`，完整路径见下文 |
| 混装球形回归，仅关闭增强热替换 | 22/22，606 tick，正常保存退出；只作对照，不替代默认配置稳定性验收 | `run/native-sublevel-direct-smoke-path-regression-no-hotswap/structure-damage-result.json`、`build/structure-path-regression-no-hotswap.log` |
| JVM `test sableCompatTest -DisDev=true` | 主测 3,250、兼容 38，失败/错误/跳过均为 0 | `build/structure-path-final-tests.log`、`build/test-results/test`、`build/test-results/sableCompatTest` |
| 开发版 `build -DisDev=true` | 成功，含 104 项编辑器测试及打包反例检查 | `build/structure-path-build-dev.log` |
| 发布版 `build -DisDev=false` | 成功，含测试与兼容打包检查 | `build/structure-path-build-release.log` |

汇总 `build/structure-path-validation-summary.json` 明确区分默认配置失败与诊断配置通过。主测、兼容和编辑器共 3,392 项 JVM 测试，未将此前可选 nativePhysicsTest 的旧报告算入本轮。

发布 JAR：`build/libs/academy-26.2.0-0.0.4-alpha-release.jar`，24,775,727 bytes，SHA-256 `C9910F2799A9F214244393D6CE082D4BE63D5B03E6A9AA39A9F16E3117C70E46`。开发 JAR：`academy-26.2.0-0.0.4-alpha-dev.jar`，32,246,828 bytes，SHA-256 `789F7B60BDB3E73A13F1B856DCED6BBD636CB33E960DF48EBF18ECAA41342A79`。已逐包核对新 API、路径游标、共享服务和 Sable 域类存在，清单为 `build/structure-path-artifacts.json`。

早期 `path-suite-01` 在矿石捕获白名单检查处失败，不能当作通过证据；`path-suite-02` 为添加植物回归前的 26 项通过结果。`path-suite-final` 的第 27 项发现 Sable 空体提前退休导致误截断，`path-plant-diag` 确认源格和草丛均已为空但返回 STALE；修复后的植物案例还验证错误姿态和已释放 plot 不可用作空格证据。功能矩阵以表中修复后的运行结果为准。

退出异常需继续跟踪：`path-native-final2` 的 27 项断言已全部通过，但保存退出时 JBR 25.0.3 的 `GC Thread#21` 发生 `EXCEPTION_ACCESS_VIOLATION`，帧为 `jvm.dll!G1ParScanThreadState::do_copy_to_survivor_space`；该次 Gradle 退出失败，不能记为完整通过。报告在 `run/native-sublevel-direct-smoke-path-native-final2/hs_err_pid24472.log`，日志 `build/structure-path-native-final2.log`。相同 JBR/GC/物理后端配置的新副本 `path-native-final3` 完成含栅栏回归的 28 项并正常保存退出（763 tick）；没有改 GC 或移除物理以绕开问题。崩溃发生前报告记录了 DLL 卸载，但这不足以确定因果，根因仍未关闭；不得把一次复跑成功写成原生稳定性问题已修复。

随后混装的球形回归 `path-regression-suite` 在前 5 项通过、尚未结束时也发生 G1 原生访问冲突：`GC Thread#5`，帧为 `OopOopIterateBackwardsDispatch<G1ScanEvacuatedObjClosure>`。报告 `run/native-sublevel-direct-smoke-path-regression-suite/hs_err_pid56780.log` 和日志 `build/structure-path-regression-suite.log` 保留；该次没有最终通过回执。该报告未检出 Academy 物理 DLL 的加载/卸载记录，现有证据不能将两次崩溃唯一归因于独立物理后端，也不能证明它们与本轮 Java 修改无关。继续使用原配置 fresh 复跑，失败不能从稳定性记录中抹掉。

第二次混装球形复跑 `path-regression-suite2` 在夹具执行之前退出，帧为 `jvm.dll!ReceiverTypeData::clean_weak_klass_links`，报告 `run/native-sublevel-direct-smoke-path-regression-suite2/hs_err_pid35276.log`，日志 `build/structure-path-regression-suite2.log`。该次同样不是功能通过。历史上已有 G1/C2 原生崩溃记录，见[旧崩溃审阅](structure_look_crash_audit_2026_10_20.zh-CN.md)，不能仅靠相似栈判定同源。只读检查最近两小时未返回 WHEA 事件，既不能确定也不能排除硬件问题。

第三次原配置复跑 `path-regression-suite3` 前 3 项通过后在 `C1 CompilerThread1` 崩溃，帧为 `ciReceiverTypeData::translate_receiver_data_from`；报告 `run/native-sublevel-direct-smoke-path-regression-suite3/hs_err_pid1316.log`。因此停止普通重试。单变量对照 `path-regression-no-hotswap` 保持 JBR 25.0.3、G1、`-Xverify:none`、模组 pin、夹具与源码不变，只在原参数后追加 `-XX:-AllowEnhancedClassRedefinition`，完整 22 项通过并正常退出。实际参数保存在 `build/structure-path-no-hotswap-vmargs.txt`。这只是增强热替换相关的诊断线索，不能由一次成功证明根因或宣称原配置已修复；本轮没有改 `build.gradle.kts` 的默认启动选项。

对照复跑：按[上一批球形回归记录](structure_damage_2026_10_25.zh-CN.md)中的命令创建新的 fresh 副本，额外传入 `-I build/structure-path-no-enhanced-hotswap.init.gradle`。该忽略文件内容如下，可在新 checkout 重建；球形夹具使用 `structure_damage.init.gradle`，而非路径夹具。

```groovy
allprojects {
    afterEvaluate {
        neoForge.runs.named('clientSable') {
            jvmArgument '-XX:-AllowEnhancedClassRedefinition'
        }
    }
}
```

## 接手顺序与剩余工作

1. 优先跟进上述 JBR/G1 崩溃，使用保留的报告和同一夹具建立可重复对照，不猜测根因或通过切 GC 宣称修复。人工使用最新 JAR，在无 Sable 与 Academy + Sable + Create + 航空学两种安装中，检查真实按键/蓄力、连续采矿、反射显示，以及运动/旋转结构上的命中感受。自动夹具通过不代替原存档体验验收。
2. 清点不经过 `LevelUtil.destroyBlocksAlongPath*` 的其他爆炸/范围破坏入口，逐个按原技能规则接入，不能改成密集小球调用来绕过预算。保留矿级、掉落模式、采集者和反射归属。
3. Sable 分裂后的来源退休/继承与外部删除仍按上一轮清单补齐；本轮只复用现有内容适配器，不赋予航空学外部结构技能来源。
4. 完成服务端持久发射账本和非玩家明确授权，再接入 Launched；随后推进 S4 真实接触汇聚、首撞一次与有限碎裂。当前不支持这些能力。
5. S0 原生密集接触红测、暖启动和吞吐对照仍独立待核查。已有 `native/academy_physics/src/lib.rs` 改动属于其他工作，不应混入本批提交。

本轮未改动原 test/test2、运行时 pin、捕获白名单、Rust/DLL 或 VFX 资源；运行副本、日志、JAR 均不入 Git。
