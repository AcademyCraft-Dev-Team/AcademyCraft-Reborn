# 精密结构的有效性、方块标签与来源筛选

基线：`80cfe99a 新增精密操作结构选取与结构集合节点`。本批接续结构选取专项，服务于矢量操作、电击使、空力使已有的整结构控制动作。

## 节点与用法

| 节点 | 输入/配置 | 输出 |
| --- | --- | --- |
| 结构是否有效 | 单个结构引用 | 布尔结果，可接条件分支 |
| 筛选有效结构 | 结构集合 | 移除失效、卸载、异维度及超范围项的集合 |
| 按方块或标签筛选结构 | 结构集合；方块 ID / `#方块标签` 列表；任意/全部匹配 | 按完整内容筛选的集合 |
| 按来源筛选结构 | 结构集合；来源模式；技能 ID | 按已确认来源筛选的集合 |

建议接线：选取点附近结构集合 → 筛选有效结构 → 按方块或标签筛选 → 按来源筛选 → 遍历结构 → 获取控制 → 移动/旋转 → 释放控制。也可把筛选结果接“集合中距选取点最近结构”。来源、材料筛选均为可选步骤。

- 方块列表支持逗号、分号或换行，最多 512 字符、64 个去重项。`minecraft:iron_block,#minecraft:logs` 表示铁块或属于 logs 标签的方块。空 ID、非法 ID、未知模式拒绝；合法但未注册的 ID/标签不匹配。
- “任意”要求至少一个非空气方块匹配列表中的某一项；“全部”要求每个非空气方块都匹配列表中的某一项。两种模式都排除空结构；空气间隙忽略，液体与装饰方块也参与判断，不仅检查碰撞形状。标签来自当前服务器数据包的方块注册表。
- 来源模式为：具有技能来源标记、没有技能来源标记、由本人技能创建、指定来源技能、技能创建的碎片。“本人”核对来源的 owner UUID；“技能碎片”来自 `SKILL_FRAGMENT`。仅在指定技能模式下使用技能 ID，默认是 `academy:kinetic_throw`。
- 来源指捕获/碎片事务记录的创建来源，不代表当前操作者或当前租约。电击使的金属规则、空力使喷口规则、各系资源/技能/区域权限仍由原控制动作复核。不能用“本人创建”绕过它们。
- 集合保留输入顺序，按完整激活身份去重。空集合的遍历直接完成；控制动作拒绝时仍在该动作处报告失败并按原程序清理，不自动忽略权限失败或继续控制后续结构。

节点 ID 分别为 `academy:program/core/query/structure_valid`、`academy:program/core/filter/structure/valid`、`academy:program/core/filter/structure/blocks`、`academy:program/core/filter/structure/source`。字段沿编辑器统一机制提供可选配置输入：`selectors`、`match`、`source_mode`、`skill`；图保存与动态配置仍经过同一 Codec 校验。

## 实现与边界

- 公共 `StructureWorldAccess.summarize(maximumCells)` 返回脱离后端的 `StructureBlockSummary`；旧后端默认返回不可用。可复用 API 最大扫描预算 65,536 格，精密运行时固定为 4,096 格，按完整内容包围盒体积计算，超过预算先拒绝读取，不返回部分匹配。
- 独立后端先验证驻留租约，再通过 `getChunkNow` 读取现有 plot 区块；Sable 从已有 chunk holder 读取。两者不加载区块、不读取方块实体 NBT、不改变方块或控制租约，也不向程序暴露 plot 地址。
- 任意必要区块缺失时整个统计不可用。`Optional.empty()` 表示不可用/不支持/超预算；存在的空统计才表示已知空内容，不能混用。
- `ServerProgramTargetResolver` 每次复核当前维度、激活 runtimeId、施术者存活状态和能力查询距离。结构卸载、删除或重新激活后，旧引用不能再通过筛选；筛选结果仍可能在后续 tick 失效，原动作必须继续核对。
- `ProgramStructureSource` 区分“未知来源”和“健康账本确认未标记”：外层 Optional 为空不能通过未标记筛选。损坏/不可用的来源账本不把技能结构降格为外部无标记结构。
- 大型或稀疏结构包围盒超过 4,096 格时，方块筛选会排除该结构；有效性/来源筛选不扫描整个方块包围盒。当前没有新增异步扫描、缓存或多结构同时持有控制租约。

## 验证入口

```powershell
$env:JAVA_HOME='C:/Users/Dusk/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
./gradlew.bat test sableCompatTest editorTest -DisDev=true --console=plain
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name filters-native-new -Fresh
./gradlew.bat runClientDev -DisDev=true -I validation/native_structure/structure_filters.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-filters-native-new --console=plain
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name filters-suite-new -Fresh
./gradlew.bat runClientSable -DisDev=true -PacademySableRuntime=suite -PacademyStructureOrientationRun=clientSable -I validation/native_structure/structure_filters.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-filters-suite-new --console=plain
./gradlew.bat build -DisDev=true --console=plain
./gradlew.bat build -DisDev=false --console=plain
```

