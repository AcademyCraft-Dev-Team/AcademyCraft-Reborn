#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Texture;
layout(location = 3) in vec2 Frag_UV;
layout(location = 2) in vec4 Frag_Color;
layout(location = 0) out vec4 Out_Color;

void main()
{
    Out_Color = Frag_Color * texture(Texture, Frag_UV.st);
}
