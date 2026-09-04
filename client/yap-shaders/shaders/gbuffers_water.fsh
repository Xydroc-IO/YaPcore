#version 120

#include "/lib/common.glsl"
#include "/lib/water.glsl"
#include "/lib/sky.glsl"

void main() {
    vec3 Nworld = yapWaterNormal(worldPos.xz, frameTimeCounter, vec3(0.0, 1.0, 0.0));
    // Micro-ripple from pack texture (grayscale height-ish)
    vec4 tex = texture2D(texture, texcoord.st);
    float micro = luma(tex.rgb) - 0.5;
    Nworld = normalize(Nworld + vec3(micro * 0.35, 0.0, micro * 0.28));

    vec3 Nview = normalize(mat3(gbufferModelView) * Nworld);

    vec3 V = normalize(-viewPos);
    vec3 L = normalize(sunPosition);
    float depthMix = clamp(length(viewPos) * 0.008, 0.0, 1.0);

    vec3 absorb = yapWaterAlbedo(depthMix, rainStrength);
    // Slight biome / vertex tint from vanilla (keeps rivers/swamps distinct)
    absorb *= mix(vec3(1.0), glcolor.rgb, 0.35);

    vec3 R = reflect(-V, Nview);
    vec3 skyR = yapSkyReflectionFallback(R, sunPosition, skyColor, fogColor);
    // Water F0 ≈ 0.02
    float F = yapFresnelSchlick(max(dot(Nview, V), 0.0), 0.02);
    F = clamp(F, 0.04, 0.88);

    // Mostly absorption in gbuffer; composite adds SSR / refraction
    vec3 col = mix(absorb * 0.95, skyR * 0.85, F * 0.55);
    col += yapWaterSpecular(Nview, V, L);

    // Clearer water than the old opaque teal sheet
    float alpha = mix(0.42, 0.82, F);
    alpha = mix(alpha, tex.a, 0.25);

    /* DRAWBUFFERS:012 */
    gl_FragData[0] = vec4(col, alpha);
    gl_FragData[1] = vec4(encodeNormal(Nview), depthMix, 1.0);
    gl_FragData[2] = vec4(1.0, depthMix, lmcoord.t, 1.0);
}
