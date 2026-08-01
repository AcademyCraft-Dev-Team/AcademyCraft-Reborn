#version 330

layout(std140) uniform Projection {
    mat4 ProjMat;
};
in vec2 Position;
in vec2 UV;
in vec4 Color;
out vec2 Frag_UV;
out vec4 Frag_Color;

void main()
{
    Frag_UV = UV;
    Frag_Color = Color;
    gl_Position = ProjMat * vec4(Position.xy, 0, 1);
}
