#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform ArcLightning { vec4 LightningParams; };
layout(location = 30) in vec3 vNormal;
layout(location = 0) in vec2 texCoord0;
layout(location = 29) in vec4 vColor;
layout(location = 31) in vec3 vViewDir;
layout(location = 0) out vec4 fragColor;

void main() {
    vec3 normal = normalize(vNormal);
    float facing = abs(dot(normal, normalize(vViewDir)));
    // Warm plasma must remain visible at grazing angles when seen along its axis.
    // Keep the blue discharge's sharper edge falloff and the existing side-view brightness.
    float plasma = smoothstep(0.0, 0.35, vColor.r - vColor.b);
    float edge = mix(pow(facing, 1.1), 0.20 + 0.80 * pow(facing, 0.85), plasma);
    float alpha = vColor.a * edge;
    vec3 color = vColor.rgb * LightningParams.z;
    // A restrained circumferential gradient separates the translucent cylindrical shells.
    float roundness = 0.82 + 0.18 * max(dot(normal, normalize(vec3(0.35, 0.8, 0.3))), 0.0);
    color *= mix(1.0, roundness, plasma);
    if (LightningParams.y > 0.5) color *= alpha;
    fragColor = vec4(color, alpha);
}
