package com.tool.script.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Hiệu ứng nhấn dạng gợn sóng nhỏ gọn cho icon: một vòng tròn loang ra từ tâm rồi mờ dần.
 * Chỉ vẽ trong hình tròn bán kính [radius], không phủ kín cả vùng chạm như hiệu ứng ô chữ nhật mặc định của Miuix,
 * nên vùng bấm vẫn đủ lớn (48dp) mà hiệu ứng nhìn gọn, bo tròn.
 */
@Stable
data class CompactRipple(
    val color: Color,
    val radius: Dp = 18.dp,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        CompactRippleNode(interactionSource, color, radius)
}

private const val PressAlpha = 0.14f
private const val StartProgress = 0.4f
private const val ExpandMillis = 260
private const val FadeMillis = 220

private class CompactRippleNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
    private val radius: Dp,
) : Modifier.Node(), DrawModifierNode {

    private val progress = Animatable(0f)
    private val alpha = Animatable(0f)
    private var expandJob: Job? = null
    private var fadeJob: Job? = null

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        fadeJob?.cancel()
                        expandJob?.cancel()
                        expandJob = coroutineScope.launch {
                            progress.snapTo(StartProgress)
                            alpha.snapTo(PressAlpha)
                            progress.animateTo(1f, tween(ExpandMillis, easing = FastOutSlowInEasing))
                        }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        // Chạm nhanh vẫn để sóng loang hết rồi mới mờ, tránh chớp một cái là mất.
                        fadeJob = coroutineScope.launch {
                            expandJob?.join()
                            alpha.animateTo(0f, tween(FadeMillis))
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val a = alpha.value
        val p = progress.value
        if (a > 0f && p > 0f) {
            drawCircle(color = color.copy(alpha = a), radius = radius.toPx() * p, center = center)
        }
    }
}
