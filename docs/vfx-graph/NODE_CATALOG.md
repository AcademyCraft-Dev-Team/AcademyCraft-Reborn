# 节点目录清单（实现清单）

> 这是 Phase 2 的节点实现清单。每节点 = 元数据（`NodeType`）+ 语义（shader：`GlslNodeGenerator`；
> vfx：`VfxNodeFactory`）+ 检查器编辑器 + 单测。当前已实现以 `✅` 标注，其余为待实现。

## Shader 节点（目标 ≥80，当前 ✅ 83）

### input（输入）

| 节点 | id | 端口/属性 | 状态 |
| --- | --- | --- | --- |
| Constant | `input.constant` | out:FLOAT；prop value | ✅ |
| Color | `input.color` | out:COLOR；prop value | ✅ |
| Time | `input.time` | out:FLOAT | ✅ |
| UV | `input.uv` | out:VEC2 | ✅ |
| Parameter (float/vec3/color) | `input.param_*` | out；prop param | ✅ |
| Texture Sample | `texture.sample` | in:uv/tiling/offset; out:COLOR; prop **texture**（A1 多样本：按属性动态分配 `Sampler0..N`） | ✅ |
| World Position | `input.world_pos` | out:VEC3 | ✅ |
| Object Position | `input.object_pos` | out:VEC3 | ✅ |
| Normal | `input.normal` | out:VEC3 | ✅ |
| View Direction | `input.view_dir` | out:VEC3 | ✅ |
| Camera Position | `input.camera_pos` | out:VEC3 | ✅ |
| Screen Position | `input.screen_pos` | out:VEC4 | ✅ |
| Vertex Color | `input.vertex_color` | out:COLOR | ✅ |
| Delta Time | `input.delta_time` | out:FLOAT | ✅ |
| Sine Time | `input.sine_time` | out:FLOAT | ✅ |
| Cosine Time | `input.cosine_time` | out:FLOAT | ✅ |

### math（数学）

| 节点 | id | 状态 |
| --- | --- | --- |
| Add / Subtract / Multiply / Divide | `math.add`/`subtract`/`multiply`/`divide` | ✅ |
| Power | `math.power` | ✅ |
| Lerp | `math.lerp` | ✅ |
| Clamp | `math.clamp` | ✅ |
| Sine / Cosine | `math.sin`/`cos` | ✅ |
| Negate | `math.negate` | ✅ |
| Add/Multiply/Lerp Vec3 | `math.add_vec3`/`multiply_vec3`/`lerp_vec3` | ✅ |
| Length / Normalize / Dot | `math.length`/`normalize`/`dot` | ✅ |
| Noise | `math.noise` | ✅ |
| Modulo / Fraction | `math.mod`/`frac` | ✅ |
| Reciprocal / Abs / Sign | `math.reciprocal`/`abs`/`sign` | ✅ |
| Floor / Ceil / Round / Trunc | `math.floor`/`ceil`/`round`/`trunc` | ✅ |
| Sqrt / Exp / Log / Exp2 / Log2 | `math.sqrt`/`exp`/`log`/`exp2`/`log2` | ✅ |
| Min / Max / Saturate | `math.min`/`max`/`saturate` | ✅ |
| Smoothstep / Step | `math.smoothstep`/`step` | ✅ |
| Remap / Inverse Lerp | `math.remap`/`inverse_lerp` | ✅ |
| Tan / Asin / Acos / Atan / Atan2 | `math.tan`/`asin`/`acos`/`atan`/`atan2` | ✅ |
| Degrees / Radians | `math.degrees`/`radians` | ✅ |
| Cross / Distance / Reflect / Refract | `math.cross`/`distance`/`reflect`/`refract` | ✅ |
| Transpose | `math.transpose` | ✅ |

### noise（噪声）

| 节点 | id | 状态 |
| --- | --- | --- |
| Value Noise | `noise.value` | ✅ |
| Perlin Noise | `noise.perlin` | ✅ |
| Simplex Noise | `noise.simplex` | ✅ |
| Voronoi | `noise.voronoi` | ✅ |

### color / gradient（颜色/渐变）

