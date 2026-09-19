# 开发顺序 4–6：技能 VFXGraph 更新

本次覆盖等离子体、矢量冲击、未元干涉／修复／解构、空力使雾气及磁场悬浮支撑电弧。未改动技能树 UI、未元修复结算数值或心智破坏。

## 行为与图资产

| 项目 | 更新 |
| --- | --- |
| 等离子体 | 总蓄力仍为 240 tick；第 6 tick 起出现汇聚，60 tick 完成。之后继续增长至 240 tick。`PlasmaChargeVisuals` 共用形成曲线；发射快照携带 `launchScale`，刚汇聚时为 0.62。核心、外晕、表面电弧采用与汇聚图完全相同的尺寸插值和材质，迟到观察者同样获得发射时尺寸。 |
| 矢量冲击 | 删除 Alt/Ctrl 拉推绑定与服务端状态机，初始化时清理旧配置键。Shift+左键远程冲击使用 `vector_blast`，局部 +Y 沿真实视线旋转。视觉已按新要求重做为白灰色云雾漏斗：`cloud_vortex` 生成翻卷云壁及外缘散雾，中央保留贯通风眼；第一人称云层不透明度为原值的 22%，第三人称保持原值，F5 切换实时生效。透明云材质表现明暗层次，不再生成螺旋线或音爆环，保留原爆炸音。伤害（含熟练度终点爆点及跨越深渊分支）使用既有 `HostileTargets.isDetectable`；光束内生物沿射线强击退并略向上，近端速度 3.2、末端 1.44，上抛 0.35，受已有运动防护约束。 |
| 未元干涉 | `darkmatter_interference` 将原版女巫／传送门粒子替换成半透明白色光束；命中反馈使用白色解构云。伤害范围与脉冲周期保持原实现。 |
| 未元修复 | `darkmatter_repair` 为贴身白色有机丝束。只有实际结算成功才触发，每个实体每 5 tick 至多一次，单次 0.3 秒，跟随实体插值位置；不改变 MP 规则。 |
| 未元解构 | `darkmatter_disassemble` 使用不规则白色丝束和扩张粒子云，替换 CLOUD 粒子；实体目标跟随目标，成功方块解构在结算点播放。丝束泛光，云层使用正确的透明混合以保留层次。 |
| 空力使 | 六种 `aeromanip_mist_*` 的频率 ×1.75、噪声／湍流幅度 ×1.5、初速度 ×1.45、旋涡强度／吸力 ×1.4、阻力 ×0.85。技能时长、粒子 lifetime、透明度与尺寸曲线均保持不变。 |
| 磁场悬浮 | 服务端每 2 tick 同步实际解算的 `closestPoint/face`，以地面参照优先、侧壁或天花板支撑回退。`magnetic_levitation_support` 从腿部两锚点接向表面；每帧更新角色挂点，使用蓝白双层电弧。关闭／失去支撑发送停止状态，丢失更新 6 tick、移除实体或切换世界时清理。 |

`DarkmatterGraphEffects` 是公开的纯视觉服务，可供其他实体和程序调用。`organic_strands` 是通用图节点，提供光线、缠绕及云状丝束三种形态；单实例最多 48 条曲线，每条最多 129 点，不依赖具体技能。瞬态音爆环改为每帧替换几何，避免编辑器定位同一时刻时重复累积。

`cloud_vortex` 是独立的通用云雾节点，沿局部 +Y 生成中空漏斗。图使用 1,024 个带噪声的透明云团面片（节点上限 1,536），由角速度、轴向流动、不同尺度的噪声及剪切形成翻卷感。`eye_ratio=0.38` 保留轴向风眼；计算净空时同时包含云团面片包围半径和偏摆，防止粒子中心虽在外侧、面片仍覆盖轴线。时间与随机种子决定位置，可重复定位与倒放；节点只维护自己生成的粒子，不影响其他节点。视觉头部宽于伤害射线，用于表现外围气流，不扩大命中范围。单次仍为 0.65 秒。已移除之前为矢量冲击添加的 `vortex_jet` 轴向丝线分支。

