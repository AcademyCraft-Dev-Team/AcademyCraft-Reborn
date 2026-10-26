# 精密操作的物理结构与结构集合选取

基线：`47e20a09`。本批按用户新增需求实现选取节点，不推进 S4 或改变三系结构控制规则。用户已确认本批 26 个相关文件的本地提交，提交信息为“新增精密操作结构选取与结构集合节点”；以包含本记录的提交为准。

## 可用节点与接线

矢量操作、电击使、空力使的精密操作编辑器均包含以下公共只读节点。公共查询也可供其他能力或扩展程序复用，但不因此获得结构控制动作。

| 节点 | 输入 | 输出 |
| --- | --- | --- |
| 选取点最近结构 | 世界坐标 `center`、半径 `radius` | 整个结构 `structure`；无目标不输出 |
| 选取点附近结构集合 | 世界坐标 `center`、半径 `radius` | 去重、近到远排列的 `structures`；无目标为空集合 |
| 集合中距选取点最近结构 | 世界坐标 `position`、结构集合 `structures` | 最近有效结构；全失效或空集合不输出 |
| 筛选选取点附近结构 | 世界坐标 `center`、半径 `radius`、结构集合 `structures` | 范围内仍有效的结构，保留输入顺序 |
| 构建、单结构、并集、交集、差集、包含、大小、获取、遍历结构集合 | 与既有实体集合节点对应的端口 | 专用 `STRUCTURE_SET` 或 `STRUCTURE_REFERENCE` |

单目标示例：**世界坐标 → 选取点最近结构 → 本能力的取得结构控制 → 移动/旋转/释放**。若选取点来自普通世界方块坐标，先经过“方块中心坐标”节点转换。

集合示例：**世界坐标 → 选取点附近结构集合 → 保存结构集合变量 → 遍历结构 → 取得结构控制 → 动作 → 释放**。循环体返回后遍历下一项。集合本身不能直接连接单结构控制端口；遍历是依次执行，不能据此宣称已有并行多结构控制。

新增集合可保存为运行期变量、经条件选择传递并用于调试输出。集合按完整引用与激活运行 ID 去重；同一存档身份重新激活后属于不同引用。旧图、旧变量类型与旧视线结构节点均保留。

## 查询和控制边界

- 使用 `StructureQueries.nearby/proximity` 公共 API；`LoadedStructureProximity` 复用本地 SubLevel 和 Sable 的已加载碰撞观察。可在两种后端共存时合并结果，无额外区块加载或控制租约。
- 测量点到当前旋转后的各局部碰撞盒并集的最短距离，不用质心或外包 AABB 替代；空洞不被填满，结构内部距离为零。所有输入输出均为宿主世界坐标，Sable plot 大坐标通过姿态变换。
- 公共球查询半径 `0..256`，最多观察 256 个候选；支持当前有完整碰撞观察的结构。超预算、不兼容后端或附近存在无法完整观察的结构时显式失败，不把不完整集合冒充最近结果。没有视线遮挡要求；附近墙后的结构也可选到。
- 精密程序另检查选取点与玩家范围、半径范围、目标到玩家距离。现有三系查询上限分别为矢量操作 24、电击使 48、空力使 32 格；集合最多返回最近 128 项。范围配置来自原能力定义，不硬编码在节点中。
- 新查询不写入来源、不取得控制权、不改变姿态或速度。选中结构不保证能控制；实际动作继续检查能力技能、材料、消耗、控制权、地形与原动作范围。
- 集合最近项和距离筛选每次重新观察引用；已删除、卸载、运行 ID 不符、跨维度或超出查询范围的结构被跳过。遍历保存输入快照，具体动作仍会重验，不能复活失效引用。
- 未修改 Sable fork/JAR、物理 DLL、JBR/GC；既有 Rust 文件改动不属于本批。

## 验证与复跑

本批已完成验证；机器可核查的结果、源码/产物 SHA 与失败轨迹见 [验收 JSON](../validation/native_structure/structure_selection_validation_2026_10_26.json)。

```powershell
$env:JAVA_HOME='C:/Users/Dusk/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
./gradlew.bat test sableCompatTest editorTest -DisDev=true --console=plain
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name selection-native-NEW -Fresh
./gradlew.bat runClientDev -DisDev=true -I validation/native_structure/structure_selection.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-selection-native-NEW --console=plain
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name selection-suite-NEW -Fresh
./gradlew.bat runClientSable -DisDev=true -PacademySableRuntime=suite -I validation/native_structure/structure_selection.init.gradle -PacademyStructureOrientationRun=clientSable -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-selection-suite-NEW --console=plain
./gradlew.bat build -DisDev=true --console=plain
./gradlew.bat build -DisDev=false --console=plain
```

