#version 120

#include "/lib/common.glsl"

void main() {
    vec4 albedo = texture2D(texture, texcoord.st) * glcolor;
    // Vanilla rain/snow is thin translucent streaks — keep it readable, not a grey sheet.
    if (albedo.a < 0.05) discard;

    float mist = clamp(rainStrength, 0.0, 1.0);
    // Soften density and brighten toward cool rain-streak color.
    albedo.a *= mix(0.42, 0.28, mist);
    albedo.rgb = mix(albedo.rgb, vec3(0.78, 0.84, 0.92), 0.35);
    albedo.rgb *= mix(1.0, 0.92, mist);
    albedo.rgb *= texture2D(lightmap, lmcoord.st).rgb;

    /* DRAWBUFFERS:012 */
    gl_FragData[0] = albedo;
    gl_FragData[1] = vec4(encodeNormal(normalize(normal)), 0.0, 1.0);
    gl_FragData[2] = vec4(0.0, 0.0, 0.0, 1.0);
}
