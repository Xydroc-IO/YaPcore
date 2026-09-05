#version 120

#include "/lib/common.glsl"
#include "/lib/varyings.glsl"

void main() {
    vec4 albedo = texture2D(texture, texcoord.st) * glcolor;
    // Hard cutout — soft/mip mid-alphas cause screen-door shredding on foliage.
    if (albedo.a < 0.5) discard;
    albedo.a = 1.0;
    vec3 light = texture2D(lightmap, lmcoord.st).rgb;
    // Keep a floor so night canopies don't crush to black static.
    light = max(light, vec3(0.08));
    albedo.rgb *= light;

    vec3 N = normalize(normal);

    float dist = length(viewPos);
    albedo.rgb = applyFog(albedo.rgb, dist, fogColor);

    /* DRAWBUFFERS:012 */
    gl_FragData[0] = albedo;
    gl_FragData[1] = vec4(encodeNormal(N), 1.0);
    gl_FragData[2] = vec4(0.0, 0.0, 0.0, 1.0);
}
