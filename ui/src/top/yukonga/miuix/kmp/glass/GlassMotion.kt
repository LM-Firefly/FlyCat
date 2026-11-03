// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.anim.folmeSpring

/** 玻璃表面与组件使用的动效。 */
object GlassMotion {

    /**
     * 把阻尼比与响应时间换算为 Compose [SpringSpec]。
     *
     * @param damping 阻尼比。1 表示无过冲收敛；小于 1 会过冲一次。
     * @param response 无阻尼振荡的周期（秒）。越小越快，且必须大于零——刚度由它相除得到。
     */
    @Stable
    fun <T> springOf(damping: Float, response: Float): SpringSpec<T> {
        require(response > 0f) { "A spring's response must be greater than zero, was $response" }
        return folmeSpring(damping, response)
    }

    /** 变换用弹簧，收敛阈值采用 Folme 的 `ValueTarget` 而非 Compose 的。 */
    @Stable
    private fun transformSpring(damping: Float, response: Float): SpringSpec<Float> = folmeSpring(
        damping = damping,
        response = response,
        visibilityThreshold = TRANSFORM_VISIBILITY_THRESHOLD,
    )

    /** 复用 Folme 的数值阈值来归一化弹出进度，不改变弹簧自身的物理特性。 */
    @Stable
    internal fun popupProgressSpring(spec: SpringSpec<Float>): SpringSpec<Float> = spring(
        dampingRatio = spec.dampingRatio,
        stiffness = spec.stiffness,
        visibilityThreshold = TRANSFORM_VISIBILITY_THRESHOLD,
    )

    /**
     * 底部栏胶囊前缘行进所用的弹簧。后缘使用更慢的 [navIndicatorTrail]，胶囊因此在移动时被拉长。
     */
    @Stable
    fun <T> navIndicator(): SpringSpec<T> = springOf(0.7f, 0.4f)

    /** 胶囊后缘行进所用的弹簧。更慢，因此胶囊被拉长。 */
    @Stable
    fun <T> navIndicatorTrail(): SpringSpec<T> = springOf(0.75f, 0.5f)

    /** 底部指示器以临界阻尼弹簧跟随拖拽目标。 */
    @Stable
    fun <T> navDragFollow(): SpringSpec<T> = springOf(1f, 0.15f)

    /** 行进方向的前缘用 [navIndicator]，后缘用 [navIndicatorTrail]。 */
    @Stable
    fun <T> edgeSpring(leading: Boolean): SpringSpec<T> = if (leading) navIndicator() else navIndicatorTrail()

    /** 胶囊填充在手指按下时到来。临界阻尼，且很快。 */
    @Stable
    fun navPressEnter(): SpringSpec<Color> = springOf(1f, 0.2f)

    /** 释放时胶囊填充退去。比按下慢，因此抬起看起来是淡出。 */
    @Stable
    fun navPressExit(): SpringSpec<Color> = springOf(0.95f, 0.35f)

    /**
     * 释放时菜单行填充退去。比底栏的更短，因为行往往在底栏淡出完成前就已消失。
     */
    @Stable
    fun popupPressExit(): SpringSpec<Color> = springOf(1f, 0.15f)

    /** 底部栏显示与隐藏所用的弹簧。 */
    @Stable
    fun <T> navShowHide(): SpringSpec<T> = springOf(1f, 0.3f)

    /** 默认弹簧，分段标签指示器用它移动。 */
    @Stable
    fun <T> default(): SpringSpec<T> = springOf(0.95f, 0.35f)

    /** 次级弹出层展开与收起所用的弹簧。 */
    @Stable
    fun secondaryPopup(expanding: Boolean): SpringSpec<Float> = if (expanding) {
        transformSpring(0.95f, 0.35f)
    } else {
        transformSpring(0.95f, 0.2f)
    }

    /** 次级弹出层遮罩的 alpha 弹簧，带自己的可见性阈值。 */
    @Stable
    internal fun secondaryPopupMask(expanding: Boolean): SpringSpec<Float> = folmeSpring(
        damping = 0.95f,
        response = if (expanding) 0.35f else 0.2f,
        visibilityThreshold = POPUP_MASK_MIN_VISIBLE_CHANGE * 0.75f,
    )

    /** 低于一个 8 位 alpha 台阶时遮罩隐藏。 */
    internal const val POPUP_MASK_MIN_VISIBLE_CHANGE: Float = 1f / 256f

    /** 顶栏展开回大标题。 */
    @Stable
    fun <T> barExpand(): SpringSpec<T> = springOf(1f, 0.3f)

    /** 顶栏收起到紧凑标题。刻意比展开更快。 */
    @Stable
    fun <T> barCollapse(): SpringSpec<T> = springOf(1f, 0.15f)

    /** 大标题跟随滚动。较慢，因此它拖在内容之后而非吸附。 */
    @Stable
    fun <T> barTrack(): SpringSpec<T> = springOf(1f, 0.6f)

    /** 浮动操作栏按钮材质的线性过渡。 */
    @Stable
    fun topBarButtonFloat(): FiniteAnimationSpec<Float> = tween(
        durationMillis = 350,
        easing = LinearEasing,
    )

