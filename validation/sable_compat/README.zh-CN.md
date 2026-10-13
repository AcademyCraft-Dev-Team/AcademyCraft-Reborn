# Suite 专服模型注册补丁

`suite_lazy_model_registration.patch` 是待审阅的外部 fork 补丁，尚未套用到 `D:/mcmodtest/create-fork`。它以 2026-10-13 读取的五个工作区文件为基准，仅将 60 处 `.blockstate(expr)` / `.model(expr)` 改为延迟的 `.blockstate(() -> expr)` / `.model(() -> expr)`，不包含这些文件已有的移植改动，也不改变回调体或生成资源。

固定 Suite `1.3.2-port.26.2.0.70` 在专服模组构造期间加载了客户端类型：`SimBlocks`、`AeroBlocks` 的模型 lambda，以及 `OffroadBlocks` 立即执行的模型回调工厂，触发 `RegistrateBlockModelGenerator extends BlockModelGenerators`。证据见 `build/sable-p17-suite-server-r1.log:23375` 起的三组启动栈。实际随 Create 分发的 Registrate 已有 supplier 重载，并在 `doDatagen` 为 false 时返回、不调用 supplier；Create 本体已采用该方式。物品模型生成器同样继承客户端类型，故一起延迟。无需添加客户端类或绕过专服的类型检查。

| 文件 | blockstate / model 改动数 |
| --- | --- |
| SimBlocks.java | 35 / 4 |
| SimItems.java | 0 / 2 |
| AeroBlocks.java | 11 / 1 |
| OffroadBlocks.java | 3 / 0 |
| OffroadItems.java | 0 / 4 |

## 套用前校验

以下 PowerShell 仅校验当前文件的原始字节 SHA-256 和补丁上下文，不修改 fork。任何一项不匹配都应停止并重新审阅；不要用 Git 重置覆盖 fork 中已有的改动。补丁路径从 fork 根目录解析。

```powershell
$forkPath = 'D:/mcmodtest/create-fork'
$patchPath = 'D:/mcmodtest/AcademyCraft-Reborn-26.2/validation/sable_compat/suite_lazy_model_registration.patch'
$sourcePrefix = '.porting/sources/Simulated-Project'
$expected = @{
    'simulated/common/src/main/java/dev/simulated_team/simulated/index/SimBlocks.java' = '5F3D21D7BEB70DABE1F8393AFCDA6D03CA21489E6E5F6F82387131F0C423F4AB'
    'simulated/common/src/main/java/dev/simulated_team/simulated/index/SimItems.java' = '092B7D5225802CE683B9D22BEFF2243BBA1E67A349A19C4F6AB3F985DAFD1C4E'
    'aeronautics/common/src/main/java/dev/eriksonn/aeronautics/index/AeroBlocks.java' = '3CC790920F90DDE370CEE9C6046A145D8C2CA26212AAFAC08820DEBAC8094C07'
    'offroad/common/src/main/java/dev/ryanhcode/offroad/index/OffroadBlocks.java' = '6E507D67250F4587237FABAF21AE99B45E4E776DA885DDF0BB997BF08F79C59E'
    'offroad/common/src/main/java/dev/ryanhcode/offroad/index/OffroadItems.java' = '01C54757CF656FE60F5859DDC6E2190A953B8F38308F75E9DF83D4949A8BA06F'
}
foreach ($entry in $expected.GetEnumerator()) {
    $sourcePath = Join-Path $forkPath (Join-Path $sourcePrefix $entry.Key)
    $actual = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash
    if ($actual -ne $entry.Value) { throw "源文件已变化，停止套用：$sourcePath" }
}
git -C $forkPath apply --check $patchPath
if ($LASTEXITCODE -ne 0) { throw '补丁上下文校验失败' }
```

本次已对上述精确源文件执行 `git apply --check`，结果通过。确认将外部修复纳入后，再在相同会话执行 `git -C $forkPath apply $patchPath`。该命令不暂存、不提交文件。没有使用 `--index`、`--3way`、强制忽略上下文或整文件替换；五个文件的原有其他改动应保留。

## 外部重建与验证

补丁尚未编译或运行，60 个回调点全部覆盖不代表排除了其他客户端类型泄漏。需要：

1. 使用 fork 现有的 JBR 25 和构建流程重建 Simulated、Aeronautics、Offroad 的 common/NeoForge 成品及 bundled Suite；确保所有嵌套 JAR 为新产物，避免复用旧文件。
2. 用新 Suite 运行真正的 dedicated server，先验证模组构造与完整启动，再运行 Academy 的 Suite 服务端兼容烟测、资源回调终止及物理法杖双向互斥夹具。
3. 运行 Suite 客户端与现有数据生成任务，确认模型和材质仍正常生成、加载；检查生成差异，不能为了服务端启动而删除模型回调或禁用数据生成。
4. 记录重建后顶层与嵌套 JAR 的版本、尺寸及 SHA-256，更新 Academy 的 `docs/sable_compat_artifacts.json` 和 `SableStaffMixinGate` 中 Simulated 的固定指纹；未改动的 Sable/Companion 指纹保留。随后重新执行构建锁校验、普通与 Sable 专用测试、开发/发布构建和 Suite 两端烟测。未知新成品应继续被现有门禁拒绝，不能先放宽门禁绕过验证。
