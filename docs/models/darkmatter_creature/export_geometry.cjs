// Exports the saved Blockbench project to the version-specific runtime geometry class.
// Usage: node docs/models/darkmatter_creature/export_geometry.cjs [source.bbmodel] [output.java]
const fs = require('fs'), path = require('path');
const dir = __dirname, rootDir = path.resolve(dir, '../../..');
const source = process.argv[2] || path.join(dir, 'darkmatter_creature.bbmodel');
const target = process.argv[3] || path.join(rootDir, 'src/main/java/org/academy/internal/client/model/DarkmatterCreatureGeometry.java');
const className = path.basename(target, '.java');
if (!/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(className)) throw new Error('Invalid runtime geometry class name');
const project = JSON.parse(fs.readFileSync(source, 'utf8'));
if (project.meta.model_format !== 'modded_entity') throw new Error('Expected Blockbench Modded Entity project');
const elements = new Map(project.elements.map(e => [e.uuid, e]));
// Blockbench 5 stores group metadata separately from the outliner hierarchy.
const groups = new Map((project.groups || []).map(g => [g.uuid, g]));
const resolveGroup = entry => ({ ...(groups.get(entry.uuid) || {}), ...entry });
const float = n => (Math.abs(n) < 0.000001 ? '0.0' : Number(n.toFixed(6)).toString().includes('.') ? Number(n.toFixed(6)).toString() : Number(n.toFixed(6)).toString() + '.0') + 'f';
const vec = a => a.map(float).join(', ');
const lines = [
    'package org.academy.internal.client.model;', '',
    'import net.minecraft.client.model.geom.PartPose;',
    'import net.minecraft.client.model.geom.builders.CubeDeformation;',
    'import net.minecraft.client.model.geom.builders.CubeListBuilder;',
    'import net.minecraft.client.model.geom.builders.LayerDefinition;',
    'import net.minecraft.client.model.geom.builders.MeshDefinition;', '',
    '/**',
    ' * Geometry exported from docs/models/darkmatter_creature/' + path.basename(source) + '.',
    ' * Edit the Blockbench project and run its export_geometry.cjs adapter to update this class.',
    ' * Hidden authoring branches are deliberately exported for runtime blueprint selection.',
    ' */',
    'final class ' + className + ' {',
    '    private ' + className + '() {', '    }', '',
    '    static LayerDefinition createBodyLayer() {',
    '        var mesh = new MeshDefinition();'
];
let count = 0;
function emit(group, parent, parentOrigin, root = false) {
    group = resolveGroup(group);
    if (group.export === false) return;
    const name = 'part' + count++, origin = group.origin || [0, 0, 0], rotation = group.rotation || [0, 0, 0];
    const at = [parentOrigin[0] - origin[0], parentOrigin[1] - origin[1], origin[2] - parentOrigin[2]];
    if (root) at[1] += 24;
    const rad = [-rotation[0], -rotation[1], rotation[2]].map(n => n * Math.PI / 180);
    const cubes = group.children.filter(c => typeof c === 'string').map(id => elements.get(id)).filter(e => e && e.export !== false);
    lines.push('        var ' + name + ' = ' + parent + '.addOrReplaceChild("' + group.name + '", CubeListBuilder.create()');
    for (const c of cubes) {
        if (c.type !== 'cube') throw new Error('Runtime expects Blockbench cubes, got ' + c.type);
        if ((c.rotation || []).some(n => Math.abs(n) > 0.000001)) throw new Error('Put cube rotation on a group: ' + c.name);
        const at = [origin[0] - c.to[0], origin[1] - c.to[1], c.from[2] - origin[2]];
        const size = c.to.map((n, i) => n - c.from[i]);
        const uv = c.uv_offset || [0, 0];
        lines.push('                .texOffs(' + uv[0] + ', ' + uv[1] + ')' + (c.mirror_uv ? '.mirror()' : '') + '.addBox(' + vec([...at, ...size]) + ', new CubeDeformation(' + float(c.inflate || 0) + '))');
    }
    lines.push('                , PartPose.' + (rad.some(n => Math.abs(n) > 0.000001) ? 'offsetAndRotation(' + vec([...at, ...rad]) : 'offset(' + vec(at)) + '));');
    for (const child of group.children.filter(c => typeof c === 'object')) emit(child, name, origin);
}
for (const group of project.outliner.filter(g => typeof g === 'object')) emit(group, 'mesh.getRoot()', [0, 0, 0], true);
lines.push('        return LayerDefinition.create(mesh, ' + project.resolution.width + ', ' + project.resolution.height + ');', '    }', '}', '');
fs.writeFileSync(target, lines.join('\n'));
console.log(JSON.stringify({ source, target, groups: count, cubes: project.elements.length }));
