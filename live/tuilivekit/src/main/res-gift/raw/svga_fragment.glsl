// SVGA 片段着色器 - OpenGL ES 3.0
#version 300 es
precision mediump float;

// 从顶点着色器接收
in vec2 v_texCoord;
in float v_alpha;

// 纹理采样器
uniform sampler2D u_texture;

// 输出颜色
out vec4 fragColor;

void main() {
    vec4 texColor = texture(u_texture, v_texCoord);
    fragColor = texColor * v_alpha;
}
