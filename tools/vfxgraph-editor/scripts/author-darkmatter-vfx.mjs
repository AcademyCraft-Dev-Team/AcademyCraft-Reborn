import { VfxGraphRepository } from './vfxgraph.mjs';

const repository = new VfxGraphRepository(process.cwd());
const block = (id, type, properties) => ({ id, type: `vfx.block.${type}`,
  properties: Object.fromEntries(Object.entries(properties).map(([k,v]) => [k,String(v)])) });

export async function authorDarkmatter() {
  for (const [id, emitters] of [
    ['darkmatter_feather', [['blade', 5, 2, 0.72, 0.75, 'surface', 'blade', 'smoke']]],
    ['darkmatter_interference', [
      ['media', 0, 8, 0.5, 0.14, 'surface', 'medium', 'cloud'],
      ['altered_light', 1, 6, 0.5, 0.3, 'ground', 'light', 'smoke'],
    ]],
    ['darkmatter_light_contact', [
      ['contact', 1, 12, 0.30, 0.75, 'surface', 'contact', 'smoke'],
      ['contact_fragments', 6, 12, 0.035, 0.6, 'surface', 'smoke', 'cloud'],
    ]],
    ['darkmatter_repair', [['growth', 2, 6, 0.15, 0.64, 'surface', 'growth', 'smoke']]],
    ['darkmatter_disassemble', [
      ['erosion', 3, 18, 0.1, 0.48, 'surface', 'erosion', 'smoke'],
      ['dissolving_smoke', 4, 14, 0.26, 0.5, 'surface', 'smoke', 'cloud'],
      ['fine_fragments', 6, 28, 0.035, 0.5, 'surface', 'smoke', 'cloud'],
    ]],
  ]) {
    const outputs = emitters.filter((entry,index,all) => all.findIndex(other => other[7]===entry[7])===index).map(([name,,,,,,shader,layer]) => block(`${name}_output`, 'output_quad', {
      vertex:shader==='smoke'?'academy:core/vfxgraph_cloud_vortex':shader==='blade'?'academy:core/vfxgraph_feather_blade':'academy:core/vfxgraph_material_sheet', shader:`academy:core/vfxgraph_material_${shader}`,
      blend:shader==='contact'?'glow':'translucent', layer, bounds_scale:shader==='smoke'?1.56:1.21,
    }));
    const graph = { version:1, kind:'vfx', id,
      parameters:Object.entries({time:-1,seed:42,width:0.6,height:1.8,range:12,light:1,strength:1,finished:0,view_first_person:0})
        .map(([id,value]) => ({id,name:id,type:'FLOAT',default:{type:'FLOAT',value}})),
      contexts:[
        {id:'spawn',type:'SPAWN',name:'01 / Bounded sampled material',x:0,y:0,blocks:emitters.map(([name,style,count,size,opacity,surface]) =>
          block(name,'material_sheets',{style,count,size,opacity,surface,lighting:name==='contact'?0.2:1}))},
        {id:'output',type:'OUTPUT',name:'02 / Depth-tested pearl material',x:620,y:0,blocks:outputs},
      ], operators:[],flow:[{from:'spawn',to:'output'}],blockFlows:[],dataEdges:[],outputs:outputs.map(b=>b.id),
    };
    const file = await repository.resolveGraph(id,{mustExist:false});
    let expectedSha256;
    try { expectedSha256=(await repository.readGraph(file)).sha256; } catch(e) { if(e.code!=='ENOENT')throw e; }
    const result=await repository.writeGraph(file,graph,{expectedSha256});
    console.log(id,JSON.stringify(result.validation));
  }
}
if (process.argv[1]?.replaceAll('\\','/').endsWith('/author-darkmatter-vfx.mjs')) await authorDarkmatter();
