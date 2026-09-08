#version 120

#include "/lib/common.glsl"
#include "/lib/varyings.glsl"
#include "/lib/water.glsl"
#include "/lib/sky.glsl"

varying float yapEntityId;

void main() {
    bool isWater = abs(yapEntityId - 10001.0) < 0.5;

    // Iris routes ALL translucent terrain (glass, ice, slime, …) through gbuffers_water.
    // Without this branch they inherit waves / SSR / water tint.
    if (!isWater) {
        vec4 albedo = texture2D(texture, texcoord.st) * glcolor;
        if (albedo.a < 0.01) {
            discard;
        }
        albedo.rgb *= texture2D(lightmap, lmcoord.st).rgb;
        float dist = length(viewPos);
        albedo.rgb = applyFog(albedo.rgb, dist, fogColor);
        /* DRAWBUFFERS:012 */
        gl_FragData[0] = albedo;
        gl_FragData[1] = vec4(encodeNormal(normalize(normal)), 1.0);
        gl_FragData[2] = vec4(0.0, 0.0, 0.0, 1.0); // not water
        return;
    }

    vec3 baseN = normalize(mat3(gbufferModelViewInverse) * normal);
    float faceUp = clamp(baseN.y, -1.0, 1.0);
    // Flat lakes / ponds ≈ 1 · streams ~0.4–0.8 · waterfalls / walls ≈ 0
    float surfaceAmt = smoothstep(0.28, 0.72, faceUp);

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

    vec3 V = normalize(-viewPos);
    vec3 L = normalize(sunPosition);
    if (dot(L, L) < 0.01) {
        L = normalize(moonPosition);
    }

    // --- Waterfalls / steep flowing faces: no lake SSR, downward flow look ---
    if (surfaceAmt < 0.45) {
        vec3 Nworld = yapWaterfallNormal(worldPos, frameTimeCounter, baseN);
        // Blend a little with geometric face so panes stay readable
        Nworld = normalize(mix(baseN, Nworld, 0.85));
        vec3 Nview = normalize(mat3(gbufferModelView) * Nworld);

        vec4 tex = texture2D(texture, texcoord.st);
        vec3 body = yapWaterfallAlbedo(rainStrength);
        // Pack flow texture for foam streaks — keep subdued so it doesn't sparkle
        vec3 foam = tex.rgb * glcolor.rgb;
        float streak = clamp(foam.r * 0.55 + foam.g * 0.35, 0.0, 1.0);
        vec3 col = mix(body, body * 1.25 + vec3(0.12), streak * 0.35);
        col *= texture2D(lightmap, lmcoord.st).rgb;

        float cosNV = max(dot(Nview, V), 0.0);
        float F = yapFresnelSchlick(cosNV, 0.020);
        F = clamp(F * 0.45, 0.04, 0.40);
        vec3 skyR = yapSkyReflectionFallback(reflect(-V, Nview), sunPosition, skyColor, fogColor);
        col = mix(col, skyR, F * 0.55);
        col += yapWaterSpecular(Nview, V, L) * 0.35;

        float alpha = mix(0.42, 0.68, streak * 0.5 + F);
        alpha = clamp(alpha, 0.38, 0.75);

        float dist = length(viewPos);
        col = applyFog(col, dist, fogColor);

        /* DRAWBUFFERS:012 */
        gl_FragData[0] = vec4(col, alpha);
        gl_FragData[1] = vec4(encodeNormal(Nview), 1.0);
        // mat.r low → composite skips lake SSR/refraction (that made falls look like glass)
        gl_FragData[2] = vec4(0.25, 0.35, lmcoord.t, 1.0);
        return;
    }

    // --- Flat / gentle surface water: Gerstner + composite SSR ---
    vec3 Nworld = yapWaterNormal(worldPos.xz, frameTimeCounter, baseN);
    vec3 Nview = normalize(mat3(gbufferModelView) * Nworld);

    float depthMix = clamp(length(viewPos) * 0.0065, 0.0, 1.0);

    vec3 absorb = yapWaterAlbedo(depthMix, rainStrength);
    absorb *= mix(vec3(1.0), glcolor.rgb, 0.22);

    vec3 R = reflect(-V, Nview);
    vec3 skyR = yapSkyReflectionFallback(R, sunPosition, skyColor, fogColor);
    float cosNV = max(dot(Nview, V), 0.0);
    float F = yapFresnelSchlick(cosNV, 0.028);
    // Looking straight down still needs a wet sheen (aerial lakes)
    F = clamp(F + 0.14 * (1.0 - cosNV), 0.12, 0.94);
    // Streams (partial surfaceAmt) get softer reflections
    F *= mix(0.55, 1.0, smoothstep(0.45, 0.90, surfaceAmt));

    vec3 col = mix(absorb * 0.78, skyR * 1.02, F * 0.82);
    col += yapWaterSpecular(Nview, V, L) * mix(0.55, 1.15, surfaceAmt);

    float alpha = mix(0.28, 0.86, F);
    alpha *= mix(1.0, 0.88, cosNV);

    /* DRAWBUFFERS:012 */
    gl_FragData[0] = vec4(col, alpha);
    gl_FragData[1] = vec4(encodeNormal(Nview), 1.0);
    // mat.r encodes surface strength for composite ( >0.5 = lake SSR )
    gl_FragData[2] = vec4(mix(0.55, 1.0, smoothstep(0.45, 0.85, surfaceAmt)), depthMix, lmcoord.t, 1.0);
}
