# 创建世界卡死：AcademyCraft / C2ME 配置生命周期修复

日期：2026-09-12

## 根因与现场证据

目标进程为 PID 29072，Minecraft 26.2、NeoForge 26.2.0.70、C2ME 0.4.2-alpha.0.96。
原实例目录：D:/MC/cs/.minecraft/versions/ACclient1.1。

原实例 latest.log 第 2849–2861 行记录：
- 20:31:21，c2me-worker-11 将主世界区块 [0, 0] 升级到 minecraft:features 时失败。
- IllegalStateException: AcademyCraftServer has not been initialized.
- 调用链：MinecraftServer.getAcademyCraftServer → AcademyCraftServer.isImagPhaseGenerationEnabled → ImagPhaseLakeFeature.place。

build/hang-29072-threads.txt 第 1395 行起记录服务端在 setInitialSpawn → PlayerSpawnFinder → ServerChunkCache.getChunkBlocking → managedBlock 等待。
这与 C2ME 记录生成失败后服务端仍等待区块的现象一致。原始异常已经被记录，不能将其描述成完全没有日志。

AcademyCraftServer 在 ServerStartedEvent 才创建；首次出生点区块生成早于此事件。
原来的 server == null 分支无效，因为 getAcademyCraftServer() 在返回之前就会抛异常。
这个生命周期错误也不应依赖非 C2ME 环境恰好未触发湖泊来规避。

## 修复

- 每个 MinecraftServer 单独持有 AcademyCraftConfig，通过 volatile 字段和同步初始化安全发布。
- ServerAboutToStartEvent 预加载并解析 GenericConfig，正常区块工作线程只读取已加载配置。
- 虚相湖开关不再访问尚未创建的 AcademyCraftServer。
- 后续运行时复用同一个配置对象，保留关闭生成、旧 genPhaseLiquid 配置迁移及不同服务器之间的隔离。
- 保持运行时在原有启动阶段初始化，避免将能力、无线网络、音乐等依赖世界的系统提前创建。
- 没有修改 C2ME、捕获并忽略生成异常，或通过一律返回 true 绕过用户配置。

## 验证

JBR 25.0.3，NeoForge 26.2.0.70；从原实例复制同一个 C2ME jar 到工作区隔离目录。

| 检查 | 结果 |
| --- | --- |
| gradlew test -DisDev=true | 2,015 项测试，0 失败，0 错误 |
| gradlew build -DisDev=true | 通过 |
| gradlew build -DisDev=false | 通过 |
| C2ME 独立服务器，开启湖泊 | 启动前 runtimeInitialized=false、enabled=true；81 个 FULL 区块、30,181 个虚相液体方块；正常保存退出 |
| C2ME 独立服务器，关闭湖泊 | 启动前 runtimeInitialized=false、enabled=false；81 个 FULL 区块、0 个虚相液体方块；正常保存退出 |
| runClientDev + C2ME 集成服务器 | 启动前配置可读；81 个 FULL 区块、31,043 个虚相液体方块；CLIENT_JOIN_PASS；正常退出 |

隔离压力测试将 lake_imag_phase_placed 的 rarity_filter chance 从 24 改为 1，保证实际覆盖湖泊生成；生产数据包保持不变。
客户端只复制世界元数据和测试数据包，不复制区块文件，实际重新生成区块。
首轮普通频率测试没有采样到湖泊，因此不计作湖泊生成验证；后续以高频测试断言为准。
客户端测试目录关闭 NeoForge 的模组图标弃用警告弹窗，原实例设置保持不变。

日志与临时探针均在忽略目录：
- build/worldgen-test.log
- build/worldgen-build-dev.log
- build/worldgen-build-release.log
- build/worldgen-c2me-stress-enabled.log
- build/worldgen-c2me-stress-disabled.log
- build/worldgen-c2me-client-final.log
- build/worldgen-validation/run.init.gradle
- build/worldgen-validation/src/org/academy/validation/WorldgenProbe.java

新增持久回归测试：src/test/java/org/academy/AcademyCraftWorldgenConfigTest.java。
覆盖运行时缺失时 8 个工作线程的 256 次读取、禁用配置、旧配置迁移、实例隔离与配置对象复用。

## 交付与范围

发布包：build/libs/academy-26.2.0-0.0.4-alpha-release.jar
SHA-256：79C254239EC539F79C3CF6345EADA39C636ADFB7B38D8997B3D0926E37D22C52

验证覆盖同版本 C2ME 与 AcademyCraft 的独立/集成服务器组合，不等同于完整原整合包所有模组组合均已验证。
未终止 PID 29072，未替换原实例模组，未修改原存档。当前卡死进程不能通过磁盘上的源码修复自动恢复，需要退出后替换旧模组并重启。
