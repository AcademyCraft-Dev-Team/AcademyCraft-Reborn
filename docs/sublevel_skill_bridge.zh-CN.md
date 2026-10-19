# SubLevel 技能/程序接入点

日期：2026-10-19

`SubLevelSkillSupport` 是服务端技能与程序使用 plot-backed SubLevel 的稳定入口：

| 方法 | 行为 |
| --- | --- |
| `capture(ServerLevel, List<BlockPos>)` | 真实捕获为 SubLevel，成功后移除源方块；失败回滚并返回 empty |
| `capture(ServerLevel, List<BlockPos>, BiPredicate)` | 权限感知捕获；每个源格在 plot 分配前先检查权限 |
| `pose(MinecraftServer, SubLevelId)` | 读取当前姿态 |
| `translate(MinecraftServer, SubLevelId, Vec3)` | 平移并停用物理 body，供事务/调试使用 |
| `rotate(MinecraftServer, SubLevelId, double)` | 绕局部中心 yaw 旋转 |
| `launch(MinecraftServer, SubLevelId, Vec3, double)` | 对活动刚体施加一次有界发射冲量 |
| `moveTo(MinecraftServer, SubLevelId, Vec3, double)` | 有界连续移动，按 tick 设置速度直到到达/超时 |
| `staticize(MinecraftServer, SubLevelId, BlockPos)` | 将 plot 内容事务式写回宿主并移除 SubLevel |

当前状态：

- 这是第二阶段完成的稳定接入面；
- 捕获、权限、pose、平移、旋转和发射冲量均已实测；
- 具体技能的逐个调用迁移留待后续阶段。

不要把该 API 误称为“技能已迁移”；它只提供 SubLevel 侧的统一入口。

拒绝路径已由客户端烟测覆盖：

```text
SUBLEVEL_SKILL_PERMISSION_DENIED position=BlockPos{x=-203, y=66, z=133} state=Block{minecraft:short_grass}
```

权限拒绝后源方块保持不变，也没有分配 plot。

发射冲量已由客户端烟测覆盖：

```text
CLIENT_SUBLEVEL_LAUNCH launched ...
CLIENT_SUBLEVEL_LAUNCH_PROBE y=68.66 initial_y=66.76 delta=1.90
```
