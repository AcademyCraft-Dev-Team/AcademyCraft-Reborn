# 本地物理结构 v2：Sable 配对诊断与实施骨架

日期：2026-10-20。交付范围：可编译的未启用骨架、可复跑的配对测量工具与实施契约。**当前玩法故障尚未修复。** 2026-10-20 第二轮：七个 PIPE 草案端口已实现为未接线服务（几何合并、地形代际门禁、子步力/力矩、抬升状态机、姿态时间轴、网格规划、分片捕获），`GAMEPLAY_ENABLED` 仍为 false；native ABI、保存协议与生产接线仍未完成。

## 1. 基线与可复用边界

固定输入使用 `docs/sable_compat_artifacts.json`：Sable `2.0.6-port.26.2.0.70`，SHA-256 `21A22BCD6B0DD5BC742472E332F7E8A17DD83CBBD471EBE97087E153ACC206CA`；内嵌 Companion `1.6.0`、Veil `4.3.2` 和对应 Rapier 包。运行前沿用 `verifySableCompatRuntime` 校验大小、哈希与重复依赖。普通 local 组不添加 Sable runtime。

本机参考源码位于 `../create-fork/.porting/sources/`，源码工作树不等同于固定 JAR 的构建证明。以下只记录实际查阅的设计，不声称源码与二进制完全一致：

| 源文件（相对于上述目录） | 已观察到的机制 | 本地方案 |
| --- | --- | --- |
| `Sable/sable_rapier/src/main/java/dev/ryanhcode/sable/physics/impl/rapier/RapierPhysicsPipeline.java` | section 体素数据、邻域状态、碰撞形状缓存 | 按 section 编译占用/材料数据，更新脏 section；设计独立 native adapter，消除每块 collider 的内部接触问题 |
| `Sable/common/src/main/java/dev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem.java` | 每个真实 substep 执行控制/施力/求解回调 | 每子步重新计算反馈；本地当前每外层 tick 一次 impulse 的路径需替换 |
| `Sable/common/src/main/java/dev/ryanhcode/sable/network/client/SubLevelSnapshotInterpolator.java` | 按 gameTick 排序的快照历史、有限外推 | 独立实现 epoch/seq/tick 时间轴；与接触采样共用 |
| `Sable/common/src/main/java/dev/ryanhcode/sable/sublevel/render/fancy/FancySubLevelSectionCompiler.java` | section 编译任务、网格缓存与渲染线程上传 | 取消“小于 512 格一直逐块绘制”的限制，按内容/光照版本复用网格 |

许可核查：`Sable/LICENSE.md` 为 PolyForm Shield 1.0.0，包含修改/分发条款、通知要求和 Noncompete 条款；`Sable-Companion/LICENSE` 为 MIT。用户允许在许可范围内参考。**本次没有复制 Sable/Companion 业务源码，没有加入它们的二进制或修改现有禁止捆绑检查。** 后续如果采用具体代码，应逐文件记录来源、版本、修改与对应许可/额外授权依据；不能把 Companion 的 MIT 自动套到 Sable 主体。是否需要额外授权不由本技术骨架作结论。

## 2. 对比口径

测量夹具见 `validation/structure_parity/`。最终两侧使用同一 8×2×8 的 128 格清单、同一石头地形和种子、JBR 25、串行进程、独立新世界。地面场景挖出周围平整石台中的相同体积；悬空场景把选中格提高 10 格，背景地形不变。暖机 60 tick，抬升控制 260 tick，投掷后观察 20 tick。每个场景每后端两次、第二轮颠倒顺序。

此前 H 失败形状保留在 `ground_128.csv`。初测发现 Sable 桥对这一清单创建后沿 X/Z 各偏移约 -4 格，未满足初始对齐条件；因此没有把它用于最终比值。最终使用首格为局部原点的 `cuboid_128.csv`，并检查实际质心与源格平均中心相差 ≤0.01。该修正只改变夹具，没有掩盖或修复 Sable 桥的偏移问题；真实 H 原位捕获仍需独立审计。

