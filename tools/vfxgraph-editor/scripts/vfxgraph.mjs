import { createHash, randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import {
  copyFile,
  mkdir,
  readFile,
  readdir,
  rename,
  rm,
  stat,
  writeFile,
} from "node:fs/promises";
import path from "node:path";
import { isDeepStrictEqual } from "node:util";

const GRAPH_SUFFIX = ".json";
const EDITOR_SUFFIX = ".editor.json";
const CONTEXT_TYPES = new Set(["SPAWN", "INITIALIZE", "UPDATE", "OUTPUT"]);

export function findProjectRoot(start) {
  let current = path.resolve(start);
  while (true) {
    if (
      existsSync(path.join(current, "build.gradle.kts")) &&
      existsSync(path.join(current, "src", "main", "resources", "assets", "academy", "vfxgraph"))
    ) {
      return current;
    }
    const parent = path.dirname(current);
    if (parent === current) break;
    current = parent;
  }
  throw new Error(`AcademyCraft project root not found from: ${start}`);
}

export function sha256(text) {
  return createHash("sha256").update(text).digest("hex");
}

export class VfxGraphRepository {
  constructor(projectRoot) {
    this.projectRoot = findProjectRoot(projectRoot);
    this.roots = {
      assets: path.join(this.projectRoot, "src", "main", "resources", "assets", "academy", "vfxgraph"),
      runtime: path.join(this.projectRoot, "run", "academy", "graphs"),
    };
    this.bridgeRoot = path.join(this.projectRoot, "run", "academy", "vfxgraph-mcp", "bridge");
    this.backupRoot = path.join(this.projectRoot, "run", "academy", "vfxgraph-mcp", "backups");
  }

  async listGraphs(area = "all") {
    const selected = area === "all" ? Object.entries(this.roots) : [[area, this.requireArea(area)]];
    const graphs = [];
    for (const [rootName, root] of selected) {
      for (const file of await walkGraphFiles(root)) {
        try {
          const record = await this.readGraph(file);
          graphs.push({ area: rootName, ...graphSummary(record.document, file, record.sha256) });
        } catch (error) {
          graphs.push({
            area: rootName,
            name: path.basename(file, GRAPH_SUFFIX),
            path: file,
            error: error.message,
          });
        }
      }
    }
    return graphs.sort((left, right) => left.name.localeCompare(right.name));
  }

  async resolveGraph(identifier, { mustExist = true, area = "assets" } = {}) {
    if (typeof identifier !== "string" || identifier.trim() === "") {
      throw new Error("graph must be a non-empty id or path");
    }
    const raw = identifier.trim();
    const candidates = [];
    if (path.isAbsolute(raw)) {
      candidates.push(path.resolve(raw));
    } else if (/^(src|run)[\\/]/i.test(raw)) {
      candidates.push(path.resolve(this.projectRoot, raw));
    } else {
      let name = raw.replace(/^academy:/, "").replace(/^vfxgraph[\\/]/, "");
      if (!name.endsWith(GRAPH_SUFFIX)) name += GRAPH_SUFFIX;
      if (mustExist) {
        candidates.push(path.resolve(this.roots.assets, name), path.resolve(this.roots.runtime, name));
      } else {
        candidates.push(path.resolve(this.requireArea(area), name));
      }
    }

    for (const candidate of candidates) {
      this.assertGraphPath(candidate);
      if (!mustExist || existsSync(candidate)) return candidate;
    }
    throw new Error(`VFXGraph not found: ${identifier}`);
  }

  assertGraphPath(candidate) {
    const resolved = path.resolve(candidate);
    if (!Object.values(this.roots).some((root) => isWithin(root, resolved))) {
      throw new Error(`path is outside VFXGraph roots: ${resolved}`);
    }
    const name = path.basename(resolved);
    if (!name.endsWith(GRAPH_SUFFIX) || name.endsWith(EDITOR_SUFFIX)) {
      throw new Error(`not a VFXGraph JSON file: ${resolved}`);
    }
  }

  requireArea(area) {
    if (!(area in this.roots)) throw new Error(`area must be one of: ${Object.keys(this.roots).join(", ")}`);
    return this.roots[area];
  }

  async readGraph(identifier) {
    const file = path.isAbsolute(identifier) ? path.resolve(identifier) : await this.resolveGraph(identifier);
    this.assertGraphPath(file);
    const raw = await readFile(file, "utf8");
    let document;
    try {
      document = JSON.parse(raw);
    } catch (error) {
      throw new Error(`invalid JSON in ${file}: ${error.message}`);
    }
    return { file, raw, document, sha256: sha256(raw) };
  }

  async validate(identifierOrDocument) {
    const document = typeof identifierOrDocument === "string"
      ? (await this.readGraph(identifierOrDocument)).document
      : identifierOrDocument;
    const knownTypes = await this.knownNodeTypes();
    return validateGraphDocument(document, knownTypes);
  }

  async writeGraph(file, document, options = {}) {
    this.assertGraphPath(file);
    const current = existsSync(file) ? await this.readGraph(file) : null;
    if (options.expectedSha256 && current?.sha256 !== options.expectedSha256) {
      throw new Error(`graph changed since it was read (expected ${options.expectedSha256}, found ${current?.sha256})`);
    }
    const validation = await this.validate(document);
    const errors = validation.issues.filter((issue) => issue.severity === "error");
    if (errors.length && !options.allowInvalid) {
      throw new Error(`write rejected: ${errors.length} validation error(s): ${errors.slice(0, 3).map((i) => i.message).join("; ")}`);
    }

    let backup = null;
    if (current) {
      const area = this.areaFor(file);
      const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
      backup = path.join(this.backupRoot, timestamp, area, path.relative(this.roots[area], file));
      await mkdir(path.dirname(backup), { recursive: true });
      await copyFile(file, backup);
    }
    const serialized = `${JSON.stringify(document, null, 2)}\n`;
    await atomicWrite(file, serialized);
    return {
      graph: file,
      changed: current?.raw !== serialized,
      sha256Before: current?.sha256 ?? null,
      sha256After: sha256(serialized),
      backup,
      validation,
    };
  }

  areaFor(file) {
    for (const [area, root] of Object.entries(this.roots)) {
      if (isWithin(root, file)) return area;
    }
    throw new Error(`path is outside VFXGraph roots: ${file}`);
  }

  async updateNode(args) {
    const record = await this.readGraph(await this.resolveGraph(args.graph));
    const matches = findNodes(record.document).filter((entry) => entry.node.id === args.node_id);
    if (matches.length !== 1) {
      throw new Error(matches.length ? `duplicate node id: ${args.node_id}` : `node not found: ${args.node_id}`);
    }
    const target = matches[0].node;
    if (!args.properties || typeof args.properties !== "object" || Array.isArray(args.properties)) {
      throw new Error("properties must be an object");
    }
    if (args.replace_all) target.properties = {};
    if (!target.properties || typeof target.properties !== "object" || Array.isArray(target.properties)) {
      target.properties = {};
    }
    for (const [key, value] of Object.entries(args.properties)) {
      if (value === null) delete target.properties[key];
      else if (["string", "number", "boolean"].includes(typeof value)) target.properties[key] = String(value);
      else throw new Error(`node property ${key} must be a string, number, boolean, or null`);
    }
    const result = await this.writeGraph(record.file, record.document, writeOptions(args));
    return { ...result, node: target, location: matches[0].location };
  }

  async updateParameter(args) {
    const record = await this.readGraph(await this.resolveGraph(args.graph));
    if (!Array.isArray(record.document.parameters)) record.document.parameters = [];
    let parameter = record.document.parameters.find((item) => item?.id === args.parameter_id);
    if (!parameter) {
      if (!args.create_if_missing) throw new Error(`parameter not found: ${args.parameter_id}`);
      if (!args.type) throw new Error("type is required when create_if_missing is true");
      parameter = {
        id: args.parameter_id,
        name: args.name || args.parameter_id,
        type: args.type,
        default: args.default,
      };
      record.document.parameters.push(parameter);
    } else {
      if (!("default" in args)) throw new Error("default is required");
      parameter.default = args.default;
      if (args.name !== undefined) parameter.name = args.name;
      if (args.type !== undefined) parameter.type = args.type;
    }
    const result = await this.writeGraph(record.file, record.document, writeOptions(args));
    return { ...result, parameter };
  }

  async applyPatch(args) {
    const record = await this.readGraph(await this.resolveGraph(args.graph));
    if (!Array.isArray(args.operations) || args.operations.length === 0) {
      throw new Error("operations must be a non-empty array");
    }
    const patched = applyJsonPatch(structuredClone(record.document), args.operations);
    return this.writeGraph(record.file, patched, writeOptions(args));
  }

  async createVariant(args) {
    const record = await this.readGraph(await this.resolveGraph(args.source_graph));
    const targetName = normalizeResourceName(args.target_name);
    const target = await this.resolveGraph(targetName, { mustExist: false, area: args.target_area || "assets" });
    if (existsSync(target) && !args.overwrite) throw new Error(`target already exists: ${target}`);
    const document = structuredClone(record.document);
    if (args.update_id !== false) document.id = targetName;
    return this.writeGraph(target, document, {
      expectedSha256: args.expected_sha256,
      allowInvalid: args.allow_invalid,
    });
  }

  async catalog() {
    const knownTypes = await this.knownNodeTypes();
    const observed = new Map();
    for (const graph of await this.listGraphs("all")) {
      if (graph.error) continue;
      const record = await this.readGraph(graph.path);
      for (const { node, location } of findNodes(record.document)) {
        const entry = observed.get(node.type) || { properties: new Map(), locations: new Set(), graphs: new Set() };
        entry.locations.add(location);
        entry.graphs.add(graph.name);
        for (const [key, value] of Object.entries(node.properties || {})) {
          if (!entry.properties.has(key)) entry.properties.set(key, value);
        }
        observed.set(node.type, entry);
      }
    }
    return [...new Set([...knownTypes, ...observed.keys()])].sort().map((type) => {
      const seen = observed.get(type);
      return {
        type,
        observedProperties: seen ? Object.fromEntries(seen.properties) : {},
        observedLocations: seen ? [...seen.locations].sort() : [],
        exampleGraphs: seen ? [...seen.graphs].sort().slice(0, 8) : [],
      };
    });
  }

  async knownNodeTypes() {
    const files = [
      path.join(this.projectRoot, "src", "main", "java", "org", "academy", "api", "client", "render", "vfxgraph", "nodes", "VfxBlocks.java"),
      path.join(this.projectRoot, "src", "main", "java", "org", "academy", "api", "client", "render", "vfxgraph", "nodes", "VfxNodes.java"),
      path.join(this.projectRoot, "src", "main", "java", "org", "academy", "api", "client", "render", "vfxgraph", "operator", "VfxOperators.java"),
    ];
    const types = new Set();
    for (const file of files) {
      if (!existsSync(file)) continue;
      const source = await readFile(file, "utf8");
      for (const match of source.matchAll(/"(vfx\.(?:block\.|op\.|)[a-z0-9_.-]+)"/g)) {
        if (!match[1].endsWith("_")) types.add(match[1]);
      }
    }
    return types;
  }

  async editorStatus() {
    const statusFile = path.join(this.bridgeRoot, "status.json");
    if (!existsSync(statusFile)) return { connected: false, reason: "editor status file not found" };
    try {
      const [raw, info] = await Promise.all([readFile(statusFile, "utf8"), stat(statusFile)]);
      const statusDocument = JSON.parse(raw);
      const ageMs = Date.now() - info.mtimeMs;
      return {
        connected: statusDocument.running === true && ageMs < 5000,
        heartbeatAgeMs: Math.max(0, Math.round(ageMs)),
        ...statusDocument,
      };
    } catch (error) {
      return { connected: false, reason: error.message };
    }
  }

  async editorCommand(action, payload = {}, timeoutMs = 3500) {
    if (action === "status") return this.editorStatus();
    const statusDocument = await this.editorStatus();
    if (!statusDocument.connected) return statusDocument;
    const id = randomUUID();
    const inbox = path.join(this.bridgeRoot, "inbox");
    const outbox = path.join(this.bridgeRoot, "outbox");
    await mkdir(inbox, { recursive: true });
    await mkdir(outbox, { recursive: true });
    await atomicWrite(path.join(inbox, `${id}.json`), `${JSON.stringify({ id, action, ...payload }, null, 2)}\n`);
    const responseFile = path.join(outbox, `${id}.json`);
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      if (existsSync(responseFile)) {
        const response = JSON.parse(await readFile(responseFile, "utf8"));
        await rm(responseFile, { force: true });
        if (!response.ok) throw new Error(`editor command failed: ${response.error}`);
        return { connected: true, ...response.result };
      }
      await delay(40);
    }
    throw new Error(`editor command timed out after ${timeoutMs}ms`);
  }
}

