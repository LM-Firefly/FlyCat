// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass.internal

/** 已编译玻璃着色器的缓存键。 */
internal const val GLASS_SHADER_KEY = "MiuixGlass"

/** 已编译轮廓蒙版着色器的缓存键。 */
internal const val GLASS_MASK_SHADER_KEY = "MiuixGlassMask"

/**
 * 轮廓定义，由材质着色器与蒙版着色器共享，两者对表面边界的位置永远不会产生分歧。
 */
private val GLASS_SDF_SOURCE: String = """
uniform float2 in_size;         // 元件尺寸
uniform float4 in_radii;        // 左上、右上、右下、左下各角半径
uniform float in_smoothing;     // 0 = 圆弧角，1 = 连续角

float roundedBoxSdf(float2 p, float2 b, float r) {
    float2 d = p - b + r;
    return min(max(d.x, d.y), 0.0) + length(max(d, float2(0.0))) - r;
}

float supercircleSdf(float2 off, float tile) {
    float2 q = max(float2(0.0), (float2(tile) + off) / tile);
    float hi = max(q.x, q.y);
    float ratio = (hi == 0.0) ? 0.0 : clamp(min(q.x, q.y) / hi, 0.0, 1.0);
    float fit = (((($FIT_4 * ratio + $FIT_3) * ratio + $FIT_2) * ratio + $FIT_1) * ratio + $FIT_0);
    float len = length(q);
    float distBase = (len + 1.0) - 1.0 / (1.0 - ratio * ratio * clamp(len, 0.0, 1.0) * fit);
    return min(max(tile + off.x, tile + off.y), 0.0) + tile * (distBase - 1.0);
}

float pickRadius(float2 local, float2 b) {
    float top = (local.x < b.x) ? in_radii.x : in_radii.y;
    float bottom = (local.x < b.x) ? in_radii.w : in_radii.z;
    return min((local.y < b.y) ? top : bottom, min(b.x, b.y));
}

float sdfShape(float2 p, float2 b, float r) {
    float box = roundedBoxSdf(p, b, r);
    float minHalf = min(b.x, b.y);
    if (in_smoothing <= 0.001 || r < $MIN_SUPERCIRCLE_RADIUS || (minHalf - r) <= 1.0) {
        return box;
    }
    float tile = min($TILE_SCALE * r, minHalf);
    return mix(box, supercircleSdf(p - b, tile), in_smoothing);
}

// 元件空间中一点到轮廓的距离，内部为负。
float silhouetteSdf(float2 local) {
    float2 b = in_size * 0.5;
    return sdfShape(abs(local - b), b, pickRadius(local, b));
}
"""

