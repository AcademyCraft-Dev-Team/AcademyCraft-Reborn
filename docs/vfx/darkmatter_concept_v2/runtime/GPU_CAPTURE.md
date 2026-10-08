# 羽毛材质实际 GPU 验证

2026 年 10 月 7 日，本轮 16 张未元物质图在项目独立编辑器渲染器中完成 109 张 1280×720 捕获。使用实际粒子缓冲、曲面顶点格式、GLSL 编译与 Screenshot 读回，GPU 为 NVIDIA RTX 5070 Ti。全部输出、数量、网格档位和实际提交计数见 `gpu_capture_manifest.json`，像素检查见 `gpu_pixel_qa.json`，完整联系图为 `gpu_contact_sheet_1.png` 至 `gpu_contact_sheet_6.png`。

性能更新前后的 104 张同镜头捕获逐像素完全一致，结果见 [gpu_performance_regression.json](gpu_performance_regression.json)。新增 5 张检查中远距网格与混合实例段，均经过像素 QA 和目视检查；这些捕获验证真实图和 GPU 提交，游戏内方向场性能另见 [performance/README.md](performance/README.md)。

新共享材质 `darkmatter_feather.glsl` 提供不对称羽片、偏置珍珠灰羽轴、斜向羽枝束、乳白羽尖与薄透边缘。细羽枝使用 fwidth 抗锯齿并在亚像素时衰减。六翼曲面、羽刃、物质扇刃和基础贴片使用同族材质；创造、塑形、抹消的专用实体面片也调用该接口。扇刃保持连续前缘，尾缘改为八个长短不同的实体羽瓣。

## 查看重点

- `before_material_six_wings_gamma.png` 与 `gpu_darkmatter_six_wings_gamma.png`：同镜头的新旧材质对照。
- `before_material_feather_alpha.png` 与 `gpu_darkmatter_feather_alpha.png`：同镜头羽刃对照；原镜头裁到羽刃两端，因此另拍 `gpu_darkmatter_feather_alpha_fit.png` 供完整轮廓检查。
- `gpu_darkmatter_six_wings_gamma_single_feather.png`：仅隔离该图既有第 35 号长羽，骨架、粒子、材质保持真实模拟；用于检查羽轴与羽枝，摄影参数记入 manifest。
- `gpu_darkmatter_six_wings_gamma.png` 与 `gpu_darkmatter_six_wings_gamma_flight.png`：接地姿态与飞行下压姿态对照。
- `before_angle_six_wings_gamma.png` 与 `gpu_darkmatter_six_wings_gamma.png`：同镜头的三对翼夹角调整。站立与飞行均按完整羽尖方向协调间隔，站立的完整曲面继续保持在脚面上方。
- `gpu_darkmatter_six_wings_gamma_blocked.png`、`*_partial_penetration.png`、`*_penetrating.png`：同一实体方块代理，穿透参数分别为 0、0.35、1。右中翼与右下翼采用局部 x≤2.2 的允许半空间，其余羽片保持原材质；4 厘米接触渐变收束边界。中间值保留穿透段的相应透明度，1 则完全跳过阻隔采样。
- `gpu_darkmatter_cut_gamma_peak.png`：0.08 秒峰值的白色前缘与完整羽瓣；0.18 秒默认画面已处于消散阶段。
- `gpu_darkmatter_six_wings_gamma_lod_medium.png`、`*_lod_far.png`：45 米和 85 米的网格档位；相同材质、132 个模拟槽，实际提交分别为 114／96 羽片、16,416／6,144 个三角形、10,032／8,448 字节。隐藏覆羽不提交，近距羽片与材质保持一致。
- 创造、塑形、抹消的 `*_time_12.png`、`*_time_24.png`、`*_time_42.png`：三个真实时间点。创造/抹消 progress = time / 0.7，塑形 progress = time / 0.65，避免只改时钟而冻结形制进度。

## 重现

使用 JBR 25，从仓库根目录运行：

```powershell
.\gradlew.bat runGraphEditor -DisDev=true -I docs/vfx/darkmatter_concept_v2/runtime/capture.init.gradle
```

该入口与本轮 init 独立保存在这里，不覆盖旧验收程序或旧 `darkmatter_redesign` 文件。完成后运行 `inspect_captures.py` 重建像素记录与联系图。

本轮实际 GPU upload 同时覆盖了修复后的缓冲生存期：quad/cube 顶点在 MeshData 关闭前复制到独立缓冲，material grid 直接生成自有缓冲，避免 ByteBufferBuilder 释放后返回悬空 native view。旧粒子实例保持 56 字节格式；新曲面单独使用 88 字节格式，追加物理法线、独立裁剪平面和穿透透明度，不把裁剪数据编码到法线中。六翼保持 132 个模拟槽，不逐帧累计粒子；公开的网格策略根据投影尺寸带迟滞选择 16×8／12×6／8×4 网格，普通正交预览默认完整档位。

档位保存在 CPU 粒子缓冲中，不修改两种 GPU 实例布局。渲染器按连续实例段提交不同网格，保留透明输出的原顺序；混合三段捕获实测 132 实例、20,416 三角形，验证上传偏移。零透明度剔除由输出节点的 `cull_zero_alpha` 显式开启，默认关闭；本轮 16 图的材质已确认遵守该契约，历史图与不依赖实例 alpha 的自定义 shader 保持兼容。

阻隔绑定是中立 `SurfaceOcclusionSampler`：每组三对左右翼共六次形状采样，采样器可返回缓存平面。真实世界实现按世界 tick 共享最多 48 条碰撞射线，正常每组 4～5 tick 刷新三条射线，并在远距降低刷新、离屏冻结；编辑器中的方块代理只证明曲面 GPU 裁剪差分，实际方块碰撞与多人预算仍以游戏内验证为准。

相关测试覆盖完整曲面接地高度、站立与飞行的完整羽尖间隔、旧公开采样器兼容、独立法线与裁剪字段扩容/交换/复用、摄像机相对坐标与负缩放平面变换、中立绑定的热重载、共享射线预算、网格档位迟滞和透明输出契约及 Iris 公共渲染兼容。共享预算也检查低帧率跨过多个世界 tick 时 108 个翼组持续获得公平服务。16 图校验全部零错误、零警告，最终构建结果汇总于 [VALIDATION.md](../VALIDATION.md)。

本轮启动成功退出，未发现 shader 编译或链接错误。日志中的 unused Sampler0 提示来自统一管线保留噪声采样绑定，而新程序羽毛材质不使用旧噪声；这是接口诊断，不是编译失败。原始日志仅保存于 `build/`，不纳入交付资源。

像素 QA 中的四个低亮画面分别是第一人称羽翼隐藏、切割消散、翼击末尾与夜间干涉介质。干涉的环境主场需要真实游戏世界、场景深度与遮挡，独立编辑器只验证介质贴片。灰色人形是中性表面与尺度 fixture；独角仙造型、实际玩家锚点、AI 动作和玩法必须以 runClientDev 验证为准。
