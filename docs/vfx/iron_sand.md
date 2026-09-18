# 铁砂操作：VFXGraph 重制

参考：用户提供的 `6842f06acec3a37e8d0e0f3e9009b5c9.mp4`（30.62 秒）。
提取厚重黑色砂流、破碎颗粒边缘，以及砂流内部的蓝白电弧；铁屑贴图为本项目重新绘制。

## 资产与行为

资产位于 `src/main/resources/assets/academy/vfxgraph/`：

| 资产 | 行为 |
| --- | --- |
| `iron_sand_defense.json` | 玩家脚边半径 2 格的低矮砂环，2800 个铁砂粒子簇，8 段蓝白双层电弧 |
| `iron_sand_guard.json` | 朝伤害来源从砂环升起弧面盾，约 0.09 秒成形，随后回落，0.65 秒结束；支持上方来袭 |
| `iron_sand_intercept.json` | 在弹射物首次进入防御球的位置，从上方 1.8 格向下劈过目标；延伸 0.055 秒，总长 3 格，0.24 秒结束 |
| `iron_sand_whip.json` | 按施放朝向扫过 120° 的密集铁砂鞭；范围来自技能同步数据 |
| `iron_sand_cloud.json` | 六条反向流动的密集砂流组成链锯云雾；范围来自技能同步数据 |

铁砂层用普通透明混合，电弧层单独进入 bloom。电弧共用电弧激发的
`ElectricArcEmitter`、`BlueWhiteArcStyle` 和 `arc_generate.json` 输出材质。
`electric_bolt` 新增可选的 `growth_time`、`downward`、`origin_y`，默认值保留既有技能行为。

铁砂节点 `vfx.block.iron_sand` 为独立于实体的解析式发射器。颗粒和电弧使用同一形状函数，
支持暂停、重置、单步和参数热重载；粒子及电弧按发射器替换，不随运行时间积累。
粒子选项与距离降低细节；持续特效按实体管理，短事件限制总实例数，退出世界时清理。

## 服务端事件

- `HealthLossGuards.set(..., onAbsorbed)` 仅在资源付款且生命写入成功后通知。
  预览、失败退款、维护写入不生成受击护盾。
- 受击方向取当前伤害事务的来源，包含高度；无方向的生命损失退回朝向。
- `HostileProjectiles.sphereEntry` 返回扫掠线段首次进入球体的位置，避免高速弹射物的
  拦截光效留在上一 tick 的远处位置。
- `DefenseVisualPacket` 只由服务端发送到同维度附近观察者。防御持续状态继续使用现有附件同步。

## 编辑器开发与复现

使用 JetBrains Runtime 25，在仓库根目录执行：

```powershell
# 可连接的交互编辑器
.\gradlew.bat runGraphEditor -DisDev=true

# 经项目编辑器 API 打开五组资产、暂停、重置、单步并检查预览
node tools/vfxgraph-editor/scripts/iron-sand.mjs --preview

# 从制作配方重新生成五组资产（会覆盖这五组资产，带 SHA 校验及编辑器备份）
node tools/vfxgraph-editor/scripts/iron-sand.mjs --author

# 重绘铁砂贴图
java scripts/GenerateIronSandTexture.java

# 使用编辑器同一 GPU 渲染器批量截帧
.\gradlew.bat runGraphEditor -DisDev=true -I tools/vfxgraph-editor/scripts/iron-sand-capture.init.gradle

# 在既有专用测试存档 run/electromaster-rework/saves/railgun 中测试
.\gradlew.bat runClientDev -DisDev=true -I tools/vfxgraph-editor/scripts/iron-sand-client.init.gradle
```

暴露参数在图的参数面板调整；MCP 对应 `update_parameter`，调试后使用 `reload/reset/step`。
`count` 控制粒子簇数，`grain_size` 控制每簇尺寸，`band_width` 控制厚度，
`arc_count/arc_width` 控制内部电弧，`source_elevation` 可预览高低方向来袭。

独立编辑器通过 `VfxGraphRenderer(textureLoader)` 使用自身纹理服务，避免访问不存在的
Minecraft 单例；游戏仍使用 Minecraft 纹理管理器。

## 验证产物

- 编辑器静帧及护盾生长/回落序列：`build/iron-sand-editor-captures/`。
- 实机：`build/iron-sand-client/`，包含左右护盾、弹射物切断、第一/第三人称云雾及关闭清理。
- 单元测试覆盖两格边界、粒子预算、消失/重置、上方来袭、技能实时范围、蓝白材质复用、
  电弧向下生长、网络坐标精度和高速弹射物入射位置。
- `academy:electromaster_rework_*` GameTest 覆盖实际生命写入、资源、攻击和弹射物；
  生命保护测试同时检查成功通知及预览/退款不通知。

截图与运行日志保留在 `build/`，不加入版本控制。
