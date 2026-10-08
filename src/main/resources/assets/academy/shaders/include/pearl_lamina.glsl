// Filled, layered pearl material for local condensation, forming and peeling surfaces.
// The emitter owns geometry and its bounded envelope; this shader never adds a glowing ring.
#moj_import <academy:darkmatter_feather.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
in vec2 texCoord;
in vec4 vertexColor;
in float materialProgress;
in float materialPhase;
out vec4 fragColor;

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float progress = clamp(materialProgress, 0.0, 1.0);
    vec2 phase = vec2(materialPhase * 0.39, materialPhase * 0.61);
    float broad = texture(Sampler0, texCoord * 0.021 + phase).r;
    // The common feather family supplies the same filled body, pearl-grey rachis and vane bundles.
    vec4 feather = academyDarkmatterFeather(texCoord, materialPhase, 0.16 + progress * 0.22);
    float silhouette = feather.a;
    vec3 material = academyDarkmatterPearl(texCoord, materialPhase, 0.16 + progress * 0.22);
    float remaining = 1.0;
#if PEARL_MOTION_KIND == 1
    // Formed plates keep their filled material; growing proxy geometry supplies the shape front.
    material += vec3(smoothstep(0.12, 0.55, progress) * 0.012);
#elif PEARL_MOTION_KIND == 2
    // Peeling removes separate vane bands from the tip and edges while the core remains readable.
    float longitudinal = (p.y + 1.0) * 0.5;
    float bands = 0.55 + sin(p.y * 17.0 + materialPhase) * 0.16 + broad * 0.22;
    float erosion = progress * 1.10 - longitudinal * 0.30;
    remaining *= smoothstep(erosion - 0.10, erosion + 0.12, bands);
    float split = abs(sin(p.y * 11.0 + p.x * 1.8 + materialPhase));
    float fracture = smoothstep(0.16, 0.65, progress);
    remaining *= mix(1.0, smoothstep(0.035, 0.10, split), fracture);
    material -= vec3(fracture * 0.018);
#endif
    float alpha = vertexColor.a * silhouette * remaining;
    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    alpha *= smoothstep(0.0, 0.000015, gl_FragCoord.z - sceneDepth);
    if (alpha < 0.002) discard;
    vec3 pearl = vertexColor.rgb * clamp(material, vec3(0.56), vec3(0.99));
    fragColor = vec4(pearl, alpha);
}
