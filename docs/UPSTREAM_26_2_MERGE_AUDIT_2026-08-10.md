# 26.2 上游合并审计（2026-08-10）

## 结论

- 已抓取并核对[官方上游仓库](https://github.com/AcademyCraft-Dev-Team/AcademyCraft-Reborn) `upstream/26.2` 的最新提交 `8baf227a9256aa814842cc5604659b996d580767`（`clean`）。
- 当前开发提交为 `50f1da8caaeed691e6e697c3ce7320788dbed1c5`，共同基点为 `7bbc2ca123b`；双方各自领先 1 个提交。
- 普通三方合并产生 43 个内容冲突。忽略纯空白差异后仍有 42 个真实冲突，因此不可直接在功能分支上执行无审查合并。
- 当前未提交的中英文语言整理与覆盖测试共 3 个文件，与上游变更路径没有重叠；生成补丁后执行 `git apply --check` 已通过。
- 已在 `build/codex-upstream-merge-8baf227a` 隔离副本中完成保守集成验证：自动接受无冲突的上游变更，对 42 个真实冲突文件完整保留当前熟练度实现，并修正两项上游基线问题。该结果通过 431 项单元测试和开发版、发行版双构建。
- 验证结果已固化为独立本地分支 `codex/upstream-26.2-integration`，合并提交为 `e16e545ac5a443f6b6ab1f7322f5410aa7c6998a`；没有切换或改写当前 `ability-port-26.2` 工作树。
- GameTest 共 23 项，21 项通过；失败项仅为既有 Mentalout 基线中的 2 项，没有新增失败类别。

这说明上游更新可以机械集成并保持当前功能可构建、可测试，但 42 个冲突文件中的上游改动仍须按能力类别逐项语义移植。当前审计只建立了独立集成分支，没有把隔离结果直接提交到正式功能分支。

## 上游变更规模

上游 `7bbc2ca123b..8baf227a9256` 的普通差异为：

- 653 个文件变更
- 7,832 行新增
- 6,683 行删除

忽略空白差异后仍有：

- 413 个文件变更
- 7,111 行新增
- 5,962 行删除

主要实质变更包括闪电/电弧 VFX 管线重构、渲染和界面调整、能力与实体控制代码整理、新增客户端性能分析命令，以及相应测试更新。不能把该提交视为单纯格式化。

## 冲突清单

普通合并比下列清单多出 `src/main/kotlin/org/academy/internal/client/gui/screen/AbilityDeveloperScreen.kt`；该文件的冲突在忽略空白差异时可以自动消除。

### 启动与服务端

- `src/main/java/org/academy/AcademyCraftServer.java`

### 矢量操控（6）

- `src/main/java/org/academy/internal/common/ability/accelerator/skills/lv1/VectorBlast.java`
- `src/main/java/org/academy/internal/common/ability/accelerator/skills/lv2/VectorAccel.java`
- `src/main/java/org/academy/internal/common/ability/accelerator/skills/lv3/VectorReduction.java`
- `src/main/java/org/academy/internal/common/ability/accelerator/skills/lv4/StormWing.java`
- `src/main/java/org/academy/internal/common/ability/accelerator/skills/lv4/VectorReflection.java`
- `src/main/java/org/academy/internal/common/ability/accelerator/skills/lv5/PlatinumWing.java`

### 气流操控（10）

- `src/main/java/org/academy/internal/common/ability/aeromanip/AeromanipFieldSyncPacket.java`
- `src/main/java/org/academy/internal/common/ability/aeromanip/skills/AtmosphereShield.java`
- `src/main/java/org/academy/internal/common/ability/aeromanip/skills/AtmosphericDominion.java`
- `src/main/java/org/academy/internal/common/ability/aeromanip/skills/FlowSense.java`
- `src/main/java/org/academy/internal/common/ability/aeromanip/skills/LaminarCutter.java`
- `src/main/java/org/academy/internal/common/ability/aeromanip/skills/PressureLock.java`
- `src/main/java/org/academy/internal/common/ability/aeromanip/skills/TailwindField.java`
- `src/main/java/org/academy/internal/common/ability/aeromanip/skills/VacuumDomain.java`
- `src/main/java/org/academy/internal/common/ability/aeromanip/skills/VortexPull.java`
- `src/main/java/org/academy/internal/common/ability/aeromanip/skills/WindCorridor.java`

### 电击使（4）

- `src/main/java/org/academy/internal/common/ability/electromaster/skills/lv3/MagneticWeapon.java`
- `src/main/java/org/academy/internal/common/ability/electromaster/skills/lv4/BioelectricOperation.java`
- `src/main/java/org/academy/internal/common/ability/electromaster/skills/lv4/ElectromagneticShield.java`
- `src/main/java/org/academy/internal/common/ability/electromaster/skills/lv5/Railgun.java`

### 原子崩坏（4）

- `src/main/java/org/academy/internal/common/ability/meltdowner/skills/lv2/MiningBeam.java`
- `src/main/java/org/academy/internal/common/ability/meltdowner/skills/lv2/ScatterBomb.java`
- `src/main/java/org/academy/internal/common/ability/meltdowner/skills/lv4/ParticleWaveCannon.java`
- `src/main/java/org/academy/internal/common/ability/meltdowner/skills/lv5/AutoCruiseBeamCannon.java`

### 心理掌控（10）

- `src/main/java/org/academy/internal/client/ability/mentalout/PlayerControlClientState.java`
- `src/main/java/org/academy/internal/client/ability/mentalout/PrecisionOperationScreen.java`
- `src/main/java/org/academy/internal/common/ability/mentalout/MentalIntrusionManager.java`
- `src/main/java/org/academy/internal/common/ability/mentalout/PlayerControlSessionManager.java`
- `src/main/java/org/academy/internal/common/ability/mentalout/control/ServerPlayerMentalControlAdapter.java`
- `src/main/java/org/academy/internal/common/ability/mentalout/precision/PrecisionGraph.java`
- `src/main/java/org/academy/internal/common/ability/mentalout/precision/PrecisionGraphCodec.java`
- `src/main/java/org/academy/internal/common/ability/mentalout/precision/PrecisionOperationRuntime.java`
- `src/main/java/org/academy/internal/common/ability/mentalout/skills/CommandPositioning.java`
- `src/main/java/org/academy/internal/common/ability/mentalout/skills/MentaloutTargeting.java`

### 空间移动（4）

- `src/main/java/org/academy/internal/common/ability/teleport/skills/lv3/FleshRipping.java`
- `src/main/java/org/academy/internal/common/ability/teleport/skills/lv4/AreaTeleportSetup.java`
- `src/main/java/org/academy/internal/common/ability/teleport/skills/lv4/AreaTeleportStart.java`
- `src/main/java/org/academy/internal/common/ability/teleport/skills/lv5/Flashing.java`

### 公共运行时、配置与 Mixin（3）

- `src/main/java/org/academy/internal/common/entitycontrol/EntityMotionGuard.java`
- `src/main/java/org/academy/internal/server/config/AbilityConfig.java`
- `src/main/java/org/academy/mixin/common/MixinLivingEntity.java`

## 已验证的保守解析策略

隔离验证使用以下原则：

1. 自动接受所有无冲突的上游文件，包括新的闪电 VFX 管线及其测试。
2. 对上述 42 个真实冲突文件完整保留当前 `HEAD`，防止熟练度逻辑被大规模代码移动或清理覆盖。
3. `AbilityDeveloperScreen.kt` 采用 Git 的合并结果；其冲突属于空白敏感差异。
4. 不单独使用 `-Xours` 作为最终解析。单独使用时曾在 `PrecisionOperationRuntime` 生成重复逻辑块并导致编译失败。
5. 合并后执行 `git diff --check HEAD`，并清除上游 `ScrollEvent.kt` 中带入的行尾空白。

建议在干净的专用集成分支中复现：

```powershell
git fetch upstream 26.2
git switch -c codex/upstream-26.2-integration
git merge --no-commit --no-ff -Xours upstream/26.2

# 将“冲突清单”中的 42 个路径赋给 $conflictPaths 后执行：
git restore --source=HEAD --staged --worktree -- $conflictPaths
```

随后必须逐类别比较 `7bbc2ca123b`、当前分支和 `upstream/26.2`，把确有行为变化的上游代码移植回对应文件。每完成一类就运行该类别测试，避免最后一次性处理 42 个文件。

## 上游基线问题

### EntityControlApiTest 不一致

上游把 `EntityControlApiTest` 中仅供测试的 `trueHealth` 与 `realHealth` 字段改成了 `final`，但测试仍要求 `NumericAccessor.write` 对其反射写入。生产实现明确拒绝写入 `final` 字段，导致完整单元测试出现 2 项失败。

隔离验证将这两个测试夹具字段恢复为普通 `float`。这样保留了生产代码的不可变字段保护，定向测试和完整测试均通过。正式合并时应保留该修正，或等待上游自行修复后重新核对。

### ScrollEvent 行尾空白

上游版本的 `ScrollEvent.kt` 会触发 `git diff --check`。隔离验证仅移除了行尾空白，没有改变行为。

## 验证结果

隔离集成副本执行结果：

| 检查 | 结果 |
| --- | --- |
| `git apply --check`（当前 3 个未提交本地化相关文件） | 通过 |
| `git diff --check HEAD` | 通过 |
| `./gradlew test -DisDev=true` | 通过，431/431 |
| `./gradlew build -DisDev=true` | 通过 |
| `./gradlew build -DisDev=false` | 通过 |
| `./gradlew runGameTestServer -DisDev=true` | 23 项中 21 项通过，2 项既有 Mentalout 基线失败 |

GameTest 基线失败：

- `academy:mentalout_path_unreachable_reports_failure`
- `academy:mentalout_direct_flying_mobs_path`

测试期间 Jade 还记录了 `Registry minecraft:loot_table not found` 的非致命日志，但没有阻止 GameTest 执行，也不是最终退出原因。

## 正式合并前置条件

1. 先提交或暂存当前语言文件与覆盖测试修改；虽然路径不重叠，但保持干净工作树能避免误操作。
2. 使用专用集成分支，不在 `ability-port-26.2` 上直接解决大批冲突。
3. 优先核对矢量操控、气流操控和 Mentalout；三者占 26 个真实冲突，且涉及运行时状态与网络同步。
4. 对 42 个暂时保留本地版本的文件逐项确认上游是否包含行为修复，不能仅凭双构建通过就视为语义集成完成。
5. 完成客户端烟雾测试，重点检查闪电/电弧 VFX、技能开发器、Mentalout 界面和两名玩家同步。自动化验证不覆盖实际渲染观感。
6. 最后重新执行单元测试、GameTest、开发版与发行版构建，并把基线失败与人工验收结果更新到本文档。
