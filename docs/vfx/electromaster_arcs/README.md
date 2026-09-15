# 电击使蓝白电弧迁移至 VFXGraph

本次将超电磁炮蓄力手环、落雷与闪电风暴的表面附着电弧、电弧激发、雷击之枪、电磁护盾和磁悬武装统一为当前超电磁炮的蓝色外晕和白色细芯。所有几何由容器式 VFXGraph 发射器生成，游戏端负责技能状态、路径控制点、挂点与网络参数。

## 设计与参数

| 图资产 | 表现 | 主要参数 |
| --- | --- | --- |
| `railgun_charge` | 参照动画截图：加粗的不规则电流环、三段局部白亮电弧、四条带细分叉的外散电流；保留环内空白 | `radius=0.30`、`rim_width=0.0055`、`width=0.014`、`filaments=4`、`filament_reach=1.15`；`strength` 随蓄力变化 |
| `arc_generate` | 四股较分散的空间折返电弧和短分叉，准确连接手部到目标端点 | `width=0.065`、`spread=1.10`、`density=1.45`、`duration=1.25`（25 tick，比初版增加 5 tick） |
| `thunder_lance` | 五股较集中的粗壮电流，保留独立分叉 | `width=0.17`、`spread=0.85`、`density=1.45`、`duration=0.75`（15 tick，比初版增加 5 tick） |
| `sky_strike_thunderclap` / `sky_strike_storm` | 双层蓝白电流贴合地面、台阶及云团底部，继续使用原来的投影与并发细节预算 | 独立的 `attachment_out` 输出；附着宽度、覆盖半径、数量、爬动速度仍可在图中编辑 |
| `electromagnetic_shield` | 六段身周游走电流；拦截时切换为攻击入射面上的两道扩散电环 | `radius=0.85`、`height=1.8`、`width=0.035`、`duration=0.25`；`impact=1` 切换拦截模式 |
| `magnetic_weapon` | 唤出时手部连向刀刃、间歇刀身电流、沿真实运动轨迹的拖尾及目标处的放电 | `width=0.035`、`max_paths=16`、`max_points=128`；通过 `paths` 输入实时路径 |

第一人称手环中心为相机局部 `(±0.30, -0.22, -0.46)`，缩放 `0.90`。比初版拉近约 16%、挂点缩放增大 25%；参照新图后，环本身半径进一步调整为 `0.30`。第三人称沿角色模型根变换跟随左右手。释放电磁炮时立即移除蓄力环，避免遮住橘色发射轨迹；硬币返回提示采用同一图的精简环。

参照补充动图增加环面摆动、轮廓形变、粗细流动、亮弧沿环游动，以及外围闪电伸缩和脉冲增亮；挂点仍固定在手部。`motion_speed` 调整动态速度（设为 0 可冻结预览），`motion_strength` 调整形变幅度。主体环边通过 `rim_width` 单独控制，默认比细线版本加粗约 3.3 倍。`charge_motion.mp4` 为编辑器 GPU 生成的 4 秒动态预览。

攻击电弧 `length` 与 `width` 独立；横向展开在极短距离自动收紧，端点仍与实际攻击路径一致。射程不再通过旧的 `branch_length_scale` 间接模拟。反射后的各线段分别生成图实例。连锁电弧复用同一风格，持续 13 tick。

## 射程与弹药数据

电弧激发基础射程为 16 格，在启用熟练度系统且熟练度达到 2000 时变为 20 格。判定与图形共用服务器解析出的端点，命中阻挡物或发生反射时按实际线段缩短。25 tick 的持续时间保持不变。

超电磁炮保留原有弹药射程加成和宽度倍率，将基础射程设为 48 格，并将伤害判定半径与方块破坏半径拆开。下表为额外射程调节、熟练度加成之前的数据：

| 弹药 | 射程（格） | 伤害半径（格） | 方块破坏半径（格） |
| --- | ---: | ---: | ---: |
| 硬币 | 48 | 2 | 1 |
| 铁锭 | 56 | 3 | 1.5 |
| 铁块 | 64 | 4 | 2 |
| 铁砧（含开裂、损坏） | 72 | 5 | 2.5 |

熟练度 2000 的原有射程 ×1.2 加成继续生效。方块破坏仍遵守世界、玩家与技能的开关和保护规则；去程、返程都使用独立的破坏半径。光柱通过同步实体读取实际射程，保留已完成的加粗、平底端面、透明度和持续时间设计。

护盾每 5 tick 生成一个跟随施法者的短脉冲，跟随位置按渲染帧插值；关闭后最多 5 tick 清理。磁悬武装保留原有攻击时序、目标包围盒、路径断点和粒子质量档位，单个刀刃使用一个图实例。刀刃移除后以游戏时钟淡出 3 tick，暂停不会跳过淡出。

## 补齐的 VFXGraph 能力