通用生成包的客户端提供每帧更新的 `view_first_person` 参数；仅消费该参数的图会受影响。`cloud_vortex.first_person_opacity` 缺省为 1，矢量冲击图设为 0.22；第三人称的 `opacity=0.5` 不变。相机切换只影响客户端透明度，不改网络协议、粒子轨迹或技能判定。

矢量冲击参考：[Blender 几何节点制作龙卷风](https://www.bilibili.com/video/BV1Z87kzCEpv/)。已在内置浏览器查看开场白色云雾龙卷风展示；随后按用户建议尝试电脑 Edge，但电脑控制工具因无法可靠识别当前页面 URL 而中止，因此未继续查看完整教程。实现参照已看到的密集云团、漏斗及外缘散雾，不声称逐节点复刻教程。

网络变化：`SkillVfxPacket` 的已发射等离子体增加一个 float；另增 `MagneticSupportPacket`。客户端和服务端需要使用本次同一版本。

未元物质参考限制：上一轮提供的未元物质 B 站页面在工具访问中不可读取；未逐帧复刻视频。未元物质按照方案明确的白色、透明、泛光、有机形态制作，效果以本目录的真实渲染截图为准。

## 复现

使用 JetBrains Runtime 25，在仓库根目录执行：

```powershell
$env:JAVA_HOME = 'C:/Users/Dusk/AppData/Local/Programs/IntelliJ IDEA/jbr'
node tools/vfxgraph-editor/scripts/author-ability-vfx.mjs
node tools/vfxgraph-editor/scripts/ability-vfx-baseline.mjs <修改前的提交>
.\gradlew.bat test editorTest -DisDev=true
.\gradlew.bat runGraphEditor -DisDev=true -I tools/vfxgraph-editor/scripts/ability-vfx-capture.init.gradle
.\gradlew.bat runClientDev -DisDev=true -I tools/vfxgraph-editor/scripts/ability-vfx-client.init.gradle
.\gradlew.bat runGameTestServer -DisDev=true '-PacademyGameTests=academy:skill_vfx*'
.\gradlew.bat build -DisDev=true
.\gradlew.bat build -DisDev=false
```

作者脚本使用项目 VFXGraph repository API 写入并校验资产。运动预设使用绝对值，可重复运行而不会叠加倍率。基线脚本只读取指定 Git 提交，在 `build/ability-vfx-baseline` 输出六个空力使原图，并核对 lifetime 与曲线完全一致。尚未提交本次修改时可省略参数，默认 `HEAD`。

客户端探针使用 `run/ability-vfx/saves/railgun` 隔离存档；本次从现有 `run/electromaster-arcs/saves/railgun` 复制。不要将探针指向正式游玩存档。探针及截图入口只属于 editor 源集，不随模组发布。`run/`、`build/` 不纳入提交。

## 验证范围与截图

2026-09-19 第一轮：2,136 项单元测试与 103 项编辑器测试全部通过；4 项 VFX GameTest 全部通过；VFXGraph repository 的 5 项 Node 测试通过；开发版与发布版构建通过。两种模组 JAR 均包含 5 个新图和磁场支撑包，均不包含 editor 捕获／客户端探针。图资产校验为零错误、零警告。客户端探针输出 `PASSED`。

同日矢量冲击云雾重做：2,137 项单元测试与 103 项编辑器测试通过；图资产校验零错误、零警告；开发版和发布版构建通过。客户端探针再次输出 `PASSED`，实际施放检查 1,024 个云团、零条曲线、敌对伤害与全目标击退，以及到期清理；增加侧面云雾截图。新增单元验证覆盖云团填满各轴向分段及内核、头部扩宽、长度与宽度独立、旋转／流动、重复定位与倒放、外部节点删粒子后的预算恢复、到期仅清理自己生成的粒子。测试存档仍有既有字体缺字及 Jade 游戏规则读取日志，本轮未修改这些无关内容。

风眼与视角透明度追加调整：2,138 项单元测试、103 项编辑器测试及开发／发布双构建通过。空洞测试核对所有云团的完整面片均在轴向风眼之外；透明度测试核对第一人称乘数、轨迹／大小不变及切回第三人称原值恢复。游戏探针对同一存活特效执行第一→第三→第一视角切换，核对云粒子最大不透明度分别不超过 0.111、大于 0.45、再次不超过 0.111，最终 `PASSED`。编辑器与游戏内截图均确认风眼可见。

单元测试覆盖 60 tick 汇聚、形成曲线单调增长、发射核心／外晕同尺寸、发射尺寸编码与非法值拒绝、光束长度与宽度独立、重复采样不累积、白色丝束预算及到期消失、支撑点／法线／停止包编码。VFX GameTest 覆盖生命周期以及 2／8／16 名观察者的退订、重新进入视野和提前发射尺寸保留。

客户端探针实际走等离子体开始／释放网络入口、矢量冲击服务端执行入口和磁场悬浮运动解算，检查友好实体不受伤但被击退、敌对实体受伤且被击退、支撑状态同步、移动后更新、第一／第三人称与关闭清理。未元三图及空力使通过各自视觉服务／通用生成包验证渲染和寿命；这些画面不代表探针执行了完整未元战斗或修复结算。既有修复结算测试随全量测试回归。

- `preview_plasma_cannon_{focus,projectile}_{3s,12s}.png`：相同相机尺度下的汇聚／发射体积对照。
- `preview_vector_blast.png`、`preview_vector_blast_{rolling,dissipating,close}.png`：云雾漏斗在 0.15／0.3／0.5 秒及局部放大的真实 GPU 渲染。
- `preview_vector_blast_eye_{first_person,third_person}.png`：同一相机、同一时刻的风眼及透明度对照。
- `preview_darkmatter_*.png`：未元物质编辑器真实 GPU 几何与混合材质。
- `preview_aeromanip_mist_*_before.png` 与 `*_late.png`：同随机种子、0.45 秒时刻的修改前后运动对照；`*_early.png` 为 0.15 秒。
- `preview_magnetic_levitation_support.png`：图编辑器的路径输入示例；实际服务端地形端点以客户端截图为准。
- `client_plasma_*.png`、`client_vector_blast.png`：真实技能执行。
- `client_vector_blast_eye.png`、`client_vector_blast_third_person.png`：同一特效在第一／第三人称之间切换，探针核对透明度恢复。
- `client_vector_blast_side.png`：同一生成包从侧面观察云雾体积；友好／敌对判定由第一人称真实施放检查。
- `client_darkmatter_*.png`、`client_aeromanip.png`：游戏中的图效果。
- `client_magnetic_support*.png`、`client_cleared.png`：悬浮、移动、第一人称及关闭清理。

运行记录：`build/ability-vfx-tests.log`、`build/ability-vfx-dev-build.log`、`build/ability-vfx-release-build.log`、`build/ability-vfx-capture.log`、`build/ability-vfx-client.log`、`build/ability-vfx-gametest.log`。

云雾重做记录：`build/vector-cloud-tests-capture.log`、`build/vector-cloud-client.log`、`build/vector-cloud-dev-build.log`、`build/vector-cloud-release-build.log`。

风眼与视角透明度记录：`build/vector-eye-tests-capture.log`、`build/vector-eye-client.log`、`build/vector-eye-dev-build.log`、`build/vector-eye-release-build.log`。

未元物质后续改为表面薄片与解构烟雾；本目录中的旧未元截图仅保留历史对照，当前效果见 [未元物质重做验收记录](../darkmatter_redesign/README.md)。
