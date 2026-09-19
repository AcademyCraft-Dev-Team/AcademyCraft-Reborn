# 未元干涉范围着色器（2026-09-19）

普通干涉的范围主体已替换为场景表面泛光与空气散射。范围内可见方块保留纹理并泛白，实体按中心点选中后覆盖真实可见模型；从外部可看到沿环境主光排列的白色光带，相机进入范围后空气层平滑降低至约 20%。独立命中反馈、追猎与六翼羽刃继续使用原来的 VFXGraph。

## 范围与材质

- `DirectionalArea` 保存 α、β 两个球形扇区的最终射程、角度和完整视线方向。服务端扫描与网络快照共用该数据，保留熟练度、六翼和技能射程倍率。浮点显示元数据的上限不会截短实际扇区。
- 不因相位权重为零而隐藏分支；原有伤害、敌对过滤、资源结算、命中反馈不变。范围泛白不代表已经造成伤害。
- 地形按场景深度逐像素重建世界坐标。实体重绘真实模型的材质遮罩，未选中的前景实体会排除地形覆盖；名字、着火效果、阴影和轮廓不重复绘制。玻璃、水保留原 UV、材质透明度及可见表面深度。
- 表面边缘在区域内约 0.22 格衰减，HUD、天空和第一人称手部不参与表面覆盖。空气散射被观察者场景深度截断，墙后的物体不会因范围着色而透视。
- 多个范围按最大覆盖合并，并限制最终强度；每批处理 16 个场，超过后继续分批，没有用固定场数量裁掉实际范围。18 个重叠场经过客户端验证。
- 快照随技能每 4–5 tick 的实际脉冲更新，停止包立即清理；断更超过 12 个世界 tick、已跟踪施术者失效或切换世界时清理。远处尚未跟踪到施术者的范围仍能根据快照显示，并受同一超时约束。

## 渲染与性能

`WorldSurfaceMasks` 提供材质遮罩，`DirectionalFieldRenderer` 提供表面与散射合成，两者位于公共渲染 API。透明地形复用当前区块网格；实体使用独立的提交与缓冲区，避免扰动正常世界渲染。

表面使用完整分辨率，空气使用半分辨率、32 次采样和深度感知上采样。空气先按范围包围球与观察射线求交，并按投影包围区域限制屏幕绘制；跨相机平面的包围区域保守采用全屏。实体表面维持全屏遮罩，避免裁掉中心位于范围内、模型伸出边界的大型实体。分辨率变化重建纹理，停止与世界切换释放场景纹理，客户端退出关闭缓冲区。

最终实测：1920×1080，NVIDIA GeForce RTX 5070 Ti，OpenGL，默认资源。固定同一场景，每组 120 帧，GPU 时间戳测量整帧；包含遮罩与现有 GlowEffect 的成本。

| 状态 | GPU 帧耗时中位数 | P95 |
| --- | ---: | ---: |
| 关闭 | 0.559 ms | 0.564 ms |
| 单个干涉场 | 1.095 ms | 1.208 ms |

本机中位数增加约 0.54 ms。这是固定测试场景的 GPU 耗时，不是端到端帧率或所有硬件的性能保证。原始摘要见 [performance.txt](performance.txt)。

空气采用环境主光与局部亮度近似，不追踪每个火把、窗孔或光源阴影。透明层使用最前方可见材质深度近似，多层透明介质没有独立光线传输求解。尚未对 Iris 外部光影包或任意第三方自定义实体渲染器做兼容性验收。

## 实机画面

| 检查 | 截图 |
| --- | --- |
| 相同位置开启前／后 | [关闭](outside_before.png) · [外侧](outside_side.png) |
| 内部视野与真实模型泛光 | [范围内](inside.png) · [实际技能](actual_skill.png) |
| 范围、玻璃、水、前景遮挡 | [前侧](outside_front.png) · [透明材质遮罩](transparent_mask.png) |
| 环境变化 | [夜间](outside_night.png) · [屋顶下](indoor.png) |
| 中心选中后的模型遮罩 | [全部实体](entity_mask.png) · [选中实体](selected_mask.png) |
| 多场、俯仰与清理 | [18 场重叠](overlapping_18.png) · [抬头范围／高处观察](pitched_overhead.png) · [停止后](stopped.png) |
| 资源重载 | [重新渲染](after_reload.png) |

截图由隔离开发客户端直接采集；重叠测试用 18 份受控快照覆盖跨批次合成，实际技能启动／停止另行调用服务端渠道验证。

## 复现与验证

使用 JetBrains Runtime 25：

```powershell
.\gradlew.bat test editorTest -DisDev=true
.\gradlew.bat runGameTestServer '-PacademyGameTests=academy:darkmatter_resource_*' -DisDev=true
.\gradlew.bat runClientDev -I tools/vfxgraph-editor/scripts/interference-field-client.init.gradle -DisDev=true
.\gradlew.bat runClientDev -I tools/vfxgraph-editor/scripts/darkmatter-vfx-client.init.gradle -DisDev=true
.\gradlew.bat build -DisDev=true
.\gradlew.bat build -DisDev=false
```

- 全量 JUnit：2,149 项通过；编辑器测试：103 项通过；未元物质 GameTest：19 项通过。
- 新测试比较 42,875 个带俯仰角的位置与现有战斗中心点判定，并检查宽近／窄远并集、大射程、顶点、相机连续衰减和协议精度。
- 客户端检查实际施放、模型遮罩、玻璃／水、日夜／屋顶、内外与高处观察、18 场合成、停止释放、施术实体移除、断更、资源热重载及带场退出世界，最终输出 `[interference-field-smoke] PASSED`。
- 资源重载期间世界计时可能暂停；超时断言等待实际世界时间，而非界面 tick 数。
- 既有未元物质实机探针也已通过：真实干涉命中、修复与空修复 MP、实体／方块解构烟雾、羽刃穿出及单次伤害，输出 `[darkmatter-vfx-smoke] PASSED`。
- 开发版与发布版 `build` 均通过。
- 此效果依赖真实场景深度和模型材质，使用实机探针验收；GraphEditor 仍负责独立命中反馈和羽刃图。旧 `darkmatter_interference.json` 仅保留历史图预览，游戏内不再生成它。
- 客户端仍有已有的 `Missing uniform Globals` 启动回退、MSDF 换行字形及旧测试存档 Jade 规则警告；本次场景着色器没有新增编译错误，资源热重载完成。

日志在 `build/field-{verify,client,darkmatter-regression,dev-build,release-build}.log`，不纳入提交。测试世界位于 `run/ability-vfx`，探针只进入编辑器 source set。