- JVM：`ProgramStructureSelectionNodesTest` 覆盖转发、空结果、失效过滤、等距顺序、类型隔离、非法半径、完整激活身份、三系真实 VM 集合变量与遍历；`LoadedStructureProximityTest` 覆盖长条旋转结构、空洞、零距离、远 plot 坐标和确定性排序。
- 实机：`structure_selection.init.gradle` 为原三系姿态夹具增加选取分支。真实类别运行时执行捕获、选取、旋转、回正、持续旋转、释放与集合遍历后的再次控制/释放；另测多个真实结构、数量限制前排序、删除后失效、无目标、伪造运行 ID、跨维度引用、范围拒绝与查询不改变占用。
- 渲染：`StructureSelectionEditorSmoke` 打开真实精密编辑器、核对翻译并保存 `screenshots/structure-selection-editor.png`；展示夹具不发存图网络包。
- 报告仍使用 `structure-program-orientation-result.json`，通过字段 `selection_nodes_and_foreach_control`、`multiple_structure_query_and_removal` 标识本批扩展。仅 Gradle 成功不足以确认夹具成功，必须检查报告、三系条目和正常退出。
- 失败轨迹保留：native-r1 夹具错误地把半径 33 作为所有能力的越界值，电击使允许该范围，触发测试断言崩溃；改为读取类别配置（实际矢量 24、电击 48、空力 32）。native-r2 的邻近对照结构在主结构提取期间自由坠落出查询范围，给对照体增加固定支撑后重跑。这两次不算验收通过。native-r3 三系 VM 全通过，随后最小化窗口打开编辑器触发 NaN 鼠标坐标取整异常；已修复编辑器正文与提示层的非有限坐标处理，加入单测，截图阶段恢复窗口后重跑。

## 最终验收结果

- 主测试 3,286、Sable 兼容测试 49、编辑器测试 104，失败/错误/跳过均为 0；最后完整命令见 `build/structure-selection-tests-final.log`。各任务可能有重叠，不相加声称独立用例数。
- 最终独立 `selection-native-r5`、四模组混装 `selection-suite-r1` 各 3 系真实 VM 全通过并正常退出；每系完成顺序 `[2,3,4,8,9,10,30,31]`，含超过 200 tick 的持续旋转与再次遍历控制。`native-r4` 也通过；r5 增加完整中文示例图截图。混装后的夹具改动只优化编辑器展示，生产源码保持相同。
- 中文截图 `run/native-sublevel-direct-smoke-selection-native-r5/screenshots/structure-selection-editor.png` 已目视核查，完整接线无错误诊断，底部显示“程序校验通过”。截图用合法示例图演示，未发送保存/执行网络包；它不是原存档真人测试。
- 开发构建 27 秒、发布构建 43 秒通过，后者有 1 项 FROM-CACHE，其余执行/UP-TO-DATE 如日志。两种产物均含新查询类，不含 gametest 或外部 Sable 类。
- 148 个隔离测试源存档文件 SHA 未变；既有 Rust SHA 为 `0B172501A5DA088A1CDA8AD13A41C88005D64B8D62D5347103DB566F84AC3C70`，未改写、未暂存。

| 产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `build\libs\academy-26.2.0-0.0.4-alpha-dev.jar` | 32325363 | `9DECC5F46F191F27DB184E744636E862C12B91C4DAF11541E0DCFB77A701DDE9` |
| `build\libs\academy-26.2.0-0.0.4-alpha-release.jar` | 24854262 | `1EA42BA127F84EA4E5BBCF5AF890665712EF20C9A06F1ACF4AA944A4A09C04E5` |

## 接续

1. 优先按最终矩阵确认本批节点在人工编排中的体验，包括空集合分支和权限失败分支；当前没有替用户执行原存档手工验收。
2. 可在后续单独增加结构材质/标签/来源筛选与多结构并行控制，不能将其混同本批已有“结构集合遍历”。
3. 发射账本具体复杂来源技能、S4、原生密集接触压力红测等沿原专项继续；本批不关闭这些工作。
