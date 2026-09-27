#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in float cloudTime;
layout(location = 3) in float cloudPhase;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    vec2 phase = vec2(cloudPhase * 0.37, cloudPhase * 0.61);
    float billow = texture(Sampler0, texCoord * 0.018 + phase + cloudTime * vec2(0.018, -0.012)).r;
    float detail = texture(Sampler0, texCoord * 0.047 - phase + cloudTime * vec2(-0.025, 0.02)).r;
    float density = 1.0 - dot(p, p) + (billow - 0.5) * 0.75;
    // Expanding, porous smoke loses density as it leaves the surface, rather than filling the target.
    float alpha = vertexColor.a * smoothstep(0.0, 0.75, density)
            * (0.45 + billow * 0.4 + detail * 0.15) * (1.0 - cloudTime * 0.5);
    float shade = 0.68 + billow * 0.26 + detail * 0.06;
    float sceneDepth = texture(Sampler1, gl_FragCoord.xy / textureSize(Sampler1, 0)).r;
    alpha *= smoothstep(0.0, 0.000015, gl_FragCoord.z - sceneDepth);
    if (alpha < 0.002) discard;
    fragColor = vec4(vertexColor.rgb * shade, alpha);
}
