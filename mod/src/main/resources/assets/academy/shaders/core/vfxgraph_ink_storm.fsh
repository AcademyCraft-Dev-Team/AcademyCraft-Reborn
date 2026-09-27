#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 30) in vec3 vNormal;
layout(location = 0) in vec2 texCoord0;
layout(location = 29) in vec4 vColor;
layout(location = 31) in vec3 vViewDir;
layout(location = 0) out vec4 fragColor;

void main() {
    vec3 n = normalize(vNormal);
    float facing = abs(dot(n, normalize(vViewDir)));
    float furrow = sin(texCoord0.x * 50.2655 + texCoord0.y * 185.0
            + sin(texCoord0.y * 71.0) * 2.4);
    float fibers = 0.65 + 0.35 * smoothstep(0.15, 0.95, furrow);
    // Dark ink and travelling violet skins share the same geometry/material pipeline.
    float ridge = pow(1.0 - facing, 2.0) * 0.023 * fibers;
    vec3 ink = vColor.rgb * (0.55 + 0.45 * fibers) + vec3(ridge * 0.86, ridge * 0.91, ridge);
    float violet = smoothstep(0.92, 1.0, furrow) * (0.3 + 0.7 * (1.0 - facing));
    ink += vec3(0.048, 0.012, 0.082) * violet;
    float highlight = smoothstep(0.12, 0.4, max(vColor.r, vColor.b));
    vec3 brightViolet = vColor.rgb * (0.85 + 0.35 * fibers);
    brightViolet += vec3(0.18, 0.09, 0.22) * pow(facing, 5.0);
    ink = mix(ink, brightViolet, highlight);
    float endFade = 1.0 - smoothstep(0.96, 1.0, texCoord0.y);
    fragColor = vec4(ink, vColor.a * endFade);
}