/** 边缘：表面在轮廓附近的弯曲方式，以及两盏定向光如何照亮它。 */
private val GLASS_RIM_SOURCE: String = """
const float3 kLumaWeights = float3(0.2126, 0.7152, 0.0722);

float luma(float3 color) {
    return dot(color, kLumaWeights);
}

float smooth5Map(float t) {
    t = clamp(mix(0.5, 1.0, t), 0.0, 1.0);
    t = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    return (t - 0.5) * 2.0;
}

// 轮廓处深度为 0，表面变平后为 1。`p` 决定收敛的快慢。
float edgeCurve(float x, float p) {
    if (x >= 0.85) return 1.0;
    float c = smooth5Map(sqrt(clamp(x, 0.0, 1.0)));
    return 1.0 - pow(1.0 - c, p);
}

// `edgeCurve` 对 `x` 的导数。
//
// 边缘轮廓以闭式求导。若用一像素差分，在本阶段运行的降采样层上一步就是带宽的六分之一，
// 这么粗的步长会把转折平均掉：带内算出的倾斜是真实值的数倍，边缘看起来就成了穹面而非棱线。
float edgeCurveSlope(float x, float p) {
    if (x >= 0.85) return 0.0;
    float t = sqrt(clamp(x, 0.000001, 1.0));
    float u = 0.5 + 0.5 * t;
    float um = u - 1.0;
    float c = smooth5Map(t);
    // d(smooth5Map(sqrt x))/dx，即五次多项式的导数经链式法则穿过平方根后的结果。
    float slope = 15.0 * u * u * um * um / t;
    return p * pow(max(1.0 - c, 0.0), p - 1.0) * slope;
}

// 距离场的增长方向：指向形状外侧的单位向量。未整形的距离场局部是一个平面，
// 因此半像素差分即可精确读出它。
float2 sdfGradient(float2 local) {
    float dx = silhouetteSdf(local + float2(0.5, 0.0)) - silhouetteSdf(local - float2(0.5, 0.0));
    float dy = silhouetteSdf(local + float2(0.0, 0.5)) - silhouetteSdf(local - float2(0.0, 0.5));
    float2 gradient = float2(dx, dy);
    float length2d = length(gradient);
    return (length2d < 0.0001) ? float2(0.0) : gradient / length2d;
}

// 把颜色提升向白色而不削顶：增益围绕随亮度变化的枢轴施加，亮像素移动得比暗像素少。
float3 liftToward(float3 color, float amount) {
    float v = luma(color);
    float w = smoothstep(0.0, 0.5, v);
    float k = mix(1.0 - v, v, w);
    float gain = 1.0 + smoothstep(0.0, 1.0, amount) * mix(0.75, 0.4, w);
    return (color + k) * gain - k;
}

float dynamicAdd(float3 color) {
    float toWhite = smoothstep(0.2, 1.0, distance(float3(1.0), color));
    return mix(0.8, mix(0.2, 1.0, toWhite), luma(color));
}

// acos 在这里不值它的开销；这是常用的基于 sqrt 的多项式拟合。
float lightFalloff(float3 normal, float3 direction, float intensity, float angleRange) {
    float dp = dot(normal, direction);
    float d = clamp(dp, -1.0, 1.0);
    float u = 1.0 - d;
    float positive = sqrt(u) * (u * (u * 0.04587603 + 0.10884186) + 1.41502593);
    float angle = (d < 0.0) ? (3.14159265 - positive) : positive;
    return max(dp, 0.0) * max(intensity * (1.0 - angle / max(angleRange, 0.0001)), 0.0);
}

// 两盏定向光照不到的位置边缘携带的光量。经标定使两段亮弧之间的侧面比表面高出约十几个灰阶。
const float kEdgeAmbient = 0.24;

// 光在边缘带内占据的范围（自轮廓向内量）。整条带负责弯折折射光线；画在带上只是一条发丝高光。
const float kRimBandFraction = 0.33;

// 两盏灯把 `color` 表面提亮的幅度，取值 `[0, 1]`。第二盏灯在 x、y 上朝向相反，
// 因此同一朝向能照亮形状两侧。
float rimLighten(float3 normal, float4 lightDir, float2 amounts, float3 color) {
    float3 lit = float3(normal.x, -normal.y, normal.z);
    float primary = lightFalloff(lit, lightDir.xyz, amounts.x, lightDir.w);
    float opposite = lightFalloff(lit, lightDir.xyz * float3(-1.0, -1.0, 1.0), amounts.y, lightDir.w);
    // 仅靠两盏相对的灯会留下两段亮弧和两段暗弧。加入一个与边缘偏离视线程度成正比的环境项，即可把它们接起来。
    float edgeFactor = clamp(length(lit.xy), 0.0, 1.0);
    float raw = clamp(primary + opposite + kEdgeAmbient * edgeFactor, 0.0, 1.0) * dynamicAdd(color);
    raw = pow(clamp(raw, 0.0, 1.0), 0.85);
    // 软拐点，背景越亮收得越紧：满光只把边缘提亮约一半，永不到纯白。
    // 没有它整条边缘带都会削顶，发丝高光便无处可立。
    float knee = mix(1.0, 0.7, smoothstep(0.0, 0.5, luma(color)));
    return raw / (raw + knee);
}

// 允许斜面达到的最陡程度（以纵向上升/横向跨度计）。
//
// 闭式导数在轮廓处可达数千，会把法线立起来，让两盏灯拿到全部增益。该上限让高光保持发丝宽。
const float kMaxBevelSlope = 1.7;
"""

