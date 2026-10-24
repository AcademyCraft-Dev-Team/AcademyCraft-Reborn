# 开发线 B：捕获预演活路径修复与验证

接续本地提交 `8dec409b`（补齐捕获前预演与来源绑定），角控制与节点已在 `edea3a45` 提交。本轮原生诊断文件 `native/academy_physics/src/lib.rs` 保持原有工作区改动，没有改动 DLL/ABI。原 `run/saves/test`、`test2` 未修改。

## 定位与修复

此前预演/绑定只经过纯逻辑测试。实机接线前审阅发现以下确定缺陷，均属于未启用的新提取服务，不能据此解释现有 H 卡位。

| 缺陷 | 修复 |
| --- | --- |
| 方块碰撞盒原本是方块内坐标，转换只减源原点，漏加当前方块坐标 | 转换为 `box + position - sourceOrigin`；非原点、多格、不规则局部盒保持真实相对位置 |
| 实际结构用局部坐标生成摘要，却与宿主坐标摘要比较 | 两侧都转换到同一局部格网；事务来源的宿主坐标摘要另行核对 |
| 已完成结果只证明目标结构，不能证明来自预演的那个源选区 | 保留有界服务端完成证据，关联原请求实例、宿主、源位置/状态、目标、runtime 与 content revision；同布局的其他来源不能替代 |
| 已绑定的重复请求绕过后续租约/版本复核 | 每次复核主体、维度、目标、令牌、租约、激活、内容、姿态及当前净空；只在仍有效时返回原绑定 |
| 捕获后依然排除旧源位置，可能忽略新放入原坑的方块 | 仅预演时排除源选区；绑定时查询完整当前宿主地形 |
| 邻居形状读取未计入预算，异常回调可能传播 | 已加载视图缓存并限制源格/邻居/BE 读取，发布前复核；缺区块和预算信号保留，第三方形状回调失败明确拒绝 |
| 摘要未包含 BE/流体数据，却可能发布可绑定预演 | 这些源内容及待执行 tick、不可破坏格明确拒绝；不声称支持未保存的数据 |

普通捕获不应支付预演几何观察的成本；只有服务端登记的预演请求需要完成证据。预演观察登记按有效期清理；完成证据随最多 256 条完成回执淘汰及停服清理，没有独立时间 TTL，预演过期仍会拒绝绑定。这些是运行期记录，不是可跨重启的来源持久化。现有捕获接口没有 actor 参数，因此还必须独立验证预演主体与当前租约；不能把事务 UUID 解释为持久的主体授权。

## 验证方法

新夹具 `StructureCapturePreviewSmoke` 在隔离副本中实际执行预演 → 捕获事务 → 取得租约 → 来源绑定。正例使用非原点的四格连通铁块组合及 3/5/7 格路径，包含偏移端点上方硬顶盖、无碰撞草、权限拒绝、未加载区块、BE 拒绝、同局部布局但不同来源的捕获，以及绑定重试的令牌/主体/激活/内容/租约失效反例。

Sable 内容失效反例经其真实 plot 方块更新入口产生事件；本地通过已有 compare-and-set API。夹具不直接修改内容版本或伪造成功回执。预演目标高度检查不是物理抬升验收：本轮没有启动新提取控制器或 200 tick 悬浮。

首次 Sable 夹具保留一格断开的铁块，实际捕获后发生 Sable 的连通性拆分，绑定返回 `STALE/capture_authority_stale`。诊断确认 runtime 相同、完成证据 content revision 为 2、当前为 3，属于真实内容变化而非姿态版本误报。原 `*-sable-final-20261023`、`*-sable-diagnostic-20261023` 失败证据保留；正例改为连通组合，断开组合另作期望拒绝的反例。生产版本校验未放宽。

拆分反例第一次还暴露夹具的子体识别时序问题：Sable 子体的 parent 标记已清空，而 bounds 尚为零。修正后仅认领初始 UUID 集合之外、处于已检查空场地的实际世界位置中的新体，并记录全部新体的 id、parent、位置、bounds；保持至少两个存活结构的断言，允许最多 40 tick 发布等待，每次等待释放租约。复测无需额外等待就识别并正常删除两个结构，同时确认父体原局部 `(2,0,0)` 变为空气，旧绑定返回 STALE。没有把拆分后的新内容冒充原预演来源。

```powershell
. ./build/native-physics-p16-env.ps1
./gradlew.bat test sableCompatTest nativePhysicsTest build -x testNativePhysicsRust -DisDev=true @physicsNativeArgs --console=plain
./gradlew.bat build -DisDev=false @physicsNativeArgs --console=plain
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name 'capture-preview-native' -Fresh
./gradlew.bat runClientDev -DisDev=true @physicsNativeArgs `
  -I validation/native_structure/structure_capture_preview.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-capture-preview-native --console=plain