export function graphSummary(document, file = null, hash = null) {
  const contexts = Array.isArray(document?.contexts) ? document.contexts : [];
  const operators = Array.isArray(document?.operators) ? document.operators : [];
  const blockCount = contexts.reduce((sum, context) => sum + (Array.isArray(context?.blocks) ? context.blocks.length : 0), 0);
  return {
    name: file ? path.basename(file, GRAPH_SUFFIX) : document?.id,
    id: document?.id ?? null,
    path: file,
    sha256: hash,
    version: document?.version ?? null,
    kind: document?.kind ?? null,
    parameterCount: Array.isArray(document?.parameters) ? document.parameters.length : 0,
    contextCount: contexts.length,
    contextTypes: contexts.map((context) => context?.type).filter(Boolean),
    blockCount,
    operatorCount: operators.length,
    outputCount: Array.isArray(document?.outputs) ? document.outputs.length : 0,
  };
}

export function graphNodeView(document) {
  return {
    ...graphSummary(document),
    parameters: document.parameters || [],
    contexts: (document.contexts || []).map((context) => ({
      id: context.id,
      type: context.type,
      name: context.name,
      blocks: (context.blocks || []).map((block) => ({
        id: block.id,
        type: block.type,
        properties: block.properties || {},
      })),
    })),
    operators: (document.operators || []).map((operator) => ({
      id: operator.id,
      type: operator.type,
      properties: operator.properties || {},
    })),
    flow: document.flow || [],
    blockFlows: document.blockFlows || [],
    dataEdges: document.dataEdges || [],
    outputs: document.outputs || [],
  };
}