这是 **服务端公开 API 集成小样本**，不是单独求解器跑分，也不是客户端/H 按键验收。Sable 总质量为 256，本地为 128，所以用相同质量归一化响应：k/m=30，d/m=16，最大 F/m=30，重力补偿 9.81。发射统一为 50 blocks/s。不能直接用两侧不同质量预算/128 对 256 的 H 选取结果计算倍数。

采集创建同步调用耗时、提交就绪耗时、服务端 tick p50/p95、实际到位/悬浮 tick、20 tick 水平位移与墙钟时间。`server_tick_ms` 从 Pre 最高优先级到 Post 最低优先级、夹具工作之前；`probe_ms` 单列，创建成本由 `capture_call_ms` 直接记录。包含整个服务器工作，不能称为“纯物理耗时”。

功能失败样本仍保留 CPU 时间，但不得宣称为完成同一任务的吞吐优势。历史 r1/r2 中参数越界、飞出已加载观测范围的失败样本，r3 的初始错位样本，以及 r4 中 4 格高结构抬升 3 格后仍受到台壁阻挡的样本，都不进入最终比例。最终采用 r5/r6 的 2 格厚结构，已对齐且抬升后可以水平离开。小样本仅用于确定修复顺序；跨运行噪声、热缓存、渲染、GC、网络和复杂存档仍需后续矩阵。

实测数值与当前局限由同目录 `structure_parity_baseline.zh-CN.md` 记录。

## 3. 设计决策

1. **正确性先于更大的预算。** 先消除地面提取内部接触与 O(n³) 地形合并。地形缺失时继续保守暂停；不得用关闭碰撞、全局缩盒、取消 CCD 或跳过必需 section 换速度。
2. **调度必须约束实际工作。** 按 body→required section 建立依赖；主线程采集不可变快照，worker 只编译快照；发布前核对 revision/epoch。预算耗尽保留已完成工作并延后，不把 plan 仅用于日志。
3. **持久化与首帧是捕获事务的一部分。** 原有 journal 提供所有权与恢复屏障；减少完整世界保存需先补局部耐久协议。源格移除、内容快照、首次姿态发布需要明确的先后关系和客户端暂存机制，不能简单把 `save` 丢进线程池。
4. **控制用物理时间，悬浮用游戏 tick。** 每实际子步反馈；原位质心固定、目标 3/5/7；第一次到位才开始 200 tick；抬升受阻独立超时并可诊断。精密节点、技能与非玩家 API 共用控制服务。
5. **渲染与碰撞共用时序。** 姿态包有时间、速度和激活代际；两者用同一插值采样。内容更新与运动更新分离，小结构也缓存网格；GPU 操作留在渲染线程。
6. **逐阶段替换。** 不重写能力/程序权限和交互掉落系统。旧 native_level、SubLevel、Sable 的 backend 身份不能混用。新 native 体素/邻接 ABI 应另加能力版本与拒绝旧库的测试，不能直接把旧 cuboid 数组重新解释成另一格式。

## 4. 实施与门槛

逐方法契约、实现状态、构建命令及 UNVERIFIED 清单集中在根目录 `FILL-SPEC.md` 的 PIPE-01..07。七个业务方法已实现为草案服务并通过单元测试，但均未接入生产路径，主类仅注册 disabled 草案；构建校验改为检查实现委托而非 TODO 占位。

建议目标（尚未达到，需固定硬件和工作负载后校准）：同场景成功抬升率 100%；首帧出现前不得暴露长时间空洞；稳定单结构服务端 p95 不超过 Sable 的 1.5 倍且总 tick 小于 50 ms；创建最大单 tick 工作尽量低于 50 ms；姿态更新不触发网格重建；投掷按游戏时间保持约定初速，墙钟速度由 tick 和传输延迟分别解释。若 Sable 本身在某一场景失败或耗时接近测量噪声底，记录绝对值，不用比值作为通过门槛。
