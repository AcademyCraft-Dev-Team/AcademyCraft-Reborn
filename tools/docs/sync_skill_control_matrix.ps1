param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../.."))
)

$ErrorActionPreference = "Stop"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$skillsPath = Join-Path $RepositoryRoot "src/main/java/org/academy/internal/common/ability/Skills.java"
$namesPath = Join-Path $RepositoryRoot "src/main/java/org/academy/internal/common/ability/SkillNames.java"
$effectPath = Join-Path $RepositoryRoot "docs/SKILL_EFFECT_COST_ITERATION_STACK_MATRIX.md"
$dependencyPath = Join-Path $RepositoryRoot "docs/ability_skill_dependencies.md"
$controlPath = Join-Path $RepositoryRoot "docs/SKILL_IMPLEMENTATION_CONTROL_MATRIX.md"

$nameMap = @{}
$nameText = [IO.File]::ReadAllText($namesPath)
[regex]::Matches($nameText, 'public static final String\s+(\w+)\s*=\s*"([a-z0-9_]+)"') | ForEach-Object {
    $nameMap[$_.Groups[1].Value] = $_.Groups[2].Value
}

$classById = @{}
$skillsText = [IO.File]::ReadAllText($skillsPath)
[regex]::Matches($skillsText, 'DeferredHolder<Skill,\s*([^>]+)>\s+\w+\s*=\s*SKILLS\.register\(SkillNames\.(\w+)') | ForEach-Object {
    $id = $nameMap[$_.Groups[2].Value]
    if ($id) {
        $classById[$id] = $_.Groups[1].Value
    }
}

$dependencyById = @{}
$dependencyText = [IO.File]::ReadAllText($dependencyPath)
[regex]::Matches($dependencyText, '(?m)^\|\s*\d+\s*\|[^\r\n]*?\(`academy:([a-z0-9_]+)`\)\s*\|\s*([^|\r\n]+)\|') | ForEach-Object {
    $dependencyById[$_.Groups[1].Value] = $_.Groups[2].Value.Trim()
}

$defaultKeyById = @{}
if (Test-Path $controlPath) {
    $oldControl = [IO.File]::ReadAllText($controlPath)
    [regex]::Matches($oldControl, '(?m)^\| `([a-z0-9_]+)` [^|]+\|[^|]+\|[^|]+\|[^|]+\|\s*([^|]+)\|') | ForEach-Object {
        $defaultKeyById[$_.Groups[1].Value] = $_.Groups[2].Value.Trim()
    }
}

$renamedKeySources = @{
    vector_deviation = "vector_reduction"
    piercing_teleportation = "cut_through"
    brain_domain_development = "level0_passive_lv1"
    multiple_brain_domain_segmentation = "level0_passive_lv2"
    parallel_thought_computation = "level0_passive_lv3"
    complete_consciousness_analysis = "level0_passive_lv4"
    absolute_self_control = "level0_passive_lv5"
}

$categoryNames = [ordered]@{
    "Aeromanip 气动操纵" = "Aeromanip 气动操纵"
    "Accelerator 矢量操纵" = "Accelerator 矢量操纵"
    "Electromaster 电气操纵" = "Electromaster 电气操纵"
    "Meltdowner 原子崩坏" = "Meltdowner 原子崩坏"
    "Teleport 空间移动" = "Teleport 空间移动"
    "Darkmatter 未元物质" = "Darkmatter 未元物质"
    "Mentalout 心理掌握" = "Mentalout 心理掌握"
    "Level0 公共脑域" = "Level0 公共脑开发"
}

$rowsByCategory = [ordered]@{}
foreach ($name in $categoryNames.Keys) {
    $rowsByCategory[$name] = [Collections.Generic.List[object]]::new()
}

$currentCategory = $null
foreach ($line in [IO.File]::ReadAllLines($effectPath)) {
    if ($line -match '^## (.+?)（\d+）$' -and $categoryNames.Contains($Matches[1])) {
        $currentCategory = $Matches[1]
        continue
    }
    if ($line.StartsWith('## ')) {
        $currentCategory = $null
        continue
    }
    if (!$currentCategory -or $line -notmatch '^\|') {
        continue
    }

    $cells = @($line.Split('|') | Select-Object -Skip 1 | Select-Object -SkipLast 1 | ForEach-Object { $_.Trim() })
    if ($cells.Count -ne 7 -or $cells[0] -notmatch '^(.+?)\s+`([a-z0-9_]+)`$') {
        continue
    }

    $displayName = $Matches[1].Trim()
    $id = $Matches[2]
    $className = $classById[$id]
    if (!$className) {
        throw "Effect matrix contains an unregistered skill: $id"
    }

    $energyCost = 0
    $classPath = Get-ChildItem (Join-Path $RepositoryRoot "src/main/java/org/academy/internal/common/ability") -Recurse -Filter "$className.java" | Select-Object -First 1
    if ($classPath) {
        $classText = [IO.File]::ReadAllText($classPath.FullName)
        $energyMatch = [regex]::Match($classText, '\.energyCost\(([\d_]+)\)')
        if ($energyMatch.Success) {
            $energyCost = [int]($energyMatch.Groups[1].Value.Replace('_', ''))
        }
    }

    $defaultKey = $defaultKeyById[$id]
    if (!$defaultKey -and $renamedKeySources.ContainsKey($id)) {
        $defaultKey = $defaultKeyById[$renamedKeySources[$id]]
    }
    if (!$defaultKey) {
        $defaultKey = "见源码（可配置）"
    }

    $rowsByCategory[$currentCategory].Add([pscustomobject]@{
        Id = $id
        Name = $displayName
        Level = $cells[1]
        Energy = $energyCost
        Cost = $cells[3]
        Effect = $cells[2]
        Key = $defaultKey
        Dependencies = if ($dependencyById.ContainsKey($id)) { $dependencyById[$id] } else { "无" }
        ClassName = $className
    })
}

