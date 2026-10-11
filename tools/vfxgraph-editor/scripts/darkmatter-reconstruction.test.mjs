import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { darkmatterGraphs } from './author-darkmatter-vfx.mjs';
import { VfxGraphRepository } from './vfxgraph.mjs';

const projectRoot = fileURLToPath(new URL('../../../', import.meta.url));
const ids = ['darkmatter_disassemble', 'darkmatter_repair'];

test('reconstruction authoring reproduces both packaged graphs', async () => {
  const repository = new VfxGraphRepository(projectRoot);
  for (const id of ids) {
    const authored = darkmatterGraphs().find(graph => graph.id === id);
    const packaged = (await repository.readGraph(id)).document;
    assert.deepEqual(authored, packaged, `${id} diverged from its editor authoring source`);
    const validation = await repository.validate(packaged);
    assert.equal(validation.valid, true);
    assert.equal(validation.warningCount, 0, JSON.stringify(validation.issues));
  }
});

test('editor saves lamina settings and per-part progress without restoring feather layers', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'academy-reconstruction-'));
  const assets = path.join(root, 'src/main/resources/assets/academy/vfxgraph');
  await mkdir(assets, { recursive: true });
  await writeFile(path.join(root, 'build.gradle.kts'), '// fixture\n');
  const repository = new VfxGraphRepository(root);
  for (const id of ids) {
    const graph = darkmatterGraphs().find(candidate => candidate.id === id);
    const file = path.join(assets, `${id}.json`);
    await repository.writeGraph(file, graph);
    await repository.updateNode({ graph: id, node_id: graph.contexts[0].blocks[0].id,
      properties: { lift: .12, width_scale: .85 } });
    await repository.updateParameter({ graph: id, parameter_id: 'surface_detail', default: { type: 'INT', value: 1 } });
    if (id === 'darkmatter_repair') {
      await repository.updateParameter({ graph: id, parameter_id: 'repair_progress_2', default: { type: 'FLOAT', value: .68 } });
    }
    const saved = JSON.parse(await readFile(file, 'utf8'));
    assert.equal(saved.contexts[0].blocks[0].properties.mode, id.slice('darkmatter_'.length));
    assert.equal(saved.contexts[0].blocks[0].properties.lift, '0.12');
    assert.equal(saved.contexts[0].blocks[0].properties.width_scale, '0.85');
    assert.equal(saved.parameters.find(parameter => parameter.id === 'surface_detail').default.type, 'INT');
    assert.equal(saved.parameters.find(parameter => parameter.id === 'surface_detail').default.value, 1);
    assert.equal(saved.outputs.length, 1);
    assert.ok(!JSON.stringify(saved).includes('material_sheets'));
    if (id === 'darkmatter_repair') {
      assert.equal(saved.parameters.find(parameter => parameter.id === 'repair_progress_2').default.value, .68);
      assert.equal(saved.parameters.find(parameter => parameter.id === 'repair_progress_3').default.value, -1);
    }
  }
});
