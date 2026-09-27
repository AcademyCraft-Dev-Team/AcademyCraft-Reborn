#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform GraphCamera {
    mat4 View;
    mat4 Projection;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Normal;
layout(location = 2) in vec2 UV0;
layout(location = 3) in vec4 Color;

layout(location = 30) out vec3 vNormal;
layout(location = 0) out vec2 texCoord0;
layout(location = 29) out vec4 vColor;
layout(location = 31) out vec3 vViewDir;

void main() {
    gl_Position = Projection * View * vec4(Position, 1.0);
    vNormal = Normal;
    texCoord0 = UV0;
    vColor = Color;
    // Position 已由 CPU 转为相机相对坐标（world - camPos），故 -Position 即碎片到相机的世界方向
    // （视图为纯旋转矩阵，方向不受旋转影响；视角相关 rim 在片元里用它与法线点积计算）。
    vViewDir = normalize(-Position);
}