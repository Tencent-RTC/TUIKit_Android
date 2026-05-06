// SVGA 顶点着色器 - OpenGL ES 3.0
#version 300 es

// 顶点属性
layout(location = 0) in vec2 a_position;   // 顶点位置
layout(location = 1) in vec2 a_texCoord;   // 纹理坐标
layout(location = 2) in float a_alpha;     // 透明度

// 变换 Uniform
uniform mat4 u_projection;    // 投影矩阵（正交）
uniform mat3 u_transform;     // 2D 仿射变换矩阵

// 传递到片段着色器
out vec2 v_texCoord;
out float v_alpha;

void main() {
    vec3 transformed = u_transform * vec3(a_position, 1.0);
    gl_Position = u_projection * vec4(transformed.xy, 0.0, 1.0);
    v_texCoord = a_texCoord;
    v_alpha = a_alpha;
}
