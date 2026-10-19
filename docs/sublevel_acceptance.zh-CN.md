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

## 2.2 64 活跃客户端渲染 / 60 FPS

集成客户端创建 64 个 2×2×2 自发光 SubLevel，稳定后测量 120 tick：

```text
SUBLEVEL_MULTI_CAPTURED structures=64
SUBLEVEL_MULTI_BENCHMARK structures=64 fps_avg=101.2 fps_min=89 ticks=120
```

结论：该场景达到首版 60 FPS 目标（1280×720，开发客户端，无光影/额外模组）；这是 2×2×2 结构基准，不代表 24³ 全尺寸结构或 256 全部同时渲染。

## 2.1 256 场景客户端渲染

`SubLevelMultiClientSmoke` 使用 `-Dacademy.sublevelMultiSmokeCount=256` 捕获 256 个 2×2×2 自发光 SubLevel，服务端按 64 活跃预算只模拟 64 个 body，客户端渲染 256 个投影：

```text
SUBLEVEL_MULTI_CAPTURED structures=256
SubLevel snapshots started: players=1 sublevels=256
SUBLEVEL_MULTI_BENCHMARK structures=256 fps_avg=91.63 fps_min=61 ticks=120
```

1280×720 开发客户端下平均 91.6 FPS、最低 61 FPS，达到 60 FPS 首版线；这是 2×2×2 结构，不是 256 个 24³ 满尺寸结构。

## 2.3 地形碰撞修复

烟测发现结构会沉入/穿过地面。根因是地形碰撞盒使用 block-local AABB 却直接减去 section 原点，所有地面 collider 都被偏移到错误位置。

修复后：

```text
CLIENT_SUBLEVEL_GROUND base_y=70 terrain_y=64
CLIENT_SUBLEVEL_FALL_PROBE y=64.00027164477902 initial_y=68.44266250000001 delta=-4.442390855220992
```

结构从约 68.44 下落到 64.00 后停在地形表面，不再穿地。

## 2.4 24³ 空心壳客户端渲染

`SubLevelLargeClientSmoke` 捕获 24×24×24 自发光空心壳（3,176 格），客户端测量 120 tick：

```text
SUBLEVEL_LARGE_CAPTURED side=24 blocks=3176 boxes=3176
SUBLEVEL_LARGE_BENCHMARK side=24 cells=3176 fps_avg=116.25 fps_min=104 ticks=120
```

为支持该场景，捕获上限提升到 4,096 格、plot 几何 8,192 盒、快照 8,192 盒。24³ 实心体仍超过当前 4,096-cell 视图上限，属于后续 section 调色板工作。

## 3. 其余已验证链路

- 客户端快照渲染：`SubLevelStatePacket received ... cells=8`、`SubLevel client projection ... added`
- 客户端碰撞：`CLIENT_SUBLEVEL_COLLISION_PROBE inside=8/7`
- 客户端破坏：`CLIENT_SUBLEVEL_BREAK_CONFIRMED cells=7`
- 客户端放置：`CLIENT_SUBLEVEL_PLACE_CONFIRMED cells=8`
- 警告屏确认：`CLIENT_SMOKE_CONFIRM_WARNING screen=LoadingErrorScreen button=proceed to main menu`

## 4. 已知问题

- 探针 `*AndStop` 已改为独立延迟停止线程；256 探针停止不再出现 shutdown StackOverflow。
- 单箱/双箱/漏斗/发射器/熔炉库存槽位代理菜单已打开验证；熔炉 cooking/lit 数据、多格方块和技能事务仍未完成；服务端 BREAK/PLACE 射线复核、USE 邻近校验已接入。
