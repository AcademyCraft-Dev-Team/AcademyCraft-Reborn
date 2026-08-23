# 术语表

| 术语 | 英文 | 定义 |
| --- | --- | --- |
| 图 | Graph | 一组节点、边与黑板参数的集合，是可序列化的资产单元 |
| 节点 | GraphNode | 图的基本单元，有类型、输入/输出端口与属性 |
| 端口 | Port | 节点的数据入口/出口，有方向与值类型 |
| 边 | Edge | 连接一个输出端口到一个输入端口的数据流 |
| 黑板 | Blackboard | 图暴露给使用方的参数集合（`GraphParameter`） |
| 图参数 | GraphParameter | 黑板上的一项：名称、类型、默认值、取值范围、绑定 |
| 节点类型 | NodeType | 节点的定义（目录项），描述端口与属性，实现其语义 |
| 节点实例 | NodeInstance | 图中一个 `NodeType` 的具体实例化（带 id、位置、属性值） |
| 值类型 | ValueType | 端口/参数的类型：`FLOAT/VEC2/VEC3/VEC4/COLOR/BOOL/INT/SAMPLER/TIME/CURVE/GRADIENT/MESH` 等 |
| 隐式转换 | TypeConversion | 类型间自动转换规则（如 float→vec4） |
| 执行计划 | ExecPlan | 编译后的 DAG 求值顺序（拓扑排序结果） |
| 常量折叠 | Constant Folding | 编译期把纯常量子图求值为常量 |
| 代码生成 | Codegen | 把编译后的图翻译为 GLSL（shader）或模拟指令（vfx） |
| 动态管线 | ShaderGraphPipeline | 由图生成的 GLSL 在运行时编译出的 `RenderPipeline` |
| 动态着色器源 | DynamicShaderSource | 实现 `ShaderSource`、返回图生成 GLSL 的解析器 |
| 材质 | GraphMaterial | 持有图参数值并绑定 uniform 到管线的运行时对象 |
| 粒子缓冲 | ParticleBuffer | SoA（结构体数组）组织的粒子内存 |
| 模拟器 | Simulator | 逐帧运行模拟节点、更新粒子缓冲的引擎 |
| 输出节点 | Output Node | VFX 图的终点，把粒子转化为渲染数据 |
| 渲染桥 | bridge | ~~`GraphVfx` → `VfxRenderData` → `VfxManager` 的桥接层~~（ADR-013 已作废：图系统自持渲染 `VfxGraphRenderer`，不经 `VfxManager`） |
| 择优 | 择优 | 每个效果决定用手写还是图资产，并登记（ADR-004） |
| 电弧 | Arc | 两点/环绕/表面游走/分叉的闪电状能量效果（M22 路径驱动子系统；方向暂停中，拟转 brush 子系统） |
| 路径 | Spine / Polyline | 电弧的权威路径数据：点列 + 每点宽度（CPU 生成，20~50 点） |
| 路径源 | PathGenerator | 生成 spine 的算法：`BoltPath`（两点）/`OrbitPath`（环绕）/`SurfaceWalk`（表面游走） |
| 约束 spine | Constraint spine | 只生成宏观路径（端点/环/表面点/分叉附着点），锯齿观感交给着色器 |
