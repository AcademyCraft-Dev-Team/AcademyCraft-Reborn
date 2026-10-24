# 本地物理结构的动态扩建

基于 `c3b9fc5d`。此前创建时的包围盒同时被用作永久放置边界、几何扫描范围和持久化范围；底层 plot 只允许 0～31 的 X/Z。渲染快照另有 −32～31 的限制。因此仅删除放置判断会产生无法碰撞、无法显示或重启丢失的外围方块。

## 实现

- `SubLevelGrowth` 和 `SubLevelRuntime.prepareEdit` 为玩家放置、旧放置接口和 `StructureWorldAccess.compareAndSet` 统一申请范围。先保证存储区块可用并持久化写入范围，再让原版放置逻辑扣除物品。门、床、双高植物预留两格数量和一格邻域。
- `localBounds` 变为实际内容范围；编辑后重新扫描、收紧边界，重建渲染和碰撞，保持结构 ID、局部坐标原点、姿态和速度。`PlotGeometry` 不再减去包围盒最小坐标，因此向负方向扩建或拆除边缘不会平移旧方块的碰撞。
- 新结构使用与旧网格分离的存储编号。每个编号预留互不重叠的 1024×1024 虚拟区域，初始只加载原来大小的四个区块，随后按编辑范围加载。不会加载整个虚拟区域。实体读取按每 tick 的既有预算处理，重复扩建不会重复排队同一租约。
- 旧存档 plot 坐标保持兼容。首次编辑时完整复制方块和 BE NBT，源/目标库存隔离，保存两边区块，通过原子 manifest 切换唯一所有者，再无掉落清理并释放旧租约。达到常规 plot 容量时允许迁移暂时多占一个替代租约；不会增加可创建结构数量。
- 决定前失败仍由旧 plot 持有内容；决定后由新 plot 持有。IO 结果不确定时冻结该结构，保留副本等待重启。旧 plot 清理失败时保留隔离与租约，避免再次分配。含实体或正在使用菜单/挖掘的旧 plot 会推迟迁移。
- 客户端快照允许扩建坐标。SubLevel 包本来就使用 short 坐标；通用 `NativeStructureView` 保留旧紧凑编码，并为超出 byte 范围的坐标增加显式扩展标记，避免坐标截断。
- 外围挖掘的掉落收集范围跟随内容边界；自动归块扫描采用同一范围预算，并读取扩建后的完整内容。

## 保留的资源边界

不再以**创建时的边界**拒绝扩建，但也不是无限扩建：

| 约束 | 上限 |
| --- | --- |
| 技能标记结构的方块数 | 256，保证既有归块事务可以接收 |
| 未标记结构新增内容的方块数 | 4096，与渲染容量对应 |
| 单轴跨度 | 256 格（放置检查包含预留邻域） |
| 扫描体积 | 262144 格 |
| 局部 X/Z 可寻址范围 | −512～511 |
| Y 范围 | 存储维度的构建高度，默认 −64～319 |

区块在被触达后保留租约，直到结构归块、移除或关闭；本轮没有增加逐次缩边卸载空区块的机制。未知模组的放置回调若任意写入邻域之外，不在本轮多格放置保障范围。原有保护检查、物品消耗和自动归块准入继续生效。客户端与服务端应同时更新，旧客户端无法解码扩大的视图。

## 验证

证据汇总：`validation/native_structure/sublevel_growth_validation_2026_10_24.json`。

`test` 3201 项、`sableCompatTest` 32 项均通过，开发版和发布版 `build` 均通过。另在无 Sable、本轮固定的 Sable＋Create＋航空学环境各完成 6 项既有 payload 回归：箱子、木桶、双陷阱箱、含水方块、未生成战利品箱、取消归块后重试。这两组回归后只增加了旧 plot 的菜单迁移保护与超额存档减块预算保护；最终扩建及重启测试包含这两项保护。

实际 Minecraft 客户端使用独立 `run/native-sublevel-direct-smoke-growth-*` 存档，验证：

- 新 plot 与旧紧凑 plot 六方向扩建，包括 X=−33、34，越过原 32 格存储边界。
- 玩家放置、旧放置接口、节点共用 CAS；取消放置不扣物品，门保留上下两半。
- 世界原点不变，外围碰撞与客户端视图同步，拆除后范围收紧。
- 对边界外的新增方块执行生存挖掘，实际触发全局 `BlockDropsEvent`，在宿主世界恰好生成一份掉落。
- 旧 plot 的 37 个钻石完整迁移，旧内容清除、旧租约释放。
- 通过旧 USE 接口打开箱子时推迟迁移，关闭菜单后才允许继续；预算单测覆盖超额旧存档减块及两格放置替换已有方块的计数。
- 关闭并重启后扩建内容和完整箱子 NBT 一致；加上技能标记后静止自动归块，并复核目标库存。
- 在 `COPY_SAVED` 和 `OWNER_SAVED` 两个迁移检查点强制退出进程，分别重新启动，恢复正确所有者并再次完成扩建。

复现示例（JBR 25）：

```powershell
./gradlew.bat test sableCompatTest build -DisDev=true
./gradlew.bat build -DisDev=false
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name growth-check -Fresh
./gradlew.bat runClientDev -DisDev=true -I validation/native_structure/sublevel_growth.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-growth-check
./gradlew.bat runClientDev -DisDev=true -I validation/native_structure/sublevel_growth.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-growth-check -PacademyGrowthMode=verify
```

迁移中断使用新的独立副本，先 `-PacademyGrowthMode=crash_COPY_SAVED` 或 `crash_OWNER_SAVED`，再用同一副本执行 `recover_COPY_SAVED` 或 `recover_OWNER_SAVED`。中断进程退出码 86 为预期；必须同时检查检查点报告及后续恢复报告，不能将 Gradle 的失败退出本身当作测试通过。首次客户端的依赖模组图标格式提示可以正常关闭；测试副本的 `neoforge-client.toml` 可设置 `showLoadWarnings=false` 以自动运行，警告仍保留于日志。

原 test/test2 和用户安装的模组未被改写。工作区已有的 `native/academy_physics/src/lib.rs` 改动不属于本轮，保留并排除提交；打包使用既有原生库，不依据这份未提交 Rust 源码重新生成 DLL。

本轮没有做大型服务器的长期负载基准，也没有模拟真实硬盘 IO 损坏。迁移中断验证采用进程强制退出；原子 manifest 未引用的存储副本不会发布为结构，后续新区域分配会清理未归属内容，旧紧凑区域的孤立磁盘残留不由本轮主动扫盘清除。
