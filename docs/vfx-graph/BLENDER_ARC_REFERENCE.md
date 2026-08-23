# Blender「闪电附着」参考文档（权威分析）

> 本文基于对 `闪电附着.blend`（Blender 5.2.0 LTS）的完整逆向导出（见 `blender-reference/` 目录），
> 是 `arc` 子系统复刻的**权威参考**。之前基于猜测的近似全部作废，以本文为准。

## 0. 导出数据（`docs/vfx-graph/blender-reference/`）

| 文件 | 内容 |
| --- | --- |
| `scene.json` | 物体（Plane/Sphere 世界变换、网格统计、修改器）+ 三个材质节点树 |
| `node_groups.json` | 全部几何节点组（`闪电附着` 276 节点 + 6 个子组），节点属性 + 完整 link 图 |
| `group_inputs.json` | 各组输入（socket）默认值与含义 |
| `texts.md` | 内嵌文本（更新日志 + 作者注） |

导出脚本逻辑见 `/tmp/opencode/export_blend.py`。

---

## 1. 场景布局

| 物体 | 类型 | 世界位置 | 网格 |
| --- | --- | --- | --- |
| **Plane** | MESH | (0,0,0) | 4 顶点 / 1 面，2×2 平地面板（x∈[-1,1], y∈[-1,1], z≈0） |
| **Sphere** | MESH | (0.52, 0.38, **4.34**) | 482 顶点 / 512 面，半径 ≈1（z 从 3.34 到 5.34），**悬浮在平面上方约 4.3** |

- 修改器 `NODES`（几何节点）挂在 **Plane** 上，主组 = `闪电附着`。
- 材质 3 个：`electricity`（主电弧）、`spark`（粒子）、`touch_electricity`（接触电弧）。

---

## 2. 主几何节点组 `闪电附着`（276 节点）

### 2.1 组输入（参数，见 `group_inputs.json`）

| 参数 | 默认 | 含义 |
| --- | --- | --- |
| `Geometry` | Plane | 输入网格（弧在其上生成） |
| `电弧密度` | 0 | 表面布点密度 |
| `电弧粗细` | **0.78** | Bezier 控制柄伸开量（弧起拱/粗细） |
| `电弧宽度` | 0 | 管半径相关 |
| `电弧高度` | **1.0** | 弧长（Curve Line 长度） |
| `噪波强度` | 0.5 | 噪声位移幅度 |
| `游离速度` | 0.5 | 噪声随场景时间漂移速度 |
| `生命周期` | 0 | 弧存活帧/秒 |
| `电弧亮度` | **5.0** | 发射强度 |
| `电弧颜色` | 0.8,0.8,0.8,1 | 弧基色 |
| `整体缩放` | 0 | 不改物体整体缩放、只调闪电大小 |
| `接触对象` | 物体(Object) | 接触/连接的目标（默认 Sphere） |
| `接触范围` | **4.1** | 距接触对象多近才保留弧（`Compare GREATER_THAN 4.1`） |
| `仅闪电` | 0 | 是否只保留闪电（去掉粒子） |

粒子/火花子系统参数：`粒子密度 0.5`、`粒子缩放 1.4`、`溅射速度 1.3`、`重力G -0.9`、`生命周期 30`、`粒子亮度 0.5`、`粒子颜色 0.8`、`发光强度 3.0`、`半径 0.8`。

### 2.2 主电弧流水线（关键链路）

```
GI.Geometry(Plane)
  └─ Reroute.004 ─ Reroute.013 ─ 随机点云阵列.Mesh     （在 Plane 表面按 电弧密度 布点）
  │                              └─ Reroute ─ Sample Nearest Surface.Mesh   （表面约束）
  └─ Reroute.006 ...

随机点云阵列.Geometry ─ Instance on Points.Points      （每表面点实例化一条弧）
  Instance = Curve Line  (Start 0,0,-0.5 / End 0,0,0.5, Length=电弧高度 1.0)
  Rotation = Align Rotation to Vector(随机点云阵列.Normal)   （沿表面法线定向）
  Scale    = Math.018 = Random[0.4..1.2] × 电弧宽度   （每弧实例随机跨度缩放，观感大小各异）
  └─ Realize Instances ─ Store Named Attribute.002(唯一ID.ID)
      └─ Simulation Input ─ Join Geometry.001
          ├─ Set Position(全部点)：offset = cross(Random[±1]³归一, 表面法线) × Random[0.01..0.03] × 游离速度
          │      → 弧基座每帧沿切平面滑移爬行（仿真区持久位置）
          ├─ Set Position.002(端点)：Sample Nearest Surface + Endpoint Selection → 端点吸附回表面
          └─ Store Named Attribute(age) + Math ADD 1 → Simulation Output
      ├─ Set Spline Type(Bezier)
      │    └─ Set Handle Positions.001/.002 (Offset = 法线 × FloatCurve.001(age/寿命20) × Random[0.4..1.2] × 电弧高度)
      │         └─ Resample Curve(Count=12)
      │              └─ Store Named Attribute(pa = FloatCurve(spline因子)×Random[0.4..2.2], 排除端点)
      │                   └─ Set Position.001(噪声位移，排除端点)
      │                        ├─ 噪声: Position+(1,1,1)×SceneTime.Seconds → Noise(Scale2/Detail2/Rough0.5) → ×pa×噪波强度
      │                        └─ 端点吸附保持
      └─ Curve to Mesh(Circle r0.01, Res8, Scale=FloatCurve.002×FloatCurve.005×电弧粗细0.78) ─ 管网格
          └─ Transform Geometry.004(Scale=整体缩放) ─ Group Output.Geometry
```

