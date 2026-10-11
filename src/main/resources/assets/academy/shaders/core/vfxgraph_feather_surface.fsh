#version 330
#moj_import <academy:darkmatter_feather.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
in vec2 texCoord;
in vec4 vertexColor;
in float materialPhase;
in float edgeActivity;
in float surfaceBoundary;
in float materialPenetration;
out vec4 fragColor;

void main() {
    // Six-wing roots narrow independently of the shared feather material used by other skills.
    float shaft = academyFeatherShaftX(texCoord.y);
    float rootWidth = mix(0.72, 1.0, smoothstep(0.02, 0.30, texCoord.y));
    vec2 featherUv = vec2(((texCoord.x * 2.0 - 1.0 - shaft) / rootWidth + shaft + 1.0) * 0.5, texCoord.y);
    vec4 feather = academyDarkmatterFeather(featherUv, materialPhase, edgeActivity);
    float alpha = vertexColor.a * feather.a;
    alpha *= mix(smoothstep(0.0, 0.04, surfaceBoundary), 1.0, clamp(materialPenetration, 0.0, 1.0));
    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    alpha *= smoothstep(0.0, 0.000015, gl_FragCoord.z - texture(Sampler1, screenUv).r);
    if (alpha < 0.002) discard;
    fragColor = vec4(vertexColor.rgb * feather.rgb, alpha);
}