| 节点 | id | 状态 |
| --- | --- | --- |
| Gradient Ramp | `color.ramp` | ✅ |
| HSV→RGB / RGB→HSV | `color.hsv2rgb`/`rgb2hsv` | ✅ |
| Contrast / Luminance | `color.contrast`/`luminance` | ✅ |
| Blend (Mix/Multiply/Screen) | `color.blend` | ✅ |
| Split / Combine | `split.vec3`/`split.vec4`/`combine.vec3`/`combine.vec4` | ✅(部分) |

### output（输出）

| 节点 | id | 状态 |
| --- | --- | --- |
| Fragment Color | `output.color` | ✅ |
| Vertex Output | `output.vertex` | ❌ M14（顶点阶段为固定全屏 quad）|
| Custom Function | `output.custom` | ✅ |
| Sub Graph | `subgraph` | ✅ M12-05（编译内联；编辑器多文档标签页已支持打开/编辑，M19/A2） |

## VFX 节点（目标 ≥45，当前 ✅ 46 块 + 23 算子）

> 容器化（M27）后 VFX 目录为**块**（`vfx.block.*`，42 块）+ 算子（`vfx.op.*`，23 个：attr-read×11、constant、param_float/vec3/color/curve/gradient×5、add/sub/mul/div×4、curve/gradient×2）；M22 增 4 arc 块 → **46 块**。
> 下表为块语义清单；算子见 `VFX_CONTAINER.md`。

### spawn（发射）

| 节点 | id | 属性 | 状态 |
| --- | --- | --- | --- |
| Spawn Rate | `vfx.spawn_rate` | rate/lifetime/size/color/vx/vy/vz/shape/.../layer | ✅ |
| Spawn Burst | `vfx.spawn_burst` | count/lifetime/.../layer | ✅ |
| Spawn Periodic Burst | `vfx.spawn_periodic` | count/interval/.../layer | ✅ |
| Spawn By Distance | `vfx.spawn_distance` | rate per meter/layer | ✅ |

> **`layer` 属性（M21）**：`fire`（默认，additive/glow 火焰层）或 `smoke`（alpha 混合烟雾层）。
> 分层渲染走**多输出节点**（M21n 数据驱动）：每个输出节点设 `layer` 过滤（`""`=全部）——fire 层输出（glow + fire 片元）与 smoke 层输出（translucent + billboard 片元）各渲各的，烟雾不进 bloom（bloom 只取 GLOW 规格）。渲染器零 smoke 概念。

### initialize（初始化）

| 节点 | id | 状态 |
| --- | --- | --- |
| Set Position (Shape) | `vfx.init_position` | ✅ |
| Set Velocity | `vfx.init_velocity` | ✅ |
| Set Color | `vfx.init_color` | ✅ |
| Set Size | `vfx.init_size` | ✅ |
| Set Rotation | `vfx.init_rotation` | ✅ |
| Set Lifetime | `vfx.init_lifetime` | ✅ |
| Set Mass | `vfx.init_mass` | ✅ |
| Randomize | `vfx.init_randomize` | ✅ |

### update（更新）

| 节点 | id | 状态 |
| --- | --- | --- |
| Integrate Velocity | `vfx.update_velocity` | ✅ |
| Gravity | `vfx.update_gravity` | ✅ |
| Age | `vfx.update_age` | ✅ |
| Fade | `vfx.update_fade` | ✅ |
| Constant Force | `vfx.update_force` | ✅ |
| Noise | `vfx.update_noise` | ✅ |
| Turbulence | `vfx.update_turbulence` | ✅ |
| Vortex | `vfx.update_vortex` | ✅ |
| Drag / Damping | `vfx.update_drag` | ✅ |
| Collision (Plane/Sphere/Ground) | `vfx.collision_*` | ✅ |
| Kill / Bounds | `vfx.kill`/`vfx.bounds` | ✅ |

### over-life（生命期变化）

| 节点 | id | 状态 |
| --- | --- | --- |
| Color Over Lifetime | `vfx.life_color` | ✅ |
| Size Over Lifetime | `vfx.life_size` | ✅ |
| Alpha Over Lifetime | `vfx.life_alpha` | ✅ |
| Velocity Over Lifetime | `vfx.life_velocity` | ✅ |

