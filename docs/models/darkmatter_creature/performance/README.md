# 独角仙性能对照场景

这套独立 fixture 只随 `editor` 源集编译，不修改正式技能、实体行为或既有九技能验收。输出来自实际 JVM / 游戏测量；尚未运行的标签不代表已经通过验证。先保存 `baseline`，优化后在相同 JVM、场景、分辨率和配置下运行 `optimized`。

## 固定模型姿态

```powershell
.\gradlew.bat beetlePoseBenchmark -DisDev=true -I docs/models/darkmatter_creature/perf.init.gradle -Dacademy.beetlePerformanceLabel=baseline
.\gradlew.bat beetlePoseBenchmark -DisDev=true -I docs/models/darkmatter_creature/perf.init.gradle -Dacademy.beetlePerformanceLabel=optimized
```

`BeetlePerformanceCaptureMain` 不创建窗口或游戏世界。它烘焙正式详细骨架，在一份共享 model 上交替运行飞行、游泳、步行状态；108 种四槽组合各测 16 / 24 / 32 格，共 324 个预先建立的状态。预热 81,000 次后测六批，每批 64,800 次 `setupAnim`，并消费实际头部/躯干姿态以防无用结果被消除。

`{label}_pose.json` 记录 JVM、体块/部位数、每批调用数和时间、批次平均值的中位数、GC 次数与姿态校验值。这个数值包括固定遍历和校验开销，只表示详细骨架 CPU 姿态成本，不表示渲染 FPS、每次调用的 P95 或低模绘制成本。

## 真实多人所有权与实体场景

```powershell
.\gradlew.bat runClientDev -DisDev=true -I docs/models/darkmatter_creature/perf.init.gradle -Dacademy.beetlePerformanceLabel=baseline
.\gradlew.bat runClientDev -DisDev=true -I docs/models/darkmatter_creature/perf.init.gradle -Dacademy.beetlePerformanceLabel=optimized
```

`BeetlePerformanceClientSmoke` 复用隔离 `run/ability-vfx` 的 `railgun` 世界，窗口请求 1280 × 720、70° FOV，关闭垂直同步并统一帧率上限。场景坐标为 `(80, 81, 188)`，与九技能摄影 fixture 分开。它连接八名真实服务端玩家，其网络通道为嵌入式 mock；每名玩家真实学习、启用未元创生，走资源池和 `tryProgramCreate` 入口，每人最多十六只，不绕过实际召唤数上限。

实体数量顺序为 0 / 16 / 64 / 128，每个数量分别观察名义距离 16 / 24 / 32 格。零实体场景保留相同玩家、平台、水体和目标，用来估计共同成本。实体包含步行 / 飞行 / 游泳三类，每类配不同头部、肢体和附加件。保留 AI、导航、物理和服务器技能 tick。游泳场景使用真实水体与玻璃顶，保留此前记录的 `applyBlueprint` 换导航后丢失 `canFloat`、液体 jump 为 `+0.3` 的既有行为；本轮性能测试不修正玩法。

每段先稳定六十个客户端 tick，随后至少运行四十个客户端 tick，并收集一百二十帧 GPU 时间戳；截图在测量结束后保存，避免截图阻塞污染计时。输出 `{label}_client.json` 与十二张 `{label}_stage_*.png`，记录实际客户端追踪实体数、三类躯干数量、移动实体到相机的实际距离、GPU 帧时间、CPU 渲染事件时间、帧间隔和服务器 tick 时间，均保存样本量、中位数和 P95。

默认不主动命令攻击。需要额外炮弹/命中负载时，可以增加 `-Dacademy.beetlePerformanceCombat=true`，此时三类蓝图带集火模块，所有者用真实最后攻击目标驱动战斗。战斗与非战斗数据应使用不同标签分别比较，不能混为同一基线。

嵌入式玩家会触发真实所有权、技能和实体追踪，但不等同八台远程客户端；网络带宽与远程 GPU 仍需独立测试。距离是到场景中心的名义值，正常移动可能改变实体距离，JSON 同时报告实际距离。GPU / CPU 帧与服务器 tick 是整个共同场景的成本，不能直接解释成独角仙单独成本，也不应从 CPU 渲染事件时长推算游戏 FPS。

优化后的客户端在预热前增加未入世界的真实实体契约检查：应用蓝图后派生 key 的替换、所有者改变、真实 NBT 保存/读取重建，以及迟注册头部和模块 handler 的兼容。测试扩展只使用 fixture 专属注册 key，不参与计时场景的蓝图。检查结果记录在 `runtimeContracts`；测量实体数量、相机、场景和采样流程保持一致。单元测试另检查全部 108 组合在三档距离下 A→B→A 回切、详细/低模关节一致和近距公开动作结果一致。

当前测量与摄影限制见 [COMPARISON.md](COMPARISON.md)。旧两轮 32 格眼位位于原有 `packed_ice` 内，原始 JSON 与黑屏截图保留，不作无遮挡低模效果结论。独立补验使用显式参数，不根据 label 隐式变更场景：

```powershell
.\gradlew.bat runClientDev '-DisDev=true' -I docs/models/darkmatter_creature/perf.init.gradle '-Dacademy.beetlePerformanceLabel=optimized_camera_clear' '-Dacademy.beetlePerformanceClearCamera=true'
node docs/models/darkmatter_creature/performance/report.cjs
```

仅 32 格摄影点局部清为空气并增加落脚；真实客户端八角眼位在计时前后均检查 `isViewBlocking`。补验数据只作该无遮挡场景的独立计量，不与旧受挡 32 格数据直接同比。报告生成器读取原始 JSON，保留 64 只 / 24 格 GPU 回退，不从零实体共同成本相减推算独角仙单独耗时。

原两轮摄影为固定悬空眼位把真实观察玩家设为 `NoGravity=true`，结束时未恢复，这个字段保存进复用世界后影响了后续方块阻隔 fixture 的正常落地检查。补验修正仅在 fixture：配置前记录原始 `noGravity`、`invulnerable`、`flying` 和游戏模式，清场时恢复，并等待客户端收到物理状态后再退出保存。新归档记录 `viewerStateBeforeFixture`、`viewerStateAfterCleanup` 与 `viewerPhysicalStateRestored`，旧两轮 JSON 不改；这是测试状态清理，未变更生产物理或阻隔采样。
