# 矿物探测重构验证记录

日期：2026-09-29。

## 行为变化

- 按玩家周围的三维距离优先扫描，半径 8 格内逐位置发布结果，随后补全 64／80 格范围。
- 使用矿物实心遮罩生成屏幕外轮廓；按照后续反馈，线宽约 3 个物理像素，不透明度 0.8，无脉冲和强光晕。
- 完整保留检测结果，无全局标记数量上限。分区 GPU 网格复用，扫描和上传分批执行，后台最多八个任务、两条工作线程。
- 接入方块变化、区块卸载与加载、资源与标签重载、技能关闭和退出世界的更新／释放。

## 自动化与构建

使用 JBR 25、仓库 Gradle wrapper：

```powershell
.\gradlew.bat test -DisDev=true
.\gradlew.bat build -DisDev=true
.\gradlew.bat build -DisDev=false
.\gradlew.bat runClientDev -DisDev=true -I tools/vfxgraph-editor/scripts/mine-detect-client.init.gradle
```

新增七项单元回归：附近三维顺序与及时发布、超过一万个结果、移动后缓存保留、替换与旧网格拒收、卸载区块不可读取、实心面及范围封口、远距离传送不溢出。

本次测试集共 2,157 项，失败和错误均为 0。开发版与发布版构建均通过，对应日志均记录 `BUILD SUCCESSFUL`。

## 游戏内验证

独立测试世界：`run/mine-detect-client/saves/railgun`，从既有可丢弃测试世界复制。脚本会修改该存档中的方块和玩家能力，只用于测试存档。

实际运行检查通过：

| 检查 | 结果 |
| --- | --- |
| 附近 2／4／8 格矿物 | 第 2 个启用状态客户端 tick 全部发现，包含上方矿物 |
| 实际轮廓 | 单块矿物无内部棱线，相接绿宝石矿脉无内部网格；墙后红石轮廓可见 |
| 数量 | 12,288 个分散矿物全部在缓存中，无数量截断 |
| 方块变化 | 挖除后移除结果，放置后加入结果 |
| 资源重载 | 重载后恢复扫描与轮廓，截图确认 |
| 反复开关 | 关闭释放缓存／渲染器，重新启用恢复附近结果 |

截图与原始数据位于 `build/mine-detect-validation/`：

- `disabled.png`：关闭技能。
- `silhouette.png`：加粗外轮廓、相连矿脉与墙后矿物。
- `mask.png`：实心遮罩，供检查内部棱线是否来自输入几何。
- `oblique.png`：斜视轮廓。
- `dense.png`：12,288 个分散矿物压力场景。
- `reloaded.png`：资源重载后。
- `result.txt`、`performance.txt`：断言结果与性能采样。

## 性能采样

环境：1280×720，NVIDIA GeForce RTX 5070 Ti，OpenGL，驱动 610.74。GPU 数据为整个渲染帧的时间戳采样，不是单独轮廓着色器的耗时。

| 指标 | 中位数 | P95 | 最大值 |
| --- | ---: | ---: | ---: |
| 扫描主线程 tick | 0.1212 ms | 1.3456 ms | 1.6213 ms |
| 关闭技能的 GPU 帧，72 个样本 | 0.367 ms | 1.744 ms | 未统计 |
| 开启技能的 GPU 帧，120 个样本 | 0.945 ms | 1.037 ms | 未统计 |

两种状态的 GPU 帧中位数相差约 0.58 ms。P95 的差异受场景与采样波动影响，不能解读为开启技能使整帧更快。扫描计时不包含 GPU 绘制；该数据不能代替其他硬件、分辨率或模组组合的实测。

## 验证边界

- 非完整矿物使用方块提供的 `VoxelShape`；第三方透明贴图、特殊模型和自定义渲染器未逐个验证。
- 尚未单独验证 Iris 光影包、4K、实际跨维度传送与窗口连续缩放；相应资源与缓存生命周期已接入，不能把代码路径存在等同于这些组合已经实测通过。
- 资源重载日志中出现已有 `CommandExecutor` 的 `Sampler0 has been closed, skipping draw call` 提示；矿物通道使用独立 `OreMask`／`HorizontalMask`，重载后的功能断言和截图通过。该通用渲染提示未在本次修改范围内修复。
- 未修改生成资源，未创建 Git 提交。原有崩溃与能力存档修复保持独立。

## 客户端半径设置验证（2026-09-29）

新增 3 项配置测试：旧 JSON 的默认行为、选择值的序列化与熟练度限制、加载／修改越界整数时的夹取。

- `test -DisDev=true`：2160 项测试，0 失败、0 错误。
- `build -DisDev=true` 与 `build -DisDev=false`：均通过。
- `runClientDev -DisDev=true -I tools/vfxgraph-editor/scripts/mine-detect-settings.init.gradle`：进入测试世界，实际打开数据终端技能设置并从终端根控件分发鼠标／键盘事件，通过全部断言后退出。
- 拖动 80 → 8 → 24，扫描范围实时变化；拖动中磁盘值仍为 80，松开后为 24；右方向键调整为 25 并保存；重新打开界面及重新从磁盘加载配置均为 25。
- 客户端设为 80 时，基础熟练度实际范围为 64；服务端授予 2000 熟练度并同步后，实际范围为 80。
- 已目视检查中文、英文界面截图：新增标题、滑块与数值完整显示；8 格世界截图中近处矿物保留轮廓，远处矿物与墙后远处红石不再显示轮廓。

截图位于 `build/mine-detect-settings-validation/`：`settings-zh-selected.png`、`settings-en-selected.png`、`settings-zh-reopened.png`、`settings-zh-minimum.png`、`settings-en-maximum.png`、`world-radius-8.png`。完整断言结果为 `settings-result.txt`。

首次实机测试脚本在世界加载界面尚未关闭时尝试打开终端，触发脚本断言；增加等待世界界面关闭后重跑通过。资源重载仍有上文记录的通用 `Sampler0` 提示，本次新增控件和范围功能通过实测。未修改生成资源。
