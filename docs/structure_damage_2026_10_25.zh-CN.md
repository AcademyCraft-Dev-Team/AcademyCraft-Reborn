# 冲击波与物理结构范围破坏接续

日期：2026-10-25。开发基线 `731e6c71`。本批落实主方案 S2 / C 线的活动玩家球形破坏路径；不替换原生 DLL，也不修改工作区原有的 `native/academy_physics/src/lib.rs` 接触诊断改动。

## 实现与调用

`StructureDamages.service()` 与 `StructureUpgradeBootstrap.LIVE.damage()` 指向 `LiveStructureDamage`，能力标记为 EXPERIMENTAL。普通动能冲击波与独立精密冲击波节点共用此服务，原有技能开关、CP、范围和伤害倍率仍由技能入口处理。铁块不因结构化而获得免疫。普通宿主方块保留按格中心判断球形范围、零半径选择所在格的规则；结构则按实际 OUTLINE 形状判断，含无碰撞植物，旋转时先把球心转换到局部坐标。

调用顺序：`query(ActiveActor, Request)` → 对返回的原始 READY `Batch` 调用一次 `apply` → `status` 获取后续 READY 批次 → 直到 COMPLETED。请求的 `maxScans/maxChanges` 是每操作、每 tick 的共享预算，邻接失去支撑的方块也消耗同一预算；不得把 RUNNING 当成全部完成。`energy` 本批保留为请求元数据，尚不参与 S4 的接触能量/硬度模型。

- 宿主格与本地/Sable 局部格走同一操作；只查询已加载的后端和区块，不启动新物理运行时、不加区块票。查询形状的依赖读取也有上限。
- 捕获/归块事务持有的结构及 `PayloadIsolation` 锁定的源格、目标格不得进入破坏；每次应用再次复核隔离状态。pending 捕获结果虽可包含已分配的结构引用，调用方仍须等待事务 succeeded。
- 使用 effect + 稳定结构引用/代际 + localPos 去重，runtime 和内容版本只用于复核。服务器保存真实批次对象，复制同值 record、重复 effect 和回调重入均不能获得写权限。
- 每格再次核对激活、姿态、完整状态/BE 内容与当前形状；保护事件在实际宿主位置触发，`StructureContexts` 提供局部地址。存储坐标不能作为宿主攻击点。Sable 原版点击事件转换为世界瞄准点，本地原生采掘 START 也能触发普通冲击波。
- 使用既有 `LevelUtil` 能力破坏和 `AbilityBlockDrops` 归属规则；工具仍为技能原有空工具，不伪造持续采掘或额外消耗工具耐久。标准掉落计算、全局修改器、BlockDropsEvent、容器内容和经验保留。掉落开关延续既有空间收纳规则，不销毁容器库存。
- 保护或掉落回调改变待删内容时，不覆盖替换后的方块。已发生的回调副作用不会伪称回滚；输出异常保存到 `data/academy/sublevel-mining-recovery`，操作进入 RECOVERY_REQUIRED，不能自动重放。
- 内容修改立即使观察失效，本地昂贵几何/质量重建按批合并，相关容器会话关闭。最后一格走各后端现有删除生命周期。
- Sable 的容器掉落可能已在加入实体前变换到宿主空间；作用域识别这一情况，保留速度并避免再次投射。输出证据逐项记录坐标空间。
- Sable 在两个预算批次之间可能拆分结构或替换激活。后续批次在固定世界作用区域内发现新激活，保留稳定格去重，不继续使用已经失效的 plot 地址。
- 修复既有 Sable 容器快照遍历的遗漏：热图分裂新增的子结构仍在下一 tick 执行完整 tick，但在本 tick 区块票判断前先刷新其世界包围盒。否则子结构会因默认包围盒落在未加载区而立即 UNLOADED。只刷新新增结构，不覆盖既有结构的上一帧包围盒。

边界：每服务器最多 16 个活动操作、256 个操作记录；每操作最多 512 个已发现激活和 65,536 个尝试格。活动操作最长 1,200 tick，完成记录保留 200 tick。超限、取消、掉线/换维停止剩余工作，不恢复已经删除的方块。预算只覆盖本服务调度的候选和直接修改，不能约束其他模组事件回调自行执行的任意工作。

## 验证与复跑

