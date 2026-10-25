# 层流切割与物理结构接续

日期：2026-10-26。基线：`00f7dd06 接入物理结构路径破坏并调整启动配置`。承接[路径破坏接续](structure_path_damage_2026_10_25.zh-CN.md)的其他范围入口清点。本批仍属于 S2 内容破坏；不启用发射授权、首撞或碎裂。

## 实现与行为

层流切割原先直接遍历宿主 `BlockPos`，无法清理独立 SubLevel 中的内容，也不能可靠处理 Sable 的局部格。普通蓄力释放与精密 `laminarCut` 都经过 `LaminarCutter.Server.executeCut`，现在统一调用 `StructureDamage.cut`。

- 即发不清理方块；半蓄力清理原有软材质清单；满蓄力额外允许硬度在 `[0, 1.5]` 的内容。铁块、基岩和超出硬度的箱子保留，箱子库存不变。原技能破坏开关及 `allowSoftBlockInteraction` 仍生效。没有改变蓄力时间、CP/气量、伤害、击退、装备效果或节点格式。
- 火和灵魂火仍可熄灭且不产生物品，但现在也走宿主保护事件，取消时保留火；旧实现的直接 `removeBlock` 绕过了这条保护。纯液体不作为可切割方块。
- 新公共 `StructureCutDamage.Request` 描述世界线段、垂直于线段的单位刀面轴、半宽/半厚、只读材料规则、掉落模式与模拟标记。材料规则由技能提供，查询与写入前复核；不把空力使的白名单硬编码到通用结构服务。`LaminarCutter.Server.canCutBlock` 公开复用原材料规则。
- 使用有向长方体与每个真实内容子盒的 SAT 相交；优先碰撞形状，空碰撞内容使用选择形状，获准但完全无形状的内容（例如火）回退所属单位格。结构姿态只用于坐标转换，不能用整座结构包围盒代替内容命中。修正旧遍历对竖直刀面只扫描上下两格的漏格；与旧格中心近似相比，斜向/非满格边缘现在由实际形状决定。
- 近到远的有界路径游标只作候选发现，半径取刀面截面的外接圆；最终不会把薄刀变为同宽圆柱。一次调用同步完成，最多 32,768 扫描工作单元和 1,024 修改尝试；与光束共用同玩家同 tick 的 65,536 / 2,048 预算，不拆成大量球形攻击绕过预算。缺覆盖或预算不足明确返回未完成，不延迟续跑。非获准材质不阻挡后方软内容，保留原清理规则。
- 复用原内容快照、宿主保护、事务隔离、重入拒绝、真实掉落投射和批次末几何刷新；不加载缺失区块、不创建假玩家。DRAFT 的 `cut` 返回 UNSUPPORTED，活动玩家 LIVE 才执行。
- 层流切割的实体遮挡检查改用已有 `WorldBlockTargets.clip(...COLLIDER)`，保持原碰撞形状语义，补齐物理结构的遮挡；不改变刀面内实体筛选和伤害计算。它使用既有射线查询契约，本批不扩展未知/不兼容外部结构的查询能力。

## 其他入口清点

| 入口 | 当前处理与接续 |
| --- | --- |
| `LevelUtil.destroyBlocksAlongPath*` 的活动玩家调用 | 上一批统一接入；本轮复跑双后端路径回归，保证切割分支不改变现有光束 |
| `KineticEnergyApplied` 普通/精密冲击波 | 前批球形服务；本批不改变球形实现 |
| `DarkmatterModifierRuntime` 命中/弹丸爆炸 | 均为 `ExplosionInteraction.NONE`，原本只对实体作用；不能擅自新增方块破坏 |
| 暗物质工具范围收获 | 属于交互、作物成熟判定、耗种和补种事务，不等同攻击破坏；仍待单独迁移结构交互坐标与资源守恒 |
| 矢量/空间移动中的 `setBlock(AIR)` | 搬运与回滚，不能改成产出战利品的攻击入口 |
| 发射台强制爆炸 | 爆炸为 NONE，仅发射台自身移除；设备生命周期，不是通用范围技能 |
| 中继卫星坠毁 | 有真正 TNT 范围破坏，来源可能是非玩家/离线主体；待持久发射账本及明确授权完成后接入，不能借用假玩家或改成球形语义 |
| Misaka 轨道打击的方块处理 | 使用游戏模式权限入口，需独立核对指令/发射授权及原破坏规则；本批未迁移 |

## 验证与复跑

`DamageCutRegionTest` 验证薄轴不随宽度膨胀、竖直刀面、共同刚体变换下的薄层空区、旋转包围盒空角，以及退化/超界参数。实机 `StructureCutDamageSmoke` 使用隔离 fresh 副本，真实内容、事件、掉落和精密 runtime，不是模拟后端。非满格夹具先断言材质允许被切，再分别检查旋转薄层的命中与空区，避免材质限制造成假通过。

```powershell
$env:JAVA_HOME='C:/Users/Dusk/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name cut-next-native -Fresh
./gradlew.bat runClientDev -DisDev=true `
  -I validation/native_structure/structure_cut_damage.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-cut-next-native --console=plain

