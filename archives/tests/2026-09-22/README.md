# 测试代码归档（2026-09-22）

`test-sources.zip` 保存本轮清理前 `src/test`、`src/editorTest` 下全部 603 个文件，包含 Java/Kotlin 源码、包注解和 GLSL 快照。归档保留原始仓库相对路径与文件字节，不在 Gradle source set 中，也不会打入模组产物。

基准提交：`317712cad59d96cbc78172d388b272c3d4d38c76`。开始整理时 Git 工作区无已有改动。

压缩包 SHA-256：`408a174742090c7111a4ed37789a2fe56b9e22046b423365f6356cc57b1f1f53`。

`manifest.json` 记录每个原文件的路径、字节数、SHA-256、处理方式及原因；部分精简的文件还列出移除的方法名。删除前已逐项验证压缩包内全部文件的 SHA-256。

## 清理范围

本轮按测试内容判断维护价值，不以文件数量或执行时间作为删除依据。

| 处理 | 内容 | 理由 |
| --- | --- | --- |
| 删除 4 个类 | `MixinLevelRendererTest`、`GlowCircleVfxReplicationTest`、`SkillVfxClientLifecycleTest`、`SpacialExcisionTest` | 主要通过源码字符串锁定方法名称、调用位置及旧实现是否消失，未运行相应渲染、生命周期或服务端上下文逻辑。 |
| 删除 2 个类 | `StormWingTest`、`VfxGraphSchemaTest` | 分别只比对单个 CP 常量、确认枚举成员存在；保留预留逻辑及图执行相关测试。 |
| 合并 1 个类 | `api/client/gui/render/GaussianSamplesTest.kt` | 归一化及采样上限已有覆盖；独有的半径 24 容量检查移入 `api/client/render/GaussianSamplesTest.kt`。 |
| 精简 15 个类、17 个方法 | 详见 manifest 的 `trim` 条目 | 移除 15 个固定调参值快照方法，以及 2 个旧实现/临时数组的源码字符串检查；保留这些类中的运算、时序及边界行为检查。 |

整理后保留原有文件中的 596 个，另新增 `src/test/README.md` 说明维护规则；测试方法净减少 37 个。固定平衡参数和被删除的源码结构约束不再由这些断言锁定；本轮没有新增等价的运行时集成测试，不能把相邻行为测试视为全部替代。若以后这些约束成为正式回归要求，应补充可执行的行为测试。

保留存档迁移、配置持久化、网络编解码、伤害与实体保护、能力运算、图编译/模拟、GUI 布局行为、编辑器测试、三种 JVM 类指针配置测试。`package-info.java` 的空值标记、共享夹具与 golden 资源均保留。`src/main` 中的 GameTest、验证工具及示例代码未改动。

## 校验和恢复

从仓库根目录运行以下 PowerShell。先校验并解压到 `.tmp`，不会覆盖当前项目文件；暂存目录已存在时会拒绝执行。恢复的测试是历史快照，之后生产 API 变化可能需要适配。

```powershell
$archiveDir = Join-Path $PWD 'archives/tests/2026-09-22'
$manifest = Get-Content (Join-Path $archiveDir 'manifest.json') -Raw | ConvertFrom-Json
$archivePath = Join-Path $archiveDir $manifest.archive
if ((Get-FileHash $archivePath -Algorithm SHA256).Hash -ne $manifest.archiveSha256) {
    throw '压缩包校验失败'
}
$restoreDir = Join-Path $PWD '.tmp/test-restore-2026-09-22'
if (Test-Path -LiteralPath $restoreDir) { throw '恢复暂存目录已存在' }
Expand-Archive -LiteralPath $archivePath -DestinationPath $restoreDir
foreach ($entry in $manifest.files) {
    $restored = Join-Path $restoreDir $entry.path
    if ((Get-FileHash -LiteralPath $restored -Algorithm SHA256).Hash -ne $entry.sha256) {
        throw "文件校验失败：$($entry.path)"
    }
}
```

校验后按 `manifest.json` 选取需要的文件，从暂存目录复制回同名路径。对于 `trim` 或 `merge` 条目，建议先比较差异、只恢复需要的方法，避免覆盖后续测试修改。恢复后运行 `test -DisDev=true` 和开发/发布两种 `build`。

## 验证记录

- 清理前：`./gradlew test -DisDev=true` 通过（复用同版本构建缓存）。
- 清理后：`./gradlew test -DisDev=true` 通过，2,122 个测试，0 失败、0 错误、0 跳过。
- `./gradlew build -DisDev=true`、`./gradlew build -DisDev=false` 均通过，包括 103 个编辑器测试及 API 示例检查；未改变构建任务、测试过滤规则或依赖。
- 三种专用 JVM 测试均通过：非压缩类指针运行 3 项；紧凑对象头及显式禁用后端两种配置各运行 1 项能力检测、按已有 JUnit assumption 跳过 2 项需要受支持后端的读写测试。
- 已按上方步骤解压至 `.tmp/test-restore-2026-09-22`，603 个文件全部通过逐文件 SHA-256 校验；580 个 `retain` 条目的现有文件仍与备份逐字节相同。
- `git diff --check` 通过。Gradle 输出包含既有弃用与 native-access 警告，无测试失败。
- 本轮仅整理测试与归档文档，未修改玩法、GUI 或渲染实现，不涉及游戏内验收。
