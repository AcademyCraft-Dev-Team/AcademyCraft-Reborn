# SubLevel 原生子系统 Phase 2：真实方块碰撞

日期：2026-10-19  
状态：服务端/客户端方块碰撞和地形碰撞链路已跑通；旋转精确化、实体承载和复杂 AI 寻路仍待完成。

## 1. 实现

| 组件 | 作用 |
| --- | --- |
| `SubLevelCollisionIndex` | 每结构保存真实 world-space boxes；query 不使用实体 AABB |
| `SubLevelCollisionRuntime` | 按 `Level` 注册碰撞索引；把 boxes 转成 `VoxelShape` |
| `MixinCollisionGetter` | 向 `CollisionGetter.getBlockCollisionsFromContext` 追加 SubLevel 形状 |
| `PathNavigationRegionAccessor` | 读取 AI 寻路 region 的宿主 level，使非 Level 的 CollisionGetter 也能追加 SubLevel 形状 |
| `SubLevelRuntime` | 创建/恢复/每 tick 姿态变化时更新碰撞索引；移除/关闭时清理 |

设计要点：

- 不再依赖 `NativeStructureProjectionEntity.getBoundingBox()` 作为碰撞；
- 使用真实 capture geometry 的逐格 boxes；
- `getBlockCollisions` 与直接调用 `getBlockCollisionsFromContext` 的实体移动路径都能拿到 SubLevel 碰撞；
- 旧投影实体的 `MixinEntityGetter` 仍独立存在，不与新 SubLevel 索引混用。

## 2. 服务端验证

探针流程：

1. 在玩家上方 5 格生成 2×2×2 石头；
2. 捕获到 plot，创建 SubLevel body；
3. 用 `level.getBlockCollisions(null, inside)` 查询结构内部；
4. 用结构右侧 3 格外的空域查询外部。

结果：

```
SubLevel collision probe: inside=8 outside=0
SubLevel path collision probe: inside=8 outside=0
```

即：

- 内部 8 个结构碰撞盒全部被普通方块碰撞查询命中；
- 外部空域没有误加的碰撞；
- 没有使用实体包围盒。

### 2.1 地形碰撞根因修复

烟测中发现 SubLevel 会沉入/穿过地面。根因是 `TerrainSectionGeometry` 把 `BlockState.getCollisionShape` 返回的 **block-local** AABB 直接减去 section 原点，导致地形碰撞盒偏移到错误位置；结构体实际上没有可碰撞的地面。

修复：先 `box.move(x, y, z)` 转成 section 坐标，再减去 section 原点；同时对相邻同尺寸盒子做贪心合并，地形 section 的 cuboid 数从约 4095 降到 3–69。

修复后的 fall probe：

```text
CLIENT_SUBLEVEL_GROUND base_y=70 terrain_y=64
CLIENT_SUBLEVEL_FALL_PROBE y=64.00027164477902 initial_y=68.44266250000001 delta=-4.442390855220992
```

结构从 68.44 下落到 64.00 后停在地形表面（terrain_y=64），不再继续穿地。

## 3. 当前边界

- 客户端已同步 SubLevel 碰撞索引并通过 `CLIENT_SUBLEVEL_COLLISION_PROBE inside=7/8` 验证；
- `SubLevelRuntime.localBoxes` 当前把 physics box 视为轴对齐；捕获几何是轴对齐的，旋转结构需要 Phase 2 后续做精确变换；
- AI 寻路的 PathNavigationRegion 已通过 accessor 追加同一套形状；仍待验证复杂寻路/避障；
- 服务器已加入玩家平移承载：结构姿态变化时，站在顶面 box 上的玩家随 origin delta 移动；仅玩家、仅平移，实体/旋转承载仍待继续。

## 3.1 玩家承载探针

```text
CLIENT_SUBLEVEL_CARRY_PLACED y=69.53560468750001
CLIENT_SUBLEVEL_CARRY_SHIFT shifted ...
CLIENT_SUBLEVEL_CARRY before_y=66.68434687347414 after_y=68.23202656250002 delta=1.547679689025884
```

结构姿态上移 2 格后，站在顶面的服务器玩家随结构一起上移。

## 4. 下一步

1. 客户端碰撞索引同步（SubLevel chunk/pose 增量）；
2. 复杂 AI 寻路/避障实机验证；
3. 旋转 box 的精确变换与空腔测试；
4. 与方块破坏/放置（Phase 3）共用同一 plot 几何版本。
