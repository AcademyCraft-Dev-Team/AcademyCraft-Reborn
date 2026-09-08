# 技能特效多人验收记录

日期：2026-09-08。基础优化提交：`3f480afe`（优化技能特效同步与渲染并修复电浆蓄力定位）。本记录同时覆盖之后的烟雾、未元切割迁移；具体提交状态以 Git 为准。

## 已完成范围

- 动能附加冲击波采用独立瞬时事件；粒机波形高速炮、电浆炮使用低频完整快照、阶段变化和独立终止/命中事件。
- 修复电浆未发射时焦点落到世界原点的问题；实际施法者位置的相机能看到蓄力球。
- Cloudroom、JetStrike 的烟雾改为一次事件，携带位置、尺寸、寿命、随机外观参数；不再创建 Smoke 实体。透明度曲线保持原规则，归零即回收，不保留剩余不可见实体寿命。尺寸现在由服务端传递。
- DarkmatterCut 的 4 tick 切割改为一次事件，不再创建 DarkmatterCutSlash 实体。客户端局部时间播放，先判断包围球，再按四个纹理帧批量绘制；复用静态四顶点网格、矩阵和按容量增长的实例缓冲，双层效果每实例只上传变换和透明度。
- 两类新事件沿用效果 ID/版本去重和维度检查；旧实体注册保留，供旧存档或其他调用者使用，迁移后的技能调用点不会同时走两条显示路径。
- 自动测试场景放在 editor 源集，普通启动默认不启用，发布 JAR 中不含 SkillVfxLiveValidation 类。测试世界、CSV、截图和日志保留在 run/、build/，不提交。

## 测试层次与边界

1. 单元测试：1,790 项，0 失败、0 错误；包含协议边界、一次性事件体积、烟雾淡入/淡出/提前终止、状态恢复、去重、视锥几何、离屏模拟节流。
2. 独立 GameTest 服务器：真实 ServerPlayer、技能逻辑实体、SkillVfxRuntime 和 Misaka 编码；EmbeddedChannel 模拟 2、8、16 名观察者，读取并解码实际出站负载。全部 3 项通过。
3. 真实双客户端：两个图形 Minecraft 进程通过 TCP 连接同一独立服务器，采集游戏渲染目标截图、RenderFrame 事件 CPU 时间及 TimerQuery GPU 时间。普通网络 live02 和带延迟限速 shaped01 均完成。
4. 额外拥塞场景 shaped08 的结果见末尾补充。

图形场景直接创建逻辑实体或调用公开反馈 API，使用施法者位置和旁观者位置的相机；并非人工按键操作全部技能流程。GameTest 另有真实开火、同 tick 移除、订阅恢复等断言。截图能确认所采样画面，不构成逐事件零漏显率统计。

两客户端同机共享 RTX 5070 Ti，1280×720、视距 6、模拟距离 5、关闭垂直同步、120 FPS 上限。平台棋盘纹理便于检查冲击波畸变。初始原生桌面控制工具不可用，后续通过 opt-in 游戏内测试场景完成真实绘制与截图，并未用模拟连接替代视觉验证。

## 双客户端结果

普通网络 live02：

- 两端均可见持续光束、电浆聚集/待发射球体/飞行、冲击波、烟雾和切割。电浆蓄力球在使用者相机前方，未再出现只有外围龙卷风的截图。
- 两端分别接收 1,589 个特效包；服务端总计 3,178 个，活动效果/待发更新/丢弃更新均为 0。客户端结束时活动 VFX 图均为 0。
- 烟雾负载每 2 tick 生成 8 个效果，连续 12 秒；切割每 2 tick 生成 4 个效果，连续 12 秒。各后半段转身观察离屏开销，最后独立等待回收。
- 截图保存于 `build/vfx-live-validation/live02/screenshots/`；例如 `caster-plasma_charge-4.png`、`observer-beam-1.png`、`observer-shockwave-2.png`、`observer-smoke-2.png`、`caster-slash-2.png`。

shaped01：每连接下行 64 KiB/s、上行 256 KiB/s；双向各 100±30 ms 延迟抖动，由仅监听 127.0.0.1 的 TCP 代理实施。代理保留字节顺序、采用有界队列和背压，不伪造应用层丢包或乱序。

