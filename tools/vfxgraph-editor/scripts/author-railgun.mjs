import { VfxGraphRepository } from './vfxgraph.mjs';

// Run from the repository root. Uses the editor repository's validation, backup and reload path.
const repository = new VfxGraphRepository(process.cwd());
const duration = 1.6;
const number = (id, value) => ({ id, name: id.replaceAll('_', ' '), type: 'FLOAT', default: { type: 'FLOAT', value } });
const curve = (id, name, points) => ({
  id, name, type: 'CURVE', default: { type: 'CURVE', curve: points.map(([t, v]) => ({ t, v, i: 'SMOOTH', it: 0, ot: 0 })) },
});
const block = (id, type, properties) => ({ id, type: `vfx.block.${type}`,
  properties: Object.fromEntries(Object.entries(properties).map(([key, value]) => [key, String(value)])) });
const context = (id, name, y, blocks) => ({ id, name, type: 'SPAWN', x: 0, y, blocks });
const graph = {
  version: 1, kind: 'vfx', id: 'railgun_shot',
  parameters: [
    number('time', -1), number('seed', 42), number('length', 50), number('width_scale', 1), number('radius', 0.832),
    number('duration', duration), number('opacity', 1), number('detail', 1), number('muzzle', 1), number('view_near_origin', 0),
    // Keep the original ignition/expansion timing; add 10 ticks to the body hold and subsequent fade.
    curve('radius_envelope', '半径 / 小 → 大 → 中', [[0, 0.18], [0.077, 1], [0.242, 0.56], [1.325, 0.56], [duration, 0.45]].map(([t, v]) => [t / duration, v])),
    curve('beam_opacity', '炮束 / 点火与余辉', [[0, 0.7], [0.0275, 1], [1.105, 0.96], [1.358, 0.45], [duration, 0]].map(([t, v]) => [t / duration, v])),
    curve('arc_opacity', '电弧 / 爆发与消散', [[0, 0], [0.0495, 1], [0.264, 0.92], [1.05, 0.42], [1.292, 0], [duration, 0]].map(([t, v]) => [t / duration, v])),
  ],
  contexts: [
    context('beam', '01 / 橙色外焰 · 金色内焰 · 白热炮芯', 0, [
      block('outer_corona', 'axial_beam', { radius_scale: 1.95, intensity: 0.15, red: 1, green: 0.20, blue: 0.025 }),
      block('orange_stream', 'axial_beam', { radius_scale: 1, intensity: 0.60, red: 1, green: 0.34, blue: 0.055 }),
      block('gold_channel', 'axial_beam', { radius_scale: 0.56, intensity: 0.94, red: 1, green: 0.69, blue: 0.28 }),
      block('white_core', 'axial_beam', { radius_scale: 0.25, intensity: 1, red: 1, green: 0.98, blue: 0.85 }),
    ]),
    context('electricity', '02 / 蓝白电弧 · 空间折返与细分支', 600, [
      block('blue_discharge', 'axial_discharge', { spread: 2.8, arcs: 7, arc_width: 0.11, arc_reach: 25, flicker_rate: 18 }),
    ]),
    context('pressure', '03 / 破碎气浪 · 放射火星', 950, [
      block('pressure_front', 'axial_shock', { shock_radius: 3.8, rings: 3, sparks: 22 }),
    ]),
    { id: 'render', name: '04 / 柔边体积光与辉光', type: 'OUTPUT', x: 700, y: 180, blocks: [
      block('luminous_output', 'output_arc', {
        vertex: 'academy:core/vfxgraph_arc', shader: 'academy:core/vfxgraph_axial_burst', blend: 'glow',
        thickness: 1, emission: 1.15, sparks: 0, segments: 16, overall_scale: 1,
        branch_depth: 0, branch_count: 0, noise_strength: 0, drift_speed: 0, branch_brightness_scale: 1,
      }),
    ] },
  ],
  operators: [], flow: ['beam', 'electricity', 'pressure'].map(from => ({ from, to: 'render' })),
  blockFlows: [], dataEdges: [], outputs: ['luminous_output'],
};
const file = await repository.resolveGraph('railgun_shot', { mustExist: false });
let expectedSha256;
try { expectedSha256 = (await repository.readGraph(file)).sha256; } catch (error) { if (error.code !== 'ENOENT') throw error; }
console.log(JSON.stringify(await repository.writeGraph(file, graph, { expectedSha256 }), null, 2));
