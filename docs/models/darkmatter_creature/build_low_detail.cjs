// Derives an editable Blockbench distance model from the authored full model, retaining every joint.
// Only the low-detail source is overwritten. The artist's full source remains untouched.
const fs = require('fs'), path = require('path');
const dir = __dirname;
const source = process.argv[2] || path.join(dir, 'darkmatter_creature.bbmodel');
const target = process.argv[3] || path.join(dir, 'darkmatter_creature_low_detail.bbmodel');
const project = JSON.parse(fs.readFileSync(source, 'utf8'));
if (project.meta.model_format !== 'modded_entity') throw new Error('Expected Blockbench Modded Entity project');
const groups = new Map((project.groups || []).map(g => [g.uuid, g]));
const cubes = new Map(project.elements.map(c => [c.uuid, c]));
const kept = [];
function boxFaces(cube) {
    const [x, y, z] = cube.to.map((v, i) => v - cube.from[i]);
    cube.uv_offset = [0, 0];
    const uv = {east: [0,z,z,z+y], north: [z,z,z+x,z+y], west: [z+x,z,2*z+x,z+y],
        south: [2*z+x,z,2*z+2*x,z+y], up: [z+x,z,z,0], down: [z+2*x,0,z+x,z]};
    cube.faces = Object.fromEntries(Object.entries(uv).map(([face, value]) => [face, {uv:value, texture:0}]));
}
function retain(cube, branch, group) {
    const n = cube.name;
    if (branch.startsWith('head_')) return n === 'head_core' || n.startsWith('horn_growth_')
        || n === 'jaw_base' || n === 'jaw_hook' || n.startsWith('cannon_growth_');
    if (branch.startsWith('torso_')) return ['abdomen_core','abdomen_taper','thorax_collar','swim_fin_root','tail_fan'].includes(n)
        || n === 'carapace_low_surface' || /^membrane_[024]$/.test(n);
    if (branch.startsWith('limbs_')) return ['femur_shell','tibia_core','guard_blade','excavation_spade','cargo_cradle'].includes(n);
    if (branch === 'additional_1') return n === 'armor_scale' && /_[02]$/.test(group.name);
    if (branch === 'additional_2') return n === 'sensor_stalk';
    if (branch === 'additional_3') return n === 'weapon_growth_base' || n === 'dorsal_low_surface';
    return false;
}
function reduce(entry, branch) {
    // BB4 stores groups inline; BB5 outliner nodes carry only UUID and children.
    // Keep the input format editable by changing the node's children without flattening its metadata.
    const group = { ...(groups.get(entry.uuid) || {}), ...entry };
    if (/^(head|torso|limbs|additional)_/.test(group.name)) branch = group.name;
    const contents = group.children.filter(c => typeof c === 'string').map(id => cubes.get(id));
    if (branch.startsWith('torso_') && group.name.startsWith('elytra_') || group.name === 'dorsal_cannon') {
        const merged = structuredClone(contents[0]);
        merged.name = group.name === 'dorsal_cannon' ? 'dorsal_low_surface' : 'carapace_low_surface';
        merged.from = [0,1,2].map(i => Math.min(...contents.map(c => c.from[i])));
        merged.to = [0,1,2].map(i => Math.max(...contents.map(c => c.to[i])));
        boxFaces(merged); cubes.set(merged.uuid, merged);
        group.children = group.children.filter(c => typeof c !== 'string');
        group.children.unshift(merged.uuid);
    }
    group.children = group.children.filter(c => typeof c !== 'string' || retain(cubes.get(c), branch, group));
    for (const child of group.children) if (typeof child === 'string') kept.push(cubes.get(child)); else reduce(child, branch);
    entry.children = group.children;
}
for (const group of project.outliner) reduce(group, '');
project.elements = kept;
project.name = 'Darkmatter Creature Low Detail';
project.model_identifier = 'darkmatter_creature_low_detail';
fs.mkdirSync(path.dirname(target), {recursive:true});
fs.writeFileSync(target, JSON.stringify(project, null, 2) + '\n');
console.log(JSON.stringify({source, target, cubes:kept.length}));