- 两端仍各接收 1,589 个特效包，服务端发送 3,178 个；全部正常关闭，最终活动效果/图和待发队列归零。
- 已目视核验限速后的施法者电浆蓄力、旁观者飞行、烟雾和切割截图。
- 代理记录两连接下行 TCP 应用字节分别 974,550 和 653,805；包含登录、区块、其他游戏包，两个客户端的等待时长不同，不能直接比较。没有包含 TCP/IP 帧头。
- 烟雾阶段约 9.3 KiB/s，未持续顶满 64 KiB/s，因此该轮主要验证延迟和抖动下的播放；不据此宣称拥塞问题彻底解决。

## 可见与离屏开销

取 live02 旁观者数据，每阶段去掉首秒；单位 ms。CPU 是 RenderFrame.Pre 至 Post 的区间，GPU 是 TimerQuery 的滚动计时（3 次平均），均不是单独某一个效果的纯耗时，也不是整体 FPS 或输入延迟。

| 场景 | 可见 CPU P50 / P95 | 离屏 CPU P50 / P95 | 可见 GPU P50 | 离屏 GPU P50 |
| --- | ---: | ---: | ---: | ---: |
| 光束/电浆混合场景 | 1.137 / 1.518 | 0.161 / 0.269 | 1.379 | 0.139 |
| 烟雾压力场景（约 95 个同时活动图） | 2.614 / 3.040 | 0.416 / 0.549 | 2.685 | 0.209 |
| 未元切割压力场景 | 0.487 / 0.681 | 0.366 / 0.501 | 0.208 | 0.171 |

这是同版转身前后的场景比较，两个视角包含的世界绘制不同；没有迁移前同配置基线，因此不报告“优化提升百分比”。图统计只包括 VFX Graph，切割走实例渲染器，不能用图数直接推导切割数量。

原始 CSV 与 `performance-summary.json` 位于对应 session 目录；运行 `scripts/summarize_vfx_validation.py` 可复算。

## 多人派发自动测试

覆盖：

- 冲击波、烟雾、切割恰好向每名范围内观察者发送一次，范围外不发送；烟雾尺寸和切割完整寿命正确，服务端没有生成这两种显示实体。
- 观察者距光束起点 400 格，但在末端附近，仍能订阅。
- 电浆蓄力中途进入、离开、发射后重新进入；焦点位置和完整飞行状态正确。
- 已启用技能的施法者实际开火，并在同一服务端 tick 删除实体；开火快照先于更高版本的终止事件。
- 取消电浆不产生爆炸；实体移除后的独立命中事件仍到达。
- 每人一束稳定光束和一个逐 tick 推进进度的电浆，运行 101 tick，检查续传、每观察者普通更新上限和队列回收。

本轮联合运行的稳定负载：

| 模拟观察者 | 同时存在效果 | 特效包数 | 编码字节总量（全部观察者） | 待发 / 丢弃 |
| ---: | ---: | ---: | ---: | ---: |
| 2 | 4 | 44 | 3,600 | 0 / 0 |
| 8 | 16 | 704 | 57,600 | 0 / 0 |
| 16 | 32 | 2,816 | 230,400 | 0 / 0 |

编码量含 Misaka 类型编号和 SkillVfxPacket，未含压缩及传输层开销。不同调度轮次可出现续传计数差异；不能把 GameTest 加速时间换算为真实每秒出口。烟雾和切割的小包单测均不超过 72 字节（指定短 ID）；长 ID 会增加 VarLong 字节数。

## 复现

JBR 25，仓库根目录。先准备隔离目录，脚本只修改 `run/vfx-validation/` 下的测试配置，沿用已经接受的 Minecraft EULA 文件：

```powershell
$env:JAVA_HOME = 'C:/Users/Dusk/AppData/Local/Programs/IntelliJ IDEA/jbr'
./scripts/prepare_vfx_validation.ps1
./gradlew.bat test -DisDev=true --console=plain
./gradlew.bat runGameTestServer -DisDev=true '-PacademyGameTests=academy:skill_vfx_multiplayer_*' --console=plain
./gradlew.bat build -DisDev=true --console=plain
./gradlew.bat build -DisDev=false --console=plain
```

