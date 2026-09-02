#version 120

#include "/lib/common.glsl"

void main() {
    vec4 tex = texture2D(texture, texcoord.st) * glcolor;
    if (tex.a < 0.05) discard;

    // Soft lighting for vanilla cloud quads (fallback when volumetric is off)
    float dayness = clamp(luma(skyColor) * 2.5, 0.0, 1.0);
    vec3 litCol = mix(vec3(0.45, 0.48, 0.58), vec3(0.95, 0.96, 0.98), dayness);
    vec3 shade = mix(vec3(0.18, 0.20, 0.28), vec3(0.62, 0.66, 0.74), dayness);
    float h = clamp((worldPos.y - cameraPosition.y) * 0.02 + 0.55, 0.0, 1.0);
    tex.rgb = mix(shade, litCol, h) * (0.85 + 0.15 * tex.r);
    tex.rgb = mix(tex.rgb, fogColor * 0.85, rainStrength * 0.45);
    tex.a *= mix(0.72, 0.92, dayness);

    float dist = length(viewPos);
    tex.rgb = applyFog(tex.rgb, dist * 0.35, fogColor);

    /* DRAWBUFFERS:012 */
    gl_FragData[0] = tex;
    gl_FragData[1] = vec4(encodeNormal(vec3(0.0, 1.0, 0.0)), 0.0, 1.0);
    gl_FragData[2] = vec4(0.0, 0.0, 0.0, 1.0);
}
