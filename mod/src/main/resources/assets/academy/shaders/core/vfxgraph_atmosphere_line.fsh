#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform ArcLightning {
    vec4 LightningParams;
};

layout(location = 30) in vec3 vNormal;
layout(location = 0) in vec2 texCoord0;
layout(location = 29) in vec4 vColor;
layout(location = 31) in vec3 vViewDir;

layout(location = 0) out vec4 fragColor;

void main() {
    float facing = abs(dot(normalize(vNormal), normalize(vViewDir)));
    float softEdge = mix(0.72, 1.0, facing);
    vec3 color = vColor.rgb * LightningParams.z * softEdge;
    float alpha = clamp(vColor.a * softEdge, 0.0, 0.58);
    fragColor = vec4(color, alpha);
}
