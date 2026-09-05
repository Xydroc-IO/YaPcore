#version 120

#include "/lib/common.glsl"
#include "/lib/varyings.glsl"

void main() {
    vec4 albedo = texture2D(texture, texcoord.st) * glcolor;
    if (albedo.a < 0.04) discard;

    float mist = clamp(rainStrength, 0.0, 1.0);
    // Subtle streaks — previous boost made a white noise curtain
    albedo.a = clamp(albedo.a * mix(0.55, 0.72, mist), 0.0, 0.42);
    albedo.rgb = mix(albedo.rgb, vec3(0.70, 0.76, 0.84), 0.18);
    albedo.rgb *= texture2D(lightmap, lmcoord.st).rgb;

    /* DRAWBUFFERS:012 */
    gl_FragData[0] = albedo;
    gl_FragData[1] = vec4(encodeNormal(normalize(normal)), 1.0);
    gl_FragData[2] = vec4(0.0, 0.0, 0.0, 1.0);
}
