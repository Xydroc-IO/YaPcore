#version 120

#include "/lib/common.glsl"
#include "/lib/varyings.glsl"
#include "/lib/water.glsl"
#include "/lib/sky.glsl"

void main() {
    vec3 baseN = normalize(mat3(gbufferModelViewInverse) * normal);

    // Keep grey-slab fix: underwater only draw soft surface underside
    if (isEyeInWater == 1) {
        if (baseN.y > -0.55) {
            discard;
        }
        vec3 Nworld = yapWaterNormal(worldPos.xz, frameTimeCounter, baseN);
        vec3 Nview = normalize(mat3(gbufferModelView) * Nworld);
        vec3 V = normalize(-viewPos);
        float facing = clamp(-dot(Nview, V), 0.0, 1.0);
        vec3 tint = yapWaterAlbedo(0.35, rainStrength) * mix(vec3(1.0), glcolor.rgb, 0.15);
        float F = yapFresnelSchlick(facing, 0.02);
        vec3 col = mix(tint, vec3(0.45, 0.55, 0.65), F * 0.35);
        float alpha = mix(0.10, 0.28, F);
        /* DRAWBUFFERS:012 */
        gl_FragData[0] = vec4(col, alpha);
        gl_FragData[1] = vec4(encodeNormal(Nview), 1.0);
        gl_FragData[2] = vec4(0.0, 0.35, lmcoord.t, 1.0);
        return;
    }

    // Pure procedural normals — pack water textures caused sparkle/static
    vec3 Nworld = yapWaterNormal(worldPos.xz, frameTimeCounter, baseN);
    vec3 Nview = normalize(mat3(gbufferModelView) * Nworld);

    vec3 V = normalize(-viewPos);
    vec3 L = normalize(sunPosition);
    // Moon fill at night so specular doesn't die
    if (dot(L, L) < 0.01) {
        L = normalize(moonPosition);
    }
    float depthMix = clamp(length(viewPos) * 0.0065, 0.0, 1.0);

    vec3 absorb = yapWaterAlbedo(depthMix, rainStrength);
    absorb *= mix(vec3(1.0), glcolor.rgb, 0.22);

    vec3 R = reflect(-V, Nview);
    vec3 skyR = yapSkyReflectionFallback(R, sunPosition, skyColor, fogColor);
    float cosNV = max(dot(Nview, V), 0.0);
    float F = yapFresnelSchlick(cosNV, 0.028);
    // Looking straight down still needs a wet sheen (aerial lakes)
    F = clamp(F + 0.14 * (1.0 - cosNV), 0.12, 0.94);

    vec3 col = mix(absorb * 0.78, skyR * 1.02, F * 0.82);
    col += yapWaterSpecular(Nview, V, L) * 1.15;

    float alpha = mix(0.28, 0.86, F);
    alpha *= mix(1.0, 0.88, cosNV);

    /* DRAWBUFFERS:012 */
    gl_FragData[0] = vec4(col, alpha);
    gl_FragData[1] = vec4(encodeNormal(Nview), 1.0);
    gl_FragData[2] = vec4(1.0, depthMix, lmcoord.t, 1.0);
}