- `EffectFrameBinding` / `ActiveEffect.bindFrame`：每帧在剔除前更新挂点和动态参数，主绘制与辉光使用同一结果；回调返回 `false` 结束实例。支持均匀缩放矩阵，适用于相机、骨骼及非玩家发射器。
- `setGameTimeLifetimeSeconds` / `bindGameTime`：暂停时停止计时，离开视野后继续老化，资源热重载保留寿命和绑定。旧的真实时间寿命接口继续保留。
- `ArcCurve.layer`：电弧按任意字符串分层，输出空层为通配；修复渲染器只绘制第一组电弧输出的问题，主落雷和附着电弧可使用不同材质。
- 新增 `electric_bolt` 与 `electric_orbit` 块；提取公共 `BlueWhiteArcStyle`，与已完成的超电磁炮发射电弧共用调色和管状轮廓。
- 新增 `electric_shield` 与 `electric_paths` 块。`EffectArcSource` / `ActiveEffect.bindArcs` 将局部坐标路径、修饰器和分支传入图，图控制统一宽度、颜色和材质；支持热重载，空输入会清除几何，不回退显示编辑器示例。每帧最多 16 条路径、每条 128 点、3 层分支深度，双层外晕与内芯不超过 32 条渲染电弧。
- 通用生成包自动绑定游戏时间，并按整段光路计算广播距离、缩放后的包围球和远裁剪面。

`org.academy.api.server.ability.ElectromasterGraphEffects.spawnBolt` 是技能、可编程动作和非玩家调用共用的服务端入口。它传递真实起点、终点、样式及随机种子，不执行伤害。当前技能样式传入的长度、宽度、分散程度、股数和持续时间覆盖资产的预览默认值；透明度密度、分叉数量和材质由图资产决定。

## 编辑与复现

从项目 VFXGraph 编辑器打开上述 JSON 即可编辑。`time=-1` 使用编辑器播放时钟；改为非负数可以直接定位动画时刻。图资产由编辑器配套仓库 API 写入并经过校验，未引入新的贴图或旧 VFX 几何渲染分支。

在项目根目录使用 JetBrains Runtime 25：

```powershell
node tools/vfxgraph-editor/scripts/author-electromaster-arcs.mjs
.\gradlew.bat test editorTest -DisDev=true
.\gradlew.bat build -DisDev=true
.\gradlew.bat build -DisDev=false
.\gradlew.bat runGraphEditor -DisDev=true -I tools/vfxgraph-editor/scripts/electromaster-arcs-capture.init.gradle
.\gradlew.bat runClientDev -DisDev=true -I tools/vfxgraph-editor/scripts/electromaster-arcs-client.init.gradle
.\gradlew.bat runClientDev -DisDev=true -I tools/vfxgraph-editor/scripts/electromaster-extension-client.init.gradle
.\gradlew.bat runGraphEditor -DisDev=true -I tools/vfxgraph-editor/scripts/electromaster-extension-capture.init.gradle
```

客户端探针需要 `run/electromaster-arcs/saves/railgun` 隔离测试存档。探针只存在于 editor 源集，需要显式系统属性启用，正常客户端与发布包不包含这些探针。保存目录、构建目录和日志不纳入提交。

## 验证范围

2026-09-14：主测试 2094 项、编辑器测试 103 项以及 JVM 布局测试 9 项通过；开发版与发布版构建通过，两个隔离客户端探针均输出 `PASSED`。相关图资产均为零错误、零警告。两个模组 JAR 已确认包含新图和公开 API，未包含 editor 捕获探针。

自动化测试覆盖端点精度（0.02、16、20、32、48、72、512 格）、长度与宽度独立、管网格预算、固定时间重复采样不累积、消失与时间轴回退、环边闭合与粗细差异、独立提示环、任意电弧层及池化重置、隐藏/暂停寿命、挂点热重载、长距离广播边界、表面投影与台阶/空洞/云底及低细节预算；新增护盾体表范围、拦截环闭合、外部路径端点与预算、空路径清理及路径绑定热重载测试。

真实客户端探针检查左右手、转头、第三人称、释放清理、两种技能的实际网络入口、两个反射形状线段、地形与云底附着、十次风暴重叠、硬币返回提示、512 格路径和最终清理。反射线段预览通过共用视觉入口构造，不表示本探针执行了一次真实一方通行反射战斗。编辑器预览使用真实 GPU 渲染器和辉光通道。

扩展客户端探针实际施放电弧激发，核对熟练度 0 / 2000 的世界端点；分别使用四种弹药施放电磁炮，核对同步实体与光柱长度。硬币测试另放置半径内外的实体和方块，确认伤害半径 2 格、破坏半径 1 格分开生效。护盾检查第一/第三人称、移动转向、关闭和拦截电环；磁悬武装通过真实切换网络入口攻击带敌对标记的目标，检查拖尾、命中电流、第一人称与关闭清理。

截图：

- `charge_right.png` / `charge_left.png`：第一人称手环。
- `charge_turn.png` / `charge_third_person*.png`：相机与角色跟随。
- `arc_generate.png` / `thunder_lance.png`：两种攻击电弧。
- `surface_attachment.png` / `storm_attachment.png` / `cloud_attachment.png`：附着效果。
- `preview_*.png`：编辑器渲染器的固定时间预览。
- `arc_range_16.png` / `arc_range_20.png`：电弧激发两档射程。
- `railgun_ammo_0.png` ～ `railgun_ammo_3.png`：四种弹药的实际技能光柱。
- `shield_first_person.png` / `shield_third_person.png` / `shield_following.png` / `shield_interception.png`：护盾与拦截。
- `magnetic_trail.png` / `magnetic_impact.png` / `magnetic_first_person.png`：磁悬武装攻击。
- `extension_cleared.png`：扩展技能停止后的清理。
- `charge_motion.mp4`：30 fps 的手环动态预览；原始 120 帧保存在构建目录，可由 `ffmpeg -framerate 30 -i build/electromaster-motion/frame_%03d.png -c:v libx264 -crf 18 -pix_fmt yuv420p -movflags +faststart docs/vfx/electromaster_arcs/charge_motion.mp4` 合成。