    /** 顶栏的覆盖遮罩另行跟随 100ms 线性过渡。 */
    @Stable
    fun topBarMask(): FiniteAnimationSpec<Float> = tween(durationMillis = 100, easing = LinearEasing)

    /** 弹出层从锚点展开。欠阻尼弹簧，展开时会过冲。 */
    @Stable
    fun <T> popupEnter(): SpringSpec<T> = springOf(0.8f, 0.28f)

    /** 弹出层收起。 */
    @Stable
    fun <T> popupExit(): SpringSpec<T> = springOf(0.95f, 0.2f)

    /** 对话框进入。 */
    @Stable
    fun <T> dialogEnter(): SpringSpec<T> = springOf(0.95f, 0.35f)

    /** 对话框离开。 */
    @Stable
    fun <T> dialogExit(): SpringSpec<T> = springOf(0.95f, 0.15f)

    /** 表面被按下。 */
    @Stable
    fun <T> pressDown(): SpringSpec<T> = springOf(0.99f, 0.15f)

    /** 表面从按下中释放。比按下慢，因此抬起看起来是回弹。 */
    @Stable
    fun <T> pressUp(): SpringSpec<T> = springOf(0.99f, 0.3f)

    /** 表面以缩放出现。 */
    @Stable
    fun <T> scaleIn(): SpringSpec<T> = springOf(0.6f, 0.35f)

    /** 表面以缩放消失。 */
    @Stable
    fun <T> scaleOut(): SpringSpec<T> = springOf(0.75f, 0.2f)

