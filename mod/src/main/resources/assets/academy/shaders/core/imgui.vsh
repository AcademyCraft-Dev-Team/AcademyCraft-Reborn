#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform Projection {
    mat4 ProjMat;
};
layout(location = 0) in vec2 Position;
layout(location = 1) in vec2 UV;
layout(location = 2) in vec4 Color;
layout(location = 3) out vec2 Frag_UV;
layout(location = 2) out vec4 Frag_Color;

void main()
{
    Frag_UV = UV;
    Frag_Color = Color;
    gl_Position = ProjMat * vec4(Position.xy, 0, 1);
}