/** 全分辨率的边缘光。 */
internal val GLASS_RIM_SHADER: String = """
$GLASS_SDF_SOURCE
$GLASS_RIM_SOURCE
uniform float4 in_rimEdge;      // 边缘宽度、边缘幂次、表面透明度、未使用
uniform float4 in_lightDir;     // 方向 xyz，角度范围 * pi
uniform float4 in_lightAmt;     // 强度、相对强度、未使用、未使用
uniform float4 in_surface;      // 材质自身颜色，代表下方的内容

half4 main(float2 coord) {
    float d = silhouetteSdf(coord);
    // 光只作用于边缘带的外侧三分之一，而非整条带。整条带负责弯折折射光线，
    // 但摊在整条带上的光看起来像斜面而不是棱线。
    float edge = max(in_rimEdge.x * kRimBandFraction, 1.0);
    // 超过带宽 85% 后表面已变平且背向两盏灯。
    if (d > 0.5 || -d >= edge * 0.85) return half4(0.0);

    float2 gradient = sdfGradient(coord);
    if (length(gradient) < 0.5) return half4(0.0);

    float depth = clamp(-d / edge, 0.0, 1.0);
    float slope = min(edgeCurveSlope(depth, in_rimEdge.y), kMaxBevelSlope);
    float3 normal = normalize(float3(gradient * slope, 1.0));
    float lighten = rimLighten(normal, in_lightDir, in_lightAmt.xy, in_surface.rgb);
    float3 added = max(liftToward(in_surface.rgb, lighten) - in_surface.rgb, float3(0.0));

    float coverage = clamp(0.5 - d, 0.0, 1.0);
    float3 light = added * (in_rimEdge.z * coverage);
    // 携带与光量相等的 alpha，而不是 0。着色器返回的是*预乘*颜色，
    // 通道值高于自身 alpha 的颜色会被削掉——这正是本阶段与泛光描边此前悄无声息画不出东西的原因。
    return half4(half3(light), half(clamp(max(max(light.r, light.g), light.b), 0.0, 1.0)));
}
"""

/** 以全分辨率把表面裁剪到轮廓。 */
internal val GLASS_MASK_SHADER: String = """
$GLASS_SDF_SOURCE
half4 main(float2 coord) {
    // 边界两侧各一个像素的覆盖率就是边缘所需的全部抗锯齿。
    return half4(half(clamp(0.5 - silhouetteSdf(coord), 0.0, 1.0)));
}
"""

