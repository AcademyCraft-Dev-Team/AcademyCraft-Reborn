#version 330

layout(std140) uniform ArcLightning {
    vec4 LightningParams;
};

in vec3 vNormal;
in vec2 texCoord0;
in vec4 vColor;
in vec3 vViewDir;

out vec4 fragColor;

void main() {
    float facing = abs(dot(normalize(vNormal), normalize(vViewDir)));
    float softEdge = mix(0.72, 1.0, facing);
    vec3 color = vColor.rgb * LightningParams.z * softEdge;
    float alpha = clamp(vColor.a * softEdge, 0.0, 0.58);
    fragColor = vec4(color, alpha);
}
