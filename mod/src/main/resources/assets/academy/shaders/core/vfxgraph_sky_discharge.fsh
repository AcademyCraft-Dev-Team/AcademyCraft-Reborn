#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform ArcLightning { vec4 LightningParams; };
layout(location = 30) in vec3 vNormal;
layout(location = 0) in vec2 texCoord0;
layout(location = 29) in vec4 vColor;
layout(location = 31) in vec3 vViewDir;
layout(location = 0) out vec4 fragColor;
void main() {
    float facing = abs(dot(normalize(vNormal), normalize(vViewDir)));
    float edge = pow(facing, 1.8);
    float alpha = vColor.a * edge;
    vec3 color = vColor.rgb * LightningParams.z;
    // The bloom target adds RGB directly; the world pass uses straight alpha.
    if (LightningParams.y > 0.5) color *= alpha;
    fragColor = vec4(color, alpha);
}
