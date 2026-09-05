# 黑翼：弯曲墨流风暴

![三视图](three_view.png)

## 设计依据

按用户提供的漫画、动画截图，黑翼从两侧肩胛喷出，主体是密集墨黑气流汇聚成的两条狭长龙卷。
根据两轮反馈，去掉动物翅膀的扇面、羽片轮廓，也去掉过直的圆锥主轴。新轮廓有连续弯折、
旋涡鼓包和收窄段，左右以不同相位变化。形状经用户确认后保留，增加沿细流外移的亮紫短带，
并在密集墨流中加入局部镂空，空隙仍有少量外层细流贯穿。

网络参考（2026-09-05 检索；作品社区资料，不作为官方设定文献）：

- [Accelerator / Abilities](https://toarumajutsunoindex.fandom.com/wiki/Accelerator/Abilities)：黑翼的背部喷流表现及不同登场阶段。
- [Awakening](https://toarumajutsunoindex.fandom.com/wiki/Awakening)：巨大黑翼及后续白翼变化。

最终以用户截图和反馈为外观依据。约 11 米展幅、3 米抬升和 2.5 米后掠是模组的视觉设计尺度，
并非对原作尺寸的断言。生成图为美术方向参考，各视图尺寸标注不用于精密测量。

## 编辑与实现

用项目 VFXGraph Editor MCP 创建、编辑并验证 `academy:vfxgraph/black_wings`。
运行 `./gradlew runGraphEditor -DisDev=true`，打开 `black_wings` 即可继续编辑。

- `left_vortex`、`right_vortex`：两个独立的 `vfx.block.vortex_jet`。
- `length`、`rise`、`back`、`radius`：长度、抬升、后掠和漏斗粗细。
- `turns`、`speed`、`phase`：螺旋圈数、流速和两侧扰动相位。
- `filaments`、`segments`、`flecks`：每侧细流、曲线采样和游离墨屑预算。
- `color`：墨色基底；材质 `vfxgraph_ink_storm.fsh` 控制少量深紫夹缝。
- `highlight_count`、`highlight_speed`、`highlight_color`：每侧亮带数量、外移速度和亮紫颜色。
- `hollow`：局部镂空强度（0 关闭，1 最大）；实际切断空隙处的三角面连接，保留部分细流桥接。
- 图参数 `sweep_left/right`、`pitch_left/right` 驱动独立横扫；
  `radial_scale`、`length_scale`、`spread_scale`、`opacity` 支持过渡收束。

每侧一条带局部开口的核心、18 条螺旋细流和 24 条短墨屑；左侧 14 条、右侧 10 条移动高光，共 110 条曲线。
高光沿现有螺旋细流外表面移动，宽度为墨流管半径的 20%，并限制到 0.012 米，避免末端粗大紫条。
相比最初每侧 8 条，先调整到每侧 10 条、每条覆盖长度增加 4%，合计覆盖密度约提高 30%。右侧效果经用户确认后保留，左侧再补至 14 条以改善稀疏感。
两处镂空宽度缩至原来的约 45%，中心沿轴向缓慢往返移动，各细流边缘错开，且每三条保留一条连续桥接。
高光端部渐细并淡入淡出，主体仍保持墨黑。
几何按发射块所属组替换，不积累上一帧副本。细流沿主轴旋转外喷，主体曲线慢速弯折。
不使用羽毛模型、静态贴片翅膀或旧黑翼圆环。

公开采样器 `org.academy.api.client.render.vfxgraph.shape.VortexJetGeometry` 不依赖玩家或技能，
可以供其他发射器、实体和后续能力系统复用。局部坐标 +Y 向上、-Z 向后。

`WingVfx` 把新图接到角色模型根矩阵的肩胛位置。第一人称使用世界空间肩胛位置，
攻击风暴会进入视野；第三人称沿用已捕获的角色飞行姿态。黑转白沿用既有过渡时间线。
白翼、白金翼、风暴之翼的渲染实现保持原有路径。

## 验证记录

- `./gradlew test -DisDev=true`：通过，含持续播放、左右横扫隔离、收束和恢复，以及高光位移、宽度上限、镂空大小、位置波动和桥接细流测试。
- `./gradlew build -DisDev=true`：通过，包含编辑器测试。
- `./gradlew build -DisDev=false`：通过。
- MCP 图结构校验：通过，0 错误、0 警告。
- 细线高光和动态小镂空已在重启后的编辑器加载，106 条曲线，无 previewError；用户截图确认右侧效果合适，本次仅补密左侧并热重载，已确认当前预览为 110 条曲线。桌面截图工具因 apply deny-read ACLs 初始化失败，本轮视觉依据为用户提供的编辑器截图。
- `./gradlew runClientDev -DisDev=true`：客户端启动后、进入世界前发生原生 GLFW 崩溃。
  `run/hs_err_pid54348.log` 记录 `EXCEPTION_ACCESS_VIOLATION`，栈位于
  `glfw.dll` / `RenderSystem.pollEvents`。因此游戏内肩胛挂接、飞行、第一人称横扫及黑转白
  仍需实机复核；编辑器预览不能替代这些验证。

## 绘图记录

先按 api2img 技能调用配置服务，但其返回 `403 INSUFFICIENT_BALANCE`，未生成图片。
三视图改由内置 image_gen 生成，并按用户两次反馈迭代，最终文件为 `three_view.png`。
最终提示词要点：同一对圆截面墨黑长龙卷的正面、右侧、顶部视图；两处肩胛细喷流；
明显 S 形弯折和不规则粗细变化；黑色为主、少量深紫旋流夹缝；无羽毛、翼膜、扇形翼面、
笔直圆锥、规则圆环或大面积发光；小型 MC 人形用于比例参照。
