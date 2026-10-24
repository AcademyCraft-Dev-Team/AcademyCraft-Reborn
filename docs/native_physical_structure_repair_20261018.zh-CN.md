# 未加载 Sable 时物理化结构修复（2026-10-18）

## 结论

此前“单方块物理化后显示 3×3×3 碰撞箱、无法移动、实体被挡住、画面抖动”不是三个技能各自的逻辑错误，而是同一条原生后端链路在普通安装中不可用：

1. 未加载 Sable 时后端选择 `NATIVE`，但普通 JAR 不包含原生物理库，`NativeStructurePhysics.createRegion` 抛出 `Structure physics is unavailable: Native physics is opt-in ...`。
2. 捕获事务此时已经提交并移除了源方块，结构留在独立存储中，没有刚体。
3. `NativeStructureProjectionEntity.makeBoundingBox` 对模型外包框 `inflate(1.0)`，单方块因此显示 3×3×3；宽相位、实体交互和调试碰撞箱都受它影响。
4. 捕获几何使用单刚体 64 盒上限，6×6×4、3×3×3 等选择经常在建体前被拒绝（`target_geometry_budget`），而技能在事务提交后才打开物理区域，失败后不回滚。
5. 磁场操作、动能投掷、高速喷流及三类精密节点的提取/控制/移动/发射节点全部复用该链路，所以表现为“所有物理化效果都有问题”。

## 修复内容

- **自包含求解器**：打包本项目自己的 `academy/native_physics/windows-x86_64/academy_physics.dll.b64` 与哈希清单。加载顺序为显式属性/环境变量、游戏目录部署、内置制品；显式配置失败不会静默回退。JDK 25 的 FFM 警告模式可直接运行；需要严格模式时设置 `-Dacademy.nativePhysics.requireNativeAccess=true`。
- **技能捕获门禁**：三个技能和三个精密 `programExtract` 在移除方块前调用 `StructurePhysics.physicalBackendProblem(server)`；后端不可用则保留方块并返回 `physics_backend_unavailable:<原因>`。
- **先建体后移除**：捕获请求新增 `physical` 标志；协调器在复制校验后、移除源方块前调用 `CaptureTarget.preparePhysics`，打开区域并 attach 刚体；失败则整体回滚。技能随后通过 `StructurePhysics.attachedRegion` 接管已准备好的区域，不再重复开区。
- **投影碰撞箱**：`makeBoundingBox` 改为真实碰撞盒并集，不再膨胀 1 格；单方块为 1×1×1，栅栏等外伸形状仍被覆盖。
- **多刚体与盒预算**：结构总预算 2048 盒，单刚体 256 盒；相邻整方块先贪心合并为长方体；仍超过 256 盒时拆成多个动态刚体，并用 Rapier fixed joint 锁成同一装配。
- **快速撞击**：动能投掷的实体检测增加沿速度的扫掠目标盒，避免 50 m/s 结构在两帧之间穿过窄实体；高速平移时不再用 0.5 格恢复容差把前方实体当骑乘者带走。
- **旧存档清理**：新增 `/academy debug structure status` 与 `/academy debug structure cleanup`。后者只卸载没有活动/已准备物理区域的原生结构投影，保留存储内容，不删除方块。

## 验证

- `test -DisDev=true`：全量单元测试通过。
- `nativePhysicsTest`：13 项 Rust ABI 测试与原生 JVM 集成测试通过（含 fixed joint）。
- `sableCompatTest`：Sable 可选桥测试通过，未引入 Sable 制品或代码。
- `build -DisDev=true`、`build -DisDev=false`：两种构建通过。
- 无 Sable、无任何 `-Dacademy.nativePhysics.*`、无 `--enable-native-access` 的专服烟测：
  `run/native-structure-physics-smoke-server-bundled-r10/native-structure-physics-server-result.txt` 为 `PASSED`，覆盖控制、承载、动能投掷飞行/撞击/跟随、高速喷流技能与程序、磁场操作技能与程序，以及加速器/空力/电磁三类精密图。

## 仍待处理

- 该轮烟测通过 `-Dacademy.nativeStructurePhysicsSkipPlasmaAim=true` 跳过了等离子瞄准面复验；该项与物理化链路无关，需单独修复瞄准面/结构面采样的一致性后再恢复强制校验。
- 本轮只内置并验证 Windows x86_64 制品。Linux/macOS 的 x86_64/aarch64 构建已接入平台识别与构建映射；Android 还需要 NDK 交叉编译及支持 FFM 的启动 JVM 实测。对应平台未内置制品时，技能会在捕获前明确拒绝。
- 旧存档中的幽灵结构不会被自动改写；请在 `/academy debug structure status` 确认后执行 `cleanup`，存储内容仍保留。