> **`layer` 过滤（M21）**：over-life 节点可设 `layer`（`""`=全部，`fire`/`smoke`=仅该层），实现"每层独立颜色/尺寸/alpha 曲线"（引擎式材质分层）。

### orient（朝向）

| 节点 | id | 状态 |
| --- | --- | --- |
| Face Camera | `vfx.orient_face_camera` | ✅ |
| Align To Velocity | `vfx.orient_velocity` | ✅ |
| Fixed Rotation | `vfx.orient_fixed` | ✅ |
| Spin | `vfx.orient_spin` | ✅ |

### output（输出）

| 节点 | id | 状态 |
| --- | --- | --- |
| Output Points | `vfx.output_point` | ✅ |
| Output Quad/Billboard | `vfx.output_quad` | ✅ |
| Output Quad/Additive | `vfx.output_quad_additive` | ✅ |
| Output Quad/Additive Glow | `vfx.output_quad_glow` | ✅ |
| Output Mesh | `vfx.output_mesh` | ✅ |
| Output Line/Trail | `vfx.output_line` | ✅ |
| Output Ribbon | `vfx.output_ribbon` | ✅ |

> **着色器不穷举（M21l / M21n）**：输出节点**必须显式**设 `vertex`（顶点着色器 id）、`shader`（片元着色器 id）、`blend`（`translucent`/`additive`/`glow`），可选 `layer`（该输出渲染的粒子层，`""`=全部）；
> 几何（quad/mesh/line/ribbon）由节点类型派生。一个效果可有**多个输出节点**（多输出分层，如 fire 层 + smoke 层），渲染器零着色器 id 引用、零 smoke 概念，缺失仅走中性默认（particle/translucent）。
> 内置外观着色器按职责命名：`vfxgraph_particle`（中性软圆斑默认）、`vfxgraph_fire`（引擎式火焰 + 火舌拉伸 vsh）、`vfxgraph_smoke`（烟雾卷须）。
> 新增粒子/火焰外观 = 新 `.fsh` 资源 + 图上设属性，零 Java 改动、零枚举。

### arc（电弧/路径驱动，M22 / M29 / ADR-026 / ARC_DESIGN.md）

> 路径驱动子系统：CPU 产约束 spine（两点/环绕/表面布点/接触闪电/粒子火花 + 每点宽度），
> 渲染为 **M22f 终态**（用户否定 M22b/c/d/e 自研方案后改用旧 vfx）：复用 `LightningMeshBuilder` 管网格
> （parallel transport ring）+ `vfxgraph_arc`（颜色 100% 图数据驱动、零代码常量）+ bloom。
> 容器化后为块（`vfx.block.arc_*`，SPAWN 类发射块 + `vfx.block.output_arc` 输出块），**VFX 目录 42 → 48 块**（含粒子系全部块）。
> **M29（2026-08-22，Blender「闪电附着」忠实复刻）**：`arc_surface` 重写为表面布点 + per-point 短弧
> （Bezier 起拱 + 重采样 + 噪声 + 端点吸附）；新增 `arc_contact`（接触闪电：距离剔除 + 端点吸附接触面）与
> `arc_spark`（粒子火花：弧→点 + 溅射 + 迷你管）。`SurfaceConstraint` 已接入 `VfxSystemSimulator.step`
> （每帧对带 surface 的弧执行端点吸附）。`MeshAssets` 内置 `builtin:plane`/`builtin:sphere` 表面。
>
> **M29b（2026-08-23，弧数爆炸修复）**：`arc_surface`/`arc_contact` 增 `frame_period`/`fps` 帧周期断续门控
> （复刻 Blender `Compare(Frame MOD N) EQUAL 0`，`frequency≤0` 保留 legacy 每帧 spawn），稳态弧数 <30；
> `arc_spark` 增 `max_sparks` 上限 + 只从本帧新增弧（`ArcCurve.fresh`）派生火花，消除指数放大。
> 编辑器视口渲染场景表面网格（`vfxgraph_surface`，Blender 式可见地面 + 悬浮球）。
>
> **M30（2026-08-23，一比一复刻，权威参数来自实际 .blend）**：`generateSurfaceArc` 方向修正——
> 基线**平躺表面**（Curve Line + Align axis=X）沿切平面随机方向，控制柄沿**法线**上推随 age 成长
> （FloatCurve.001）→ 帐篷拱；管半径 = FloatCurve.002(端粗中细) × FloatCurve.005(age 衰减)。
> `arc_contact` 改**直线弧**（P→接触面最近点，仅末端吸附 pinStart，半径仅 age 衰减 flatRadius）；
> `arc_spark` 改**迷你管对齐速度** + 每帧速度/重力积分（`ArcCurve.sparkVelocity`）。
> `vfxgraph_arc.fsh` 改不透明自发光（Blender Emission，alpha=1）。

