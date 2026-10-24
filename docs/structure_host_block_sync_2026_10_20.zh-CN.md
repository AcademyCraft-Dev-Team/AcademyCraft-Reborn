# 捕获源区块在 FULL 非 ticking 状态下的同步

日期：2026-10-20。

## 范围与证据边界

这是一项独立的源方块同步缺陷，不等于已经解释用户在 `test2` 草地遇到的“不可见但移动或跳跃被挡住”。
后者仍需检查玩家接触、空间空洞和姿态一致性。

最初步行夹具的 `source != AIR` 失败，后经双端读取证实为同一格自然流入 `water[level=4]`。
该失败不是客户端残留石块；步行夹具加防水边界后双端源格为空气。

## 原因与修复

本地 26.2 源码中存在两个独立门槛：

1. `Level.markAndNotifyBlock` 即使有 `UPDATE_CLIENTS`，也仅在 `LevelChunk.getFullStatus() >= BLOCK_TICKING` 时调用 `sendBlockUpdated`。
2. `ChunkHolder.blockChanged` 仅在 `getTickingChunk()` 非空时记录待发送格子。单独补调 `sendBlockUpdated` 仍不能解决 FULL 区块。

捕获支持已经加载的 FULL 区块。客户端已有该区块基底，但服务端尚未晋级或已降级到 FULL 时，源格删除可以成功而缺少客户端更新。

新增 `StructureHostBlockUpdates.publish`：

- 在完成源格批量删除后，按 section 合并坐标，用当前服务器方块状态构造原版 `ClientboundSectionBlocksUpdatePacket`。
- 只发给原版 `ChunkMap.getPlayers` 返回的已经跟踪该区块、仍在同一维度的玩家；尚未收到区块基底的客户端由后续完整区块包得到当前内容。
- 不强加载缺失区块，不周期性刷新整座结构，也不改变原版方块更新 flags 或邻居逻辑。
- 回滚后同步实际状态，包括部分回滚；需要时追加真实方块实体更新包。
- 接入通用捕获协调器和 debug SubLevel 捕获路径。

## 实际客户端对照验证

`SubLevelHostBlockSyncSmoke` 在独立世界先让客户端观察到两块石头，再临时调整真实区块票据，达到目标区块 `FULL`、`getTickingChunk()==null`、仍被该客户端跟踪的状态。
普通客户端模拟距离的合法下限是 5；第一次探针误用 2 被原版回退成 12，不能作为验证成功。第二次使用 view=10、simulation=5 和距离 8 个区块的目标。

`build/host-block-sync-r2.log` 记录：

- `SUBLEVEL_HOST_SYNC_FULL`：FULL、非 ticking、已跟踪、非模拟区域。
- 控制格使用原有 flags 直接删除：服务端空气，客户端继续为石头。
- 另一格执行真实 `StructureTransactions.capture`：服务端与客户端均变为空气。
- 对控制格调用新同步方法后，客户端也变为空气。
- 最后恢复测试票据、清理测试结构，出现 `SUBLEVEL_HOST_SYNC_PASSED`。

这条对照实验确认同步修复有效，没有借助客户端直接改方块或生产测试旁路。它不能用于宣称所有第三方模组附件的回滚同步已验证。

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat runClientDev -DisDev=true @physicsNativeArgs `
  -I validation/native_structure/sublevel_host_block_sync.init.gradle `
  -PacademyHostBlockSyncDirectory=run/native-sublevel-host-sync-r2 --console=plain
```

必须预先准备新的隔离世界目录；上述 `r2` 是本轮已用过的证据目录，不应再次覆盖。