```

Sable 使用新副本，改为 `runClientSable`，增加 `-PacademySableRuntime=sable -PacademyStructureCapturePreviewRun=clientSable`。结果为副本目录下的 `structure-capture-preview-result.json`；Gradle 退出成功不能替代该文件的 `passed=true`。Rust S0 已知失败前置仍显式跳过，没有移除依赖或宣称原生全绿。

Sable 拆分反例再增加 `-PacademyCapturePreviewDisconnected=true`；它与默认连通正例分开运行。三个最终结果分别为 `capture-preview-native-connected-20261023`（34 项）、`capture-preview-sable-connected-20261023`（34 项）、`capture-preview-sable-split-r2-20261023`（7 项），均位于 `run/native-sublevel-direct-smoke-*`，结果 `passed=true`。完整 JSON 及失败历史保存在[本轮机器证据](../validation/native_structure/structure_upgrade_line_b_capture_validation_2026_10_23.json)。

最终复测：JVM **2,997 项**、Sable 专项 **23 项**、原生桥接 **36 项**、编辑器 **104 项**，均 0 失败、0 错误、0 跳过；`verifyStructureUpgradeScaffold` 与开发/发布两种 `build` 通过。新增 5 项 JVM 用例覆盖局部形状坐标、事务证据请求身份、重复绑定约束及形状回调失败分类。日志为 `build/line-b-capture-validation-dev-final.log` 与 `build/line-b-capture-validation-release.log`。原生桥接通过不替代 Rust S0；其已知 23 通过/5 失败记录仍有效，本轮未重跑。

## H 连续场景诊断

前次连跑 tier1 与单档成功的 source_origin 都是 `(-79,120,47)`，源内容均为 128 格，现场重采几何 34 盒且没有重叠。连跑安装地形为 8 sections/57 boxes，单档为 4/36；差额正好是上一档的 4/21。新区四个 section 的安装顺序、数量相同，且一直 `covered=true`。这些证据不足以判定原生缓存、动态体或接触历史中的哪一项为根因。

新增诊断开关 `-PacademySubLevelLiftDiscardBetweenTiers=true`：默认关闭；开启后保持原发射后等待 20 tick，再只移除上一档夹具创建的结构，失败立即终止。记录清理前后和下一 tick 的结构、地形及活动数量，不强刷缓存，不改生产技能。使用相同固定锚点 `'-PacademySubLevelLiftAnchor=-156,123,61'` 的两个 fresh 副本比较保留/清理模式。清理模式成功也不表示 H 已修复。

```powershell
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name 'capture-h-retained' -Fresh
./gradlew.bat runClientDev -DisDev=true @physicsNativeArgs `
  -I validation/native_structure/sublevel_lift_tiers.init.gradle `
  -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-capture-h-retained `
  -PacademySubLevelLiftEmbedded=true -PacademySubLevelLiftTerrain=step `
  -PacademySubLevelLiftDisturbance=true '-PacademySubLevelLiftAnchor=-156,123,61' --console=plain
```

第二份 fresh 副本改名 `capture-h-discarded`，额外加入 `-PacademySubLevelLiftDiscardBetweenTiers=true`。H 夹具以日志的 `SUBLEVEL_LIFT357_SMOKE_PASSED/FAILED` 判断结果，进程正常退出不等于通过。

| 固定坐标对照 | 结果 | 日志 |
| --- | --- | --- |
| 保留已发射上一档结构（默认生产行为） | tier0：3.064378 格、200 tick；tier1：0.319660 格，超时，tier2 未跑 | `build/line-b-capture-h-retained.log` |
| 发射后只清理夹具上一档结构 | 3.064378 / 5.064378 / 7.064378 格，各完整 200 tick | `build/line-b-capture-h-discarded.log` |

清理后下一 tick 原 4 sections/21 boxes 尚存在，新捕获完成时变为 4/36；保留模式是 8/57。这排除了“坐标不同”作为本次对照的解释，但清理同时改变动态体、地形存留与原生插入/接触历史，**尚未隔离哪个因素致因**。此前失败数值在当前修订逐值重现，不能归功于预演修复或声称问题已关闭。

下一项原生诊断应比对发送到求解器的真实地形盒指纹及对应 body pose，再隔离旧动态体和旧地形两个因素。目前 Java 缓存仅保留原生 body 与盒数量，native ABI 不支持反查形状或枚举孤儿 collider；现场重采无重叠不是已安装几何无重叠的证明。

## UNVERIFIED 与下一步

- `UPG-CTRL-01..04` 的结构侧规划、真实子步路径跟随、状态和取消仍未接线；EXTRACTION 玩法能力保持关闭，H 沿用原控制器。
- 捕获耗时超过预演有效期、复杂第三方形状/权限回调、跨区块卸载等组合仍需扩展实际验证。绑定会再次执行原捕获 permission predicate；当前三系的空间权限谓词兼容，但依赖源方块状态的扩展谓词可能因源格已变空气而拒绝，不能声明任意谓词兼容。
- 普通 H 连续多结构场景卡位、S0 原生接触失败尚未解决；不能用独立预演或单档成功关闭。
- 捕获基准持久化、自动归块、技能破坏与碎裂、MPS/流体/软体属于后续阶段。

下一阶段在上述来源绑定成立后，实现结构侧提取操作，并在实际子步重新验证路径；到位和稳定后才开始完整 200 tick 悬浮，不允许已有结构用 setPose 脱困。
