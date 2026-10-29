# 多人结构姿态批量同步与客户端生命周期验证

基线 `741e9b54`。本轮由服务端按观察者合并结构姿态消息，保持原有姿态精度与服务器更新频率；完整方块内容仅在首次进入、内容变化或重新进入兴趣区时发送。兴趣距离按结构实际包围盒计算，进入 256 格、离开 272 格，避免站在边界反复全量同步。

## 客户端旧世界清理

`SubLevelClientState` 原有 `WeakHashMap<ClientLevel, Map<..., NativeStructureProjectionEntity>>` 的值包含实体，实体反向持有 ClientLevel，仅依赖弱键无法释放旧世界。现在利用已有 `MixinMinecraft.tick HEAD → SubLevelClientState.tick` 入口，在暂停或空世界判断前处理世界切换：丢弃旧投影、移除投影 ID、清状态/碰撞姿态/诊断缓存，注销旧碰撞索引并清承载帧。同一世界直接返回，不在每个 tick 重复扫描投影。

测试会保留旧 ClientLevel 的强引用，再在真实断线后检查所有 level map 与旧索引均清除，因此通过结果不依赖垃圾回收碰巧发生。

## 真 TCP 证据

最终隔离轮次为 `run/sublevel-batch-multiplayer-20261029-batch-r4`。一个新建专用服务器与两个独立 Java 客户端均使用 JBR 25；两个不同离线玩家通过 127.0.0.1:25575 的真实 TCP 连接，明确拒绝内存连接或集成服务器。服务端建立 8 个带真实质量和碰撞形状的原生铁块刚体，持续设置低速平移和角速度，实际运行原生求解与网络发布。

| 检查 | Actor | Observer |
| --- | ---: | ---: |
| 移动阶段姿态批数 / 条目数 | 99 / 792 | 99 / 792 |
| 每批条目 | 8 | 8 |
| 旧逐体姿态包 | 0 | 0 |
| 移动观察样本 | 100 | 100 |
| 远离兴趣区时投影数 | 8 | 0 |
| 返回后投影数 | 8 | 8 |
| 删除后状态 / 投影 / 碰撞姿态 | 0 / 0 / 0 | 0 / 0 / 0 |
| 重建后、断线前活投影 | 8 | 8 |
| 断线清理实际 discard 数 | 8 | 8 |
| 断线后保留 level / ID / 承载帧 | 0 / 0 / 0 | 0 / 0 / 0 |
| 旧 CollisionRuntime 注销 | 通过 | 通过 |

服务器记录 221 个移动 tick；移动阶段 199 个相邻 tick 的实际间隔 P50 为 50.0136 ms、P95 为 52.0096 ms。三进程均退出 0，并记录 ServerStopped 正常停止事件。服务器先验证删除阶段为 0 体，随后为断线覆盖重建 8 体并主动关闭两条 TCP 通道；这些结构作为测试内容保留在隔离存档中，不应把删除阶段的 0 误读为最终存档为空。

相对于每条姿态各一包，两个客户端的姿态消息数量均减少 87.5%。该比例只比较姿态消息数，不能解释为字节量、CPU、延迟或 FPS 同比例提升；tick 间隔也是此本机夹具的观察结果，不是隔离的性能对照基准。双端截图均目视确认 8 个结构可见，系统通知位于上部，未遮挡结构。

机器报告：[`sublevel_batch_multiplayer_validation_2026_10_29.json`](../validation/native_structure/sublevel_batch_multiplayer_validation_2026_10_29.json)。其中记录三端结果、Java 启动参数文件、进程日志、截图、源文件及原生库 SHA-256。实际使用由当前随包 Base64 解码并验证的 Windows 库 `F68C901911B18BE6F92A9EA9E44B1E09CEA8D34DD75FF08AF50F035F6DCF4A06`，未使用可能含既有 Rust 改动的 build/native-physics/artifacts 库。

## 准备与复跑

```powershell
$env:JAVA_HOME='C:/Users/Dusk/.gradle/jdks/jetbrains_s_r_o_-25-amd64-windows.2'
$session='fresh-batch-session'
$testRoot=./validation/native_structure/prepare_batch_multiplayer.ps1 -Name $session
foreach ($role in @('server','actor','observer')) {
    ./gradlew.bat exportSubLevelBatchMultiplayerLaunch -DisDev=true --no-daemon --console=plain -I validation/native_structure/sublevel_batch_multiplayer.init.gradle "-PacademyStructureMultiplayerRole=$role" "-PacademyStructureMultiplayerSession=$session" "-PacademyNativePhysicsLibrary=$testRoot/native-runtime/academy_physics.dll" "-PacademyNativePhysicsManifest=$testRoot/native-runtime/academy_physics.properties"
}
& 'C:/Users/Dusk/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' validation/native_structure/run_batch_multiplayer.py $session
```

准备脚本只创建不存在的新会话，拒绝复用旧结果和已占用的 25575 端口，不删除世界或终止其他进程。三个导出顺序完成后，启动器从 Java 参数文件直接启动三个进程，避免多 Gradle 守护进程同时改写开发参数；仅回收本启动器创建的精确进程句柄。每角色有独立 `java.io.tmpdir`，避免 ImGui 在 Windows 上同时解压同一 JNI DLL。客户端为 1280×720、中文、视距 6、模拟距离 5、最大帧率 60、失焦不暂停。

## 失败尝试与边界

- r1 的网络、兴趣区、移除阶段及三端退出通过，但两玩家同站位，另一玩家面部遮挡截图；不作为最终效果图，也未验证旧世界生命周期。
- r2 网络阶段通过，但在发生断线的首个 ClientTick.Post 立刻检查旧 map 得到 1 个 level。断线可发生在该 tick 的 HEAD 之后；最终检查等待一个完整的后续生产 tick，仍严格断言 0，夹具不调用任何清理方法。
- r3 在第一个客户端 tick 前因 ImGui 全局临时 DLL 文件占用失败，保留 crash；之后隔离每角色临时目录。此轮不能算物理或网络成功验证。
- r4 包含活投影断线的新严格场景并全通过。两端各确认生产清理实际丢弃 8 个实体，证明并非仅在空 map 上退出。

本夹具验证单机回环、两真实玩家、8 个单铁块刚体与观察者兴趣切换，没有执行玩家按键技能、站立碰撞、WAN 延迟整形、混装 Sable 多人、异形大型结构或长期压力测试。服务器与客户端需使用同批构建。上述边界仍须各自验收，不能因消息合批通过而关闭。
