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
    vec4 feather = academyDarkmatterFeather(texCoord, materialPhase, edgeActivity);
    float alpha = vertexColor.a * feather.a;
    alpha *= mix(smoothstep(0.0, 0.04, surfaceBoundary), 1.0, clamp(materialPenetration, 0.0, 1.0));
    vec2 screenUv = gl_FragCoord.xy / textureSize(Sampler1, 0);
    alpha *= smoothstep(0.0, 0.000015, gl_FragCoord.z - texture(Sampler1, screenUv).r);
    if (alpha < 0.002) discard;
    fragColor = vec4(vertexColor.rgb * feather.rgb, alpha);
}
