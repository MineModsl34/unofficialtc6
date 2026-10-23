#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 vertexColor;
out vec3 projective;
void main(){
    gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);
    vertexColor=Color;
    // Native overlay SHORT fields carry raw bits of the source GL_EYE_LINEAR Q float.
    // Preserve homogeneous interpolation and divide in the fragment, never quantize Q.
    uint qbits=(uint(UV1.x)&65535u)|((uint(UV1.y)&65535u)<<16u);
    // GLSL150/OpenGL3.2 does not require ARB_shader_bit_encoding. Decode IEEE754
    // with integer masks and exp2 instead of a 3.3-only uintBitsToFloat intrinsic.
    uint exponent=(qbits>>23u)&255u;
    float mantissa=float(qbits&8388607u);
    float q=exponent==0u?mantissa*exp2(-149.0):(1.0+mantissa/8388608.0)*exp2(float(exponent)-127.0);
    if((qbits&2147483648u)!=0u)q=-q;
    projective=vec3(UV0,q);
}
