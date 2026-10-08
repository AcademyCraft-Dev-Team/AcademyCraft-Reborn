#version 330
#moj_import <academy:darkmatter_feather.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
in vec2 texCoord;
in vec4 vertexColor;
in float materialPhase;
in float surfaceBoundary;
in float materialPenetration;
out vec4 fragColor;

void main() {
    float across = texCoord.x;
    float tail = texCoord.y;
    float sector = across * 8.0;
    float ordinal = floor(sector);
    float local = fract(sector);
    float variation = fract(ordinal * 0.6180339 + materialPhase * 0.1);
    // Eight long/short vanes join a continuous cutting front. The back edge has substantial
    // feather lobes instead of dozens of shallow uniform saw teeth.
    float lobe = pow(max(0.0, sin(local * 3.14159265)), 0.58);
    float tailLength = 0.58 + lobe * (0.22 + variation * 0.08);
    float edge = tailLength - tail;
    float coverage = smoothstep(-max(fwidth(edge), 0.003), max(fwidth(edge), 0.003), edge);
    float ends = smoothstep(0.0, 0.055, across) * smoothstep(0.0, 0.055, 1.0 - across);
    float leadingEdge = 1.0 - smoothstep(0.015, 0.13, tail);
    vec3 pearl = academyDarkmatterPearl(vec2(local, tail / max(0.1, tailLength)),
            materialPhase + ordinal * 0.37, 0.25);
    pearl *= mix(1.0, 0.91, smoothstep(0.10, 0.78, tail));
    pearl = mix(pearl, vec3(0.98, 0.977, 0.970), leadingEdge);
    float overlap = academyFeatherLine(local - 0.08, 0.015) * smoothstep(0.18, 0.55, tail);
    pearl -= overlap * 0.075;
    float alpha = vertexColor.a * ends * coverage * mix(0.66, 1.0, smoothstep(0.0, 0.10, edge));
    alpha *= mix(smoothstep(0.0, 0.04, surfaceBoundary), 1.0, clamp(materialPenetration, 0.0, 1.0));
    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    alpha *= smoothstep(0.0, 0.000015, gl_FragCoord.z - texture(Sampler1, screenUv).r);
    if (alpha < 0.002) discard;
    fragColor = vec4(vertexColor.rgb * pearl, alpha);
}
