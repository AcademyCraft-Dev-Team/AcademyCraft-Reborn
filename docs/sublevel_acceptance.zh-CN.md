# SubLevel 首版验收证据

日期：2026-10-19  
环境：Windows、Java 25、NeoForge 26.2.0.70、开发客户端 1280×720、隔离世界。

## 1. 256 持久 / 64 活跃

服务端探针在干净世界创建 256 个 2×2×2 石头 SubLevel：

```text
SubLevel persistent probe: requested=256 created=256 active=64 inactive=192 universe=64 stored=256
```

重启同一世界后：

```text
SubLevel restore verify: stored=256 active=64 entries=256
```

结论：256 个 plot/manifest 条目和碰撞/视图可恢复；物理 universe 严格限制为 64 个活跃 body，其余保持 INACTIVE，不占用 Rapier 活跃预算。

## 2. 64 活跃客户端渲染 / 60 FPS

集成客户端创建 64 个 2×2×2 自发光 SubLevel，稳定后测量 120 tick：

```text
SUBLEVEL_MULTI_CAPTURED structures=64
SUBLEVEL_MULTI_BENCHMARK structures=64 fps_avg=101.2 fps_min=89 ticks=120
```

结论：该场景达到首版 60 FPS 目标（1280×720，开发客户端，无光影/额外模组）；这是 2×2×2 结构基准，不代表 24³ 全尺寸结构或 256 全部同时渲染。

## 2.1 地形碰撞修复

烟测发现结构会沉入/穿过地面。根因是地形碰撞盒使用 block-local AABB 却直接减去 section 原点，所有地面 collider 都被偏移到错误位置。

修复后：

```text
CLIENT_SUBLEVEL_GROUND base_y=70 terrain_y=64
CLIENT_SUBLEVEL_FALL_PROBE y=64.00027164477902 initial_y=68.44266250000001 delta=-4.442390855220992
```

结构从约 68.44 下落到 64.00 后停在地形表面，不再穿地。

## 3. 其余已验证链路

- 客户端快照渲染：`SubLevelStatePacket received ... cells=8`、`SubLevel client projection ... added`
- 客户端碰撞：`CLIENT_SUBLEVEL_COLLISION_PROBE inside=8/7`
- 客户端破坏：`CLIENT_SUBLEVEL_BREAK_CONFIRMED cells=7`
- 客户端放置：`CLIENT_SUBLEVEL_PLACE_CONFIRMED cells=8`
- 警告屏确认：`CLIENT_SMOKE_CONFIRM_WARNING screen=LoadingErrorScreen button=proceed to main menu`

## 4. 已知问题

- 256 探针的 `persistentProbeAndStop` 在服务器启动阶段触发原版 `halt` 时出现 shutdown StackOverflow；数据已 persist，但停止路径需要改为更安全的延迟停止。
- 256 场景未做客户端 256 全量渲染；首版只要求 64 活跃。
- 差量快照、容器/多格方块、技能事务仍未完成；服务端 BREAK 射线复核已接入，USE/PLACE 尚待补全。
