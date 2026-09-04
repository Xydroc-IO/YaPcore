#version 120

#include "/lib/common.glsl"
#include "/lib/caustics.glsl"

void main() {
    vec4 albedo = texture2D(texture, texcoord.st) * glcolor;
    if (albedo.a < 0.1) discard;
    vec3 light = texture2D(lightmap, lmcoord.st).rgb;
    albedo.rgb *= light;

    // Shore / shallow bed caustics hint (strengthened in composite for true underwater)
    vec3 N = normalize(normal);
    albedo.rgb = yapApplyCaustics(albedo.rgb, worldPos, N, sunPosition, 0.25 * (1.0 - rainStrength));

    float dist = length(viewPos);
    albedo.rgb = applyFog(albedo.rgb, dist, fogColor);

    /* DRAWBUFFERS:012 */
    gl_FragData[0] = albedo;
    gl_FragData[1] = vec4(encodeNormal(N), 0.0, 1.0);
    gl_FragData[2] = vec4(0.0, 0.0, 0.0, 1.0); // not water
}
