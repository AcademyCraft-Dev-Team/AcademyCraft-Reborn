// Generates the comparison from measured JSON, retaining regressions and distinguishing blocked photography.
const fs = require('fs'), path = require('path');
const dir = __dirname;
const read = name => JSON.parse(fs.readFileSync(path.join(dir, name), 'utf8'));
const before = read('baseline_client.json'), after = read('optimized_client.json');
const poseBefore = read('baseline_pose.json'), poseAfter = read('optimized_pose.json');
const clearPath = path.join(dir, 'optimized_camera_clear_client.json');
const clear = fs.existsSync(clearPath) ? read('optimized_camera_clear_client.json') : null;
const fixed = (n, digits=3) => n.toFixed(digits);
const pair = (b, a) => `${fixed(b)} → ${fixed(a)}`;
for (const report of [before, after, clear].filter(Boolean)) {
    if (report.status !== 'PASSED' || report.framesPerStage !== 120 || report.stages.length !== 12) throw Error('Capture incomplete');
    for (const row of report.stages) if (row.scene.requestedCreations !== row.scene.trackedCreationsAtEnd || row.gpuFrameMilliseconds.samples !== 120) throw Error('Capture counts mismatch');
}
if (clear && !clear.clearCameraFixture) throw Error('Supplement must use the explicit clear-camera fixture');
if (clear && !clear.viewerPhysicalStateRestored) throw Error('Supplement viewer physical state was not restored');
const improvement = (1-poseAfter.medianBatchMeanPoseNanos/poseBefore.medianBatchMeanPoseNanos)*100;
const lines = [
    '# 独角仙性能实测对照', '',
    '2026-10-07 使用 JBR 25.0.3、1280 × 720、70° FOV、关闭垂直同步、相同帧率上限和实体 fixture。原始 baseline 与 optimized 文件均保留。每轮客户端十二段、每段 120 帧 GPU 时间戳，并记录真实追踪数量、移动实体到相机的实际距离和服务器 tick 样本。', '',
    '## 固定骨架 CPU', '',
    `详细骨架的固定 324 状态、81,000 次预热和六批共 388,800 次测量中，批次平均姿态成本的中位数由 **${fixed(poseBefore.medianBatchMeanPoseNanos)} ns → ${fixed(poseAfter.medianBatchMeanPoseNanos)} ns**，降低 **${fixed(improvement,2)}%**；两轮计时内 GC 次数均为零。这个数值只测 setupAnim 与固定遍历/校验开销，不是 FPS 或每次调用的 P95。`, '',
    '模型每次只复位当前可动骨架和选中分支根节点（最多约 26 个节点）并计算当前六足，完整几何与近距动作保持。24 格外的可编辑 Blockbench 派生低模从总体 320 体块降至 95 体块，单个组合从 84–132 降至 24–39；原版约 32 格剔除、AI、导航、资源及命中时序不变。', '',
    '## 同一原始场景的 16 / 24 格观测', '',
    '下面均为整个共同场景的中位时间，单位 ms，箭头为 baseline → optimized。CPU 是渲染事件 Pre/Post 区间；GPU 是整帧时间戳；服务器是整个 tick。零实体行保留共同玩家、平台、水体与目标，直接列出，不做减法估算单只独角仙成本。实际距离为移动实体到眼位的中位数，单位格；两轮 AI 自然运动有差异。', '',
    '| 真追踪实体 | 名义距离 | 实际距离 B → A | CPU 渲染 ms | GPU 帧 ms | 服务器 tick ms |',
    '| ---: | ---: | ---: | ---: | ---: | ---: |'
];
after.stages.forEach((a,i) => {
    if (a.scene.nominalCameraDistance === 32) return;
    const b = before.stages[i];
    const distance = a.scene.actualDistances.samples ? pair(b.scene.actualDistances.median,a.scene.actualDistances.median) : '—';
    lines.push(`| ${a.scene.trackedCreationsAtEnd} | ${a.scene.nominalCameraDistance} | ${distance} | ${pair(b.cpuRenderEventMilliseconds.median,a.cpuRenderEventMilliseconds.median)} | ${pair(b.gpuFrameMilliseconds.median,a.gpuFrameMilliseconds.median)} | ${pair(b.serverTickMilliseconds.median,a.serverTickMilliseconds.median)} |`);
});
const gpuBefore = before.stages[7].gpuFrameMilliseconds.median, gpuAfter = after.stages[7].gpuFrameMilliseconds.median;
lines.push('', `**64 只 / 24 格的 GPU 中位时间回退 ${fixed(gpuBefore)} → ${fixed(gpuAfter)} ms**，本次未进一步隔离原因；64 只 / 16 格的 CPU 与 GPU 也有轻微回退。因此不声称全部场景或 GPU 一致提升。服务器共同成本在零实体行已发生变化，不能把整段服务器差值归因为实体优化。P95、帧间隔和每段样本数均保留在原始 JSON。`, '',
    '## 32 格摄影问题与独立补验', '',
    '旧两轮 32 格相机为 (80,82,156)，眼部八角采样涉及的 (79/80,83,155/156) 四格在保存区块中均为 minecraft:packed_ice，位于 fixture 清场 z172–202 的范围之外。原版 ScreenEffectRenderer 判为 isViewBlocking 后绘制深色方块覆图，故这些截图全黑。旧 32 格 CPU/GPU 数值原样留档，但不用于无遮挡低模收益或显示正确性结论。16 / 24 格对应眼位在保存区块中为空气，截图可正常观察。', '',
    '补验只通过显式 -Dacademy.beetlePerformanceClearCamera=true 清除 32 格相机附近 5×5×5 体积并增加 3×3 落脚。生产实现不变，标签为 optimized_camera_clear；每段 32 格计时前后检查真实客户端八角眼位均无 isViewBlocking 方块。新 32 格场景只作无遮挡显示和计量，不同旧受挡数据直接同比。', '');
