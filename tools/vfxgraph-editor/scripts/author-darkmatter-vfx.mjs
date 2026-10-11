import { VfxGraphRepository } from './vfxgraph.mjs';

const repository = new VfxGraphRepository(process.cwd());
const block = (id, type, properties) => ({ id, type: `vfx.block.${type}`,
  properties: Object.fromEntries(Object.entries(properties).map(([k, v]) => [k, String(v)])) });

export const DARKMATTER_GRAPH_DEFAULTS = {
  time: -1, progress: 0.25, seed: 42, width: 0.6, height: 1.8, range: 12,
  light: 1, strength: 1, finished: 0, sheet_scale: 1, view_first_person: 0,
  alpha: 0.5, beta: 0.5, gamma: 0, density: 0.5, flexibility: 0.5, edge_activity: 0.5,
  shape: 0, spread: 1, attack: 0, lod: 1, radius: 4.4, arc: 2.2, reverse: 0,
  field_surface_strength: 0.16, field_air_strength: 0.10, field_edge_width: 0.22, field_scatter_count: 32,
};

const sheets = (id, style, count, size, opacity, shader, layer = 'smoke', surface = 'surface') =>
  ({ id, spawn: block(id, 'material_sheets', { style, count, size, opacity, surface, lighting: shader === 'contact' ? 0.2 : 1 }),
    output: block(`${id}_output`, 'output_quad', {
      vertex: shader === 'smoke' ? 'academy:core/vfxgraph_cloud_vortex' : shader === 'blade' ? 'academy:core/vfxgraph_feather_blade' : 'academy:core/vfxgraph_material_sheet',
      shader: `academy:core/vfxgraph_material_${shader}`,
      blend: 'translucent', layer, bounds_scale: shader === 'smoke' ? 1.56 : 1.21, cull_zero_alpha: true,
    }) });
const shape = (id, type, properties, shader, layer) =>
  ({ id, spawn: block(id, type, { ...properties, ...(type === 'material_fan'
    ? { radius_curve: 'fan_growth', width_curve: 'fan_width', opacity_curve: 'fan_fade' }
    : { opacity_curve: 'wing_opacity' }) }), output: block(`${id}_output`, 'output_material_surface', {
    vertex: `academy:core/${shader}`, shader: `academy:core/${shader}`,
    blend: 'translucent', layer, soft_depth: false, bounds_scale: 0, cull_zero_alpha: true,
  }) });
const laminae = (id, mode, patches, strands, fragments) =>
  ({ id, spawn: block(id, 'surface_laminae', {
    mode, patches, strands, fragments, opacity: .96, length_scale: 1, width_scale: 1, lift: .16, surface: 'surface',
  }), output: block(`${id}_output`, 'output_material_surface', {
    vertex: 'academy:core/vfxgraph_surface_lamina', shader: 'academy:core/vfxgraph_surface_lamina',
    blend: 'translucent', layer: 'lamina', soft_depth: true, bounds_scale: 0, cull_zero_alpha: true,
  }) });

const reconstructionDefaults = (repair) => ({
  time: -1, progress: .35, seed: 42, width: .6, height: 1.8, light: 1, strength: 1, finished: 0,
  alpha: .5, beta: .5, gamma: 0, density: .5, flexibility: .5, edge_activity: .5,
  lod: 1, surface_patches: repair ? 6 : 1,
  ...(repair ? Object.fromEntries(Array.from({ length: 6 }, (_, index) => [`repair_progress_${index}`, -1])) : {}),
});

