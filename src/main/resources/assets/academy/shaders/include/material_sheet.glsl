uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
in vec2 texCoord;
in vec4 vertexColor;
in float materialProgress;
in float materialPhase;
out vec4 fragColor;

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    vec2 phase = vec2(materialPhase * 0.39, materialPhase * 0.61);
    float broad = texture(Sampler0, texCoord * 0.021 + phase).r;
    float detail = texture(Sampler0, texCoord * 0.058 - phase).r;
    // Broad pearl-grey material with a short branching feather edge, never a luminous strand.
    float taper = pow(max(0.0, 1.0 - abs(p.y)), 0.55);
    float barbs = sin(p.y * 18.0 + abs(p.x) * 10.0 + materialPhase) * 0.018;
    float edge = taper * (0.65 + broad * 0.3) - abs(p.x) + barbs;
    float silhouette = smoothstep(-0.055, 0.16, edge) * (1.0 - smoothstep(0.82, 1.0, abs(p.y)));
    float shade = 0.7 + broad * 0.23 + detail * 0.07;
    float alpha = silhouette * vertexColor.a;
#if MATERIAL_STYLE == 5
    // Pointed blade, a pale spine and restrained feather barbs.
    float bladeWidth = pow(max(0.0, 1.0 - abs(p.y)), 0.65) * (0.75 + p.y * 0.14);
    float bladeEdge = bladeWidth - abs(p.x) + sin(p.y * 24.0 + abs(p.x) * 8.0) * 0.018;
    alpha = vertexColor.a * smoothstep(-0.045, 0.07, bladeEdge);
    shade = 0.8 + broad * 0.14 + exp(-abs(p.x) * 24.0) * 0.12;
#elif MATERIAL_STYLE == 1
    // Irregular patches of altered illumination, not a solid white decal.
    alpha *= smoothstep(0.25, 0.75, broad) * (0.45 + detail * 0.55);
    shade = 0.85 + detail * 0.15;
#elif MATERIAL_STYLE == 2
    // A porous growth front closes inward, settles, then the instance envelope removes the patch.
    float growth = smoothstep(0.0, 0.62, materialProgress);
    float front = growth - (1.0 - length(p * vec2(0.7, 0.65))) + (broad - 0.5) * 0.35;
    alpha *= smoothstep(-0.12, 0.05, front);
    shade += 0.1 * (1.0 - smoothstep(0.0, 0.18, abs(front)));
#elif MATERIAL_STYLE == 4
    // A compact white impact: a soft core and broken luminous rim on the struck surface.
    float radius = length(p * vec2(0.85, 0.9));
    float core = 1.0 - smoothstep(0.0, 0.65, radius);
    float rim = exp(-pow((radius - 0.58) * 11.0, 2.0)) * (0.55 + broad * 0.45);
    alpha = vertexColor.a * (core * 0.75 + rim * 0.55);
    shade = 1.0;
#elif MATERIAL_STYLE == 3
    // Eroding islands and flakes lose material as they move a short distance off the surface.
    alpha *= smoothstep(materialProgress * 0.72, materialProgress * 0.72 + 0.2, broad * 0.7 + detail * 0.3);
#endif
    // The depth attachment tests occlusion. A very narrow intersection fade keeps surface patches readable.
    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    alpha *= smoothstep(0.0, 0.000015, gl_FragCoord.z - sceneDepth);
    if (alpha < 0.002) discard;
    vec3 color = vertexColor.rgb * shade;
#if MATERIAL_STYLE == 4
    color *= alpha; // Additive impact output requires premultiplied emission.
#endif
    fragColor = vec4(color, alpha);
}
