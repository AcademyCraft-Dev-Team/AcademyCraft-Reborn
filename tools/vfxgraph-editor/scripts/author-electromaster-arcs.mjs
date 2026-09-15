import { VfxGraphRepository } from './vfxgraph.mjs';

const repository = new VfxGraphRepository(process.cwd());
const number = (id, value) => ({ id, name: id.replaceAll('_', ' '), type: 'FLOAT', default: { type: 'FLOAT', value } });
const block = (id, type, values) => ({ id, type: `vfx.block.${type}`,
  properties: Object.fromEntries(Object.entries(values).map(([key, value]) => [key, String(value)])) });
const output = (id, layer) => block(id, 'output_arc', {
  vertex: 'academy:core/vfxgraph_arc', shader: 'academy:core/vfxgraph_axial_burst', blend: 'glow', layer,
  thickness: 1, emission: 1.15, sparks: 0, segments: 12, overall_scale: 1,
  branch_depth: 0, branch_count: 0, noise_strength: 0, drift_speed: 0, branch_brightness_scale: 1,
});
async function write(id, graph) {
  const file = await repository.resolveGraph(id, { mustExist: false });
  let expectedSha256;
  try { expectedSha256 = (await repository.readGraph(file)).sha256; } catch (e) { if (e.code !== 'ENOENT') throw e; }
  console.log(JSON.stringify(await repository.writeGraph(file, graph, { expectedSha256 })));
}
for (const [id, kind, params] of [
  ['arc_generate', 'electric_bolt', { length: 16, width: 0.065, spread: 1.10, strands: 4, forks: 3, duration: 1.25, density: 1.45 }],
  ['thunder_lance', 'electric_bolt', { length: 32, width: 0.17, spread: 0.85, strands: 5, forks: 3, duration: 0.75, density: 1.45 }],
  ['railgun_charge', 'electric_orbit', { radius: 0.30, width: 0.014, rim_width: 0.0055, strength: 1, hint: 0, filaments: 4, filament_reach: 1.15, motion_speed: 1, motion_strength: 1 }],
  ['electromagnetic_shield', 'electric_shield', { radius: 0.85, height: 1.8, width: 0.035, duration: 0.25, phase: 0, impact: 0 }],
  ['magnetic_weapon', 'electric_paths', { width: 0.035, max_paths: 16, max_points: 128 }],
]) {
  await write(id, { version: 1, kind: 'vfx', id,
    parameters: Object.entries({ time: -1, seed: 42, opacity: 1, detail: 1, flicker_rate: 18, ...params }).map(([k, v]) => number(k, v)),
    contexts: [
      { id: 'discharge', type: 'SPAWN', name: ({
          electric_orbit: '01 / 加粗不规则电流环 · 局部白亮电流 · 外散分叉细丝',
          electric_shield: '01 / 身周游走电流 · 拦截面扩散电环',
          electric_paths: '01 / 刀刃路径输入 · 唤出电流 · 运动拖尾 · 命中放电',
        })[kind] ?? '01 / 蓝色外晕 · 白色细芯 · 空间折返', x: 0, y: 0,
        blocks: [block('current', kind, params)] },
      { id: 'render', type: 'OUTPUT', name: '02 / 与超电磁炮共用电弧材质', x: 650, y: 0,
        blocks: [output('electric_out', 'electricity')] },
    ], operators: [], flow: [{ from: 'discharge', to: 'render' }], blockFlows: [], dataEdges: [], outputs: ['electric_out'],
  });
}
{
  const { document: graph } = await repository.readGraph(await repository.resolveGraph('railgun_shot'));
  graph.parameters.find(p => p.id === 'length').default.value = 48;
  await write('railgun_shot', graph);
}
for (const id of ['sky_strike_thunderclap', 'sky_strike_storm']) {
  const { document: graph } = await repository.readGraph(await repository.resolveGraph(id));
  const outputs = graph.contexts.find(c => c.id === 'output');
  outputs.blocks.find(b => b.id === 'current_out').properties.layer = 'sky_current';
  outputs.blocks = outputs.blocks.filter(b => b.id !== 'attachment_out');
  outputs.blocks.push(output('attachment_out', 'electrical_attachment'));
  if (!graph.outputs.includes('attachment_out')) graph.outputs.push('attachment_out');
  graph.contexts.find(c => c.id === 'attachment').name = '04 / 蓝白双层表面电弧 · 贴合地形与云底';
  await write(id, graph);
}
