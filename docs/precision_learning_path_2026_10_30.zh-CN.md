# 精密操作批次 B：查找与可编辑模板

基线：`dbc71964`（批次 A）。本批落实[总方案](precision_operation_redesign_2026_10_30.zh-CN.md)的学习路径，保留节点运行语义、旧 ID、强类型端口、能力差异与普通图格式。菜单分组不是运行节点合并，也不代表已完成性能缓存。

## 如何使用

- 默认打开“常用”：按“选取目标”“移动物理结构”等用途展开，再选择真实节点。群组结构动作排在单体动作前；单体动作仍须遵守已有控制权和必接端口要求。
- “进阶”保留完整已开放节点与分类入口。常用层搜索也能找到进阶节点，包括扩展模组的预设；按原 ID 搜索仍可用。多个同 ID、不同配置的预设分别保留。
- “兼容”显式列出旧隐藏节点与三个未正式开放的还原节点。原图不迁移、不改写；普通连线候选不推荐未开放节点。兼容标记不是运行禁用开关，已有还原节点仍可能在独立后端实际归块，务必阅读限制。
- 目录单节点悬浮提示显示从实际预设读取的“初始配置”；已有图节点显示当前配置。未接输入的行为仍见真实节点说明，不为必接端口虚构默认值。
- 在空槽点击“模板”，选择本能力的已满足学习门槛的任务示例。生成的是普通节点和连线，可逐项修改、一次撤销或重做；需要自行保存和施放。非空槽不能覆盖。原“施放输出”“聊天触发”基础示例保留。

## 模板与能力边界

| 能力 | 任务 | 节点数 | 初始行为与限制 |
| --- | --- | --- | --- |
| 矢量操作 | 牵引 / 旋转准星结构 | 各 2 | 牵引沿视线 3 格、持续 1 秒；旋转为世界 Y 轴 90° 的绝对姿态，完成后释放控制 |
| 电击使 | 牵引 / 旋转准星结构 | 各 2 | 同上，且要求有效磁性参与 |
| 空力使 | 牵引 / 旋转准星结构 | 各 2 | 同上，且要求目标结构上挂有本人有效喷口，无须预先启动 |
| 传送使 | 沿视线前移 | 7 | 自身脚下位置 + 视线方向 × 3 格；显式连接落点，同维传送仍检查安全和技能条件 |
| 原子崩坏 | 沿视线采掘 | 2 | 使用已有采掘光束默认视线，保留破坏权限和技能消耗 |
| 未元物质 | 分解准星方块 | 3 | 显式选择方块并接入分解，不隐式改为范围目标 |
| 心理掌握 | 使准星生物呆然自失 | 4 | 实体选择 → 单成员集合 → 呆然自失；保留实际可影响目标、保护、范围和费用检查 |

十个任务均使用原节点配置。前三系的六个模板复用此前已有的两节点最简操作，不新增自动持有或结束语义。模板菜单检查原节点 scope；心理模板额外要求真实 `mental_stupor` 学习能力，补足旧桥接节点仅按分类开放的目录门槛。目录里的旧心理行动仍可能可编排但未学，实际施放继续由原执行器检查。

## 实现与兼容

- `ProgramPaletteCatalog` 只返回客户端卡片，保留真实 Entry 引用、预设和 schema；常用根菜单最多 12 张用途卡。权限和单入口约束在根菜单、搜索、展开成员中保持一致。
- `ProgramTaskTemplates` 在独立 `ProgramEditorDocument` 中构造普通图并编译验证；拒绝非空（含悬空边）、错误分类、未学技能。原槽的 UUID、名称、分类和版本保持。不会保存、发包或执行行动。
- 屏幕统一使用同一张卡片列表渲染、点击和提示；层切换、搜索及家族返回只改浏览状态。模板通过一次 `setProgram(program, true)` 安装，支持一步撤销。窄窗口显式打开模板会收起侧栏。
- 新增 59 组双语键；没有重写冻结节点清单。未开放节点只从兼容层显式添加，连线快捷候选过滤未开放状态；已有节点的编辑、编译和运行能力不变。
- 配置切换仍沿原 `configureNode` 检查已有连线；不兼容变更会拒绝，不通过删边制造成功。

## 验证与接续

最终主测试 3,385 / Sable 49 / 编辑器 104 全通过，开发和发布构建通过。中英文隔离客户端各 11 个实际 screen 场景通过，另各校验 10 个模板、59 组新增双语键。检查包含三层/家族浏览原图不变、跨进阶搜索、兼容节点可达、真实默认值、一键模板的单步撤销/重做/编辑/非空保护，以及真实窗口 520×340 GUI 的抽屉与弹窗。长搜索越界在初轮截图中发现并修复，最终 r2 截图已确认裁剪到输入区域；完整查询保留且可悬浮查看。

真实目录和冻结基线保持 342 ID / 353 个有限单字段模式契约；原始基线未改。目录导出仅比 A 增加语言资源总键数，未改节点定义。完整命令结果、文件/源码/产物哈希及截图路径见[机器报告](../validation/precision_nodes/precision_batch_b_validation_2026_10_30.json)。开发 JAR SHA `5F9F6E3DD07B5B15F92BC4DDF623A191C7BEDDDE7A75BBEC0C9428A89895680B`，发布 JAR SHA `D799A0F9CA0C070127B4D4901A48C22A00E9820D00AA78B2395EB91A29BF20BA`。

复跑使用 JBR 25，临时 Gradle JVM 参数 `-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=512m -XX:TieredStopAtLevel=1` 与 `-Pkotlin.compiler.execution.strategy=in-process`，没有改产品 JVM：

```powershell
./gradlew.bat test sableCompatTest build -DisDev=true
./gradlew.bat build -DisDev=false
./validation/native_structure/prepare_default_skill_smoke.ps1 -Name precision-b-new-check -Fresh
# 只调整新隔离副本 options.txt 的 lang=zh_cn/en_us、guiScale=2、pauseOnLostFocus=false。
./gradlew.bat runClientDev -DisDev=true -I validation/precision_nodes/precision_learning_path.init.gradle -PacademySubLevelSmokeDirectory=run/native-sublevel-direct-smoke-precision-b-new-check
python validation/precision_nodes/verify_catalog_contract.py run/native-sublevel-direct-smoke-precision-b-new-check/precision-node-catalog-audit.json
```

两份最终目录为 `run/native-sublevel-direct-smoke-precision-b-{zh,en}-r2-20261030`。真实交互是反射调用生产 screen 点击/控件回调，内存 session 提供合成能力；没有程序保存包或技能执行。模型编译不等于真实技能施放验收。用户学习耗时、首次成功率尚待人工试用，不能以卡片数量代替。

英文最终首轮资源重载仍报 `Missing uniform Globals`，首图显示 `Resource reload failed`，重载后完成 11 项；中文最终轮未出现。记录其未解决，不把本批写成全资源无告警。另发现牵引的 `seconds` 与配置推断 `duration_seconds` 同译名，后续应显示覆盖优先级，不能直接删端口或改旧图。

下一批先按总方案 C 记录真实编译、查询、UI 重建与大图开销，再决定有界缓存；保持随机、实时查询、共享变量和物理频率语义。此前长时间字形/旧节点漏绘、首轮 `Missing uniform Globals`、静态化后端差异、跨维和 64 体压力等专项未由本批自动关闭。
