import { VfxGraphRepository } from "./vfxgraph.mjs";

// Reproducible authoring through the editor's validated, backed-up asset API.
const repo = new VfxGraphRepository(process.cwd());
const param = (id, value) => ({ id, name: id, type: "FLOAT", default: { type: "FLOAT", value } });
const sand = {
  defense: { form: 0, radius: 2, count: 2800, grain_size: 0.085, band_width: 0.34, arc_count: 8 },
  guard: { form: 1, radius: 2, count: 3200, grain_size: 0.078, band_width: 0.23, height: 2.1, duration: 0.65, arc_count: 8, source_elevation: 0 },
  whip: { form: 2, radius: 12, count: 3000, grain_size: 0.14, band_width: 0.48, duration: 0.5, arc_count: 10, arc_width: 0.022 },
  cloud: { form: 3, radius: 16, count: 5600, grain_size: 0.28, band_width: 0.55, height: 4, speed: 1.9, arc_count: 18, arc_width: 0.028 },
};

async function author(name, properties) {
  const graph = `iron_sand_${name}`;
  let current;
  try { current = await repo.readGraph(graph); } catch {
    await repo.createVariant({ source_graph: "arc_generate", target_name: graph });
    current = await repo.readGraph(graph);
  }
  const arc = structuredClone((await repo.readGraph("arc_generate")).document.contexts[1].blocks[0]);
  const intercept = name === "intercept";
  const parameters = [param("time", -1), param("seed", 42), param("opacity", 1), param("detail", 1),
    ...Object.entries(properties).map(([id, value]) => param(id, value))];
  const contexts = [{ id: "motion", type: "SPAWN", name: intercept ? "01 / 复用电弧激发 · 由上向下切断" : "01 / 密集铁砂 · 连续砂流与内部电弧",
    x: 0, y: 0, blocks: [{ id: "sand_motion", type: intercept ? "vfx.block.electric_bolt" : "vfx.block.iron_sand",
      properties: Object.fromEntries(Object.entries(properties).map(([key, value]) => [key, String(value)])) }] },
  { id: "render", type: "OUTPUT", name: "02 / 暗色铁屑 · 电弧激发蓝白双层材质", x: 650, y: 0,
    blocks: intercept ? [arc] : [{ id: "sand_out", type: "vfx.block.output_quad", properties: {
      vertex: "academy:core/vfxgraph_particle", shader: "academy:core/vfxgraph_iron_sand",
      texture: "academy:textures/ability/electromaster/skill/iron_sand_arsenal/effect/iron_sand.png",
      blend: "translucent", layer: "iron_sand", bounds_scale: "1.5" } }, arc] }];
  const result = await repo.applyPatch({ graph, expected_sha256: current.sha256, operations: [
    { op: "test", path: "/id", value: graph },
    ...Object.entries({ parameters, contexts, operators: [], flow: [{ from: "motion", to: "render" }],
      blockFlows: [], dataEdges: [], outputs: intercept ? [arc.id] : ["sand_out", arc.id] })
      .map(([key, value]) => ({ op: "replace", path: `/${key}`, value })),
  ] });
  console.log(graph, result.validation);
}

if (process.argv.includes("--author")) {
  for (const [name, props] of Object.entries(sand)) await author(name, props);
  await author("intercept", { length: 3, width: 0.032, spread: 0.20, strands: 2, forks: 2,
    duration: 0.24, density: 1, downward: 1, origin_y: 1.8, growth_time: 0.055 });
}
if (process.argv.includes("--preview")) {
  for (const name of [...Object.keys(sand), "intercept"]) {
    const graph = `iron_sand_${name}`;
    await repo.editorCommand("open", { path: await repo.resolveGraph(graph) });
    await repo.editorCommand("set_playback", { playing: false, loop: false });
    await repo.editorCommand("reset");
    let state;
    for (let i = 0; i < 8; i++) state = await repo.editorCommand("step");
    if (!state.connected || state.previewError || !(state.particleCount > 0)) throw new Error(JSON.stringify(state));
    console.log(graph, { connected: state.connected, time: state.time, particleCount: state.particleCount, previewError: state.previewError ?? null });
  }
}