夹具 `StructureDamageSmoke` 运行于 fresh 隔离副本。检查 `structure-damage-result.json` 的 `passed`，不能只看 Gradle 成功退出。22 项覆盖铁块掉落开关、空洞、台阶空区、植物、箱子 19 个物品、保护取消、回调替换、掉落取消/替换与 7 点经验、伪造/重放/重入、单格预算续跑、旧内容/姿态拒绝、普通与精密冲击波、旋转台阶与箱子、宿主和结构混合破坏及基岩保留、结构点击入口、源/目标载荷锁定以及查询后才加锁的拒绝。结构点击项目调用实际事件处理入口，不等同于人工鼠标/网络操作验收。

```powershell
$env:JAVA_HOME='C:/Users/Dusk/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name damage-next-native -Fresh
./gradlew.bat runClientDev -DisDev=true `
  -I validation/native_structure/structure_damage.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-damage-next-native --console=plain

./validation/native_structure/prepare_default_skill_smoke.ps1 -Name damage-next-suite -Fresh
./gradlew.bat runClientSable -DisDev=true -PacademySableRuntime=suite `
  -I validation/native_structure/structure_damage.init.gradle -PacademyDamageRun=clientSable `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-damage-next-suite --console=plain
```

最终验证（2026-10-25）：

| 检查 | 结果 | 本地证据 |
| --- | --- | --- |
| 独立原生后端 `runClientDev` | 22/22，606 tick，`passed=true`，正常退出 | `run/native-sublevel-direct-smoke-damage-native-final/structure-damage-result.json`、`build/structure-damage-native-final.log` |
| Academy + Sable + Create + 航空学 `runClientSable` | 22/22，606 tick，`passed=true`，正常退出 | `run/native-sublevel-direct-smoke-damage-suite-final/structure-damage-result.json`、`build/structure-damage-suite-final.log` |
| JVM `test -DisDev=true` | 3,245 项，零失败/错误/跳过 | `build/structure-damage-final-tests.log`、`build/test-results/test` |
| `sableCompatTest -DisDev=true` | 38 项，零失败/错误/跳过；属于完整测试的专项复跑 | `build/test-results/sableCompatTest` |
| 开发版 `build -DisDev=true` | 通过，包含打包隔离与构建反例检查 | `build/structure-damage-build-dev.log` |
| 发布版 `build -DisDev=false` | 通过，包含完整测试、编辑器测试与打包检查 | `build/structure-damage-build-release.log` |

可人工测试的产物：`build/libs/academy-26.2.0-0.0.4-alpha-release.jar`（24,754,437 bytes，SHA-256 `DD8C0C3B877C056DB8D66128CD50DB0B4757F715AC74791F0CCD9AC6AED76D4B`）；开发版 `academy-26.2.0-0.0.4-alpha-dev.jar`（32,225,538 bytes，SHA-256 `0F6BE55A1F1DBFEDDF8B0B39C9B66E07EB0748C7E583EF2C7C5271A1B15A231B`）。两包均已检查包含共享破坏服务与 Sable 子结构包围盒修复类；完整产物清单在 `build/structure-damage-artifacts.json`。

本次实机是自动夹具验证，未代替玩家在原存档中的人工体验测试。范围内移动由真实重力步进覆盖；未将其称作高速首撞验收。

初轮混装测试暴露了箱子内容已投射、最后一格失效两个后端差异。单格预算失败的最终证据在 `build/structure-damage-suite-09.log`：健康子结构质量为 4，移除原因为 UNLOADED，调用链来自 `PhysicsChunkTicketManager.update`。这是快照遍历漏刷子结构包围盒，不是方块被删除或质量初始化失败。之前对 pending 捕获的猜测不成立；夹具等待 succeeded 和事务隔离检查作为独立正确性修复保留，另用明确加锁的反例验证。不能把失败运行当成通过证据。失败副本保留实际恢复记录，未自动重放掉落。

## 后续工作

1. 将射线、路径、爆炸等其他破坏技能迁移到相同授权/掉落与预算规则；当前 Request 只描述球形范围，不应由调用方模拟无限小球密集扫描来绕过预算。
2. 扩充 Sable 分裂与外部删除的持久来源退休/继承规则。此处仅追踪后续范围内的新激活，没有赋予外部结构技能来源，也没有实现受控碎裂或碎片自动归块。
3. 补发射账本与非玩家明确授权，再启用 Launched。当前明确 UNSUPPORTED，不用假玩家绕过保护。
4. 继续 S4 真实接触流、首撞一次预算、有限碎裂和完整故障注入；不要把范围破坏当成物理撞击系统已完成。
5. S0 暖启动、密集接触红测和吞吐对照仍是独立工作。本批功能回归不证明原生求解器压力红测已修复。

原始 test/test2 存档、旧 VFX 记录和既有 Rust 改动均不应混入本批提交。
