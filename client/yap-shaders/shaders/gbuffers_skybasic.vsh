#version 120

#include "/lib/common.glsl"
#include "/lib/sky.glsl"

varying vec3 viewDir;

void main() {
    vec4 view = gl_ModelViewMatrix * gl_Vertex;
    viewPos = view.xyz;
    viewDir = normalize((gbufferModelViewInverse * view).xyz);
    texcoord = gl_MultiTexCoord0;
    glcolor = gl_Color;
    gl_Position = gl_ProjectionMatrix * view;
}