**核心要点：**
1. **表面布点**：`随机点云阵列` 在 **Plane** 表面按 `电弧密度` 布点，每点实例化一条**短 Curve Line**（长度 = `电弧高度` 1.0）。
2. **沿法线定向**：实例沿表面法线（`Align Rotation to Vector(Normal)`）+ 绕法线随机轴角。
3. **仿真区爬行**：`Set Position`（全部点）每帧沿切平面随机滑移累积（×`游离速度`），端点随后被 `Set Position.002` 吸附回表面 → **电弧群游走/爬行**。
4. **Bezier 起拱**：`Set Spline Type(Bezier)` + `Set Handle Positions`（控制柄沿**表面法线**上推 `FloatCurve.001(age/寿命) × Random[0.4..1.2] × 电弧高度`，**不含电弧粗细**——粗细只缩放管半径）→ 弧随 age 从平躺长成帐篷拱。
5. **重采样 12 点**：`Resample Curve(Count=12)`。
6. **噪声位移**：`Position + (1,1,1)×SceneTime.Seconds`（漂移 = 场景秒，**不乘游离速度**）→ `Noise(Scale2,Detail2,Rough0.5)` → `×(noise−0.5)×pa×噪波强度`（`pa` = 脉冲曲线(spline因子)×`Random[0.4..2.2]`，端点 0）→ 弧中段蜿蜒抖动、端点不位移。
7. **端点吸附**：`Sample Nearest Surface` + `Endpoint Selection` 把弧**两端贴回表面**（附着）。
8. **管化**：`Curve to Mesh(Circle profile)` → 管网格 → 整体缩放。

### 2.3 子组 `随机点云阵列`（表面布点 + 断续时序）

```
Distribute Points on Faces (density=3.8, Seed=SceneTime.Frame  → 每帧点分布变化/群集)
  └─ Delete Geometry.001  Selection=Random(Probability=1-出现概率, Seed=Frame)  → 随机删 ~50%
       └─ Delete Geometry.003  Selection=NOT(Compare.001)  → 时序门控
            时序: Compare.001 = (Frame MOD 0.03) EQUAL 0  → 按帧周期断续出现
            周期: Compare.002 = (1/散布频率 < Seconds) → Switch 到 ROUND 值
  └─ Group Output.Geometry / .Normal / .Rotation
```

效果：**表面点群随机删减 + 按帧周期断续出现**，形成「电弧群游走/忽隐忽现」的观感。

### 2.4 「两个物体连接」机制（接触弧，第二套系统）

```
输入菜单(接触对象=Sphere)  ─ Geometry ─ Sample Nearest Surface.002.Mesh
  └─ Sample Nearest Surface.002.Value ─ Vector Math.017(DISTANCE)
                                          └─ Compare(GREATER_THAN 接触范围 4.1)
                                               └─ Delete Geometry.Selection
                                                    └─ Instance on Points.002 (第二套弧)
                                                        Instance = Curve Line.002
```

- `输入菜单`（`Menu Switch`）解析 `接触对象`（默认 `物体`=Sphere）。
- `Sample Nearest Surface.002` 求**每个点到 Sphere 表面最近点**，`Vector Math.017(DISTANCE)` 求距离。
- `Compare GREATER_THAN 4.1`：**距离 Sphere 超过 `接触范围`(4.1) 的点被剔除**。
- 保留下来的点走第二套弧 `Instance on Points.002` → **只有靠近 Sphere 的点才产生弧**。
- 由于 Sphere 悬浮在 Plane 上方约 4.3（在接触范围内），这些弧从平面向球方向连接 → **「两个物体通过电弧连接」**。

