# 开发线 B：捕获前规划与来源绑定

基于已提交的 [B 线真实角控制接线](structure_upgrade_line_b_live_2026_10_23.zh-CN.md)（`edea3a45`），本轮填 `UPG-CTRL-10/11`：捕获前的服务端预演与服务端来源绑定。`UPG-CTRL-01..04`（真实子步提取、状态与取消）仍是骨架，未改任何玩法入口。

## 实现位置与契约

| 位置 | 实现行为 |
| --- | --- |
| `api/common/structure/upgrade/StructureExtraction`、`StructureExtractions` | 公开契约未改；`planCapture` 只产出显示用 `CapturePlan`，`bindCapture` 只接受服务端 ID。`StructureExtractions` 提供 `service()` 与只读 `context()`（复用角服务的后端观测，不加载结构、不取租约）。 |
| `control/CaptureSourceSnapshot` | 有界（≤256 格）规范快照：宿主格 + 精确方块状态，按坐标排序后取 SHA-256；只作证据，不加载区块、不授予捕获或租约。 |
| `control/CapturePlanRegistry` | 每服务器有界（64）预演登记表：主体、维度、摘要、请求、到期时间全部留在服务端；单次绑定语义（同一捕获/控制复用返回首个绑定，不同捕获为冲突）、过期清理、容量不足直接拒绝而不挤掉活动预演。 |
| `control/ExtractionPlanBuilder` | 把纯 `ExtractionPlanner` 映射为 `StructureOperation.Status`：CLEAR→READY、软障碍→BLOCKED、硬阻挡→BLOCKED（附首个受阻格）、覆盖未知→TERRAIN_PENDING、预算耗尽→CONFLICT、版本变化→STALE；非 READY 不发布路径。 |
| `control/LevelCaptureSource`、`LoadedChunkView` | 只用已加载区块读取源格与真实碰撞盒，邻居依赖的形状也不触发区块加载；缺失按“未知”上报，不当作空气。 |
| `control/LevelExtractionObstacles` | 逐查询的宿主形状快照：排除源选区、包含 ±1 邻居格、软内容（无碰撞格但有选择体）标记为需授权；已加载的其他刚体一律不建模，直接令覆盖 UNKNOWN 保守失败。 |
| `control/LiveStructureExtraction` | `planCapture`：复核服务器线程、主体、维度、逐格权限，读源快照，规划净空，登记预演。`bindCapture`：核对预演主体/控制令牌/到期，`StructureCaptureCoordinator.poll` 的完成结果、结构身份、direct-control 租约、runtime/内容版本，再把实际结构内容按捕获原点重算摘要与预演摘要比对；最后用真实姿态/碰撞几何重新规划并登记绑定。`plan/begin/status/cancel` 仍委托 DRAFT 占位返回 UNSUPPORTED。 |
| `StructureUpgradeBootstrap.LIVE` | 只替换 EXTRACTION 为上述实现，能力声明为 `EXPERIMENTAL` 且 `enabled=false`（未接玩法）；ORIENTATION 不变；其余服务仍是禁用骨架。 |

预演阶段的 `terrainRevision` 固定为 `0`：捕获前不存在结构地形版本，此阶段的权威证据是内容摘要；绑定阶段用同一语义复核结构内容，结构地形版本留给 `UPG-CTRL-01..04` 的真实提取接线。

## 行为边界

- 预演不捕获、不移动、不授权，也不获取租约；`controlId` 只是之后要用的令牌。
- 绑定要求租约已真实存在（`StructureDirectControl.validate == ACTIVE`）。调用顺序必须是：预演 → 捕获提交 → 取得独占租约 → 绑定。
- 绑定不接受调用方提供的 `CapturePlan`、摘要或结果；未登记的预演、他人预演、过期预演分别返回 DENIED/STALE。
- 捕获结果未知（不在服务端已完成结果内）返回 CONFLICT，不猜测成功；PENDING 不等于成功。
- 结构内容无法读取按 TERRAIN_PENDING 处理，摘要不一致才按 DENIED 处理，两者不混用。
- 已加载的其他刚体出现在净空包络内时整份快照转 UNKNOWN，宁可拒绝预演也不假称可通行；同一结构自身不算障碍。

## 构建与验证

使用 JBR25；本机环境脚本在忽略的 `build/` 下。

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat verifyStructureUpgradeScaffold test -DisDev=true @physicsNativeArgs --console=plain
./gradlew.bat build -DisDev=true @physicsNativeArgs --console=plain
./gradlew.bat build -DisDev=false @physicsNativeArgs --console=plain
```

本轮新增 JVM 测试 14 项（`CaptureSourceSnapshotTest` 3、`CapturePlanRegistryTest` 5、`ExtractionPlanBuilderTest` 6），覆盖摘要稳定性与敏感性、原点重定基、越界/重复/超限拒绝、预演归属与过期、单次绑定与重复回执、容量拒绝、摘要不匹配拒绝，以及 CLEAR/软障碍/硬阻挡/覆盖未知/版本变化/截断预算到公开状态的映射。全量 JVM 由 2,978 增至 **2,992 项，0 失败 0 错误**，`verifyStructureUpgradeScaffold`、开发与发布两种 `build` 均通过。机器可读证据见 [本轮验证摘要](../validation/native_structure/structure_upgrade_line_b_capture_preview_2026_10_23.json)。

## UNVERIFIED 与后续

- 活路径（`LevelCaptureSource`、`LoadedChunkView`、`LevelExtractionObstacles`、`LiveStructureExtraction`）尚未做游戏内夹具：没有真实捕获 + 绑定 + 摘要比对的双后端证据，也没有复杂台阶/凹角/顶盖/偏心扰动下的预演结果。
- 预演与绑定的端到端顺序、租约时序、Sable 结构内容读取、以及捕获耗时超过预演期限的边界，都需要隔离世界夹具；当前只保证编译与纯逻辑测试。
- `UPG-CTRL-01..04` 未开：没有子步限力提取、没有悬浮计时、没有取消/受阻语义，因此 H 仍走原有实现。
- 未启用任何新玩法；`EXTRACTION` 能力声明为 EXPERIMENTAL 且关闭，不能把本轮结果读成提取已可用。

下一阶段：将本服务接入 H 的捕获前预演与捕获后绑定（保持原 test/test2 不变），再按 `UPG-CTRL-01..04` 实现真实子步提取与到位后 200 tick 悬浮。