export function findNodes(document) {
  const result = [];
  for (const context of Array.isArray(document?.contexts) ? document.contexts : []) {
    for (const block of Array.isArray(context?.blocks) ? context.blocks : []) {
      result.push({ node: block, location: `context:${context.id}` });
    }
  }
  for (const operator of Array.isArray(document?.operators) ? document.operators : []) {
    result.push({ node: operator, location: "operators" });
  }
  return result;
}

export function validateGraphDocument(document, knownTypes = new Set()) {
  const issues = [];
  const issue = (severity, code, message, jsonPath = "") => issues.push({ severity, code, message, path: jsonPath });
  if (!document || typeof document !== "object" || Array.isArray(document)) {
    issue("error", "document.type", "graph root must be an object");
    return validationResult(issues);
  }
  if (document.version !== 1) issue("error", "version.unsupported", "version must be 1", "/version");
  if (document.kind !== "vfx") issue("error", "kind.invalid", 'kind must be "vfx"', "/kind");
  if (typeof document.id !== "string" || document.id.trim() === "") issue("error", "id.missing", "id must be a non-empty string", "/id");

  const contexts = requireArray(document, "contexts", issues);
  const operators = optionalArray(document, "operators", issues);
  const flow = optionalArray(document, "flow", issues);
  const blockFlows = optionalArray(document, "blockFlows", issues);
  const dataEdges = optionalArray(document, "dataEdges", issues);
  const outputs = requireArray(document, "outputs", issues);
  const parameters = requireArray(document, "parameters", issues);

  const contextById = new Map();
  const nodeById = new Map();
  const nodeContextType = new Map();
  const parameterIds = new Set();
  parameters.forEach((parameter, index) => {
    const id = parameter?.id;
    if (typeof id !== "string" || !id) issue("error", "parameter.id", "parameter id is required", `/parameters/${index}/id`);
    else if (parameterIds.has(id)) issue("error", "parameter.duplicate", `duplicate parameter id: ${id}`, `/parameters/${index}/id`);
    else parameterIds.add(id);
    if (typeof parameter?.name !== "string") issue("error", "parameter.name", "parameter name is required", `/parameters/${index}/name`);
    if (typeof parameter?.type !== "string" || !parameter.type) issue("error", "parameter.type", "parameter type is required", `/parameters/${index}/type`);
    if (!parameter?.default || typeof parameter.default !== "object" || Array.isArray(parameter.default)) {
      issue("error", "parameter.default", "parameter default must be an encoded value object", `/parameters/${index}/default`);
    }
  });

  contexts.forEach((context, contextIndex) => {
    const contextPath = `/contexts/${contextIndex}`;
    if (!context || typeof context !== "object") {
      issue("error", "context.type", "context must be an object", contextPath);
      return;
    }
    const id = context.id;
    if (typeof id !== "string" || !id) issue("error", "context.id", "context id is required", `${contextPath}/id`);
    else if (contextById.has(id)) issue("error", "context.duplicate", `duplicate context id: ${id}`, `${contextPath}/id`);
    else contextById.set(id, context);
    if (!CONTEXT_TYPES.has(context.type)) issue("error", "context.kind", `unknown context type: ${context.type}`, `${contextPath}/type`);
    if (typeof context.x !== "number" || typeof context.y !== "number") issue("error", "context.position", "context x and y must be numbers", contextPath);
    const blocks = Array.isArray(context.blocks) ? context.blocks : [];
    if (!Array.isArray(context.blocks)) issue("error", "blocks.type", "context blocks must be an array", `${contextPath}/blocks`);
    blocks.forEach((block, blockIndex) => registerNode(block, `${contextPath}/blocks/${blockIndex}`, context.type));
  });
  operators.forEach((operator, index) => registerNode(operator, `/operators/${index}`, null));

  function registerNode(node, nodePath, contextType) {
    if (!node || typeof node !== "object") {
      issue("error", "node.type", "node must be an object", nodePath);
      return;
    }
    if (typeof node.id !== "string" || !node.id) issue("error", "node.id", "node id is required", `${nodePath}/id`);
    else if (nodeById.has(node.id)) issue("error", "node.duplicate", `duplicate node id: ${node.id}`, `${nodePath}/id`);
    else {
      nodeById.set(node.id, node);
      if (contextType) nodeContextType.set(node.id, contextType);
    }
    if (typeof node.type !== "string" || !node.type) issue("error", "node.kind", "node type is required", `${nodePath}/type`);
    else if (knownTypes.size && !knownTypes.has(node.type) && !node.type.startsWith("vfx.op.attr_")) {
      issue("warning", "node.unknown", `node type was not found in the current registry source: ${node.type}`, `${nodePath}/type`);
    }
    if (!node.properties || typeof node.properties !== "object" || Array.isArray(node.properties)) {
      issue("error", "properties.type", "node properties must be an object", `${nodePath}/properties`);
    }
    if (!contextType && (typeof node.x !== "number" || typeof node.y !== "number")) {
      issue("error", "operator.position", "operator x and y must be numbers", nodePath);
    }
  }

  if (![...contextById.values()].some((context) => context.type === "SPAWN")) issue("error", "flow.no_spawn", "vfx system has no SPAWN context", "/contexts");
  if (![...contextById.values()].some((context) => context.type === "OUTPUT")) issue("error", "flow.no_output_context", "vfx system has no OUTPUT context", "/contexts");
  const adjacency = new Map();
  const inDegree = new Set();
  flow.forEach((edge, index) => {
    if (!contextById.has(edge?.from)) issue("error", "flow.from_missing", `flow from missing context: ${edge?.from}`, `/flow/${index}/from`);
    if (!contextById.has(edge?.to)) issue("error", "flow.to_missing", `flow to missing context: ${edge?.to}`, `/flow/${index}/to`);
    if (contextById.has(edge?.from) && contextById.has(edge?.to)) {
      if (!adjacency.has(edge.from)) adjacency.set(edge.from, []);
      adjacency.get(edge.from).push(edge.to);
      inDegree.add(edge.to);
    }
  });
  for (const [id, context] of contextById) {
    if (context.type !== "SPAWN" && !inDegree.has(id)) issue("error", "flow.no_upstream", `context has no upstream flow: ${id}`, "/flow");
  }
  detectCycles(contextById.keys(), adjacency, (id) => issue("error", "flow.cycle", `flow cycle detected involving context: ${id}`, "/flow"));

  blockFlows.forEach((edge, index) => {
    if (!nodeById.has(edge?.from)) issue("error", "block_flow.from_missing", `block flow from missing block: ${edge?.from}`, `/blockFlows/${index}/from`);
    if (!nodeById.has(edge?.to)) issue("error", "block_flow.to_missing", `block flow to missing block: ${edge?.to}`, `/blockFlows/${index}/to`);
    if (nodeById.has(edge?.from) && nodeContextType.get(edge.from) !== "SPAWN") issue("error", "block_flow.from_context", `block flow source is not in SPAWN: ${edge.from}`, `/blockFlows/${index}/from`);
    if (nodeById.has(edge?.to) && nodeContextType.get(edge.to) !== "INITIALIZE") issue("error", "block_flow.to_context", `block flow target is not in INITIALIZE: ${edge.to}`, `/blockFlows/${index}/to`);
  });
  dataEdges.forEach((edge, index) => {
    if (!nodeById.has(edge?.from?.nodeId)) issue("error", "data_edge.from_missing", `data edge from missing node: ${edge?.from?.nodeId}`, `/dataEdges/${index}/from/nodeId`);
    if (!nodeById.has(edge?.to?.nodeId)) issue("error", "data_edge.to_missing", `data edge to missing node: ${edge?.to?.nodeId}`, `/dataEdges/${index}/to/nodeId`);
    if (typeof edge?.from?.portId !== "string") issue("error", "data_edge.from_port", "data edge source portId is required", `/dataEdges/${index}/from/portId`);
    if (typeof edge?.to?.portId !== "string") issue("error", "data_edge.to_port", "data edge target portId is required", `/dataEdges/${index}/to/portId`);
  });
  if (outputs.length === 0) issue("error", "outputs.empty", "vfx system has no output block", "/outputs");
  outputs.forEach((id, index) => {
    if (!nodeById.has(id)) issue("error", "outputs.missing", `output references missing node: ${id}`, `/outputs/${index}`);
    else if (!String(nodeById.get(id).type).startsWith("vfx.block.output_")) issue("warning", "outputs.kind", `output does not reference an output block: ${id}`, `/outputs/${index}`);
  });
  return validationResult(issues);
}

