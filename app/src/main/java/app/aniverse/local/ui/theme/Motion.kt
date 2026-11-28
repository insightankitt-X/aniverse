package app.aniverse.local.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The app's motion language. Everything is short and spring-based; nothing bounces for the sake
 * of it. Reduce motion turns every spec into a snap.
 */
object Motion {
    const val Fast = 160
    const val Medium = 240
    const val Slow = 340
    const val EmphasizedMs = 420

    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    val Press = spring<Float>(dampingRatio = 0.55f, stiffness = 700f)
    val Settle = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)
    val Page = spring<Float>(dampingRatio = 0.9f, stiffness = 300f)

    fun <T> slide(duration: Int = Medium): TweenSpec<T> =
        tween(durationMillis = duration, easing = Emphasized)

    fun <T> fade(duration: Int = Fast): TweenSpec<T> =
        tween(durationMillis = duration, easing = Emphasized)
}

/** Press-scale feedback with an enlarged hit area supplied by the caller. */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) pressedScale else 1f,
        animationSpec = if (reduceMotion) snap() else Motion.Press,
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