    /**
     * 底栏为自身内容渐变所用的曲线——指示器的填充，以及选中项经过时目标项的着色。
     */
    @Stable
    fun navContent(): FiniteAnimationSpec<Color> = tween(
        durationMillis = 150,
        easing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f),
    )

    /** [navContent]，用于以普通数值动画的一切。 */
    @Stable
    fun navContentFloat(): FiniteAnimationSpec<Float> = tween(
        durationMillis = 150,
        easing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f),
    )

    /**
     * 不透明度不使用弹簧。弹出层以此淡入，比其缩放收敛更快，因此表面在仍在移动时就已可读。
     */
    @Stable
    fun fadeIn(): FiniteAnimationSpec<Float> = tween(durationMillis = 80, easing = LinearEasing)

    /** 离场时的不透明度，比 [fadeIn] 慢，因此关闭不会显得闪烁。 */
    @Stable
    fun fadeOut(): FiniteAnimationSpec<Float> = tween(durationMillis = 150, easing = LinearEasing)

    /**
     * 锚定菜单几何形变所用的弹簧。
     *
     * 它同时带动宽度与宽高比，而非等比缩放。面板的右边缘与上边缘从不移动；
     * 菜单向左、向下展开，起初又宽又扁，之后才放出高度。
     */
    @Stable
    fun popupMorph(): SpringSpec<Float> = springOf(0.82f, 0.33f)

    /**
     * 菜单不透明度及其模糊修正所用的曲线：200ms 线性淡入、150ms 淡出，因此几何仍在收敛时面板已完全绘出。
     *
     * @param entering 菜单是否正在打开。
     */
    @Stable
    fun popupMorphFade(entering: Boolean): FiniteAnimationSpec<Float> = tween(durationMillis = if (entering) 200 else 150, easing = LinearEasing)

    /**
     * 菜单模糊自身内容所用的曲线：双向均 200ms 线性。对于没有图标可交接的菜单，由模糊撑起整个开场。
     */
    @Stable
    fun popupMorphBlur(): FiniteAnimationSpec<Float> = tween(durationMillis = 200, easing = LinearEasing)

    /**
     * 从控件自身长出的菜单几何所用的弹簧。
     *
     * 这是两种开场中的另一种：用于控件本身参与变形的场合，面板不是在控件旁显现，
     * 而是“变成”控件，从控件自身的矩形与圆角长成面板的矩形与圆角。
     *
     * @param entering 菜单是否正在打开。
     */
    @Stable
    fun transformBounds(entering: Boolean): SpringSpec<Float> = if (entering) {
        transformSpring(0.8f, 0.4f)
    } else {
        transformSpring(0.8f, 0.28f)
    }

    /**
     * 面板*中心*行进所用的弹簧。刻意不同于 [transformBounds]：面板会先到达该去的位置，再长到最终尺寸。
     *
     * @param entering 菜单是否正在打开。
     */
    @Stable
    fun transformCenter(entering: Boolean): SpringSpec<Float> = if (entering) {
        transformSpring(0.8f, 0.25f)
    } else {
        transformSpring(0.8f, 0.4f)
    }

    /**
     * 面板接管时控件自身内容溶解而出的斜坡。两次溶解共用 80ms 斜坡、间隔 50ms，且先后顺序随方向互换。
     *
     * @param entering 菜单是否正在打开。
     */
    @Stable
    fun transformIconMaterial(entering: Boolean): FiniteAnimationSpec<Float> = tween(
        durationMillis = TRANSFORM_MATERIAL_MS,
        delayMillis = if (entering) 0 else TRANSFORM_MATERIAL_DELAY_MS,
        easing = LinearEasing,
    )

    /** 面板内容溶解而入的斜坡。[transformIconMaterial] 的另一半。 */
    @Stable
    fun transformContentMaterial(entering: Boolean): FiniteAnimationSpec<Float> = tween(
        durationMillis = TRANSFORM_MATERIAL_MS,
        delayMillis = if (entering) TRANSFORM_MATERIAL_DELAY_MS else 0,
        easing = LinearEasing,
    )

    /** 该溶解任一半的时长（毫秒）。 */
    const val TRANSFORM_MATERIAL_MS: Int = 80

    /** 溶解的后半等前半多久（毫秒）。 */
    const val TRANSFORM_MATERIAL_DELAY_MS: Int = 50

    /**
     * 该溶解两半的模糊距离（像素）。
     *
     * 是像素而非 dp，因为它直接送入平台模糊，从不按密度缩放。因此屏幕越密，溶解越柔和。
     */
    const val TRANSFORM_BLUR_PX: Float = 50f

    /** Folme `ValueTarget`：`0.002` 最小可见变化乘以 `0.75` 系数。 */
    internal const val TRANSFORM_VISIBILITY_THRESHOLD: Float = 0.0015f

    /** 菜单开场宽度，按其最终宽度的比例计。 */
    const val POPUP_START_WIDTH: Float = 0.15f

    /** 该时刻的高度，按其自身宽度的比例计。远比最终形态扁。 */
    const val POPUP_START_RATIO: Float = 0.2f

    /** 菜单开场圆角（dp），随后过渡到面板自身的圆角。 */
    const val POPUP_START_CORNER_DP: Float = 4f

    /** 菜单开场时模糊自身内容的距离（像素而非 dp）。 */
    const val POPUP_MORPH_BLUR_PX: Float = 40f

    /**
     * 下拉面板*尺寸*所用的弹簧。
     *
     * 三种开场中的第三种，也是唯一边缘不静止的：面板尺寸与中心由两根速度不同的弹簧驱动，
     * 这使得行进轨迹呈弧线。打开时中心更快；关闭时两者互换。
     *
     * @param entering 下拉面板是否正在打开。
     */
    @Stable
    fun <T> arcBounds(entering: Boolean): SpringSpec<T> = if (entering) springOf(0.8f, 0.35f) else springOf(0.8f, 0.22f)

    /** 该下拉面板*中心*行进所用的弹簧。[arcBounds] 的另一半。 */
    @Stable
    fun <T> arcPosition(entering: Boolean): SpringSpec<T> = if (entering) springOf(0.8f, 0.22f) else springOf(0.8f, 0.35f)

    /**
     * 下拉面板淡入淡出的斜坡：50ms 入、200ms 出，因此面板刚变得可见时就已可读。
     *
     * @param entering 下拉面板是否正在打开。
     */
    @Stable
    fun arcFade(entering: Boolean): FiniteAnimationSpec<Float> = tween(durationMillis = if (entering) 50 else 200, easing = LinearEasing)

    /**
     * 下拉面板离场时模糊自身的斜坡。
     *
     * 单向：面板只在离场时模糊，因此开场完全没有模糊，内容直接变清晰而非虚焦入场。
     *
     * @param entering 下拉面板是否正在打开。
     */
    @Stable
    fun arcBlur(entering: Boolean): FiniteAnimationSpec<Float> = if (entering) snap() else tween(durationMillis = 200, easing = LinearEasing)

    /** 该下拉面板开场宽度，按其最终宽度的比例计。 */
    const val ARC_START_WIDTH: Float = 0.69f

    /** 该时刻的高度，按其自身宽度的比例计。 */
    const val ARC_START_RATIO: Float = 0.2f

    /** 该下拉面板离场时模糊自身内容的距离（dp）。 */
    const val ARC_EXIT_BLUR_DP: Float = 30f

    /**
     * 尺寸要走过多少，面板才会真正显示。
     *
     * 约 30ms 处。此时不透明度已达约 60%，因此出现的是可读的胶囊，
     * 而不是一个微小的透明斑点。
     */
    const val ARC_VISIBLE_FRACTION: Float = 0.1f

    /** 弹出层的起始缩放，在其锚点角处度量。 */
    const val POPUP_ENTER_SCALE: Float = 0.2f

    /** 弹出层收回到的缩放。它离开时比到达时更大。 */
    const val POPUP_EXIT_SCALE: Float = 0.5f

    /** 按压缩放的下限，无论表面尺寸如何。 */
    const val PRESS_SCALE_MIN: Float = 0.9f

    /** 按下时表面内缩的距离，横跨其较短边全长（dp）。 */
    const val PRESS_INSET_DP: Float = 10f

    /** 底栏离场时收缩到的缩放。 */
    const val NAV_HIDE_SCALE: Float = 0.6f

    /** 底栏离场结束时模糊自身的距离（dp）。 */
    const val NAV_HIDE_BLUR_DP: Float = 18f

    /** 底栏回来之前等待的时长（毫秒）。 */
    const val NAV_SHOW_DELAY_MS: Long = 100
}
