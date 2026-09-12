#version 320 es
#include <flutter/runtime_effect.glsl>

// 光学·径向转场（preview card ⇄ reader Hero 飞行内容滤镜）。
// 从 build/optical-transition-demo.html 的 radial 分支移植：
// 内容位于屏幕后方、视点前移 —— 位移 ∝ 像素到中心的距离（透镜式非均匀映射），
// 散焦/色散/变暗全部由同一「光程差」驱动，两端（amt=0）严格归零。
//
// ImageFilter.shader 约定（dart:ui painting.dart）：
//  - 第一个 uniform 必须是 vec2（引擎自动填入被过滤内容的尺寸，物理像素）；
//  - 第一个 sampler2D 由引擎绑定为过滤输入（child 的光栅）；
//  - Dart 侧 setFloat 的槽位只数非 sampler uniform：u_p=2, u_strength=3,
//    u_blur_max=4, u_off_ref=5, u_radius=6。

uniform vec2 u_size;
uniform sampler2D u_texture;
uniform float u_p;         // 0 = 卡片, 1 = 全屏（pop 方向天然 1→0）
uniform float u_strength;  // 光学强度（demo 默认 1）
uniform float u_blur_max;  // 散焦峰值半径（物理像素，demo 默认 24）
uniform float u_off_ref;   // 光程差归一化参考值（radial = 0.32）
uniform float u_radius;    // 当前圆角半径（物理像素，与 Dart 侧 ClipRRect 一致）

out vec4 fragColor;

const float PI = 3.14159265359;
const float GA = 2.399963229728653;  // 黄金角（模糊采样螺旋）

void main() {
  vec2 uv = FlutterFragCoord().xy / u_size;
  // GLES 后端纹理 y 轴反向（dart:ui painting.dart 文档要求）。
  // 径向位移场关于中心对称，翻转后自洽。
#ifdef IMPELLER_TARGET_OPENGLES
  uv.y = 1.0 - uv.y;
#endif

  float p = clamp(u_p, 0.0, 1.0);
  // 光学量统一驱动：两端归零、正中峰值 —— 首末帧等于原始内容。
  float amt = u_strength * sin(p * PI);

  // 像素到中心的矢量（按矩形宽高比校正 y）。
  float ra = u_size.y / max(u_size.x, 1.0);
  vec2 q = vec2(uv.x - 0.5, (uv.y - 0.5) * ra);
  float k = amt * 0.34;
  vec2 disp = q * k;              // 采样偏移（透镜式：中心 0，边缘最大）
  float off = length(q) * k;      // 光程差 —— 散焦/色散/变暗的唯一来源

  float offN = clamp(off / max(u_off_ref, 1e-4), 0.0, 1.0);
  // 面板越小绝对模糊越小（demo 的尺寸补偿）。
  float sizeK = clamp(u_size.x / 420.0, 0.22, 2.0);
  float blurPx = u_blur_max * offN * sizeK;

  vec2 tuv = clamp(uv + disp, vec2(0.0), vec2(1.0));
  vec4 col = vec4(0.0);
  float wsum = 0.0;
  if (blurPx < 0.6) {
    col = texture(u_texture, tuv);
    wsum = 1.0;
  } else {
    // 13 次黄金角螺旋高斯近似（同 demo）。
    vec2 radius = vec2(blurPx / u_size.x, blurPx / u_size.y);
    for (int i = 0; i < 13; i++) {
      float fi = float(i);
      float r = sqrt(fi + 0.5) / sqrt(13.0);
      float th = fi * GA;
      vec2 o = vec2(r * cos(th), r * sin(th)) * radius;
      float w = 1.0 - r;
      col += texture(u_texture, tuv + o) * w;
      wsum += w;
    }
    // 色散：沿位移方向拉开 R/B（只在有光程差处出现）。
    vec2 dir = normalize(disp + vec2(1e-5));
    float ca = offN * 2.6 * sizeK;
    vec2 caOff = dir * ca / u_size;
    col.r += texture(u_texture, clamp(tuv + caOff, vec2(0.0), vec2(1.0))).r * 0.6;
    col.b += texture(u_texture, clamp(tuv - caOff, vec2(0.0), vec2(1.0))).b * 0.6;
    wsum += 1.2;
  }
  col /= max(wsum, 1e-4);

  // 变暗：与光程差同源（边缘暗、中心亮）。
  float dim = 1.0 - offN * 0.34;
  col.rgb *= dim;

  // 玻璃边缘微高光（圆角矩形 SDF，半径与 Dart 侧 ClipRRect 同步，只在飞行
  // 中存在 —— amt 两端归零）。
  vec2 hp = (uv - 0.5) * u_size;
  float rad = max(u_radius, 0.5);
  vec2 b = u_size * 0.5 - rad;
  float sd = length(max(abs(hp) - b, 0.0)) - rad;
  float rim = (1.0 - smoothstep(0.0, 2.2, abs(sd))) * 0.30 * amt;
  col.rgb += rim * vec3(0.62, 0.70, 1.0);

  fragColor = vec4(col.rgb, col.a);
}