`输入菜单` 逻辑：`Menu Switch(Menu=物体)` → 选 `Object Info`（默认无）或 `Collection Info`；另经 `Switch.002`。`Sample Nearest Surface.003`（Mesh=Transform Geometry.005=输入菜单 几何）输出到 `Reroute.011 → Switch.True → Store Named Attribute.003`。

### 2.5 其余子组

- **`唯一ID`**：`Index × 100` + `SceneTime×100` → 每弧/每帧稳定唯一 ID（供随机/寿命）。
- **`设置生命周期`**：`Store Named Attribute("age")` + `Math ADD 1`（帧）+ `Compare GREATER_THAN 10`（寿命）→ 弧过期删除。
- **`几何校验`**：`Compare GREATER_THAN 0` 校验。
- **`矢量选择`**：法线/矢量选择，`Math × -1`、`+100`。

---

## 3. 材质（白炽自发光）

| 材质 | 用途 | Emission Color | Emission Strength |
| --- | --- | --- | --- |
| `electricity` | 主电弧 | `Attribute(LColor)` 蓝 [0.13, 0.21, 1.0] | `Attribute(Light) × 6`（亮度 5.0） |
| `spark` | 粒子 | `Attribute(PColor)` 蓝 [0.23, 0.35, 0.69] | `Attribute(PLight) × 6`（默认 20） |
| `touch_electricity` | 接触弧 | `Attribute(TColor)` 白 | `Attribute(TLight)` |

全部用 `Principled BSDF` 的 **Emission**（自发光）输出，颜色/强度来自 `Attribute` 节点（弧顶点色/亮度属性）。**白炽自发光 + bloom** 是核心观感。

---

## 4. 与当前 `arc` 实现的差距对照

> 状态（2026-08-23 M30）：**全部按实际 `.blend` 权威参数一比一复刻**（此前 M29 的
> `generateSurfaceArc` 方向错误——垂直基线+切向控制柄 vs Blender 平躺基线+法线上拱——已修正）。

| Blender 参考 | 当前实现（M30） | 状态 |
| --- | --- | --- |
| 表面布点（随机点云阵列：density=电弧密度、出现概率、散布频率帧门控） | `vfx.block.arc_surface`（`SurfaceDistributor.distribute` 面积加权 + 概率删减 + 帧门控） | ✅ **已实现** |
| 实例化短 Curve Line（长=电弧高度，Align axis=X 平躺表面 + 绕法线随机旋转，Scale=Random 0.4~1.2×电弧宽度） | `CurveGenerator.generateSurfaceArc`（基线切平面随机方向 + `ArcCurve.archBase`） | ✅ **已实现** |
| Set Spline Type(Bezier) + Set Handle Positions（Offset=表面法线×FloatCurve.001(age/寿命20)×Random[0.4..1.2]×电弧高度，**不含电弧粗细**）→ 帐篷拱 | `CurveGenerator.sampleSurfaceArch`（控制柄沿法线上推随 age 成长、span=height×实例随机跨度 0.4~1.2×），`VfxSystemSimulator` 每帧重采样 | ✅ **已实现（M30）** |
| Resample(12) + 噪声位移（(noise−0.5)×pa×噪波强度，pa=脉冲曲线(spline因子)×Random[0.4..2.2] 逐点、端点 0；域扭曲 Position+(1,1,1)×场景秒） | `NoiseAnimator`（`(noise−0.5)×arc.pa(i)×噪波强度`，漂移=场景秒，幅度对齐 Blender ±0.3） | ✅ **已实现（M30）** |
| 仿真区爬行（Set Position：全部点 offset=cross(Random[±1]³,法线)×Random[0.01..0.03]×游离速度，持久累积 + 端点吸附拉回） | `VfxSystemSimulator.wanderArcBase`（每帧弧基座沿切平面滑移累积 `ArcCurve.wander`，端点随后 SurfaceConstraint 拉回） | ✅ **已实现（M30）** |
| 端点吸附（Sample Nearest Surface + Endpoint） | `SurfaceConstraint`（`MeshDistance.nearestPoint` 真最近表面点）+ `VfxSystemSimulator.step` 每帧接线 | ✅ **已实现** |
| 管半径 = Circle(r0.01) × FloatCurve.002(端粗中细) × FloatCurve.005(age 衰减) × 电弧粗细 | `CurveToMeshBuilder` 读每点 width（`sampleSurfaceArch` 写入 profile×age） | ✅ **已实现** |
| 第二套接触弧（随机点云阵列.001 + 到接触面距离剔除 + 直线弧仅末端吸附） | `vfx.block.arc_contact` + `CurveGenerator.generateContactArc`（P→最近点 N 直线、pinStart 仅末端） | ✅ **已实现** |
| 粒子/火花（Curve to Points → Delete → 溅射速度+重力持续模拟 → 迷你管对齐速度） | `vfx.block.arc_spark` + `ArcCurve.sparkVelocity`（每帧速度/重力积分）+ `BlenderArcCurves.PARTICLE_LIFE` 缩放 | ✅ **已实现** |
| 白炽自发光材质（LColor/Light × 6，不透明 alpha=1） | `vfxgraph_arc.fsh` 改不透明自发光（color=顶点色×Light×6） | ✅ **已实现** |
| age 亮度闪烁（Light=FloatCurve.004(age/寿命)×亮度+0.33×亮度 ×6；接触 TLight=FloatCurve.009×发光强度 直接；粒子 PLight=FloatCurve.003×亮度 ×6） | `VfxGraphRenderer.arcLight` 烘焙进管顶点色（surface=.004+0.33 / contact=.009 / spark=.003），UBO emission 图数据驱动 | ✅ **已实现（M30）** |
| 整体缩放 | `overall_scale` | 已实现 |

