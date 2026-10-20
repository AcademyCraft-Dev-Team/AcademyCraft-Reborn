# Local / Sable 配对测量

只在全新 `run/sable-compat/parity-<RunId>/server` 生成世界。不会读写玩家 test 存档。需要已接受 EULA 的开发服务器文件、JBR 25、已配置的本地 native 库和固定 Sable Maven 输入。

```powershell
. ./build/native-physics-p16-env.ps1
./validation/structure_parity/run-pair.ps1 -RunId local-ground-example -Backend local -Fixture ground -AcceptedEulaFile run/sable-compat/stress-server-sable-r5/server/eula.txt
./validation/structure_parity/run-pair.ps1 -RunId sable-ground-example -Backend sable -Fixture ground -AcceptedEulaFile run/sable-compat/stress-server-sable-r5/server/eula.txt
# 再用 Fixture floating 各运行一次；复验时颠倒 local/Sable 顺序并用新 RunId。
```

原始 JSON：对应 server/result.json；Gradle/游戏日志：build/parity-<RunId>.log。禁止同时运行两侧；保存无效结果，换 RunId 重跑。脚本拒绝已有目录，并检查 result.measurement_status；Gradle 退出 0 不自动等于测量成功。

`ground_128.csv` 是先前 H 实测采集的 128 格相对位置，不来自 Sable。该形状在 Sable 捕获桥出现初始错位，不能直接当公平对照。当前默认 `cuboid_128.csv` 为 8×2×8、首格是局部原点；同时校验创建后质心对齐。夹具通过既有 `StructureTransactions`、`StructureDirectControl` 和 backend launch 调用真实后端。两侧材料质量不同，以单位质量控制响应对齐；此夹具不是 H 质量准入/能力耗能/鼠标选择验收。

tick 原始记录包含 warmup/capture/lift/flight、server_tick_ms、probe_ms、rise、speed。暖机和捕获不要混进稳定运动 p95；创建调用成本单列；p95 用排序后 ceil(0.95*N)-1 的值。比值为 local/Sable；失败样本只报告诊断成本，不报告成功吞吐。建议至少两轮 AB/BA 小样本，正式验收增加独立重复、空载基线及多结构矩阵。

待补：client frame、可视首帧、GC/分配、包字节、H 及节点端到端、复杂地形、质量/质心/碰撞形状跨后端对齐。新世界暖机 60 tick 不能消除全部 JIT/首次缓存差异。

汇总最终已对齐且测量完成的配对组（只计算诊断成本，功能结果仍分别列出）：

```powershell
./validation/structure_parity/summarize.ps1 -RunIds local-ground-r5,sable-ground-r5,local-floating-r5,sable-floating-r5,local-ground-r6,sable-ground-r6,local-floating-r6,sable-floating-r6 -OutputFile build/structure-parity-summary.json
```

原始每 tick 数据保留在 run 目录，报告另保存其 SHA-256；不要提交 run/build 日志。正式实验建议另外固定 CPU/GPU、系统电源设置、后台负载与 JVM 内存，并记录空载对照。
