#version 330

layout(std140) uniform GraphCamera {
    mat4 View;
    mat4 Projection;
};

in vec3 Position;
in vec3 Normal;
in vec2 UV0;
in vec4 Color;

out vec3 vNormal;
out vec2 texCoord0;
out vec4 vColor;
out vec3 vViewDir;

void main() {
    gl_Position = Projection * View * vec4(Position, 1.0);
    vNormal = Normal;
    texCoord0 = UV0;
    vColor = Color;
    // Position 已由 CPU 转为相机相对坐标（world - camPos），故 -Position 即碎片到相机的世界方向
    // （视图为纯旋转矩阵，方向不受旋转影响；视角相关 rim 在片元里用它与法线点积计算）。
    vViewDir = normalize(-Position);
}