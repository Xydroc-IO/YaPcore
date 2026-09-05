#ifndef YAP_VARYINGS_GLSL
#define YAP_VARYINGS_GLSL

// Shared gbuffer varyings — every paired .vsh must write all of these
// or Iris will warn about missing outs / patch zeros.
varying vec4 texcoord;
varying vec4 lmcoord;
varying vec4 glcolor;
varying vec3 viewPos;
varying vec3 worldPos;
varying vec3 normal;

#endif