每次用新的隔离目录；中文截图需将该目录 `options.txt` 的 `lang` 设为 `zh_cn`。禁止在用户原存档运行自动夹具。检查 `structure-program-orientation-result.json` 中总通过标志、三系结果、`content_source_validity_filters`，同时检查正常退出，不能只看 Gradle 返回码。

- `ProgramStructureFilterNodesTest` 覆盖来源模式、未知来源、顺序与去重、转发接口、非法配置、空结构以及三系真实 VM 的保存往返/空集合/拒绝控制语义。
- `StructureBlockSummariesTest` 覆盖扫描预算、整数极值、不完整数据与已知空区域。实际方块和标签由真实客户端注册表验证。
- `StructureFilterFixtureSupport` 在两种后端检查纯铁与铁/橡木混合内容、任意/全部模式、标签、未知 ID/标签、低预算拒绝、技能与无标记来源、失效引用；额外以真实方块状态检查空气、液体和无碰撞装饰及部分读取失效。
- `StructureProgramOrientationSmoke` 通过真实三系提取/控制/旋转/回正/持续旋转/释放/遍历再控制，新增过滤链位于周边集合到变量存储之间。`StructureSelectionEditorSmoke` 展示新增节点的合法中文图并截图；展示夹具不发送保存/执行网络包。

## 接续顺序

1. 人工在目标模组组合中编排节点，确认列表编辑、条件分支、空集合提示和控制失败反馈。自动 VM 与截图不能替代真人手感、多人服务器或具体领地模组验收。
2. 如需要同时移动多结构，先设计多租约的资源预算、部分失败与退出清理。现有遍历仍为逐项执行，不能宣称并行控制已完成。
3. 卫星/轨道/持续延迟投射的持久授权接线，按 `structure_launch_authority_2026_10_26.zh-CN.md` 继续，保留各技能爆炸、熔岩、工具掉落与保护契约；尤其不能在已有 Sable 原生爆炸处理上重复追加一次破坏。
4. S4 首撞一次/有限碎裂、I/O 全故障组合、长期账本回收、原生接触压力红测与 JBR 崩溃根因仍独立待办。本批未修改既有 Rust 改动、Sable pin、DLL、JBR 或 GC。

## 最终验收与产物

机器可核对的报告为 [structure_filter_validation_2026_10_26.json](../validation/native_structure/structure_filter_validation_2026_10_26.json)，包含 23 个源文件/夹具/启动脚本的 SHA、客户端报告与截图 SHA、构建日志及产物哈希。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 主测试 / Sable 专项 / 编辑器测试 | 3293 / 49 / 104，均 0 failure/error/skipped | `build/structure-filters-tests-final.log` |
| 独立客户端最终复跑 | 3 系通过，65 秒，正常退出 | `build/structure-filters-native-r2.log` |
| Academy + Sable + Create + 航空学最终复跑 | 3 系通过，70 秒，正常退出 | `build/structure-filters-suite-r2.log` |
| 开发构建 | 通过，27 秒，7 executed / 25 up-to-date | `build/structure-filters-dev-build.log` |
| 发布构建 | 通过，42 秒，15 executed / 1 from cache / 16 up-to-date | `build/structure-filters-release-build.log` |

专用 JVM 任务可能覆盖重叠用例，不把这些数字相加为独立用例总数。最终两种客户端每系节点完成顺序均为 `[2,3,4,8,9,10,30,31]`，包括超过 200 tick 的持续旋转、过滤后集合存储和遍历再控制。最终复跑增加超范围拒绝断言；此前 native-r1、suite-r1 同样三系通过，后续只完善测试覆盖及截图布局，生产逻辑相同。

已目视检查两种环境的 `run/native-sublevel-direct-smoke-filters-{native,suite}-r2/screenshots/structure-filters-editor.png`：11 节点完整接线可见，中文正常，底部显示“程序校验通过”。配置摘要按现有节点宽度省略显示；本批不改变编辑器保存/执行协议。

开发中先修正了夹具访问 VM 构造器的问题、空方块 ID 的拒绝，以及单测缺少执行 frame 的问题；首个客户端命令还在 Gradle init 阶段因嵌套脚本属性失败，游戏没有启动，修正后使用同一未启动副本验证。失败日志保留，均不算通过证据。原用户存档未用于自动测试，148 个基线存档文件的数量与 SHA 未变。

| 产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `build/libs/academy-26.2.0-0.0.4-alpha-dev.jar` | 32343588 | `468D1BDEF71FA60DB6D83323CF72D06E6DA2E9D46EA43494E8E91AC59377E8EC` |
| `build/libs/academy-26.2.0-0.0.4-alpha-release.jar` | 24872487 | `D8C67CC7861F7FCE1D93309F80E50E80D358E73D6777236AA5C3FEFE0A07E112` |

两种 JAR 均包含新增 API 与筛选实现，不含新实机夹具或外部 Sable 类。既有 Rust 文件 SHA 保持 `0B172501A5DA088A1CDA8AD13A41C88005D64B8D62D5347103DB566F84AC3C70`，未改写或暂存。用户已确认本批 27 个相关文件的本地 Git 提交，提交信息为“新增精密结构有效性与材质来源筛选”，以包含本条记录的提交为准；构建/运行产物及既有 Rust 改动均排除。
