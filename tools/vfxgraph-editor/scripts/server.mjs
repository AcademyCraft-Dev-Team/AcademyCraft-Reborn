#!/usr/bin/env node
import process from "node:process";
import { VfxGraphRepository, findProjectRoot, graphNodeView, graphSummary } from "./vfxgraph.mjs";

const projectArg = process.argv.find((arg) => arg.startsWith("--project-root="))?.slice("--project-root=".length);
const projectRoot = findProjectRoot(projectArg || process.env.ACADEMY_PROJECT_ROOT || process.cwd());
const repository = new VfxGraphRepository(projectRoot);

const tools = [
  {
    name: "list_graphs",
    description: "List AcademyCraft VFXGraph assets with ids, locations, hashes, and node counts.",
    inputSchema: {
      type: "object",
      properties: { area: { type: "string", enum: ["all", "assets", "runtime"], default: "all" } },
      additionalProperties: false,
    },
  },
  {
    name: "get_graph",
    description: "Read a VFXGraph by resource id or path. Use nodes view before editing and keep its SHA for optimistic concurrency.",
    inputSchema: {
      type: "object",
      properties: {
        graph: { type: "string", description: "Resource id such as demo_fire, academy:demo_fire, or a path under a VFXGraph root." },
        view: { type: "string", enum: ["summary", "nodes", "full"], default: "nodes" },
      },
      required: ["graph"],
      additionalProperties: false,
    },
  },
  {
    name: "list_node_catalog",
    description: "List registered VFX node/block/operator types plus property keys and example values observed in current graph assets.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
  },
  {
    name: "validate_graph",
    description: "Validate graph schema, ids, context flow, block flow, data-edge references, outputs, and registry node types without writing.",
    inputSchema: {
      type: "object",
      properties: { graph: { type: "string" } },
      required: ["graph"],
      additionalProperties: false,
    },
  },
  {
    name: "update_node",
    description: "Atomically update one node's string properties by node id, validate, back up the old graph, and hot-reload the editor when connected. Null deletes a property.",
    inputSchema: {
      type: "object",
      properties: {
        graph: { type: "string" },
        node_id: { type: "string" },
        properties: { type: "object", additionalProperties: { type: ["string", "number", "boolean", "null"] } },
        replace_all: { type: "boolean", default: false },
        expected_sha256: { type: "string" },
        allow_invalid: { type: "boolean", default: false },
        reload_editor: { type: "boolean", default: true },
      },
      required: ["graph", "node_id", "properties"],
      additionalProperties: false,
    },
  },
  {
    name: "update_parameter",
    description: "Atomically update or create a graph parameter, including curve and gradient defaults, then validate, back up, and hot-reload.",
    inputSchema: {
      type: "object",
      properties: {
        graph: { type: "string" },
        parameter_id: { type: "string" },
        default: {},
        name: { type: "string" },
        type: { type: "string" },
        create_if_missing: { type: "boolean", default: false },
        expected_sha256: { type: "string" },
        allow_invalid: { type: "boolean", default: false },
        reload_editor: { type: "boolean", default: true },
      },
      required: ["graph", "parameter_id", "default"],
      additionalProperties: false,
    },
  },
  {
    name: "apply_json_patch",
    description: "Apply atomic RFC 6902-style add/replace/remove/test operations using JSON Pointer paths. Validates and backs up before commit.",
    inputSchema: {
      type: "object",
      properties: {
        graph: { type: "string" },
        operations: {
          type: "array",
          minItems: 1,
          items: {
            type: "object",
            properties: { op: { enum: ["add", "replace", "remove", "test"] }, path: { type: "string" }, value: {} },
            required: ["op", "path"],
            additionalProperties: false,
          },
        },
        expected_sha256: { type: "string" },
        allow_invalid: { type: "boolean", default: false },
        reload_editor: { type: "boolean", default: true },
      },
      required: ["graph", "operations"],
      additionalProperties: false,
    },
  },
  {
    name: "create_variant",
    description: "Create a validated VFXGraph variant from an existing graph in packaged assets or the runtime workspace.",
    inputSchema: {
      type: "object",
      properties: {
        source_graph: { type: "string" },
        target_name: { type: "string", description: "Lowercase resource path without .json." },
        target_area: { type: "string", enum: ["assets", "runtime"], default: "assets" },
        update_id: { type: "boolean", default: true },
        overwrite: { type: "boolean", default: false },
        allow_invalid: { type: "boolean", default: false },
        reload_editor: { type: "boolean", default: true },
      },
      required: ["source_graph", "target_name"],
      additionalProperties: false,
    },
  },
  {
    name: "editor_command",
    description: "Connect to the running VFXGraph editor to query status or open, reload, save, play, pause, reset, or step the live preview.",
    inputSchema: {
      type: "object",
      properties: {
        action: { type: "string", enum: ["status", "open", "reload", "save", "play", "pause", "reset", "step", "set_playback"] },
        graph: { type: "string", description: "Required for open; optional for reload." },
        playing: { type: "boolean" },
        loop: { type: "boolean" },
      },
      required: ["action"],
      additionalProperties: false,
    },
  },
];

