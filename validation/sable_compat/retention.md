# 原生完整存档混装保护验证

此流程复制已通过三阶段原生持久化验证的完整世界，源世界只读。脚本不会删除、移动或覆盖世界；发现链接、已有目标或未停服进程会停止。

```powershell
$sourceRun = 'D:/mcmodtest/AcademyCraft-Reborn-26.2/run/native-structure-physics-persistence-20261013-p16-r1'
$targetRun = 'D:/mcmodtest/AcademyCraft-Reborn-26.2/run/sable-compat/p17-retention-r1'
./validation/sable_compat/retention-world.ps1 -Mode Prepare -SourceRunDirectory $sourceRun -GameDirectory "$targetRun/server"
```

`Prepare` 要求源的 prepare/unavailable/resume 结果全部通过且服务器线程已退出，检查 Java 进程与世界锁，递归拒绝重解析点。复制后对完整文件清单、尺寸和 SHA-256 逐项比对；基线写在副本世界外。它不创建 EULA 同意，也不启动服务器。

在独立 server 目录按现有已获授权的运行流程准备 EULA 与 `server.properties`：`server-ip=127.0.0.1`、`level-name=academy-sable-smoke`。不要启用原生引擎、原生持久化烟测或普通 Sable 装配烟测属性。

```powershell
./gradlew.bat runServerSable -DisDev=true -PacademySableRuntime=sable -PacademySableRetention=true "-PacademySableRunDirectory=$targetRun"
./validation/sable_compat/retention-world.ps1 -Mode Verify -GameDirectory "$targetRun/server"
```

保护夹具独占运行，要求 `MIGRATION_REQUIRED`，先验证公共查询不创建原生运行时，再调用旧处理器使用的内部运行时入口，验证创建、加载和物理区域开启被拒绝。它不伪造网络包。总共运行 120 个连续 tick，跨过索引的 100 tick 保存边界；每次比较元数据文件集合和实际字节，正常停服且服务器线程退出后再次比较。

只有 Java 结果、退出记录和外部 `Verify` 都通过才算完成。外部验证还要求原完整源世界逐文件保持不变。副本的 `level.dat` 等正常 Minecraft 保存文件允许变化；保留维度文件的字节差异仅作为观察写入证据，不据此宣称内容损坏。此次检查只验证原生存档受到保护，不执行原生到 Sable 的迁移。