$allRows = @($rowsByCategory.Values | ForEach-Object { $_ })
if ($allRows.Count -ne $classById.Count -or $allRows.Count -ne 94) {
    throw "Expected 94 registered skills, found matrix=$($allRows.Count), source=$($classById.Count)"
}

function Escape-Cell([string]$value) {
    return $value.Replace('|', '\|').Replace("`r", '').Replace("`n", '<br>')
}

$out = [Collections.Generic.List[string]]::new()
$out.Add('# 技能实现与调控总表')
$out.Add('')
$out.Add('本文档由当前 `Skills` 注册表、全技能效果总表和前置关系清单汇总，共 94 个已注册技能。运行 `powershell -NoProfile -ExecutionPolicy Bypass -File tools/docs/sync_skill_control_matrix.ps1` 可在基础文档变更后重新生成本表。按键是源码默认值或“见源码”提示；玩家实时覆盖值以 `config/academy-client.json` 为准。')
$out.Add('')
$out.Add('## 标记说明')
$out.Add('')
$out.Add('- `↓`：按下；`↑`：松开；同一技能同时列出两项表示按住/蓄力或开始/结束。')
$out.Add('- `IF` 为当前源码 `energyCost`；消耗栏来自全技能效果总表。')
$out.Add('- “见源码（可配置）”表示尚未在本表固化默认组合，不表示技能没有按键。')
$out.Add('')
$out.Add('## 全局控制')
$out.Add('')
$out.Add('| 功能 | 默认按键 | 说明 |')
$out.Add('| --- | --- | --- |')
$out.Add('| 能力 HUD | `V↓` | 打开或关闭技能 HUD |')
$out.Add('| HUD 上一技能 | `↑↓` | 技能轮盘向上 |')
$out.Add('| HUD 下一技能 | `↓键↓` | 技能轮盘向下 |')
$out.Add('| 数据终端 | `右 Alt↓` | 打开数据终端及设置应用 |')

foreach ($category in $categoryNames.Keys) {
    $out.Add('')
    $out.Add("## $($categoryNames[$category])")
    $out.Add('')
    $out.Add('| 技能 | 状态 | 等级 / IF / 消耗 | 实现与当前效果 | 默认按键 | 直接前置 | 实现类 |')
    $out.Add('| --- | --- | --- | --- | --- | --- | --- |')
    foreach ($row in $rowsByCategory[$category]) {
        $energy = if ($row.Energy -ge 1000) { "$($row.Energy / 1000)k" } else { "$($row.Energy)" }
        $out.Add("| ``$($row.Id)`` $(Escape-Cell $row.Name) | 现行 | $($row.Level) / $energy / $(Escape-Cell $row.Cost) | $(Escape-Cell $row.Effect) | $(Escape-Cell $row.Key) | $(Escape-Cell $row.Dependencies) | ``$($row.ClassName)`` |")
    }
}

$out.Add('')
$out.Add('## 默认按键冲突与调控优先级')
$out.Add('')
$out.Add('| 优先级 | 冲突/问题 | 影响 | 建议 |')
$out.Add('| --- | --- | --- | --- |')
$out.Add('| P0 | Electromaster：`electrical_contact` 与 `current_recharge` 都以 `H↓` 启动 | 同一按下动作可能切换带电接触并开始充能 | 为其中一个技能更换默认绑定，并在客户端验收按下/松开事件 |')
$out.Add('| P1 | Electromaster：`ball_lightning` 为 `Y↓`，`current_symbiosis` 为 `Y↑` | 一次完整按键可能施放球状闪电并切换电流共生 | 为其中一个技能换键，或明确消费输入事件 |')
$out.Add('| P1 | 新增和重命名技能仍有“见源码”按键项 | 表格不能独立用于默认键冲突审计 | 后续从统一输入注册表导出默认键，避免手工维护 |')
$out.Add('| P1 | 高伤害、方块修改、区域传送和玩家控制技能 | 数值统一前仍需服务端权威、权限与多人生命周期验收 | 按 `RUNTIME_ACCEPTANCE.md` 完成客户端和双玩家专服门禁 |')
$out.Add('')
$out.Add('## 建议的统一修改入口')
$out.Add('')
$out.Add('- 等级、IF、基础 CP、维持 CP、依赖：各技能构造器的 `Skill.Builder`。')
$out.Add('- 默认键：各技能 `initClient()` 中的 `InputSystem.combo(...)`；玩家覆盖值由统一 `InputSystem` 配置保存。')
$out.Add('- 范围、伤害、持续时间、扫描间隔：各类常量、计算方法和运行时管理器。')
$out.Add('- 熟练度成本与迭代修正：`SkillProficiencyProfiles.java`。')
$out.Add('- 当前验收边界和未关闭人工检查：`RUNTIME_ACCEPTANCE.md`。')

[IO.File]::WriteAllLines($controlPath, $out, $utf8NoBom)
Write-Output "Generated $controlPath with $($allRows.Count) skills."
