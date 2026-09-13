# 区块跃迁到末地时的崩溃修复

## 证据与原因

2026-09-13 09:21:59 的服务器报告保留了最初异常：

`C2ME VanillaWorldGenerationDelegate → ChunkGenerator.applyBiomeDecoration → BetterEnd be_placeSpike → WorldConfigImpl.saveFile → NbtIo.writeCompressed → CompoundTag.write → Object2ObjectOpenHashMap.MapIterator.nextEntry`

异常为 `this.wrapped is null`。09:22:29 的报告也发生于 `minecraft:end_spike`，但没有原始调用栈。较晚的世界 tick 报告只保留了空指针类型，不能单凭该报告确认其原始抛出位置。

核对用户提供的 BetterEnd 26.2.3、WorldWeaver 26.2.2 和 C2ME 0.4.2-alpha.0.96 字节码后，确认末地尖塔生成任务会直接读写 WorldWeaver 共享的 pillars CompoundTag，并立即保存整个根标签。末地基座还会向根标签写入 portal。并行生成时，这些写入与 NBT 遍历没有共同的同步保护，导致底层 fastutil 映射的迭代状态失效。

## 修改

- WorldWeaver 的根标签取得、嵌套标签创建和保存使用同一把可重入锁。
- 在 BetterEnd 的 mixin 合并完成后，精确替换尖塔生成、尖塔高度读取和末地基座中的七处 NBT 访问，与保存操作共享锁。仅匹配指定目标类、BetterEnd 的 MixinMerged 来源和方法签名；不改动其他模组的处理器。
- 锁内不包含区块请求、地形高度查询或方块生成，以免持锁等待其他生成任务。
- WorldWeaver 未安装时跳过其伪 mixin；BetterEnd 未安装时字节码替换为无操作。
- 保留区块跃迁的加载失败处理：异常或失败 ChunkResult 均终止后续操作、释放本次加载票据，并向玩家反馈原因。只有区块及邻居已驻留后才允许继续。

## 验证方式

使用 JetBrains Runtime 25 和仓库 Gradle wrapper：

- `test -DisDev=true`：完整单元测试，包括并行 pillars/portal 写入与 NBT 序列化、异常后锁释放、字节码转换边界和预加载失败传播。
- `runGameTestServer -DisDev=true -PacademyGameTests=academy:chunk_leap_end_player_preload`：预加载末地、创建测试玩家、传送并继续运行 40 tick。分别验证不装第三方模组，以及 BetterEnd / WorldWeaver / BCLib / WunderLib / C2ME 组合。
- 从实际运行导出的类检查七处 NBT 调用，确认均使用共享锁；WorldConfigImpl 的三个入口也已包装。
- `build -DisDev=true` 和 `build -DisDev=false`。

组合 GameTest 使用隔离目录 run/worldweaver-compat-gametest。BetterEnd 在开发模式注册调试物品会先触发无关的 intrusive holder 错误，因此只在测试副本中移除了 EndItems.ensureStaticallyLoaded 对 DebugHelpers.generateDebugItems 的调用。世界生成代码保持原样。该测试绕过不进入 AcademyCraft 发布包，也不修改用户下载的原始模组。

验证日志位于 build/worldweaver-*.log。此测试覆盖服务器侧生成和传送；用户原存档及完整客户端交互没有在本地复现。
