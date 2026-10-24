# 未元创生：Blockbench 独角仙

`darkmatter_creature.bbmodel` 是正式可编辑源，格式为 **Minecraft Java Modded Entity / Mojang 1.17+**，内嵌 `128 × 128` 纹理。模型以纯白独角仙为母题：上弯角、左右颚、分层鞘翅、六足和薄翼；珍珠灰只用于甲壳起伏、翼脉与关节深处。

四个蓝图槽保留原来的部位编号与互斥选择规则。Blockbench 默认显示 `head_0 + torso_0 + limbs_0 + additional_0`，其他分支只在编辑视图隐藏，全部保持可导出。

| 分支 | 造型与动作轴 |
| --- | --- |
| `head_0` | 长角、左右近战颚 `jaw_left/right` |
| `head_1` | 短角、单直射炮 `cannon`，空心炮口 |
| `head_2` | 短角、双追踪炮 `cannon`，空心炮口 |
| `torso_0` | 步行腹部与闭合鞘翅 |
| `torso_1` | 飞行腹部、鞘翅铰链 `elytra_left/right`、薄翼 `wing_left/right` |
| `torso_2` | 流线腹部、划水鳍 `fin_left/right`、尾扇 `swim_tail` |
| `limbs_0` | 六足、前足护卫刃 |
| `limbs_1` | 六足、前足挖掘铲与齿 |
| `limbs_2` | 六足、后足承托片 |
| `additional_0` | 无附加部件 |
| `additional_1` | 额外分层甲壳 |
| `additional_2` | 双感应触角 `sensor_left/right` |
| `additional_3` | 从甲壳生出的背炮 `dorsal_cannon` |

编辑时先关闭旧部位分支，再打开同槽目标分支，避免把全部蓝图叠在一起。躯干、头部、肢体、附加件分别选择，飞行与游泳躯干可搭配全部既有肢体。

## 导出

1. 在 Blockbench 打开 `.bbmodel`，保留 Modded Entity 格式和 `128 × 128` 纹理尺寸。
2. 保留 `all`、上述部位编号、左右足的 `leg_left/right_front/middle/rear`、`tibia` 与各动作轴名称。旋转放在组上，体块本身不设独立旋转。
3. 使用 **File → Export → Export Java Entity** 检查原生导出；再保存 `.bbmodel`。
4. 在仓库根目录运行 `node docs/models/darkmatter_creature/export_geometry.cjs`。适配器把已保存的 Blockbench 几何写入 `DarkmatterCreatureGeometry.java`，保留隐藏但可供蓝图选择的分支，转换项目版本的坐标与 `LayerDefinition` 接口。

游戏继续使用 `DarkmatterCreatureModel` 选择分支并驱动动作。其公开的 `applyLocomotion`、`applyHeadPose`、`applyModulePose` 只接受编号、姿态、时间和动作进度，不依赖玩家；未来实体和精密操作系统可复用同一套骨架动作。每帧先重置骨架，再施加动作。

2026-10-07 已在 **Blockbench 5.2.1 网页版**实际打开模型、切换蓝图分支、目视检查，并通过原生 Java Entity 导出与保存项目。`blockbench_native_export.java`、`blockbench_saved_project.bbmodel`、`blockbench_model.jpg` 保留这次 UI 验收产物。只读对照确认原生导出和运行时导出都有 113 个部位、320 个体块，体块坐标/尺寸完全一致；原生模板把旋转弧度保留到四位小数，适配器保留六位。

原生 Java 模板面向旧版泛型 `EntityModel<T extends Entity>`，且把不同分支下同名骨骼提升为重复字段；`armor_-1_*` 组名也无法直接用作 Java 变量。它用于验证 Blockbench 几何导出，不能直接加入本项目编译。适配器采用唯一局部变量、保留原始骨骼名称和本项目渲染状态接口，因此解决模板的变量冲突与版本差异。适配器同时支持原始 4.10 内联组格式与 Blockbench 5.0 的独立 `groups` 元数据，可直接传入 UI 保存版本：

```powershell
node docs/models/darkmatter_creature/export_geometry.cjs docs/models/darkmatter_creature/blockbench_saved_project.bbmodel
```

`build_source.cjs` 只重建本次初始造型，**会覆盖后续 Blockbench 编辑**；通常只运行 `export_geometry.cjs`。

## 距离细节

完整 Blockbench 源与贴图保持不变。`darkmatter_creature_low_detail.bbmodel` 从正式源派生，保留全部 113 个命名部位、相同位置/尺寸、蓝图编号和动作轴；删除脚趾、翼脉和甲壳细边等远距细节，并按原体块边界合并鞘翅大面。低模共有 95 个体块，每个选中组合实际为 24–39 个；它仍有六条带关节的腿、弯角、颚、炮和飞行薄翼。

```powershell
node docs/models/darkmatter_creature/build_low_detail.cjs
node docs/models/darkmatter_creature/export_geometry.cjs docs/models/darkmatter_creature/darkmatter_creature_low_detail.bbmodel src/main/java/org/academy/internal/client/model/DarkmatterCreatureLowDetailGeometry.java
```

`build_low_detail.cjs` 会覆盖低模的人工编辑；完整源不受影响。低模也可直接在 Blockbench 打开、调整并保存，然后只运行第二条导出命令保留编辑结果。原适配器按输出文件名确定 Java 类名，不再需要另写一套几何。

派生器也接受可选的输入与输出路径，并支持 Blockbench 4 内联组和 5 分离组。已用实际 UI 保存的 `blockbench_saved_project.bbmodel` 进行 5.0 roundtrip 派生：113 个关节路径和 95 个体块一致，关节坐标仅有约 `1.78e-15` 的 JSON 浮点误差；六位精度适配后的 Java 与当前正式低模 SHA-256 完全一致（`E469CF313127A2D249EE1890F0460EA255DA137A87DB147B5954C49C647CA053`）。验证产物仅留在 `build/beetle_bb5_roundtrip`。

游戏采用公开、实体无关的 `DistanceModelPolicy`：到相机距离 ≤16 格保留完整姿态；16–24 格每两 tick 对次要动作采样并停止细微触角/颚闲动；>24 格使用独立低模、每四 tick 采样主动作。各实体用自己的编号错开采样相位。瞄准与攻击进度继续使用当前状态，AI、命中、导航、体型与原版约 32 格的实体距离剔除不改变。详细和低模都在每次实际绘制前复位选中的骨架，遮挡 mask 重放及 A→B→A 共享实例顺序不会依赖上一实体状态。

性能对照命令与实际测量见本地记录 `performance/README.md` 与 `performance/COMPARISON.md`；优化前的源码审计是 `performance/AUDIT.md`。三者都是验收证据，不随仓库分发。

## 验证

`DarkmatterCreatureModelTest` 检查正式源的部位导出与 UV 边界、108 种蓝图组合的骨架烘焙/互斥选择，以及每帧翼姿态不累积。实际体型碰撞仍沿用现有实体尺寸，角与展开翼属于造型外延。攻击动作接收现有实体的挥动进度；技能特效由 VFXGraph 单独承担。