export function applyJsonPatch(document, operations) {
  for (const [index, operation] of operations.entries()) {
    if (!operation || !["add", "replace", "remove", "test"].includes(operation.op)) {
      throw new Error(`operation ${index}: op must be add, replace, remove, or test`);
    }
    const tokens = pointerTokens(operation.path);
    if (tokens.length === 0) throw new Error(`operation ${index}: root replacement is not supported`);
    const { parent, key } = pointerParent(document, tokens, index);
    const exists = Array.isArray(parent)
      ? key !== "-" && Number.isInteger(Number(key)) && Number(key) >= 0 && Number(key) < parent.length
      : Object.prototype.hasOwnProperty.call(parent, key);
    if (operation.op === "test") {
      if (!exists || !isDeepStrictEqual(parent[key], operation.value)) throw new Error(`operation ${index}: test failed at ${operation.path}`);
    } else if (operation.op === "remove") {
      if (!exists) throw new Error(`operation ${index}: path does not exist: ${operation.path}`);
      if (Array.isArray(parent)) parent.splice(Number(key), 1);
      else delete parent[key];
    } else if (operation.op === "replace") {
      if (!exists) throw new Error(`operation ${index}: path does not exist: ${operation.path}`);
      parent[key] = structuredClone(operation.value);
    } else if (Array.isArray(parent)) {
      if (key === "-") parent.push(structuredClone(operation.value));
      else {
        const arrayIndex = Number(key);
        if (!Number.isInteger(arrayIndex) || arrayIndex < 0 || arrayIndex > parent.length) throw new Error(`operation ${index}: invalid array index: ${key}`);
        parent.splice(arrayIndex, 0, structuredClone(operation.value));
      }
    } else {
      parent[key] = structuredClone(operation.value);
    }
  }
  return document;
}

