import fs from 'node:fs/promises';
import { execFileSync } from 'node:child_process';

// Read-only Git reference; outputs remain in build/. Pass the pre-update commit after committing this work.
const ref = process.argv[2] ?? 'HEAD';
const directory = 'src/main/resources/assets/academy/vfxgraph';
await fs.mkdir('build/ability-vfx-baseline', {recursive:true});
for (const name of ['blade','burst','field','ring','stream','vortex']) {
  const id = `aeromanip_mist_${name}`;
  const before = execFileSync('git',['show',`${ref}:${directory}/${id}.json`],{encoding:'utf8'});
  const old = JSON.parse(before), current = JSON.parse(await fs.readFile(`${directory}/${id}.json`,'utf8'));
  const temporal = g => ({parameters:g.parameters, nodes:g.contexts.flatMap(c=>c.blocks).map(b=>({
    id:b.id, lifetime:b.properties.lifetime,
    curves:/life_alpha|life_size/.test(b.type)?b.properties:undefined,
  }))});
  if(JSON.stringify(temporal(old))!==JSON.stringify(temporal(current)))throw Error(`Lifetime/curve drift: ${id}`);
  await fs.writeFile(`build/ability-vfx-baseline/${id}.json`,before);
  console.log(`${id}: lifetime and alpha/size curves unchanged`);
}