/** 玻璃材质：在模糊背景上的一趟 AGSL/SkSL 处理。 */
internal val GLASS_SHADER: String = """
uniform shader child;

uniform float2 in_pad;          // 元件原点在填充图层中的位置
uniform float2 in_maxCoord;     // 填充图层的采样钳制范围
uniform float4 in_alphaEdge;    // alpha、边缘宽度、厚度、反射偏移
uniform float4 in_iorRefl;      // 折射率、反射强度、反射提亮、色彩 gamma
uniform float4 in_tint;         // 着色 rgb、着色强度
uniform float4 in_whiteMixBg;   // 色彩白度、色彩混合、背景饱和度、背景亮度
uniform float4 in_darker;       // 加暗起始、加暗结束、加暗量、内侧底部
uniform float4 in_lightDir;     // 方向 xyz，角度范围 * pi
uniform float4 in_lightAmt;     // 强度、相对强度、亮度调整量、溢出
uniform float4 in_lumCurve;     // 三次曲线系数 A、B、C、D
uniform float4 in_satBri;       // 饱和度、亮度、burn、unshade
uniform float4 in_edgePow;      // 边缘幂次、宽采样半径

const float3 kCurveWeights = float3(0.2125, 0.7153, 0.0721);
const float3 kShadowTint = float3(0.07874, 0.02848, 0.09278);

// ------------------------------------------------- 轮廓与边缘轮廓
$GLASS_SDF_SOURCE
$GLASS_RIM_SOURCE
// ------------------------------------------------------------------ 采样

float4 tapLayer(float2 local) {
    return float4(child.eval(clamp(local + in_pad, float2(0.5), in_maxCoord)));
}

// 光线会伸出轮廓之外，而录制图层只在模糊填充范围内携带背景。超出范围的采样返回透明黑，
// 会在形状周围晕出一圈暗环，因此回退到表面内部最近的一次采样。
float4 sampleBackdrop(float2 local) {
    float4 col = tapLayer(local);
    if (col.a < 0.02) {
        col = tapLayer(clamp(local, float2(0.5), in_size - 0.5));
    }
    if (col.a > 0.004) {
        col = float4(col.rgb / col.a, 1.0);
    }
    float t = smoothstep(in_darker.x, in_darker.y, luma(col.rgb)) * in_darker.z;
    col.rgb = mix(col.rgb, kShadowTint, t);
    return col;
}

// 代替第二道宽得多的模糊。
//
// 采样点落在沃格尔螺线上，均匀覆盖整个圆盘而不会在中间留空；
// 稀疏的环会把它留下的图案画在亮物体上。采样按覆盖率加权平均，
// 因此落在录制背景之外的采样点贡献为零，而不会把颜色拉向黑色。
float4 sampleWide(float2 local) {
    float2 outer = in_edgePow.yz;
    if (outer.x <= 0.5 || outer.y <= 0.5) return sampleBackdrop(local);
    // 色彩纹理以远大于其上任何表面的半径模糊，因此横跨一条栏或一个按钮时几乎是同一种颜色。
    // `in_edgePow.w` 表示这种近似的程度：为 1 时圆盘以表面为圆心，每个像素读到的平均值相同。
    float2 origin = mix(local, in_size * 0.5, in_edgePow.w);
    float3 sum = float3(0.0);
    float coverage = 0.0;
    for (int i = 0; i < 25; i++) {
        float index = float(i);
        float radius = sqrt((index + 0.5) * 0.04);
        float angle = index * 2.39996323;
        float4 tap = tapLayer(origin + float2(cos(angle), sin(angle)) * radius * outer);
        sum += tap.rgb;
        coverage += tap.a;
    }
    if (coverage < 0.0001) return sampleBackdrop(local);
    float3 color = sum / coverage;
    float t = smoothstep(in_darker.x, in_darker.y, luma(color)) * in_darker.z;
    return float4(mix(color, kShadowTint, t), 1.0);
}

// ------------------------------------------------------------------- 调色

float4 adjustColor(float4 color, float saturation, float brightness) {
    float l = dot(color.rgb, kCurveWeights);
    return float4(mix(float3(l), color.rgb, saturation) + float3(brightness * color.a), color.a);
}

float4 luminanceCurve(float4 color) {
    float a = max(color.a, 0.0001);
    float3 straight = color.rgb / a;
    float l = clamp(dot(straight, kCurveWeights), 0.0, 1.0);
    float adjusted = ((in_lumCurve.x * l + in_lumCurve.y) * l + in_lumCurve.z) * l + in_lumCurve.w;
    adjusted = clamp(adjusted, 0.0, 1.0);
    float scale = adjusted / max(l, 0.01) * smoothstep(0.0, 0.1, l);
    return float4(mix(straight, straight * scale, in_lightAmt.z) * a, color.a);
}

float4 processColor(float4 color) {
    color = luminanceCurve(color);
    color.rgb = adjustColor(color, in_satBri.x, in_satBri.y).rgb;
    return color;
}

float4 processGlassColor(float4 material, float2 local) {
    float4 wide = adjustColor(sampleWide(local), in_whiteMixBg.z, in_whiteMixBg.w);
    wide.rgb = mix(wide.rgb, float3(1.0), in_whiteMixBg.x);

    float lumin = clamp(luma(wide.rgb), 0.0, 1.0);
    float burn = pow(lumin, max(in_satBri.z, 0.5)) - 0.5;
    float colorRatio = 0.8 * mix(lumin, 1.0, (1.587 * burn * burn * burn) + 0.5);

    float3 ratio = mix(float3(1.0), wide.rgb, colorRatio);
    float mean = (ratio.r + ratio.g + ratio.b) * 0.33333333;
    // 把接近灰色的背景彻底拉回灰色，使玻璃不会带上虚假的偏色。
    ratio = mix(float3(mean), ratio, smoothstep(0.0, 0.4, distance(wide.rgb, float3(mean))));
    ratio = mix(ratio, in_tint.rgb, in_tint.a);

    material.rgb *= ratio;
    material.rgb = mix(material.rgb, ratio, in_whiteMixBg.y * colorRatio);
    return material;
}

float3 addLight(float3 color, float3 lightColor, float strength) {
    float toWhite = smoothstep(0.2, 1.0, distance(float3(1.0), color));
    return color + lightColor * strength * mix(0.3, 1.0, toWhite);
}

// ---------------------------------------------------------------------- 主流程

// 给定某点深入边缘的程度与边缘朝向，求该点的材质。
//
// `d` 由外部传入而不现场测量：调用者在一个像素跨度上推移它，而在这个距离内
// 距离场是一个平面，所以步进该值既精确又零成本。
float4 shadeMaterial(float2 local, float d, float2 gradient, float edge) {
    float depth = clamp(-d / edge, 0.0, 1.0);
    float shaped = edgeCurve(depth, in_edgePow.x);
    // 反射与附加光与边缘光一样只作用于带外侧三分之一。若摊满整条带，
    // 它们会变成比边缘宽好几倍的柔光晕——因为本趟无法分辨转折实际发生的位置。
    float nmlZ = edgeCurve(min(depth / kRimBandFraction, 1.0), 1.0);

    bool isFlat = shaped >= 1.0;
    float3 normal = isFlat
        ? float3(0.0, 0.0, 1.0)
        : normalize(float3(gradient * edgeCurveSlope(depth, in_edgePow.x), 1.0));

    float2 refractUv = local;
    if (!isFlat) {
        float3 bent = refract(float3(0.0, 0.0, -1.0), normal, 1.0 / max(in_iorRefl.x, 1.0));
        float thickness = in_alphaEdge.z;
        refractUv += bent.xy * mix((thickness - edge) * 2.0, thickness * 2.0, shaped);
    }
    float4 material = sampleBackdrop(refractUv);

    float rimZ = 1.0 - nmlZ;
    float4 mirrored = float4(0.0);
    if (rimZ > 0.000001) {
        float3 bounced = reflect(float3(0.0, 0.0, -1.0), normal);
        mirrored = sampleBackdrop(local + bounced.xy * (in_alphaEdge.w * (1.0 - shaped)));
        material = mix(material, mirrored, clamp(rimZ * in_iorRefl.y, 0.0, 1.0));
    }
    material.rgb = addLight(material.rgb, mirrored.rgb, (1.0 - shaped) * in_iorRefl.z);

    material = processColor(material);
    material = processGlassColor(material, local);

    // 底边的加法辉光。
    float bottom = smoothstep(1.0, 0.0, 1.0 - local.y / max(in_size.y, 1.0));
    material.rgb += bottom * bottom * in_darker.w;


    material = pow(max(material, float4(0.0)), float4(in_iorRefl.w));
    material.rgb = mix(clamp(material.rgb, 0.0, 1.0), in_tint.rgb, in_satBri.w);
    return material;
}

// ---------------------------------------------------------------------- 主流程

half4 main(float2 coord) {
    float2 local = coord - in_pad;
    float d = silhouetteSdf(local);
    // 越出轮廓：蒙版趟已在全分辨率上裁边，本趟只需伸出轮廓即可。
    if (d >= in_lightAmt.w) return half4(0.0);

    // 完全无阴影（unshade）的样式只是平面着色形状，跳过整个材质。
    if ((1.0 - in_satBri.w) <= 0.000001) {
        float flatAlpha = in_alphaEdge.x;
        return half4(half3(in_tint.rgb * flatAlpha), half(flatAlpha));
    }

    float edge = max(in_alphaEdge.y, 1.0);
    float2 gradient = sdfGradient(local);
    float4 material;

    // 法线精确后，边缘的受光部分只有几个显示像素宽——比本图层的一个像素还窄。
    // 每像素只读一次会混叠成阶梯，因此带内改为跨像素积分材质。
    // 一个维度就够：边缘沿距离场梯度方向变化，而沿其垂直方向不变。
    if (-d < edge && length(gradient) > 0.5) {
        material = shadeMaterial(local - gradient * 0.375, d - 0.375, gradient, edge) * 0.25;
        material += shadeMaterial(local, d, gradient, edge) * 0.5;
        material += shadeMaterial(local + gradient * 0.375, d + 0.375, gradient, edge) * 0.25;
    } else {
        material = shadeMaterial(local, d, gradient, edge);
    }

    float alpha = material.a * in_alphaEdge.x;
    return half4(half3(material.rgb * alpha), half(alpha));
}
"""