function pointerTokens(pointer) {
  if (typeof pointer !== "string" || !pointer.startsWith("/")) throw new Error(`invalid JSON Pointer: ${pointer}`);
  return pointer.slice(1).split("/").map((token) => token.replace(/~1/g, "/").replace(/~0/g, "~"));
}

function pointerParent(document, tokens, operationIndex) {
  let current = document;
  for (const token of tokens.slice(0, -1)) {
    if (!current || typeof current !== "object" || !(token in current)) {
      throw new Error(`operation ${operationIndex}: parent path does not exist`);
    }
    current = current[token];
  }
  if (!current || typeof current !== "object") throw new Error(`operation ${operationIndex}: parent is not a container`);
  return { parent: current, key: tokens.at(-1) };
}

function requireArray(document, key, issues) {
  if (!Array.isArray(document[key])) {
    issues.push({ severity: "error", code: `${key}.type`, message: `${key} must be an array`, path: `/${key}` });
    return [];
  }
  return document[key];
}

function optionalArray(document, key, issues) {
  if (document[key] === undefined) return [];
  return requireArray(document, key, issues);
}

function validationResult(issues) {
  return {
    valid: !issues.some((issue) => issue.severity === "error"),
    errorCount: issues.filter((issue) => issue.severity === "error").length,
    warningCount: issues.filter((issue) => issue.severity === "warning").length,
    issues,
  };
}

