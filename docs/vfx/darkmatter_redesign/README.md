# 未元物质重做与验收（2026-09-19）

本轮在新方案上增加了可见性调整、减小羽片锯齿、独立命中反馈、实体／方块解构烟雾，以及追猎词条与六翼干涉共用的羽刃外观。没有恢复螺旋丝线或从玩家眼位发出的干涉光束。

后续更新：普通干涉的范围主体现已使用 [场景泛光与空气散射](../darkmatter_interference_field/README.md)，下文关于稀疏媒介与地面光斑的描述保留为早期实现记录。游戏内回归截图包含新的范围着色器。

## 实现

- **干涉**：每个施术者维持一个场，8 片稀疏侧缘／上缘媒介和 6 处地形受光斑驳；根据环境亮度和太阳方向调整材质。命中后另行播放约 0.18 秒的目标表面白色接触斑与细屑。日夜变化只影响外观，不改变伤害。
- **视野与羽片**：普通羽片锯齿振幅从 0.065 收到 0.018，频率从 29 收到 18，并扩大边缘过渡。媒介在相机 1 格内完全隐藏，1–1.9 格淡入；第一人称近处准星方向额外衰减，目标命中点仍保留。
- **修复**：成功结算后才发送身体／实际可见装备部位。连续脉冲更新同一个图，背包内物品单独修复不生成世界补片。人物使用身体骨骼锚点；第一人称采用保守手部区域，并进一步缩小补片。无实际变化不扣 MP 的规则不变。
- **解构**：18 片剥离薄片、28 颗细屑、14 团柔软烟雾，约 0.65 秒内由表面向外扩散、变淡。实体只在实际伤害／死亡后反馈；方块只在确实移除后采样露出的面。保留既有掉落与原版破坏反馈。
- **羽刃**：追猎和六翼干涉使用同一实体及 `darkmatter_feather` 图。两片交叉的白色薄刃沿速度方向排列，带浅锯齿和亮脊。命中后取消追踪并沿原方向穿出，随后消失；保留单次伤害，不追加后排伤害。同期重叠碰撞也不会重复结算。
- **生命周期**：图按目标／类型合并，客户端最多维持 64 个主体效果，显式停止、失去刷新、目标失效、切换世界都会清理；表面采样绑定支持图热重载。羽刃随实际实体创建和移除。

参考：[一方通行 VS 未元物质](https://www.bilibili.com/video/BV1q7411Y77o/)、[Blender 消散几何节点资产](https://www.bilibili.com/video/BV1U8AuzeE87/)。后者登录后核对约 2:26 的表面分裂、边缘细化画面，采用其运动关系，没有下载或复制视频资产。

## 画面

以下是实际编辑器 GPU 渲染与隔离开发客户端截图；GIF 为连续截图的采样回放，播放速度不代表精确游戏帧率。

| 内容 | 查看 |
| --- | --- |
| 羽刃材质与轮廓 | [编辑器羽刃](preview_darkmatter_feather.png) / [游戏内采样](client_feather_sequence.gif) |
| 独立命中反馈 | [材质预览](preview_darkmatter_light_contact.png) / [实际干涉命中](client_interference_contact.png) |
| 实体解构 | [剥离预览](preview_darkmatter_disassemble_peel.png) / [消散预览](preview_darkmatter_disassemble_eroded.png) / [游戏内采样](client_disassemble_sequence.gif) |
| 方块解构 | [移除后](client_disassemble_block.png) / [烟雾阶段](client_disassemble_block_smoke.png) |
| 环境受光与视野 | [白天开启前](client_day_before.png) / [开启后](client_day_active.png) / [夜间](client_night_active.png) / [室内](client_indoor_active.png) |
| 修复部位与清理 | [身体](client_repair_body.png) / [连续治疗](client_repair_body_continuous.png) / [手部](client_repair_hand.png) / [仅背包](client_repair_inventory_only.png) / [清理后](client_cleared.png) |

这是基于表面代理的粒子表达，不会切割 Minecraft 的实体模型。非玩家模型采用包围盒表面代理；手持物和第一人称补片没有声称精确采样任意物品网格。多人高密度叠加及特殊模型的人工观感仍需实际游玩检查。

## 复现与验证

使用 JetBrains Runtime 25 和仓库 Gradle wrapper：

```powershell
node tools/vfxgraph-editor/scripts/author-darkmatter-vfx.mjs
.\gradlew.bat test editorTest -DisDev=true
.\gradlew.bat runGraphEditor -I tools/vfxgraph-editor/scripts/darkmatter-vfx-capture.init.gradle -DisDev=true
.\gradlew.bat runClientDev -I tools/vfxgraph-editor/scripts/darkmatter-vfx-client.init.gradle -DisDev=true
.\gradlew.bat runGameTestServer '-PacademyGameTests=academy:darkmatter_resource_*' -DisDev=true
.\gradlew.bat build -DisDev=true
.\gradlew.bat build -DisDev=false
```

- 全量 JUnit：2,143 项通过；编辑器：103 项通过；未元物质 GameTest：19 项通过。
- 图脚本的结构验证全部通过，五份新图没有 arc 输出。烟雾扩张／淡出、羽刃持续可见、实例粒子上限、表面重绑定、协议读写有回归覆盖。
- 客户端探针实际执行干涉、治疗／物品修复、实体与方块解构及羽毛发射；断言实际伤害、方块移除、连续效果合并、空修复不扣 MP、穿出后的单次伤害与停止清理。最终日志有 `[darkmatter-vfx-smoke] PASSED`。
- 开发与发布构建均通过。探针／截图程序位于 `src/editor`，不进入 mod JAR。
- 客户端启动仍出现已有的 `Missing uniform Globals` 资源重载回退警告（旧的矢量风眼验收日志也存在）；新图完成加载和渲染，没有新增材质编译错误。截图保留了该警告，没有裁掉它。

日志位于 `build/darkmatter-{verify,client,gametest,dev-build,release-build}.log`，不纳入提交。