export function darkmatterGraphs() {
  const definitions = [
    ['darkmatter_generation', [sheets('condensation', 2, 10, .055, .58, 'condense'), sheets('pearl_core', 2, 12, .13, .68, 'condense')]],
    ['darkmatter_elimination', [sheets('peeling', 3, 14, .11, .64, 'peel'), sheets('white_fragments', 6, 14, .035, .50, 'smoke', 'cloud')]],
    ['darkmatter_shaping', [sheets('growing_ribs', 2, 14, .095, .65, 'form'), sheets('settling_surface', 2, 12, .13, .66, 'form')]],
    ['darkmatter_phase_tuning', [sheets('phase_laminae', 2, 10, .10, .60, 'blade')]],
    ['darkmatter_feather', [sheets('blade', 5, 2, .72, .72, 'blade')]],
    ['darkmatter_creation_trail', [sheets('projectile_lamina', 5, 2, .30, .66, 'blade'), sheets('projectile_tail', 6, 6, .025, .35, 'smoke', 'cloud')]],
    ['darkmatter_interference', [
      sheets('media', 0, 8, .5, .14, 'medium', 'cloud'),
      sheets('altered_light', 1, 6, .5, .18, 'light', 'smoke', 'ground'),
    ]],
    ['darkmatter_light_contact', [sheets('contact', 1, 8, .24, .66, 'contact'), sheets('contact_fragments', 6, 8, .025, .44, 'smoke', 'cloud')]],
    ['darkmatter_repair', [laminae('reconstruction_front', 'repair', 6, 4, 0)]],
    ['darkmatter_disassemble', [laminae('separation_front', 'disassemble', 6, 8, 12)]],
    ['darkmatter_creation_summon', [sheets('bone_growth', 2, 16, .13, .64, 'growth'), sheets('shell_plates', 2, 10, .16, .66, 'blade')]],
    ['darkmatter_creation_dismiss', [sheets('shell_peeling', 3, 16, .13, .58, 'erosion'), sheets('bone_fragments', 6, 18, .045, .48, 'smoke', 'cloud')]],
    ['darkmatter_creation_contact', [sheets('jaw_contact', 1, 8, .18, .62, 'contact'), sheets('impact_laminae', 3, 6, .05, .48, 'blade')]],
    ['darkmatter_cut', [shape('matter_fan', 'material_fan', { radius: 4.4, arc: 2.2, width: .9, lifetime: .32, opacity: .82 }, 'vfxgraph_material_fan', 'material_fan')]],
    ['darkmatter_wing_strike', [shape('wing_fan', 'material_fan', { radius: 2.2, arc: 2.6, width: .55, lifetime: .32, opacity: .72 }, 'vfxgraph_material_fan', 'material_fan')]],
    ['darkmatter_six_wings', [shape('six_wings', 'feather_array', { pairs: 3, feathers: 14, coverts: 8, span: 3.6, root_height: 1.25, root_depth: -.20, spread: 1, flex: .10, opacity: .90, grounded: 1, minimum_height: .12, occlusion: 'occlusion', block_penetration: 0, surface_detail: 0 }, 'vfxgraph_feather_surface', 'feather')]],
  ];
  return definitions.map(([id, layers]) => {
    const reconstruction = id === 'darkmatter_disassemble' || id === 'darkmatter_repair';
    const defaults = reconstruction ? reconstructionDefaults(id === 'darkmatter_repair') : DARKMATTER_GRAPH_DEFAULTS;
    // Emitters that share a particle layer share one output, avoiding duplicate blending.
    const outputs = layers.filter((entry, index, all) =>
      all.findIndex(other => other.output.properties.layer === entry.output.properties.layer) === index).map(entry => entry.output);
    return {
      version: 1, kind: 'vfx', id,
      parameters: [
        ...Object.entries(defaults).map(([id, value]) => ({ id, name: id, type: 'FLOAT', default: { type: 'FLOAT', value } })),
        ...(id === 'darkmatter_six_wings' ? Object.entries({ grounded: 1, minimum_height: .12, transition_seconds: .30, block_penetration: 0,
          span: 3.6, root_height: 1.25, root_depth: -.20 })
          .map(([id, value]) => ({ id, name: id, type: 'FLOAT', default: { type: 'FLOAT', value } })) : []),
        ...(id === 'darkmatter_six_wings' || reconstruction ? Object.entries(reconstruction
          ? { surface_detail: 0 } : { surface_detail: 0, surface_detail_override: -1 })
          .map(([id, value]) => ({ id, name: id, type: 'INT', default: { type: 'INT', value } })) : []),
        ...[['camera', [0, 2, -8]], ['camera_forward', [0, 0, 1]], ['sun_direction', [.3, .9, .3]]].map(([id, value]) =>
          ({ id, name: id, type: 'VEC3', default: { type: 'VEC3', x: value[0], y: value[1], z: value[2] } })),
        ...Object.entries(reconstruction ? {} : { fan_growth: [[0, .8], [.2, 1], [1, 1]], fan_width: [[0, .7], [.35, 1], [1, .8]],
          fan_fade: [[0, 0], [.12, 1], [.65, .8], [1, 0]], wing_opacity: [[0, 1], [1, 1]] })
          .map(([id, keys]) => ({ id, name: id, type: 'CURVE', default: { type: 'CURVE',
            curve: keys.map(([t, v]) => ({ t, v, it: 0, ot: 0, i: 'SMOOTH' })) } })),
      ],
      contexts: [
        { id: 'spawn', type: 'SPAWN', name: reconstruction ? '01 / Continuous surface reconstruction' : '01 / Pearl material motion and shape', x: 0, y: 0, blocks: layers.map(layer => layer.spawn) },
        { id: 'output', type: 'OUTPUT', name: reconstruction ? '02 / Depth-tested solid white laminae' : '02 / Depth-tested white layers', x: 620, y: 0, blocks: outputs },
      ],
      operators: [], flow: [{ from: 'spawn', to: 'output' }], blockFlows: [], dataEdges: [], outputs: outputs.map(b => b.id),
    };
  });
}

export async function authorDarkmatter() {
  for (const graph of darkmatterGraphs()) {
    const file = await repository.resolveGraph(graph.id, { mustExist: false });
    let expectedSha256;
    try { expectedSha256 = (await repository.readGraph(file)).sha256; } catch (e) { if (e.code !== 'ENOENT') throw e; }
    const result = await repository.writeGraph(file, graph, { expectedSha256 });
    console.log(graph.id, JSON.stringify(result.validation));
  }
}
if (process.argv[1]?.replaceAll('\\', '/').endsWith('/author-darkmatter-vfx.mjs')) await authorDarkmatter();
