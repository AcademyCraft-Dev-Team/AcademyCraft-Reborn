# 九技能客户端验证

入口：`src/editor/java/org/academy/desktop/grapheditor/preview/DarkmatterConceptClientSmoke.java`，仅包含在独立编辑器开发运行，不进入模组发行包。

从仓库根目录运行：

```powershell
.\gradlew.bat runClientDev -DisDev=true -I docs/vfx/darkmatter_concept_v2/runtime/client.init.gradle
```

运行复用隔离目录 `run/ability-vfx` 中的 `railgun` 测试世界，在平坦平台调用九技能的实际服务端入口。验证创造/抹消、塑形、相态调谐、实体/方块解构、普通/γ 延迟切割、干涉停止、治疗、三个真实独角仙召唤以及六翼开启/第一人称/翼击/关闭。三只构造生命分别使用步行、飞行和游泳躯干，真实蓝图 AI 负责动作和炮击；效果由游戏现有事件与网络桥接触发，没有直接构造 VFX 示例。

游泳场景分两步：先通过实际 `tryProgramCreate` 在干地成功生成游泳蓝图实体，再由测试场景把该实体放进玻璃水箱。生产程序召唤在请求点检查碰撞后创建实体；测试只安排场景位置，不修改该规则。水箱用玻璃顶盖保持封闭真实水体，没有冻结 AI 或改写水中标志。服务端、客户端以及近距截图前都硬检查 `isInWater`。清场包含前次失败水箱的完整高度，游泳前 100 个服务端 tick 记录服务端游戏时间、位置、速度、水/碰撞状态和当前方块。

逐 tick 诊断定位了既有上浮行为：`FloatGoal` 在构造时对初始导航设置 `canFloat(true)`，随后 `applyBlueprint → configureNavigation` 换成新的 `AmphibiousPathNavigation`，没有保留该设置。当前 26.2 的 `Mob.jumpInFluid → jumpInLiquidInternal` 在导航 `canFloat=false` 时每次液体跳跃给纵速加 `0.3`，与实测速度一致；实体在第 8 个服务端 tick 上浮到 `y=86.6` 并受真实顶盖碰撞阻挡，仍持续 `isInWater=true`。此行为在本轮之前已经存在，本次遵循保持玩法的要求，没有修改上浮或导航逻辑。验收确认游泳蓝图的实际入水、模型和水中动画，不代表自然水下导航已修复；此前失败不能归因于安全召唤或截图耗时。

加载后先预热 200 tick，让既有资源恢复提示结束，再执行 612 tick 的实际技能流程并自动退出。创造/抹消用第一人称掌部视角，同时捕获开始后 4/7/10 tick 的动作序列；塑形/调谐保留第三人称观察。构造生命另有距离约 1.88 米、50 度 FOV 的近距观察，让角、甲壳、足和薄翼完整进入画面。

截图和短动作序列写入本目录；只在所有断言成功时生成 `smoke_result.txt` 并在日志输出 `[darkmatter-concept-smoke] PASSED`。启动或截图存在并不代表验证成功，需同时检查退出结果、该标志及截图中的造型/范围/遮挡。

旧 `DarkmatterVfxClientSmoke` 与其初始化脚本保留，继续用于穿透尾羽、室内/日夜干涉、背包修复等专项验证。108 种部位组合由 JUnit 覆盖，本客户端流程只观察三个实际躯干场景。

2026-10-07 性能更新后的最终摄影运行正常退出，日志 `build/darkmatter-all9-standing-final.log` 为 `BUILD SUCCESSFUL`，本目录 `smoke_result.txt` 为 `PASSED`。观察到 15 种真实效果图，γ 切割有 2 个独立实例，真实翼击、炮弹轨迹、实体接触均触发，技能结束后没有存活的未元物质图实例。近距三图为 `08_creation_walk_close.png`、`08_creation_fly_close.png`、`08_creation_swim_close.png`；测试生成后清除实体名称，仅为避免准星选中时的大字遮挡。入口明确恢复摄影玩家的正常重力，避免复用存档继承其他压力 fixture 的 `NoGravity` 状态；站立夹角与石墙裁剪另由 `wings_block.init.gradle` 硬检查真实落地并摄影验证。
