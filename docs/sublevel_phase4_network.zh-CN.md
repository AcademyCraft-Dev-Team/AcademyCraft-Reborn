# SubLevel 原生子系统 Phase 4：网络与客户端状态（原型）

日期：2026-10-19  
状态：SubLevel 快照包、客户端状态存储、client-only 方块渲染、客户端碰撞、客户端破坏与放置已接入并实机验证；差量/插值/非 BlockItem 使用仍待完成。

## 1. 实现

| 组件 | 作用 |
| --- | --- |
| `SubLevelStatePacket` | 服务端→客户端快照：UUID、plot、pose、local collision boxes、block-state view cells、removed |
| `PacketTypes.SUBLEVEL_STATE` | 网络类型注册 |
| `SubLevelClientState` | 客户端按 `ClientLevel` 保存快照；创建 client-only 投影实体复用现有投影渲染器；可选注册客户端碰撞索引 |
| `SubLevelServer.sendSnapshots` | 每 5 tick 向宿主维度 256 格内玩家发送快照 |
| `ClientSmokeWarningConfirmer` | 所有 opt-in 客户端烟测自动确认 NeoForge 加载警告屏 |
| `SubLevelInteractPacket` | 客户端→服务端 USE/BREAK/PLACE；客户端射线命中后发送 |
| `SubLevelClientState.raycast` | 基于 state-only view 的精确方块射线 |

设计要点：

- 快照同时包含碰撞盒、pose 和 state-only block view cells；
- 客户端用这些 cells 创建 client-only `NativeStructureProjectionEntity`，复用现有投影渲染器；
- 客户端碰撞默认 **关闭**（`-Dacademy.sublevel.clientCollision=true` 才启用），避免客户端同步/渲染验收前出现不可见屏障；
- 当前是完整快照，后续替换为 section 调色板 + 差量。

## 2. 验证

- `SubLevelStatePacketTest`：pose/plot/boxes/removed 编解码 round-trip 通过；
- 服务端真实快照 codec probe：`SubLevel snapshot codec probe: cells=8`（2×2×2 石头结构）；
- 客户端实机烟测（`validation/native_structure/sublevel_rendering.init.gradle`）：
  - `CLIENT_SMOKE_CONFIRM_WARNING screen=LoadingErrorScreen button=proceed to main menu`
  - `SubLevelStatePacket received ... cells=8`
  - `SubLevel client projection ... added entityId=-1000000`
  - `SUBLEVEL_CLIENT_SMOKE_PASSED ... cells=8 frames=79 screenshot=...`
  - `CLIENT_SUBLEVEL_BREAK_SENT` → `CLIENT_SUBLEVEL_BREAK_CONFIRMED cells=7`
  - `CLIENT_SUBLEVEL_PLACE_RAY ... hit=Hit[...]` → `CLIENT_SUBLEVEL_PLACE_SENT` → `CLIENT_SUBLEVEL_PLACE_CONFIRMED cells=8`
  - `CLIENT_SUBLEVEL_COLLISION_PROBE inside=8`
  - 截图 `run/native-sublevel-client-smoke/screenshots/sublevel-client-smoke-6308-1792343210006.png` 验证破坏后放置恢复 8 cells；
- 完整 `test -DisDev=true` 通过；
- 服务端无玩家时不发送快照，现有专服启动/捕获/碰撞/交互探针不受影响。

客户端视觉实机验证已完成：集成客户端进入世界后成功确认 NeoForge 警告屏，接收 8-cell 快照，创建 client-only 投影并提交 61–107 帧；详见下方证据。

## 3. 当前边界

- 当前复用旧投影实体渲染器，尚未替换为 section 批处理网格；
- 无 section 调色板/差量，带宽随盒数线性增长；
- 无 pose 插值/客户端平滑；
- 通用移除没有广播 removed 包（仅协议支持）；
- 客户端射线/交互包未接；
- 客户端碰撞只有盒数据，渲染前默认关闭；
- 尚未做 64 活跃/多结构/60 FPS 客户端压测；
- 烟测中仍有无关的 `Resource reload failed` 覆盖层，需要单独修复资源重载。

## 4. 下一步

1. 发送 plot section 调色板 + 方块状态，客户端构建 section 网格；
2. 替换完整快照为差量 + pose 插值；
3. 客户端射线和 Phase 3 交互包；
4. 启客户端碰撞并做 60 FPS 实机验收。