if (clear) {
    lines.push('| 真追踪实体 | 实际距离中位 | CPU 渲染 ms 中位 / P95 | GPU 帧 ms 中位 / P95 | 服务器 tick ms 中位 / P95 |',
        '| ---: | ---: | ---: | ---: | ---: |');
    for (const row of clear.stages.filter(r => r.scene.nominalCameraDistance === 32)) {
        if (row.scene.cameraViewBlocking) throw Error('Supplement camera still blocked');
        const r=row.scene, cpu=row.cpuRenderEventMilliseconds,gpu=row.gpuFrameMilliseconds,tick=row.serverTickMilliseconds;
        lines.push(`| ${r.trackedCreationsAtEnd} | ${r.actualDistances.samples ? fixed(r.actualDistances.median) : '—'} | ${fixed(cpu.median)} / ${fixed(cpu.p95)} | ${fixed(gpu.median)} / ${fixed(gpu.p95)} | ${fixed(tick.median)} / ${fixed(tick.p95)} |`);
    }
    lines.push('', '补验全部十二段通过，32 格四段均保留 120 帧 GPU 时间戳与真实追踪断言；计时前后八角眼位检查通过。真实观察玩家的原始 noGravity、invulnerable、flying 与游戏模式在清场后恢复，并记录到新 JSON；旧轮未恢复 NoGravity 的归档原样保留。');
} else lines.push('补验尚待分配 GPU 窗口，本节暂不填写新的测量结果。');
lines.push('', '## 正确性与边界', '',
    '模型/API 单元测试覆盖 108 组合 × 三档距离的共享 model A→B→A 回切、详细/低模相同关节姿态，以及近距公开旧动作结果的精确等价；实际客户端另外通过真实蓝图替换、owner 变更、NBT 保存/读取重建、迟注册头部和模块 handler 检查。结束先真实销毁所有造物并注销八名测试所有者，等客户端实体清空后才输出 PASSED。', '',
    '近距独角仙的结论来自上述逐组合等价测试与游戏目视，不是独角仙逐像素摄影对照。实体有正常 AI 和随机 UUID，前后截图不会逐像素相同。八名嵌入式网络玩家练习真实所有权、技能和追踪，但不等同八台远程客户端，也不包含远程机器 GPU 或完整网络带宽测量。默认场景没有主动集火，不能把本表当成持续炮击负载。', '',
    '原始数据：[baseline_pose.json](baseline_pose.json)、[optimized_pose.json](optimized_pose.json)、[baseline_client.json](baseline_client.json)、[optimized_client.json](optimized_client.json)' + (clear ? '、[optimized_camera_clear_client.json](optimized_camera_clear_client.json)。' : '。'), '',
    '正常目视截图：[128 只 / 16 格](optimized_stage_9_n128_d16.png)、[128 只 / 24 格](optimized_stage_10_n128_d24.png)' + (clear ? '、[补验 128 只 / 32 格](optimized_camera_clear_stage_11_n128_d32.png)。' : '。'), '');
fs.writeFileSync(path.join(dir,'COMPARISON.md'),lines.join('\n'));
console.log(JSON.stringify({report:'COMPARISON.md',supplement:clear?.status || 'pending',poseReductionPercent:improvement}));
