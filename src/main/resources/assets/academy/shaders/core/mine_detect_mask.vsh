#version 330
#moj_import <minecraft:dynamictransforms.glsl>

layout(std140) uniform MineView {
    mat4 ViewProjection;
    vec4 ScanCenterRadius;
};
in vec3 Position;
in vec4 Color;
in vec2 UV0;
out vec4 oreColor;
flat out float inRange;

bool inside(int index) {
    vec3 block = vec3(index & 7, (index >> 3) & 7, index >> 6);
    vec3 delta = block + ModelOffset - ScanCenterRadius.xyz;
    return dot(delta, delta) <= ScanCenterRadius.w * ScanCenterRadius.w;
}

void main() {
    int index = int(UV0.x + 0.5);
    bool visible = inside(index);
    if (UV0.y > 0.5) visible = visible != inside(int(UV0.y + 0.5) - 1);
    inRange = visible ? 1.0 : 0.0;
    gl_Position = ViewProjection * vec4(Position + ModelOffset, 1.0);
    oreColor = Color;
}