function detectCycles(nodes, adjacency, onCycle) {
  const visiting = new Set();
  const visited = new Set();
  function visit(node) {
    if (visited.has(node)) return false;
    if (visiting.has(node)) {
      onCycle(node);
      return true;
    }
    visiting.add(node);
    for (const next of adjacency.get(node) || []) if (visit(next)) return true;
    visiting.delete(node);
    visited.add(node);
    return false;
  }
  for (const node of nodes) if (visit(node)) break;
}

function writeOptions(args) {
  return { expectedSha256: args.expected_sha256, allowInvalid: args.allow_invalid };
}

function normalizeResourceName(value) {
  if (typeof value !== "string") throw new Error("target_name is required");
  const name = value.trim().replace(/\.json$/, "");
  if (!/^[a-z0-9][a-z0-9_/-]*$/.test(name) || name.split(/[\\/]/).includes("..")) {
    throw new Error("target_name must be a lowercase resource path using a-z, 0-9, _, -, and /");
  }
  return name;
}

async function walkGraphFiles(root) {
  if (!existsSync(root)) return [];
  const result = [];
  async function walk(dir) {
    for (const entry of await readdir(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) await walk(full);
      else if (entry.isFile() && entry.name.endsWith(GRAPH_SUFFIX) && !entry.name.endsWith(EDITOR_SUFFIX)) result.push(full);
    }
  }
  await walk(root);
  return result;
}

function isWithin(root, candidate) {
  const relative = path.relative(path.resolve(root), path.resolve(candidate));
  return relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative));
}

async function atomicWrite(target, content) {
  await mkdir(path.dirname(target), { recursive: true });
  const temp = path.join(path.dirname(target), `.${path.basename(target)}.${randomUUID()}.tmp`);
  await writeFile(temp, content, { encoding: "utf8", flag: "wx" });
  try {
    await rename(temp, target);
  } catch (error) {
    await rm(temp, { force: true });
    throw error;
  }
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
