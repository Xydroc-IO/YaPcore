#ifndef YAP_SSR_GLSL
#define YAP_SSR_GLSL

// Screen-space reflection raymarch (YaP original) — denser steps for wet look.
vec4 yapSSR(vec3 viewPos, vec3 viewNormal, sampler2D colorTex, sampler2D depthTex) {
#ifdef SSR
    vec3 V = normalize(viewPos);
    vec3 R = reflect(V, normalize(viewNormal));

    if (R.z > 0.2) {
        return vec4(0.0);
    }

    float stepSize = 0.28 + length(viewPos) * 0.010;
    vec3 ray = viewPos;
    vec2 hitUV = vec2(0.0);
    bool hit = false;

    int steps = int(SSR_STEPS);
    steps = clamp(steps, 8, 64);

    for (int i = 0; i < 64; i++) {
        if (i >= steps) break;
        ray += R * stepSize;
        stepSize *= 1.035;

        vec3 screen = viewToScreen(ray);
        if (screen.x < 0.0 || screen.x > 1.0 || screen.y < 0.0 || screen.y > 1.0
         || screen.z < 0.0 || screen.z > 1.0) {
            break;
        }

        float sceneZ = texture2D(depthTex, screen.xy).r;
        if (sceneZ >= 1.0) continue;

        vec3 sceneView = screenToView(vec3(screen.xy, sceneZ));
        float delta = ray.z - sceneView.z;

        if (delta > 0.0 && delta < stepSize * 3.2) {
            hitUV = screen.xy;
            hit = true;
            break;
        }
    }

    if (!hit) {
        return vec4(0.0);
    }

    float edge = smoothstep(0.0, 0.08, hitUV.x) * smoothstep(1.0, 0.92, hitUV.x)
               * smoothstep(0.0, 0.08, hitUV.y) * smoothstep(1.0, 0.92, hitUV.y);
    vec3 col = texture2D(colorTex, hitUV).rgb;
    return vec4(col * edge, edge);
#else
    return vec4(0.0);
#endif
}

#endif