| 块/节点 | id | 属性 | 状态 |
| --- | --- | --- | --- |
| Arc Bolt（两点） | `vfx.block.arc_bolt` | from/to、segments、jagged、strands、width（管半径）、branch_count/depth、lifetime、interval、flicker、color | ✅ |
| Arc Orbit（环绕） | `vfx.block.arc_orbit` | radius、speed、segments、strands、width、wobble、tilt、lifetime、color | ✅ |
| Arc Surface（表面电弧，M29→M30） | `vfx.block.arc_surface` | mesh、density、probability、frequency、frame_period、fps、height、curve、width、segments、lifetime、color、emission、noise_strength、drift_speed、origin_x/y/z | ✅ |
| Arc Contact（接触闪电，M29→M30） | `vfx.block.arc_contact` | mesh、contact_mesh、contact_range、contact_origin_x/y/z + Arc Surface 全部 | ✅ |
| Arc Spark（粒子火花，M29→M30） | `vfx.block.arc_spark` | probability、max_sparks、splash_speed、gravity、lifetime、scale、radius、color、emission | ✅ |
| Output Arc | `vfx.block.output_arc` | vertex/shader/blend/layer + ARC 观感属性（sparks、spark_speed/size/period/travel/length/radius/curve/wobble、thickness、emission，M22h） | ✅ |

> **ARC（M22f，改用旧 vfx 渲染）**：`vfxgraph_arc` 渲染 `LightningMeshBuilder` 管网格（parallel transport ring，
> `ARC_TUBE_FORMAT` = Position+UV+Color）；`ArcLightning` UBO 仅渲染标量（aces 开关 + 发射增强），**零颜色常量**；
> 主 pass 透明 / bloom pass additive → bloom。组合结构固定，外观数据驱动（颜色由图数据 `color` 属性、观感由上述 ARC 属性）。

### param（存活参数，M15-04/ADR-020）

> 扁平旧节点（`VfxNodes`，M28 待移除）；**容器模型下为算子** `vfx.op.param_*`（见 VFX_CONTAINER/MODULES MOD-14）。

| 节点 | id | 属性 | 状态 |
| --- | --- | --- | --- |
| Float Parameter | `vfx.param_float` | param/value | ✅ |
| Vec3 Parameter | `vfx.param_vec3` | param/x/y/z | ✅ |
| Color Parameter | `vfx.param_color` | param/r/g/b/a | ✅ |
| Curve Parameter | `vfx.param_curve` | param/curve | ✅ |
| Gradient Parameter | `vfx.param_gradient` | param/gradient | ✅ |

### shape（发射形状）

| 形状 | 类 | 状态 |
| --- | --- | --- |
| Point | `PointShape` | ✅ |
| Sphere | `SphereShape` | ✅ |
| Box | `BoxShape` | ✅ |
| Cone | `ConeShape` | ✅ |
| Cylinder | `CylinderShape` | ✅ |
| Torus | `TorusShape` | ✅ |
| Circle Edge | `CircleEdgeShape` | ✅ |
| Disc（平面基盘） | `DiscShape` | 火焰/篝火基底发射器（XZ 平面半径内均匀采样） | ✅（M21） |
| Mesh Surface | `MeshShape` | 属性 `mesh`/`mesh_scale`；OBJ 三角形面积加权采样，缺省单位立方体 | ✅（A3） |