./validation/native_structure/prepare_default_skill_smoke.ps1 -Name cut-next-suite -Fresh
./gradlew.bat runClientSable -DisDev=true -PacademySableRuntime=suite `
  -I validation/native_structure/structure_cut_damage.init.gradle -PacademyDamageRun=clientSable `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-cut-next-suite --console=plain
```

检查 `structure-cut-damage-result.json` 的 `passed`、28 个 case 和正常退出，不能只看 Gradle 成功。夹具覆盖三档材料、火/火保护、旋转命中与空区、空洞、取消/替换、掉落修改、重入、载荷锁、模拟、无掉落、宿主/结构混合宽厚边界、未切箱子库存、共享技能入口、实际精密水平/竖直节点、破坏开关、1,024 写上限及跨 cut/path 的 2,048 总预算、物理结构实体遮挡正反例。没有把服务端调用等同于真人按键蓄力或原存档体验测试。

最终矩阵（本轮沿用默认关闭增强类重定义的启动配置）：

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 独立层流切割，增强空洞夹具 | 28/28，756 tick，正常保存退出 | `build/structure-cut-hollow-final-native.log` |
| Academy + Sable + Create + 航空学层流切割，增强空洞夹具 | 28/28，756 tick，正常保存退出 | `build/structure-cut-hollow-final-suite.log` |
| 独立路径回归 | 28/28，763 tick，正常保存退出 | `build/structure-cut-path-native.log` |
| 四模组混装路径回归 | 28/28，763 tick，正常保存退出 | `build/structure-cut-path-suite.log` |
| `test sableCompatTest -DisDev=true` 及构建中的编辑器测试 | 主测试 3,255、兼容 38、编辑器 104，共 3,397；失败/错误/跳过均为 0 | `build/structure-cut-final-tests.log`、`build/test-results/` |
| `build -DisDev=true` | 成功，27 秒 | `build/structure-cut-final-build-dev.log` |
| `build -DisDev=false` | 成功，42 秒 | `build/structure-cut-final-build-release.log` |

测试按同代码/同配置复用已有成功结果，构建日志包含 UP-TO-DATE/缓存任务，不把它们称为每次全部重跑。结果 JSON 位于 `run/native-sublevel-direct-smoke-<运行名>/structure-cut-damage-result.json` 或 `structure-path-damage-result.json`；实际 JVM 参数快照位于 `build/structure-<运行名>-vmargs.txt`。最终汇总入口为 `build/structure-cut-validation-summary.json`。

初轮 `cut-native-01` 因夹具未声明 action 的受检异常而编译失败；`cut-final-native` 因夹具误用旧实体类型常量名而编译失败，两者均未启动客户端。`cut-native-02` 为早期 23 项；`cut-native-03` / `cut-suite-01` 为实体遮挡修正前的 26 项；`cut-final-native-r2` / `cut-final-suite` 的 28 项通过时，空洞案例仍只是整体外框之外的偏移负例。最终空洞用例改为结构外框内部、两侧均为可切石块的真实空区；上述早期运行不替代最终矩阵。

两个 JAR 均已核对包含新公共 API、刀面几何、共享服务与技能服务端类，清单为 `build/structure-cut-artifacts.json`：

| 产物 | 大小（字节） | SHA-256 |
| --- | --- | --- |
| [开发 JAR](../build/libs/academy-26.2.0-0.0.4-alpha-dev.jar) | 32,253,596 | `655411F7D45CBF6AFCCB551F221E2C950FE0746795E2ABC4287BB1A255338378` |
| [发布 JAR](../build/libs/academy-26.2.0-0.0.4-alpha-release.jar) | 24,782,495 | `62C184A2994A5F5EF7B16796D50B024C02A5BCCC3750DCF3A40D09022934E2B1` |

用户已明确确认创建本批 11 个相关文件的本地提交，提交信息为“接入层流切割物理结构破坏与实体遮挡”，以新增本记录的提交为准。既有 Rust 文件 SHA-256 保持 `0B172501A5DA088A1CDA8AD13A41C88005D64B8D62D5347103DB566F84AC3C70`，不纳入提交。

## 下一步

1. 先按本记录核对实际混装与独立结果，再人工检查层流切割蓄力、刀面方向和运动结构边缘；客户端 VFX/实体刀面边缘筛选仍保留原算法，本轮没有重写它们。
2. 推进 Sable 分裂后的持久来源继承/退休及外部删除核对；禁止把航空学外部结构自动标为技能创建。
3. 补服务端持久发射账本与非玩家明确授权，再逐项迁移卫星/轨道等来源复杂的破坏入口，随后推进 S4 真实接触汇聚、首撞一次与有限碎裂。不可由本批切割验收推导这些已完成。
4. S0 原生密集接触红测和 JBR 间歇性原生崩溃仍独立跟踪。使用现有默认启动设置；不改 GC、JBR、物理后端或测试断言来绕开失败。

原 test/test2、Sable runtime pin、Rust/DLL 和既有 Rust 改动均不属于本批；运行副本、日志、JAR 不提交。
