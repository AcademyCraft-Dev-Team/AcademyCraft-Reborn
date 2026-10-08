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
    vec2 phase = vec2(materialPhase * 0.39, materialPhase * 0.61);
    float broad = texture(Sampler0, texCoord * 0.021 + phase).r;
    float detail = texture(Sampler0, texCoord * 0.058 - phase).r;
    vec4 feather = academyDarkmatterFeather(texCoord, materialPhase, 0.25);
    vec3 pearl = feather.rgb;
    float alpha = feather.a * vertexColor.a;
#if MATERIAL_STYLE == 1
    // The illumination patch borrows the feather material, with an incomplete altered-light body.
    alpha *= smoothstep(0.25, 0.75, broad) * (0.45 + detail * 0.55);
#elif MATERIAL_STYLE == 2
    // The complete white body grows from its root toward its tip, without a hollow circular front.
    float growth = smoothstep(0.0, 0.62, materialProgress);
    alpha *= smoothstep(-0.09, 0.04, growth - texCoord.y);
#elif MATERIAL_STYLE == 4
    // Restrained local contact softness; this is a small impulse rather than a luminous outline.
    float radius = length(p * vec2(0.85, 0.9));
    float core = 1.0 - smoothstep(0.06, 0.70, radius);
    alpha = vertexColor.a * core * (0.78 + broad * 0.22);
    pearl = mix(academyDarkmatterPearl(texCoord, materialPhase, 0.2), vec3(0.98), core * 0.6);
#elif MATERIAL_STYLE == 3
    // Separate vane bundles lift off and vanish. The remaining white body is still filled.
    float bundle = fract((texCoord.y - abs(p.x) * 0.22) * 9.0 + materialPhase * 0.1);
    float remaining = broad * 0.62 + detail * 0.22 + bundle * 0.16;
    alpha *= smoothstep(materialProgress * 0.78, materialProgress * 0.78 + 0.16, remaining);
#endif
    // The depth attachment tests occlusion. A very narrow intersection fade keeps surface patches readable.
    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    float sceneDepth = texture(Sampler1, screenUv).r;
    alpha *= smoothstep(0.0, 0.000015, gl_FragCoord.z - sceneDepth);
    if (alpha < 0.002) discard;
    vec3 color = vertexColor.rgb * pearl;
    fragColor = vec4(color, alpha);
}