/** 已编译泛光描边着色器的缓存键。 */
internal const val GLASS_STROKE_SHADER_KEY = "MiuixGlassStroke"

/** 泛光描边：沿轮廓的受光边缘。 */
internal val GLASS_STROKE_SHADER: String = """
$GLASS_SDF_SOURCE
uniform float2 in_halfViewFloor; // 取整后的半尺寸，使折叠落在像素边界上
uniform float2 in_strokeBand;    // 描边宽度、内侧模糊半径
uniform float4 in_strokeColor;   // 描边 rgb、描边不透明度
uniform float in_strokeAlpha;    // 表面透明度
uniform float4 in_light1;        // 方向 xyz，强度
uniform float4 in_light1Color;   // 颜色 rgb，未使用
uniform float4 in_light2;        // 方向 xyz，强度
uniform float4 in_light2Color;   // 颜色 rgb，未使用

// 斜面法线：折叠到一个象限内构建，再按偏移符号展开——边缘是对称的，只需推理四分之一。
float3 rimNormal(float2 coord, float2 half2d, float sdf, float radius, float inner) {
    float2 offset = coord - in_halfViewFloor;
    float2 folded = abs(offset);
    // 把该点抬离表面。深度以边缘带为度量，高度以像素为度量。
    float depth = smoothstep(-inner, 0.0, sdf);
    float3 point = float3(folded, -sqrt(max(inner * inner - depth * depth, 0.0)));

    float bevel = max(radius, inner);
    float2 reference = half2d - bevel;
    reference.x = min(reference.x, folded.x);
    reference.y = min(reference.y, folded.y);
    float2 toPoint = point.xy - reference;
    float len = length(toPoint);
    reference += ((len > 0.0001) ? toPoint / len : float2(0.0, 1.0)) * (bevel - inner);

    // 参考点以内表面是平的，且正对两灯的反方向。
    if (folded.x < reference.x || folded.y < reference.y) return float3(0.0, 0.0, -1.0);

    float3 normal = normalize(point - float3(reference, 0.0));
    normal.xy *= sign(offset);
    return normal;
}

// MiBloomStrokeFilter 使用固定的垂直衰减轴，与光源位置无关。
// 保持衰减值带符号：原生着色器钳制的是乘积，而不是任一点积。
float3 rimLight(float3 normal, float axisY, float4 light, float3 color) {
    float falloff = axisY * normal.y;
    float lit = clamp(dot(normal, light.xyz) * falloff, 0.0, 1.0);
    return color * (lit * lit * light.w);
}

half4 main(float2 coord) {
    float2 half2d = in_size * 0.5;
    float2 folded = abs(coord - half2d);
    float radius = pickRadius(coord, half2d);
    float inner = max(in_strokeBand.y, 0.5);
    float bevel = max(radius, inner);

    // 内部没有可照亮的边缘，而它占了表面的绝大部分。
    if (folded.x < half2d.x - bevel && folded.y < half2d.y - bevel) return half4(0.0);

    float sdf = silhouetteSdf(coord);
    // 紧贴外缘的近阶跃带，通过叠画两次使其平方。
    float width = max(in_strokeBand.x, 0.75);
    float band = smoothstep(-width, -width + 1.0, sdf);
    float3 rgb = in_strokeColor.rgb * (in_strokeColor.a * band * band);

    float3 normal = rimNormal(coord, half2d, sdf, radius, inner);
    rgb += rimLight(normal, -1.0, in_light1, in_light1Color.rgb);
    rgb += rimLight(normal, 1.0, in_light2, in_light2Color.rgb);

    // 只有光：alpha 保持为 0，使透明表面永远不会因边缘而获得不透明度。
    float coverage = clamp(0.5 - sdf, 0.0, 1.0);
    float3 light = rgb * (in_strokeAlpha * coverage);
    // 预乘，因此 alpha 必须与光量一致——原因见边缘趟中“为 0 会丢失”的说明。
    return half4(half3(light), half(clamp(max(max(light.r, light.g), light.b), 0.0, 1.0)));
}
"""

