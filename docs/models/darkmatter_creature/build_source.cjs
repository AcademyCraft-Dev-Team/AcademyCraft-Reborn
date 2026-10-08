// Authoring scaffold. The saved .bbmodel is the editable source; export_geometry.cjs exports edits.
const fs = require('fs'), path = require('path'), crypto = require('crypto'), zlib = require('zlib');
const dir = __dirname, rootDir = path.resolve(dir, '../../..');
const groups = [], cubes = [];
const uv = { white: [0, 0], shell: [0, 32], wing: [0, 64], joint: [64, 0], bore: [64, 32], claw: [64, 64] };
const uuid = () => crypto.randomUUID();
const all = { name: 'all', origin: [0, 0, 0], rotation: [0, 0, 0], uuid: uuid(), children: [], export: true, visibility: true, isOpen: true };
groups.push(all);
// Runtime local Y points down; Blockbench X and Y point in the opposite direction.
function group(parent, name, at = [0, 0, 0], angle = [0, 0, 0], visible = true) {
    const origin = [parent.origin[0] - at[0], parent.origin[1] - at[1], parent.origin[2] + at[2]];
    const out = { name, origin, rotation: [-angle[0], -angle[1], angle[2]], uuid: uuid(), children: [], export: true, visibility: visible, isOpen: true };
    parent.children.push(out); groups.push(out); return out;
}
function box(parent, name, at, size, material = 'white') {
    const [x, y, z] = at, [w, h, d] = size, [u, v] = uv[material];
    const from = [parent.origin[0] - x - w, parent.origin[1] - y - h, parent.origin[2] + z];
    const to = [from[0] + w, from[1] + h, from[2] + d];
    const faces = { east: [u, v+d, u+d, v+d+h], north: [u+d, v+d, u+d+w, v+d+h], west: [u+d+w, v+d, u+2*d+w, v+d+h], south: [u+2*d+w, v+d, u+2*d+2*w, v+d+h], up: [u+d+w, v+d, u+d, v], down: [u+d+2*w, v, u+d+w, v+d] };
    const element = { name, type: 'cube', uuid: uuid(), from, to, origin: parent.origin.slice(), rotation: [0, 0, 0], inflate: 0, box_uv: true, uv_offset: [u, v], autouv: 0, color: Object.keys(uv).indexOf(material), export: true, visibility: true, faces: Object.fromEntries(Object.entries(faces).map(([key, value]) => [key, { uv: value, texture: 0 }])) };
    cubes.push(element); parent.children.push(element.uuid);
}
function horn(parent, long) {
    const branch = group(parent, 'horn', [0, -2, -2.8]);
    const sections = long ? [[-1.05,-.4,-2,2.1,1.5,3.1],[-.92,-1.35,-3.3,1.84,1.4,2.2],[-.75,-2.65,-4.05,1.5,1.6,1.7],[-.55,-4.1,-4.6,1.1,1.8,1.2],[-.3,-5.55,-4.95,.6,1.65,.7]] : [[-.8,-.5,-1.8,1.6,1.2,2.3],[-.55,-1.9,-2.4,1.1,1.5,1.2],[-.3,-3,-2.75,.6,1.3,.7]];
    sections.forEach((s, i) => box(branch, 'horn_growth_' + i, s.slice(0,3), s.slice(3)));
}
function headCore(parent) {
    box(parent, 'head_core', [-2.6,-2.3,-3.5], [5.2,3.8,4.6]);
    box(parent, 'head_crown', [-2.1,-3,-2.8], [4.2,1.2,3.6], 'shell');
    box(parent, 'neck_fold', [-2.3,-1.2,.4], [4.6,2.7,1.7], 'joint');
    for (const sign of [-1,1]) {
        box(parent, 'eye_' + sign, [sign < 0 ? -2.75 : 2.45,-1.4,-2.9], [.3,.55,1.1], 'bore');
        const a = group(parent, 'antenna_' + (sign < 0 ? 'left' : 'right'), [sign*2.15,-2.4,-2.8], [0,sign*18,sign*20]);
        box(a, 'antenna_stem', [-.16,-.2,-2.2], [.32,.32,2.35]);
        box(a, 'antenna_tip', [-.32,-.35,-2.6], [.64,.5,.6], 'shell');
    }
}
for (let id=0; id<3; id++) {
    const h = group(all, 'head_' + id, [0,-8,-4], [0,0,0], id===0);
    headCore(h); horn(h,id===0);
    if (id===0) for (const sign of [-1,1]) {
        const j = group(h, 'jaw_' + (sign < 0 ? 'left' : 'right'), [sign*2.15,.75,-2.7], [0,-sign*12,0]);
        box(j,'jaw_base',[-.58,-.35,-2.1],[1.16,1.35,2.4]);
        box(j,'jaw_hook',[sign < 0 ? 0 : -1,-.2,-3],[1,.85,1.7],'claw');
        box(j,'jaw_tooth',[sign < 0 ? .7 : -1.05,0,-3.1],[.35,.6,.7]);
    } else {
        const c = group(h,'cannon',[0,-.65,-2.6]);
        (id===1 ? [0] : [-1.35,1.35]).forEach((cx,n) => {
            box(c,'cannon_growth_'+n,[cx-.95,-.85,-2.5],[1.9,1.8,2.7]);
            box(c,'muzzle_top_'+n,[cx-.8,-.85,-4.3],[1.6,.36,1.9]);
            box(c,'muzzle_bottom_'+n,[cx-.8,.49,-4.3],[1.6,.36,1.9]);
            box(c,'muzzle_left_'+n,[cx-.8,-.5,-4.3],[.36,1,1.9]);
            box(c,'muzzle_right_'+n,[cx+.44,-.5,-4.3],[.36,1,1.9]);
            box(c,'muzzle_depth_'+n,[cx-.44,-.49,-2.58],[.88,.98,.08],'bore');
        });
    }
}
function shell(parent, sign, fly) {
    const side = sign < 0 ? 'left' : 'right', e = group(parent,'elytra_'+side,[sign*.4,-2.4,-.9]);
    for (let i=0;i<4;i++) {
        const z=-2.4+i*1.8, w=i===0 ? 2.75 : i===3 ? 2.35 : 3.35;
        box(e,'carapace_plate_'+i,[sign < 0 ? -w : 0,-.55-(i===1||i===2 ? .45 : 0),z],[w,1.1,2.35],'shell');
        box(e,'carapace_edge_'+i,[sign < 0 ? -w-.25 : w-.55,.12,z+.2],[.8,.5,2.05]);
    }
    if (fly) {
        const w = group(parent,'wing_'+side,[sign*2.6,-1.7,-.5]);
        for (let i=0;i<5;i++) {
            const len=7.3-Math.abs(i-2)*.8, f=group(w,'wing_blade_'+i,[0,0,-1.3+i*1.15],[0,-sign*(12+i*3),0]);
            box(f,'membrane_'+i,[sign < 0 ? -len : 0,-.09,-.2],[len,.18,1.6],'wing');
            box(f,'wing_vein_'+i,[sign < 0 ? -len : 0,-.13,-.12],[len,.08,.12],'shell');
        }
    }
}
for (let id=0;id<3;id++) {
    const t=group(all,'torso_'+id,[0,-5.2,1.1],[0,0,0],id===0);
    box(t,'abdomen_core',[-3.35,-1.9,-3.6],[6.7,3.9,id===2?10:8.7]);
    box(t,'abdomen_taper',[-2.65,-1.4,4.6],[5.3,3,1.65]);
    box(t,'thorax_collar',[-3.5,-2,-3.8],[7,3.4,1.6],'shell');
    for(let i=0;i<3;i++) box(t,'ventral_fold_'+i,[-2.9+i*.1,1.7,-1.8+i*1.8],[5.8-i*.2,.42,1.1],'joint');
    shell(t,-1,id===1); shell(t,1,id===1);
    if(id===2) {
        for(const sign of [-1,1]) {
            const f=group(t,'fin_'+(sign<0?'left':'right'),[sign*2.5,0,3.5],[0,-sign*25,0]);
            box(f,'swim_fin_root',[sign<0?-1.8:0,-.12,0],[1.8,.24,2.8],'wing');
            box(f,'swim_fin_tip',[sign<0?-3.15:1.35,-.09,1.2],[1.8,.18,2.8],'wing');
        }
        const tail=group(t,'swim_tail',[0,.6,5.5]);
        box(tail,'tail_axis',[-.55,-.4,-.2],[1.1,.8,2.2]); box(tail,'tail_fan',[-2.35,-.12,1.7],[4.7,.24,1.8],'wing');
    }
}
for(let id=0;id<3;id++) {
    const l=group(all,'limbs_'+id,[0,0,0],[0,0,0],id===0);
    for(const sign of [-1,1]) for(let n=0;n<3;n++) {
        const side=sign<0?'left':'right', loc=['front','middle','rear'][n];
        const leg=group(l,'leg_'+side+'_'+loc,[sign*3.1,-4.7,-2.7+n*3.4],[0,sign*(n-1)*18,sign*15]);
        box(leg,'coxa',[sign<0?-2.9:-.2,-.6,-.65],[3.1,1.2,1.3],'joint');
        box(leg,'femur_shell',[sign<0?-2.65:.35,-.88,-.85],[2.3,.65,1.7],'shell');
        const tibia=group(leg,'tibia',[sign*2.6,.45,0],[0,0,-sign*25]);
        box(tibia,'tibia_core',[-.5,-.2,-.5],[1,3.9,1]); box(tibia,'tibia_cover',[-.65,-.3,-.7],[1.3,2.6,1.4],'shell');
        const foot=group(tibia,'tarsus',[0,3.4,0],[0,0,sign*10]);
        box(foot,'foot_pad',[-.6,-.25,-.95],[1.2,.65,1.8],'claw');
        for(const toe of [-1,1]) box(foot,'toe_'+toe,[toe*.33-.17,.06,-1.55],[.34,.35,1]);
        if(id===1&&n===0) {
            box(foot,'excavation_spade',[-1.3,-.2,-2.35],[2.6,.5,1.7],'shell');
            for(let tooth=0;tooth<3;tooth++) box(foot,'spade_tooth_'+tooth,[-1.05+tooth*.8,-.1,-2.85],[.5,.35,.8]);
        }
        if(id===2&&n===2) box(leg,'cargo_cradle',[sign<0?-2.7:.4,-1.35,-1.2],[2.3,.6,2.4],'shell');
        if(id===0&&n===0) box(tibia,'guard_blade',[sign<0?-.9:.4,.6,-.8],[.5,2.3,1.6],'shell');
    }
}
group(all,'additional_0');
const armor=group(all,'additional_1',[0,-8.7,1.1],[0,0,0],false);
for(const sign of [-1,1]) for(let i=0;i<4;i++) {
    const p=group(armor,'armor_'+sign+'_'+i,[sign*1.5,-.12,-1.6+i*1.85],[0,0,sign*12]);
    box(p,'armor_scale',[-1.55,-.35,-.6],[3.1,.65,2.7],'shell'); box(p,'armor_tip',[-1.3,-.15,1.5],[2.6,.36,1.15]);
}
const sensor=group(all,'additional_2',[0,-8.4,0],[0,0,0],false);
for(const sign of [-1,1]) {
    const a=group(sensor,'sensor_'+(sign<0?'left':'right'),[sign*1.5,0,0],[12,0,sign*16]);
    box(a,'sensor_stalk',[-.18,-3.4,-.18],[.36,3.5,.36]); box(a,'sensor_crown',[-.75,-3.65,-.36],[1.5,.4,.72],'shell'); box(a,'sensor_inner',[-.48,-3.7,-.38],[.96,.13,.76],'wing');
}
const weapon=group(all,'additional_3',[0,-9.3,3.2],[0,0,0],false);
box(weapon,'weapon_growth_base',[-1.6,-.3,-1.25],[3.2,1.7,2.5],'shell');
const gun=group(weapon,'dorsal_cannon',[0,-.55,-.45],[-8,0,0]);
box(gun,'weapon_top',[-.9,-.8,-4.1],[1.8,.35,4.1]); box(gun,'weapon_bottom',[-.9,.45,-4.1],[1.8,.35,4.1]);
box(gun,'weapon_side_left',[-.9,-.46,-4.1],[.35,.92,4.1]); box(gun,'weapon_side_right',[.55,-.46,-4.1],[.35,.92,4.1]); box(gun,'weapon_muzzle_depth',[-.55,-.45,-2],[1.1,.9,.15],'bore');
function crc32(bytes) { let c=0xffffffff; for(const b of bytes) {c^=b;for(let i=0;i<8;i++)c=(c>>>1)^(0xedb88320&-(c&1));} return(c^0xffffffff)>>>0; }
function chunk(type,data) {const tag=Buffer.from(type),len=Buffer.alloc(4),crc=Buffer.alloc(4);len.writeUInt32BE(data.length);crc.writeUInt32BE(crc32(Buffer.concat([tag,data])));return Buffer.concat([len,tag,data,crc]);}
const raw=Buffer.alloc(128*513);
for(let y=0;y<128;y++) for(let x=0;x<128;x++) {
    const lane=Math.floor(y/32),second=x>=64;let tone=second?[216,191,233,248][lane]:[250,245,252,248][lane];
    if(!second&&lane===1)tone-=((x+Math.floor(y/2))%17<1?10:0)+Math.floor((y%32)/10);
    if(!second&&lane===2)tone-=((x+2*y)%23<1?17:0);if(second&&lane===0)tone+=(x+y)%5===0?4:0;
    const p=y*513+1+x*4;raw[p]=tone;raw[p+1]=tone;raw[p+2]=Math.min(255,tone+2);raw[p+3]=255;
}
const ihdr=Buffer.alloc(13);ihdr.writeUInt32BE(128,0);ihdr.writeUInt32BE(128,4);ihdr[8]=8;ihdr[9]=6;
const png=Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ihdr),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]);
fs.writeFileSync(path.join(dir,'darkmatter_beetle.png'),png);
fs.writeFileSync(path.join(rootDir,'src/main/resources/assets/academy/textures/entity/darkmatter_beetle.png'),png);
const project={meta:{format_version:'4.10',model_format:'modded_entity',box_uv:true},name:'DarkmatterCreatureGeometry',model_identifier:'darkmatter_creature',modded_entity_version:'1.17',modded_entity_flip_y:true,box_uv:true,visible_box:[2,2,.5],resolution:{width:128,height:128},elements:cubes,outliner:[all],textures:[{path:'darkmatter_beetle.png',name:'darkmatter_beetle.png',folder:'',namespace:'academy',id:'0',particle:false,render_mode:'default',render_sides:'auto',visible:true,internal:true,saved:true,uuid:uuid(),source:'data:image/png;base64,'+png.toString('base64'),width:128,height:128,uv_width:128,uv_height:128}],display:{},animations:[],animation_variable_placeholders:'',academy:{body_slots:['head_0..2','torso_0..2','limbs_0..2','additional_0..3'],atlas_materials:uv,default_blueprint:[0,0,0,0],notes:'White rhinoceros beetle. Alternate slot branches are hidden for authoring and selected by blueprint at runtime. Pale grey is material relief, not a metallic palette.'}};
fs.writeFileSync(path.join(dir,'darkmatter_creature.bbmodel'),JSON.stringify(project,null,2)+'\n');
console.log(JSON.stringify({groups:groups.length,cubes:cubes.length,source:path.join(dir,'darkmatter_creature.bbmodel')}));