三个终端按服务器、施法者、旁观者顺序启动；每轮使用新的 session 名，避免读取旧的 ready/phase 文件。服务器启动后会等待两个客户端就绪，场景结束自动关闭三个游戏进程。

```powershell
./gradlew.bat runServerVfxValidation -DisDev=true -PacademyVfxSession=review01 --console=plain
./gradlew.bat runClientVfxCaster -DisDev=true -PacademyVfxSession=review01 --console=plain
./gradlew.bat runClientVfxObserver -DisDev=true -PacademyVfxSession=review01 --console=plain
```

限速测试另开一个终端先运行代理（Python 标准库即可），两条客户端命令均加 `-PacademyVfxPort=25576`，服务器仍使用 25575。所有命令须使用同一个新的 session 名：

```powershell
python scripts/vfx_validation_proxy.py --output build/vfx-live-validation/shaped-review01 --kib 64
python scripts/summarize_vfx_validation.py build/vfx-live-validation/shaped-review01
```

代理默认双向 100±30 ms、600 秒超时，两个客户端连接结束后自动退出。8 KiB/s 场景使用 `--kib 8`。阶段标记从同机文件读取，不占游戏带宽；数据分析排除阶段首秒，但深度积压时仍可能有跨阶段到达的数据。

日志：`build/vfx-extension-test.log`、`build/vfx-extension-gametest.log`、`build/vfx-extension-build-{dev,release}.log`，以及各 `build/vfx-*-{server,caster,observer,proxy}.log`。

## 仍需独立推进的范围

- 未元羽刃、矛、生物炮弹包含追踪、碰撞或伤害；未元生物有 AI/实体交互。本轮没有取消其玩法实体同步，后续需要各自轨迹和终止协议。
- 未元六翼已用附加状态驱动 VFX，但逐帧模型构建仍可继续优化；本轮未改动其网格所有权。
- 其他服务端显示实体入口仍有 ArcEffect、LightOrb、GlowCircle、RailgunRay；需分别梳理动态路径、跟随、反射和声音后迁移。本轮不宣称全部技能均已完成迁移。
- 未完成真实多机/不同显卡、持续服务器总出口饱和、链路断开重连、切维度、快速反复转身、全部反射/停时技能的图形矩阵。
- 没有逐事件首次呈现时间与漏显率统计，也没有迁移前出口和 GPU 基线。TCP 队列已积压的数据无法被本协议插队；降低显示实体流量不能消除所有网络延迟。


## 8 KiB/s 拥塞补充（shaped08）

在相同双向 100±30 ms 延迟下，将每连接下行降至 8 KiB/s。烟雾可见阶段两连接分别转发 48,315 和 48,036 字节，后续离屏烟雾阶段分别转发 49,432 和 49,774 字节（每阶段约 6 秒，边界可能切入一个 TCP 数据块）。相比 64 KiB/s 场景约 55～57 KB 的可见烟雾阶段流量，出现跨阶段排队；吞吐量已接近设置上限。这是对该场景积压的证据，并非服务端总出口压力测试。

- 两客户端最终均接收 1,589 个特效包，与服务端 3,178 次发送匹配；效果/图、待发更新全部归零，代理两连接 error 均为 null。
- 实际截图确认施法者待发射电浆球、烟雾和短切割可见。该轮沿用测试世界时钟，截图为夜间；不将其 GPU 数据与日间场景直接比较。
- 排队仍会推迟首次显示；本轮没有逐事件首次呈现时间分布，不能把完整收包数写成“零视觉延迟”或“每次均已目视确认”。
- 原始资料：`build/vfx-live-validation/shaped08/`，包括双方 CSV、screenshots、server-result.txt、proxy-result.json；代理记录少量阶段切换瞬间的空阶段字节，分析中不能将其当成丢包。

构建验收：`test -DisDev=true` 通过 1,790 项；两个 build 变体均成功，发布包核查含新切割着色器/渲染器且不含 editor 中的自动联机场景。
