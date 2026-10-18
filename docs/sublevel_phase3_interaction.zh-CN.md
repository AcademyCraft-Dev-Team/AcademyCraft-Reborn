# SubLevel 原生子系统 Phase 3：服务端通用交互

日期：2026-10-19  
状态：服务端破坏/使用/放置桥、客户端 SubLevel 射线和交互包已实现；客户端破坏、放置和拉杆使用均已实测，BlockEntity 菜单仍待完成。

## 1. 实现

| 入口 | 行为 |
| --- | --- |
| `SubLevelInteractionService.breakBlock` | 在真实 plot Level 上调用 `destroyBlock`，随后重建 body/collision |
| `SubLevelInteractionService.useBlock` | 用 plot Level 和真实 `BlockState.useItemOn/useWithoutItem` |
| `SubLevelInteractionService.placeBlock` | 用 `BlockPlaceContext(plotLevel, player, ...)` 调用通用 `BlockItem.place` |
| `SubLevelRuntime.refreshGeometry` | 方块变化后重采 plot 几何，删除/重建 body 和碰撞索引 |
| `SubLevelInteractPacket` | 客户端→服务端：id、local pos、face、hit point、hand、USE/BREAK/PLACE |
| `SubLevelClientState.raycast` | 客户端用 state-only view 做精确方块射线，不依赖投影实体 AABB |
| `SubLevelClientInteraction` | 接入 `Minecraft.startAttack/startUseItem`，命中 SubLevel 时发送交互包 |
| `SubLevelChestContainer` | 单箱菜单代理，槽位委托 plot 的真实 `ChestBlockEntity`，`stillValid` 用宿主玩家距离 |

设计要点：

- 交互直接作用于 plot 的真实方块，不再走旧 storage 白名单；
- 方块变化后物理 body 和碰撞同一事务式刷新；
- 破坏使用原版 `destroyBlock`，保留 loot/BE/邻居更新路径；
- 放置/使用已按通用原版 context 实现，等待客户端射线与玩家上下文测试。

## 2. 服务端验证

干净世界、捕获 2×2×2 石头后：

```
SubLevel collision probe: inside=8 outside=0
SubLevel path collision probe: inside=8 outside=0
SubLevel break probe: removed=true air=true inside_after=7
```

证明：

1. 破坏真实移除了 plot 中的方块；
2. `refreshGeometry` 后 body/collision 从 8 盒降为 7 盒；
3. 普通碰撞和 AI 寻路碰撞同步更新；
4. 没有整体 AABB 残留。

客户端真实交互烟测（集成客户端，2×2×2 自发光结构）：

```
CLIENT_SUBLEVEL_BREAK_SENT
CLIENT_SUBLEVEL_BREAK_CONFIRMED cells=7
CLIENT_SUBLEVEL_PLACE_RAY ... hit=Hit[...]
CLIENT_SUBLEVEL_PLACE_SENT
CLIENT_SUBLEVEL_PLACE_CONFIRMED cells=8
CLIENT_SUBLEVEL_USE_PREPARED local=BlockPos{x=0, y=0, z=0}
CLIENT_SUBLEVEL_USE_SENT local=BlockPos{x=0, y=0, z=0}
SubLevel use ... lever[powered=false] -> lever[powered=true] result=Success
CLIENT_SUBLEVEL_USE_CONFIRMED powered=true
CLIENT_SUBLEVEL_COLLISION_PROBE inside=7
SUBLEVEL_CLIENT_SMOKE_PASSED ... frames=865
```

证明客户端射线命中真实 SubLevel、C2S 包到达服务端、服务端删除方块、快照降为 7 cells；随后客户端再次射线命中剩余方块，发送 PLACE，服务端通用 `BlockItem.place` 将方块放回，快照恢复 8 cells，客户端碰撞同步恢复 8 盒。截图：`run/native-sublevel-client-smoke/screenshots/sublevel-client-smoke-6308-1792343210006.png`。

## 3. 当前边界

- 客户端破坏、放置和拉杆 `useBlock` 已实机验证；服务端 BREAK/PLACE 射线复核已接入，USE 射线复核暂缓（避免移动中的小型交互方块被误拒）；
- 单箱代理菜单已实机打开（27 格容器 + 36 格玩家背包）；双箱/多格方块和其他容器尚未桥接；
- `useBlock/placeBlock` 的 vanilla context 已实现，但尚未在带真实玩家/BlockEntity 的世界中验证；
- 容器菜单（箱子/熔炉）和多格方块尚未桥接；
- 交互权限目前只做 plot `mayInteract`，未接技能事务/保护事件；
- 掉落物仍在 plot 维度，尚未回传到宿主玩家位置。

## 4. 下一步

1. 客户端 `useBlock/placeBlock` 实机验证；
2. 掉落物/经验/音效/粒子从 plot 映射回宿主；
3. 容器菜单桥；
4. 技能权限、保护事件、冷却与事务 journal。
