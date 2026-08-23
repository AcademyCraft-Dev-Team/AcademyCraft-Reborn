# ARC 表面附着：通用表面数据（SurfaceSource）

> 状态（2026-08-22）：**容器执行器端点吸附已接入**（`VfxSystemSimulator.step` 每帧对带 `surface()`
> 的弧执行 `SurfaceConstraint.constrain`，见 M29）。`SurfaceConstraint` 升级为**真最近表面点**
> （`MeshDistance.nearestPoint`，Closest Point on Triangle），复刻 Blender `Sample Nearest Surface`，
> 不再要求投影落在三角形内。MC 方块/玩家模型 → 通用三角面 `float[]` 的**转换器留作后续目标**。

## 背景

Blender「闪电附着」（闪电附着.blend）用 `Sample Nearest Surface` + `Endpoint Selection` 把电弧**两端贴到
接触对象表面**。我们要在 VFX 里复刻，需要：

1. 一个「通用表面数据」抽象（任意模型 → 一组三角形），让 `SurfaceConstraint`/`SurfaceDistributor` 都能消费。
2. 若干「转换器」：MC 方块模型、玩家模型、任意 OBJ → 该通用数据。
3. 电弧两端端点吸附到该表面（**已实现**，见下）。

## 通用数据格式（已就绪）

仓库已有的 `float[]` 三角形格式即通用表面数据，贯穿整个 arc/surface 体系：

| 类型 | 说明 | 位置 |
| --- | --- | --- |
| `float[]` 三角形 | 每 9 个 float = 1 个三角形（3×xyz），顶点顺序决定法线 | `SurfaceDistributor(float[])` |
| `SurfaceDistributor` | 面积加权撒点 + `tangentDirection` | `arc/SurfaceDistributor.java` |
| `SurfaceConstraint` | **端点-only** 表面投影（复刻 `Sample Nearest Surface` + `Endpoint Selection`） | `arc/SurfaceConstraint.java` |
| `MeshAssets` | id → `float[]` 三角形注册表 | `shape/MeshAssets.java` |
| `ObjMeshParser` | OBJ v/f 纯解析 + 扇形三角化 → `float[]` | `shape/ObjMeshParser.java` |

**核心：任何模型只要转成 `float[] triangles`（每 9 float 一个三角形），即可被上述全部消费。**

## 目标：`SurfaceSource` 抽象

新增一个轻抽象层，统一「从何取得表面数据」：

```
SurfaceSource {
    float[] triangles();   // 三角形（xyz×3/三角形）
    // （可选）每三角形法线预计算由 SurfaceDistributor 构造时完成
}
```

实现计划（后续会话）：
- `ObjMeshSurfaceSource`：包 `ObjMeshParser`（已存在）。
- `BlockModelSurfaceSource`：MC 方块模型 → 三角面（方块面/裁剪/旋转）。
- `EntityModelSurfaceSource`：玩家/实体模型 → 三角面（模型层 → 三角形）。
- `PlayerSurfaceSource`：玩家自身模型 → 三角面（用于「附着在玩家身上」）。

这些转换器**本次未实现**，仅定型接口与目标。当前 `SurfaceConstraint`/`SurfaceDistributor` 已直接以
`float[]` 工作，OBJ 路径经 `MeshAssets` 已可用（`arc_surface` 块的 `mesh` 属性）。

## 接入目标：容器执行器端点吸附（✅ 已完成，M29）

`VfxSystemSimulator.step` 目前对 arc 只做噪声 + 老化，**不调用 `SurfaceConstraint`**。

目标接线：
1. `arc_bolt`/`arc_surface` 块增 `surface` 属性（引用 `SurfaceSource`/`MeshAssets` id）。✅
2. `VfxSystemSimulator` 在 UPDATE 阶段（或 arc 生成后）对每弧调用
   `new SurfaceConstraint(distributor).constrain(arc)`，端点吸附到表面。✅
3. 无 surface 时跳过（自由弧，保持当前行为）。✅

**M29 实现**：`ArcCurve.setSurface(float[])` + `VfxSystemSimulator.step`（噪声动画后对带表面弧执行
`SurfaceConstraint.constrain`）；`SurfaceConstraint` 真最近表面点（`MeshDistance.nearestPoint`）；
`MeshAssets.resolve` 内置 `builtin:plane`/`builtin:sphere`。

## 当前已实现（M29）

- `CurveGenerator`：复刻 Blender `Curve Line → Bezier → Handle Positions → Resample`，
  `generateSurfaceArc`（per-point 短弧：沿法线 + 控制柄起拱 + 重采样），`arc_bolt` 接 from/to。
- `NoiseAnimator`：低频 value noise（Scale2/Detail2/Roughness0.5）+ `Position+time×游离速度` 域扭曲。
- `SurfaceConstraint`：**端点-only 真最近表面点吸附**（参考 `Endpoint Selection`），中间点保留起拱/漂浮形态。
- 断续出现：`arc_bolt`/`arc_surface` 按 `probability` 随机跳过（复刻 `随机点云阵列` 的 Delete Geometry）。
- 新块：`vfx.block.arc_surface`（表面电弧）、`vfx.block.arc_contact`（接触闪电）、`vfx.block.arc_spark`（粒子火花）。

## 验证

- 端点吸附：`SurfaceConstraintTest`（端点被拉回表面、内部点不受约束）。
- 起拱：`CurveGeneratorTest`（主弧 from→to、控制柄沿法线、分支独立 segment）。
- 噪声低频平滑：`NoiseAnimatorTest`（确定性 + 零强度不动 + 位移）。
