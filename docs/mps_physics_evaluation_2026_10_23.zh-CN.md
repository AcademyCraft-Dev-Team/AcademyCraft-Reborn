# MPS 参考、采用与流体/软体扩展评估

日期：2026-10-23（Asia/Singapore）。状态：**源码审查与设计；未运行、未构建、未替换 DLL。** 配套主方案见[技能物理结构完善方案](structure_skill_physics_upgrade_plan_2026_10_23.zh-CN.md)。

## 1. 结论

**将 MPS 列为统一物理底座的重点候选，建立独立 provider 原型进行验收。** 本项目后续要加入流体和软体，因此其宽泛包装具备直接价值；不能只按当前刚体需求评价它。近期同时吸收批量边界、几何构建和测量方式，修复主方案中的技能层缺口。

“比 Sable 快”仍是待验证命题。固定版本的公开报告没有同场景 Sable 对照；其 10,000 球体基准主要采首次步进，明确不证明长期堆叠、穿透或睡眠收敛。报告也将若干性能增益列为尚未证实。该报告适合作为测试方法参考，不能外推 Minecraft FPS 或可承载结构数量。[MPS 基准](https://github.com/DeepSpace-Studio/mps/blob/2cf8cc8bf43f55a005a2ad7b3352ba4e49286dee/docs/world-step-benchmarks.md)

## 2. 固定来源和许可

本次 GitHub API 核查锁定以下版本；不要使用主页缓存中曾显示的旧 Rapier `2fb9215`。

| 组成 | 本次锁定 |
| --- | --- |
| MPS | [DeepSpace-Studio/mps @ 2cf8cc8bf43f55a005a2ad7b3352ba4e49286dee](https://github.com/DeepSpace-Studio/mps/tree/2cf8cc8bf43f55a005a2ad7b3352ba4e49286dee) |
| Rapier gitlink | [Polari-Stars-MC/rapier @ 7f9a9e2771a635210b38df552722950a92a4fca4](https://github.com/Polari-Stars-MC/rapier/tree/7f9a9e2771a635210b38df552722950a92a4fca4)；workspace 标识 0.35.3 |
| Parry gitlink | [Polari-Stars-MC/parry @ 1be4b1a7cd0a090bd7efb1207b7bc0d453f4132e](https://github.com/Polari-Stars-MC/parry/tree/1be4b1a7cd0a090bd7efb1207b7bc0d453f4132e) |
| 当前 Academy 对照 | 本地 official `rapier3d-f64=0.36.1`，自有 ABI v2；不是上述 MPS ABI |

MPS 根许可证为 GPLv3 文本；本项目 `gradle.properties` 和原生 wrapper 声明 GPL-3.0-only，具备按 GPL 条件接入的基础。采用前仍记录实际文件许可头、上游修改与依赖清单，不把“根目录有 LICENSE”代替逐项来源核对。分发派生源代码/DLL 时保留许可、版权与修改说明，并提供符合 GPL 要求的对应源码和构建材料。[MPS LICENSE](https://github.com/DeepSpace-Studio/mps/blob/2cf8cc8bf43f55a005a2ad7b3352ba4e49286dee/LICENSE)

Rapier fork 保留 Apache-2.0；采用其代码仍保留该许可、归属及适用的 NOTICE、修改标记。不能将 MPS 包装的 GPL 与 Rapier 的 Apache 混成一份许可。Parry 及其他依赖在实际采用清单中继续逐项核对。[固定 Rapier LICENSE](https://github.com/Polari-Stars-MC/rapier/blob/7f9a9e2771a635210b38df552722950a92a4fca4/LICENSE)

实际引用时新增 `thirdparty/mps_dependencies.md` 和机器可读来源表，至少记录原仓库、完整 commit、路径、原文件摘要、修改说明、许可证及产物哈希；完整许可置于 `thirdparty/licenses/`。本轮只创建审查文档，源码参考副本在忽略的 `build/mps-readonly-audit/`，没有复制到生产包。用户提到的现成 DLL 未提供具体二进制，本轮无法验证它是否对应这些源码。

Sable 参考/修改继续按其自身及用户已有授权的具体范围处理，MPS 的许可不能代替 Sable 的许可，也不能由它推导 Sable 代码可随意重分发。

## 3. 可借鉴机制与源码问题

| 项目 | 已读源码事实 | Academy 采用方式与前置条件 |
| --- | --- | --- |
| 批量 FFI | `world_body_snapshot` 批读句柄与位置/四元数/线角速度；另有批写姿态/速度 | 优先增量扩展自有 ABI；使用复用缓冲、调用前容量检查、generation 校验。现实现仍构造全量 handles/snapshot，不宣称零分配或脏体增量。[world.rs](https://github.com/DeepSpace-Studio/mps/blob/2cf8cc8bf43f55a005a2ad7b3352ba4e49286dee/crates/mps-core/src/rapier/world.rs#L1628) |
| 批量 collider | 按父 body、材料和碰撞属性分组构造 compound | 借鉴分组，但保留局部格/子形状映射；当前批接口先插入再按输出容量截断 handles，须改成容量预检/完整回执或整批回滚。[batch.rs](https://github.com/DeepSpace-Studio/mps/blob/2cf8cc8bf43f55a005a2ad7b3352ba4e49286dee/crates/mps-core/src/rapier/batch.rs#L614) |
| 体素几何 | 动态/静态分别选择盒、贪心合并或表面 mesh，按 Y 分片构建 | 适合满格地形；半砖/台阶/栅栏保留真实形状。每分片分配全 grid visited 的实现需压缩/复用，小结构并行开销另测。[voxel.rs](https://github.com/DeepSpace-Studio/mps/blob/2cf8cc8bf43f55a005a2ad7b3352ba4e49286dee/crates/mps-core/src/rapier/voxel.rs#L144) |
| 活动岛/约束求解 | fork 有持久岛、接触图维护、着色和并行求解路径 | 作为候选引擎实验，先与相应上游版本做差异核对；源码机制存在不等于创新归属或性能增益已证明。[solve.rs](https://github.com/Polari-Stars-MC/rapier/blob/7f9a9e2771a635210b38df552722950a92a4fca4/src/pipeline/physics_pipeline/solve.rs#L172) |
| 配置和测量 | 显式运行时配置、步进矩阵、可选 profiler | 只在配置变化时更新；高速技能不能照搬关闭 CCD/事件的低开销示例；测绝对耗时关闭 profiler，热点分析单独开。[运行参数](https://github.com/DeepSpace-Studio/mps/blob/2cf8cc8bf43f55a005a2ad7b3352ba4e49286dee/docs/world-step-benchmarks.md) |

另外两项必须进入集成门槛：

- fork manifest 的默认 features 实际包含 `block-solver`，相邻注释却说明其在 3D domino 场景抖动且非默认。应显式固定 features，并分别跑堆叠、楔紧及旋转稳定性；不能相信注释推断实际二进制行为。[固定 manifest](https://github.com/Polari-Stars-MC/rapier/blob/7f9a9e2771a635210b38df552722950a92a4fca4/crates/rapier3d-f64/Cargo.toml#L23)
- MPS 的 FFM crate 主要声明版本/探针，不能理解为可替换 Academy 的完整 Java 适配器；其 opaque world 指针、packed handles、arena 及错误码均有自己的契约。共享内存须验证线程、生命周期和溢出，不因宣称零拷贝就直接跨线程访问。[边界说明](https://github.com/DeepSpace-Studio/mps/blob/2cf8cc8bf43f55a005a2ad7b3352ba4e49286dee/docs/BOUNDARY.md)

这些是静态审查发现和适配要求，未运行复现；不能写成已修复或已验证的崩溃。

## 4. 采用路线与可替换边界

建议保留 `StructureRef → SubLevel 内容/生命周期`，将其下方的原生求解抽象成 provider：

```text
技能 / 精密节点 / 来源 / 伤害 / 恢复事务
                   ↓
    宿主物理协调器、能力与预算、空间查询
                   ↓
  Academy Rapier provider | MPS provider
       刚体               刚体 / 软体 / 流体

Sable：独立内容与物理世界，由后端适配器接共同玩法协议
```

1. **G0 来源和能力探针**：锁定完整子模块、Cargo.lock、工具链、target、feature 与许可证；检测 ABI、数据布局、单位、矩阵/四元数和引擎能力。现成 DLL 必须能关联到可复现构建，不能重命名成 academy_physics.dll 直接加载。
2. **G1 原生对照**：先在隔离 harness 实现现有刚体操作的 MPS adapter。保持 f64、质量/惯量、重力、睡眠、关节、CCD、覆盖暂停及 panic/失败隔离语义；验证旧/错世界句柄、销毁、越界、坏输出与容量失败。补 MPS 批量插入不完整回执等问题。
3. **G2 游戏对照**：同一个 SubLevel 内容、网络、控制、恢复入口，仅切 provider。跑 H、防楔紧、薄墙、暂停隔离、交互/掉落与 N 扫描，分离求解器收益与 wrapper 收益。
4. **G3 流体/软体原型**：使用同一协调器和批量协议，独立对象类型、存储和渲染；先做本地与宿主地形、刚体的稳定耦合，再接技能和精密节点。
5. **G4 选择生产默认**：以刚体性能/正确性、流体软体完整性、平台可构建性和维护成本共同决定整合 MPS、移植部分实现或保留多 provider。当前刚体修复不必等待该决定。

provider 在启动时确定，同一物体仅有一个求解权威；不运行中热替换库，不把句柄写存档，不由本地与 Sable 同时积分同一刚体。新版 ABI 与文件格式显式版本化，不支持的能力返回具体原因。Sable fork 不能直接链接同名 MPS DLL 并复用它的句柄。

并行求解固定线程上限并测小 N；异步工作只访问不可变物理/内容快照，世界修改留主线程。不能为提高测分在求解期间从 native 线程直接调用 Minecraft 方块 API。

## 5. 流体、软体：纳入主架构而非事后插件

已核实 MPS 不只提供公式：流体接口可以创建/管理 SPH 世界和粒子，另有对刚体施加浮力/阻力的入口；软体包含刚体弹簧骨架、点质量软体和体素到粒子的映射。fork 中存在 MassSpring/XPBD 及体积、撕裂等约束路径。这些都是值得采用的真实代码基础，组合效果仍须实测。[流体接口](https://github.com/DeepSpace-Studio/mps/blob/2cf8cc8bf43f55a005a2ad7b3352ba4e49286dee/crates/mps-core/src/rapier/fluid_sph.rs) · [软体接口](https://github.com/DeepSpace-Studio/mps/blob/2cf8cc8bf43f55a005a2ad7b3352ba4e49286dee/crates/mps-core/src/rapier/soft_body.rs)

静态审查发现四项前置工作：

1. SPH 的 `fluid.step` 后，有碰撞代理的粒子会被代理位置/速度覆盖；代理接收的是步前状态，本路径未见 SPH 力注入代理。存在丢失 SPH 积分结果的风险，必须用启用/关闭碰撞的压力、重力对照验证并修正耦合顺序。[world.rs](https://github.com/DeepSpace-Studio/mps/blob/2cf8cc8bf43f55a005a2ad7b3352ba4e49286dee/crates/mps-core/src/rapier/world.rs#L1017)
2. 软体 `collide=true` 时跳过通常的 step 投影，外部代理路径使用力驱动；不能据此宣称 XPBD 体积/距离约束与刚体碰撞已完整组合。须添加联合测试与专用耦合步骤。[soft_body.rs](https://github.com/Polari-Stars-MC/rapier/blob/7f9a9e2771a635210b38df552722950a92a4fca4/src/dynamics/soft_body.rs#L1174)
3. 当前 SPH 两轮全粒子邻域遍历为 O(N²)，每步分配加速度数组。大水体需要空间邻居索引、复用缓冲和严格粒子/接触预算，不能仅提高粒子上限。[fluid.rs](https://github.com/Polari-Stars-MC/rapier/blob/7f9a9e2771a635210b38df552722950a92a4fca4/src/dynamics/fluid.rs#L169)
4. 流体重复开启碰撞及开启后新增粒子的代理维护需补生命周期回归；已读路径存在重建代理映射未先清旧体、增粒子未同步建代理的风险。逐粒子刚体代理本身也需要单独测成本。[fluid_sph.rs](https://github.com/DeepSpace-Studio/mps/blob/2cf8cc8bf43f55a005a2ad7b3352ba4e49286dee/crates/mps-core/src/rapier/fluid_sph.rs)

以上是未运行的代码审查结论，既不是否定 MPS，也不是已完成的修复。采用计划应明确包含这些耦合完善工作。

长期抽象应覆盖不同物理对象，但保留已存在的刚体结构 API。拟议能力至少分为 `RIGID_BODY、SOFT_BODY、FLUID、SHAPE_QUERY、CONTACT_EVENTS、TWO_WAY_COUPLING、BATCH_SNAPSHOT`。能力还需声明规模、形状、单/双向耦合、线程和保存限制；不能一个 `supportsPhysics=true` 包办所有语义。

### 5.1 对象与同步

- 刚体继续使用 StructureRef 与单一姿态；软体使用独立持久对象 ID、拓扑版本、节点/单元映射和变形快照。局部格经过变形后不能继续用单个四元数逆变换进行挖掘或命中。
- 流体使用区域/粒子集合 ID、材料、体积/质量、速度与来源归属。粒子不是 Minecraft 方块或掉落物，不能逐粒子持久化为实体并沿用方块战利品。
- 快照按对象类型批量化，位置/拓扑/内容版本分离；客户端粒子与网格更新单独预算。状态分块发送，恢复后校验总质量/拓扑，不持久化临时 native 地址。
- 节点可共用查询、冲量及材料语义，但把刚体角度回正用于软体时返回不支持；软体需要独立的目标形状/约束操作，不假装存在一个完整刚体旋转角。

### 5.2 耦合顺序与游戏语义

先明确一个权威时步顺序：应用外力/控制 → 刚体、软体和流体耦合求解 → 汇总接触及状态 → 服务端消费内容变更。双向作用不能只推动一边；不能把自主粒子积分和碰撞代理积分叠加，或用代理旧状态覆盖新积分。重力、浮力、阻力与排水反作用分别记账。

流体 MVP 建议先验证有界水体、重力与固壁、排水/浮力、运动刚体交互，再决定与原版水方块的转换规则。区块边界、桶/技能取放必须守恒体积，原版流体 tick 与粒子解算不能同时拥有同一份水。

软体 MVP 建议先验证单块可形变物、形状/体积保持、固定节点和刚体接触，再增加撕裂、材质与多块结构。撕裂继承来源并受碎片预算，拓扑变化必须同步到命中、渲染和保存。

Sable 刚体与本地流体/软体的双向耦合单独验收：需要 Sable 提供同步形状、速度和受力入口；做不到时明确仅支持本地同求解世界的耦合，不以每 tick 复制位置冒充完整耦合。

### 5.3 归块与预算

刚体的静止归块规则不能直接套给软体/流体。软体先定义可接受的形变回收政策、原始材料单元所有权和无冲突目标；流体按体积归还可接收格或存储容器，不能靠四舍五入粒子生成额外水源。未知/未支持内容保留为持久对象，不自动删除。

分别限制刚体数、软体节点/约束、流体粒子/邻居对、耦合接触、几何重建和同步字节。远处对象可降低表现采样；物理降频必须与耦合时间步一致。达到预算时拒绝新创建或持久化挂起，不静默丢约束/丢质量。

## 6. 验收和待确认项

主方案的全部刚体矩阵适用于 MPS provider；额外覆盖：

| 范围 | 完成判据 |
| --- | --- |
| 批量 ABI | 缓冲不足零隐藏创建、旧句柄拒绝、状态完整、有限值、销毁无悬空引用 |
| 性能宣称 | 本地与 Sable 同硬件/同内容/同可动数量/同质量门槛；原生与游戏成本分开；p95/p99 及原始样本可复现 |
| 引擎替换 | 楔紧、堆叠、斜面、关节、覆盖暂停、CCD、首撞和掉落无回归；不通过关闭碰撞提高成绩 |
| 流体 | 静水/落水/固壁/高速移动边界，长期质量漂移、穿透率和耦合反作用受控；启用碰撞不丢失压力/重力效果 |
| 软体 | 体积/拉伸/固定点稳定；同时启用刚体碰撞与约束时约束仍执行；拓扑变更、保存/加载一致 |
| 内容归属 | 原版水/粒子水、原方块/软体材料不能重复存在；清理、掉落、归块各有明确所有者 |
| 发布 | DLL 与源码/子模块/锁文件/feature 对应；许可证、修改记录、对应源码齐备；平台支持范围明确 |

本轮 UNVERIFIED：MPS 未在本机编译或执行，未测当前提供者 DLL，未做 Sable 配对；所有性能增益、长时间稳定性、跨后端耦合、流体/软体游戏接入及分发清单仍待实现验证。不要把仓库测试数量或公式数量当作这些玩法通过的证据。
