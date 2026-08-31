import assert from "node:assert/strict";
import { mkdtemp, mkdir, readFile, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import {
  VfxGraphRepository,
  applyJsonPatch,
  validateGraphDocument,
} from "./vfxgraph.mjs";

async function fixtureProject() {
  const root = await mkdtemp(path.join(os.tmpdir(), "academy-vfxgraph-mcp-"));
  const graphRoot = path.join(root, "src", "main", "resources", "assets", "academy", "vfxgraph");
  await mkdir(graphRoot, { recursive: true });
  await writeFile(path.join(root, "build.gradle.kts"), "// fixture\n");
  const graph = {
    version: 1,
    kind: "vfx",
    id: "fixture",
    parameters: [],
    contexts: [
      { id: "spawn", type: "SPAWN", x: 0, y: 0, blocks: [{ id: "spawn_rate", type: "vfx.block.spawn_rate", properties: { rate: "10" } }] },
      { id: "output", type: "OUTPUT", x: 400, y: 0, blocks: [{ id: "out", type: "vfx.block.output_quad", properties: {} }] },
    ],
    operators: [],
    flow: [{ from: "spawn", to: "output" }],
    blockFlows: [],
    dataEdges: [],
    outputs: ["out"],
  };
  await writeFile(path.join(graphRoot, "fixture.json"), `${JSON.stringify(graph, null, 2)}\n`);
  return { root, graph };
}

test("validates a minimal container graph", () => {
  const graph = {
    version: 1,
    kind: "vfx",
    id: "minimal",
    parameters: [],
    contexts: [
      { id: "spawn", type: "SPAWN", x: 0, y: 0, blocks: [] },
      { id: "out_ctx", type: "OUTPUT", x: 400, y: 0, blocks: [{ id: "out", type: "vfx.block.output_quad", properties: {} }] },
    ],
    operators: [],
    flow: [{ from: "spawn", to: "out_ctx" }],
    blockFlows: [],
    dataEdges: [],
    outputs: ["out"],
  };
  assert.equal(validateGraphDocument(graph).valid, true);
});

test("rejects a missing output reference and a flow cycle", () => {
  const graph = {
    version: 1,
    kind: "vfx",
    id: "bad",
    parameters: [],
    contexts: [
      { id: "spawn", type: "SPAWN", x: 0, y: 0, blocks: [] },
      { id: "out", type: "OUTPUT", x: 400, y: 0, blocks: [] },
    ],
    operators: [],
    flow: [{ from: "spawn", to: "out" }, { from: "out", to: "spawn" }],
    blockFlows: [],
    dataEdges: [],
    outputs: ["missing"],
  };
  const result = validateGraphDocument(graph);
  assert.equal(result.valid, false);
  assert.ok(result.issues.some((issue) => issue.code === "flow.cycle"));
  assert.ok(result.issues.some((issue) => issue.code === "outputs.missing"));
});

test("applies JSON Pointer patches", () => {
  const graph = { contexts: [{ blocks: [{ properties: { rate: "10" } }] }] };
  applyJsonPatch(graph, [
    { op: "test", path: "/contexts/0/blocks/0/properties/rate", value: "10" },
    { op: "replace", path: "/contexts/0/blocks/0/properties/rate", value: "24" },
    { op: "add", path: "/contexts/0/blocks/0/properties/lifetime", value: "1.5" },
  ]);
  assert.deepEqual(graph.contexts[0].blocks[0].properties, { rate: "24", lifetime: "1.5" });
});

test("updates a node atomically and creates a backup", async () => {
  const { root } = await fixtureProject();
  const repository = new VfxGraphRepository(root);
  const before = await repository.readGraph("fixture");
  const result = await repository.updateNode({
    graph: "fixture",
    node_id: "spawn_rate",
    properties: { rate: 25 },
    expected_sha256: before.sha256,
  });
  assert.ok(result.backup);
  assert.equal(result.validation.valid, true);
  const updated = JSON.parse(await readFile(result.graph, "utf8"));
  assert.equal(updated.contexts[0].blocks[0].properties.rate, "25");
  const backup = JSON.parse(await readFile(result.backup, "utf8"));
  assert.equal(backup.contexts[0].blocks[0].properties.rate, "10");
});

test("rejects stale writes", async () => {
  const { root } = await fixtureProject();
  const repository = new VfxGraphRepository(root);
  await assert.rejects(
    repository.updateNode({ graph: "fixture", node_id: "spawn_rate", properties: { rate: 30 }, expected_sha256: "stale" }),
    /changed since it was read/,
  );
});