**结论（2026-08-23 M30）**：§2 全部系统已按 Blender **实际文件**落地（几何/算法/参数全对齐），
`BlenderArcReference`/`BlenderArcCurves` 固化权威数据，`BlenderArcGeometryTest` 与实测对照。
示例资产 `demo_blender_arc.json`/`surface_arc.json`/`contact_arc.json`/`spark.json`。
剩余目标（非 Blender 参考结构）：MC 方块/玩家模型 → 通用三角面转换器（见 ARC_SURFACE.md）。

---

## 5. 关键参数速查（权威，2026-08-23 从 modifier 实际 socket 值提取）

> 注：以下为 GeometryNodes modifier 实例上存储的**实际生效值**（`m.properties.inputs.Socket_xx.value`），
> 与界面显示默认值不同（界面默认仅是模板）。复刻目标观感 = 亮蓝电光（LColor 蓝 0.13,0.21,1.0）。

**表面电弧（electricity 材质）**
- 电弧密度 **1.0**；电弧粗细 **0.78**；电弧宽度 **1.0**；电弧高度 **1.0**。
- 游离速度 **1.5**；噪波强度 **0.5**；生命周期 **20 帧**；电弧亮度 **1.0**；电弧颜色 0.8 灰（复刻改亮蓝）。
- 随机点云阵列：出现概率 0.0204（保留 ~2%，极稀疏）、散布频率 30。
- 管半径实测 0.0024~0.0034（端粗中细）。

**接触闪电（touch_electricity 材质，白）**
- 接触范围 **4.1**；Density **1.47**；生命周期 **6 帧**；发光强度 **3.0**；半径 **0.8**；噪波强度 **0.5**；出现概率 0.15。

**粒子（spark 材质，蓝）**
- 粒子密度 **0.48**；粒子缩放 **0.83**；溅射速度 **1.23**；重力G **-0.9**；生命周期 **30 帧**；粒子亮度 **1.0**。

**曲线（FloatCurve 控制点）**
- FloatCurve.001 弧拱成长：(0,0.112)→(0.677,0.394)→(1,1)；FloatCurve.002 半径剖面：(0,0.931)→(0.5,0.475)→(1,0.925)；
  FloatCurve.005 半径 age 衰减：(0,1)→(0.695,0.763)→(1,0)；FloatCurve.003 粒子生命：(0,1)→(0.659,0.769)→(1,0)。
- **M30 补充（2026-08-23 从 .blend 提取）**：FloatCurve 无名（pa 脉冲）：(0,0)(0.1,0)(0.1,1)(0.9,1)(0.9,0)(1,0)；
  FloatCurve.007（shapep，同 pa 脉冲）；FloatCurve.004（Light 亮度，先亮后灭）：(0,0)(0.123,0.275)(0.514,0.9125)(0.796,0.594)(1,0)；
  FloatCurve.009（接触半径/发光生命）：(0,1)(0.714,0.744)(1,0)；FloatCurve.006（表面寿命沿弧）：(0,0)(0.132,0.188)(0.468,0.944)(0.846,0.194)(1,0)；
  FloatCurve.008（接触寿命沿弧）：(0.005,0.013)(0.496,0.763)(1,0)。全部固化于 `BlenderArcCurves`/`BlenderArcReference`。

**场景**：Plane 2×2 在原点、Sphere (0.52,0.38,4.34) 半径≈1。
