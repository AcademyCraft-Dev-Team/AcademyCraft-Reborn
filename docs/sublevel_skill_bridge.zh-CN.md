# SubLevel 技能/程序接入点

日期：2026-10-19

`SubLevelSkillSupport` 是服务端技能与程序使用 plot-backed SubLevel 的稳定入口：

| 方法 | 行为 |
| --- | --- |
| `capture(ServerLevel, List<BlockPos>)` | 真实捕获为 SubLevel，成功后移除源方块；失败回滚并返回 empty |
| `pose(MinecraftServer, SubLevelId)` | 读取当前姿态 |
| `translate(MinecraftServer, SubLevelId, Vec3)` | 平移并停用物理 body，供事务/调试使用 |
| `rotate(MinecraftServer, SubLevelId, double)` | 绕局部中心 yaw 旋转 |

当前状态：

- 这是第二阶段新增的稳定接入面；
- 现有技能仍走旧 `StructurePhysics` / Region 路径，尚未逐个迁移；
- 迁移技能时需要把它们的捕获、权限、事务 journal 和运动控制映射到 `SubLevelSkillSupport`。

不要把该 API 误称为“技能已迁移”；它只提供 SubLevel 侧的统一入口。