async function callTool(name, args) {
  switch (name) {
    case "list_graphs":
      return { projectRoot, graphs: await repository.listGraphs(args.area || "all") };
    case "get_graph": {
      const record = await repository.readGraph(await repository.resolveGraph(args.graph));
      if (args.view === "full") return { path: record.file, sha256: record.sha256, graph: record.document };
      if (args.view === "summary") return graphSummary(record.document, record.file, record.sha256);
      return { path: record.file, sha256: record.sha256, graph: graphNodeView(record.document) };
    }
    case "list_node_catalog":
      return { projectRoot, nodes: await repository.catalog() };
    case "validate_graph": {
      const record = await repository.readGraph(await repository.resolveGraph(args.graph));
      return { path: record.file, sha256: record.sha256, ...(await repository.validate(record.document)) };
    }
    case "update_node": {
      const result = await repository.updateNode(args);
      return withEditorReload(result, args);
    }
    case "update_parameter": {
      const result = await repository.updateParameter(args);
      return withEditorReload(result, args);
    }
    case "apply_json_patch": {
      const graphPath = await repository.resolveGraph(args.graph);
      const result = await repository.applyPatch(args);
      return withEditorReload({ graph: graphPath, ...result }, args);
    }
    case "create_variant": {
      const result = await repository.createVariant(args);
      return withEditorReload(result, args);
    }
    case "editor_command": {
      const payload = {};
      if (args.graph) payload.path = await repository.resolveGraph(args.graph);
      if (args.playing !== undefined) payload.playing = args.playing;
      if (args.loop !== undefined) payload.loop = args.loop;
      if (args.action === "open" && !payload.path) throw new Error("graph is required for editor action open");
      return repository.editorCommand(args.action, payload);
    }
    default:
      throw new Error(`unknown tool: ${name}`);
  }
}

async function withEditorReload(result, args) {
  if (args.reload_editor === false) return result;
  const editor = await repository.editorCommand("reload", { path: result.graph });
  return { ...result, editor };
}

let input = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => {
  input += chunk;
  let newline;
  while ((newline = input.indexOf("\n")) >= 0) {
    const line = input.slice(0, newline).trim();
    input = input.slice(newline + 1);
    if (line) void handleLine(line);
  }
});
process.stdin.on("end", () => {
  const line = input.trim();
  if (line) void handleLine(line);
});

async function handleLine(line) {
  let request;
  try {
    request = JSON.parse(line);
  } catch (error) {
    send({ jsonrpc: "2.0", id: null, error: { code: -32700, message: error.message } });
    return;
  }
  if (!("id" in request)) return;
  try {
    let result;
    switch (request.method) {
      case "initialize":
        result = {
          protocolVersion: request.params?.protocolVersion || "2024-11-05",
          capabilities: { tools: { listChanged: false } },
          serverInfo: { name: "academy-vfxgraph-editor", version: "0.1.0" },
          instructions: "Read a graph and its SHA before edits. Prefer update_node/update_parameter; use apply_json_patch for structural changes. Writes are validated, backed up, and reloaded in the live editor when available.",
        };
        break;
      case "ping":
        result = {};
        break;
      case "tools/list":
        result = { tools };
        break;
      case "tools/call": {
        try {
          const output = await callTool(request.params?.name, request.params?.arguments || {});
          result = toolResult(output, false);
        } catch (error) {
          result = toolResult({ error: error.message }, true);
        }
        break;
      }
      default:
        throw Object.assign(new Error(`method not found: ${request.method}`), { code: -32601 });
    }
    send({ jsonrpc: "2.0", id: request.id, result });
  } catch (error) {
    send({ jsonrpc: "2.0", id: request.id, error: { code: error.code || -32603, message: error.message } });
  }
}

function toolResult(output, isError) {
  return {
    content: [{ type: "text", text: JSON.stringify(output, null, 2) }],
    structuredContent: output,
    ...(isError ? { isError: true } : {}),
  };
}

function send(message) {
  process.stdout.write(`${JSON.stringify(message)}\n`);
}

process.on("uncaughtException", (error) => console.error(`[vfxgraph-mcp] ${error.stack || error.message}`));
process.on("unhandledRejection", (error) => console.error(`[vfxgraph-mcp] ${error?.stack || error}`));
