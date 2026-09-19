import { authorDarkmatter } from './author-darkmatter-vfx.mjs';
import { VfxGraphRepository } from './vfxgraph.mjs';
import fs from 'node:fs/promises';

const repository = new VfxGraphRepository(process.cwd());
const number = (id, value) => ({ id, name: id.replaceAll('_', ' '), type: 'FLOAT', default: { type: 'FLOAT', value } });
const block = (id, type, properties) => ({ id, type: `vfx.block.${type}`,
  properties: Object.fromEntries(Object.entries(properties).map(([k, v]) => [k, String(v)])) });
const arcOutput = (id, layer) => block(id, 'output_arc', {
  vertex: 'academy:core/vfxgraph_arc', shader: 'academy:core/vfxgraph_axial_burst', blend: 'glow', layer,
  thickness: 1, emission: 1.05, sparks: 0, segments: 6, overall_scale: 1,
  branch_depth: 0, branch_count: 0, noise_strength: 0, drift_speed: 0,
});
async function read(id) { return (await repository.readGraph(await repository.resolveGraph(id))).document; }
async function write(id, graph) {
  const file = await repository.resolveGraph(id, { mustExist: false });
  let expectedSha256;
  try { expectedSha256 = (await repository.readGraph(file)).sha256; } catch (e) { if (e.code !== 'ENOENT') throw e; }
  const result = await repository.writeGraph(file, graph, { expectedSha256 });
  console.log(id, JSON.stringify(result.validation));
}
function graph(id, params, spawn, outputs) {
  return { version: 1, kind: 'vfx', id, parameters: Object.entries({ time: -1, seed: 42, ...params }).map(([k,v]) => number(k,v)),
    contexts: [
      { id: 'spawn', type: 'SPAWN', name: '01 / Authored local geometry', x: 0, y: 0, blocks: spawn },
      { id: 'output', type: 'OUTPUT', name: '02 / Translucent white glow', x: 600, y: 0, blocks: outputs },
    ], operators: [], flow: [{from:'spawn',to:'output'}], blockFlows: [], dataEdges: [], outputs: outputs.map(b => b.id) };
}
await write('vector_blast', graph('vector_blast', { length:64, width:1, duration:0.65, view_first_person:0 }, [
  block('cloud_funnel','cloud_vortex',{ count:1024, length:64, width:1, duration:0.65,
    root_radius:0.35, head_radius:8.5, rotation_speed:17, flow_speed:14, turbulence:0.24, size:2,
    opacity:0.5, eye_ratio:0.38, first_person_opacity:0.22 }),
], [block('cloud_output','output_quad',{ vertex:'academy:core/vfxgraph_cloud_vortex',
  shader:'academy:core/vfxgraph_cloud_vortex', blend:'translucent', layer:'wind_volume', bounds_scale:1.56 })]));
await authorDarkmatter();
{
 const g=await read('magnetic_weapon'); g.id='magnetic_levitation_support';
 g.contexts[0].name='01 / Two leg anchors to authoritative support surface';
 const p=g.contexts[0].blocks[0].properties; p.width='0.024'; p.max_paths='2'; p.max_points='96';
 for (const [k,v] of Object.entries({width:0.024,max_paths:2,max_points:96})) g.parameters.find(p=>p.id===k).default.value=v;
 await write(g.id,g);
}
{
 const focus=await read('plasma_cannon_focus');
 focus.parameters.find(p=>p.id==='convergence_progress').default.value=1;
 focus.parameters.find(p=>p.id==='formation_progress').default.value=0.62;
 const f=Object.fromEntries(focus.contexts.flatMap(c=>c.blocks).map(b=>[b.id,b.properties]));
 f.spawn_converging_motes.formation_param='formation_progress';
 // Both sides of launch use precisely the same material and size interpolation.
 f.out_focus_core.shader='academy:core/vfxgraph_plasma_liquid';
 await write(focus.id,focus);
 const g=await read('plasma_cannon_projectile');
 if(!g.parameters.some(p=>p.id==='launch_scale')) g.parameters.push(number('launch_scale',0.62));
 const b=Object.fromEntries(g.contexts.flatMap(c=>c.blocks).map(b=>[b.id,b.properties]));
 for(const part of ['core','halo']) {
  const fp=f['follow_focus_'+part], p=b['follow_'+part];
  for(const key of ['size_min','size_max','size_power'])p[key]=fp[key];
  p.size_param='launch_scale';
  b['spawn_'+part].size=fp.size_min;
  b['spawn_'+part].color=f['spawn_focus_'+part].color;
 }
 Object.assign(b.spawn_surface_lightning, {progress_param:'launch_scale',emission_param:'launch_scale'});
 for(const key of ['radius','radius_min','radius_max','radius_power','surface_offset'])b.spawn_surface_lightning[key]=f.spawn_focus_surface_lightning[key];
 await write(g.id,g);
}
// Explicit motion presets make this author script idempotent. Lifetimes and curves are never touched.
const presets=JSON.parse(await fs.readFile(new URL('./ability-vfx-motion.json',import.meta.url),'utf8'));
for(const [id, overrides] of Object.entries(presets)){
 const g=await read(id);
 for(const b of g.contexts.flatMap(c=>c.blocks))if(overrides[b.id])Object.assign(b.properties,overrides[b.id]);
 await write(id,g);
}