/** 已编译阴影着色器的缓存键。 */
internal const val GLASS_SHADOW_SHADER_KEY = "MiuixGlassShadow"

/** 表面投下的阴影，与表面本身取自同一距离场。 */
internal val GLASS_SHADOW_SHADER: String = """
$GLASS_SDF_SOURCE
uniform float2 in_shadowOffset;  // 位移（像素）
uniform float2 in_shadowShape;   // 延伸范围、扩散度
uniform float4 in_shadowColor;   // rgb、强度

half4 main(float2 coord) {
    float d = silhouetteSdf(coord - in_shadowOffset);
    float reach = max(in_shadowShape.x, 0.5);
    float t = clamp(1.0 - d / reach, 0.0, 1.0);
    // 扩散度重塑衰减曲线：低于一半时紧贴边缘，高于一半时铺开变薄。
    float falloff = pow(t, 1.0 / max(in_shadowShape.y, 0.05));
    float a = falloff * in_shadowColor.a;
    return half4(half3(in_shadowColor.rgb * a), half(a));
}
"""

internal const val GLASS_COLOR_BLEND_SHADER_KEY = "MiuixGlassColorBlend"

/** 玻璃表面铺在模糊背景上的色彩处理。 */
internal val GLASS_COLOR_BLEND_SHADER: String = """
uniform shader child;

uniform float4 in_blend0;     // 图层 0 颜色（直通）；w = 用量
uniform float4 in_blend1;     // 图层 1
uniform float4 in_blend2;     // 图层 2
uniform float4 in_blendMode;  // 图层 0..2 的模式；w = 生效图层数

const float3 kLumWeights = float3(0.3, 0.59, 0.11);

float lumOf(float3 c) {
    return dot(c, kLumWeights);
}

// 把颜色拉回单位立方体内而不改变其亮度——正是它让不可分离的混合模式
// 不会削顶成与计算值不同的色调。
float3 clipColor(float3 c) {
    float l = lumOf(c);
    float n = min(min(c.r, c.g), c.b);
    float x = max(max(c.r, c.g), c.b);
    if (n < 0.0) c = l + (c - l) * l / max(l - n, 0.0001);
    if (x > 1.0) c = l + (c - l) * (1.0 - l) / max(x - l, 0.0001);
    return c;
}

float3 setLum(float3 c, float l) {
    return clipColor(c + (l - lumOf(c)));
}

float softLightChannel(float d, float s) {
    float dd = (d <= 0.25) ? ((16.0 * d - 12.0) * d + 4.0) * d : sqrt(d);
    return (s <= 0.5)
        ? d - (1.0 - 2.0 * s) * d * (1.0 - d)
        : d + (2.0 * s - 1.0) * (dd - d);
}

float3 blendLayer(float3 d, float4 layer, float mode) {
    float3 s = layer.rgb;
    float a = layer.a;
    int m = int(mode + 0.5);
    float3 b;
    if (m == 1) {
        // plus darker：减法混合，压暗背景而不是把背景变灰
        return clamp(d - a * (1.0 - s), 0.0, 1.0);
    } else if (m == 2) {
        // plus lighter：加法提亮
        return clamp(d + a * s, 0.0, 1.0);
    } else if (m == 3) {
        b = float3(
            softLightChannel(d.r, s.r),
            softLightChannel(d.g, s.g),
            softLightChannel(d.b, s.b)
        );
    } else if (m == 4) {
        // hard light：由图层决定
        b = mix(1.0 - 2.0 * (1.0 - s) * (1.0 - d), 2.0 * s * d, step(s, float3(0.5)));
    } else if (m == 5) {
        // overlay：由背景决定
        b = mix(1.0 - 2.0 * (1.0 - d) * (1.0 - s), 2.0 * d * s, step(d, float3(0.5)));
    } else if (m == 6) {
        b = setLum(d, lumOf(s));
    } else if (m == 7) {
        b = min(float3(1.0), d / max(1.0 - s, float3(0.0001)));
    } else if (m == 8) {
        b = 1.0 - min(float3(1.0), (1.0 - d) / max(s, float3(0.0001)));
    } else {
        b = s;
    }
    return clamp(mix(d, b, a), 0.0, 1.0);
}

half4 main(float2 coord) {
    half4 src = child.eval(coord);
    float a = float(src.a);
    if (a <= 0.0) return src;
    float3 d = float3(src.rgb) / a;
    d = blendLayer(d, in_blend0, in_blendMode.x);
    if (in_blendMode.w > 1.5) d = blendLayer(d, in_blend1, in_blendMode.y);
    if (in_blendMode.w > 2.5) d = blendLayer(d, in_blend2, in_blendMode.z);
    return half4(half3(d * a), src.a);
}
"""
