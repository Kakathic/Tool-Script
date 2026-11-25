package com.tool.script.ui.main

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Mép mờ dần ở vùng cuộn, tương đương android:requiresFadingEdge="vertical" của View.
// Mép nào đã chạm đầu / cuối nội dung thì không mờ. Trạng thái cuộn chỉ đọc ở bước vẽ nên cuộn không gây recomposition.

private val FadingEdgeLength = 16.dp

/** Đặt ở ngoài cùng của vùng cuộn (trước verticalScroll / trước khung chứa LazyColumn). */
fun Modifier.fadingEdges(state: LazyListState, length: Dp = FadingEdgeLength): Modifier =
    fadingEdges(canScrollBackward = { state.canScrollBackward }, canScrollForward = { state.canScrollForward }, length = length)

/** Đặt ở ngoài cùng của vùng cuộn (trước verticalScroll). */
fun Modifier.fadingEdges(state: ScrollState, length: Dp = FadingEdgeLength): Modifier =
    fadingEdges(canScrollBackward = { state.canScrollBackward }, canScrollForward = { state.canScrollForward }, length = length)

private fun Modifier.fadingEdges(
    canScrollBackward: () -> Boolean,
    canScrollForward: () -> Boolean,
    length: Dp,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = length.toPx().coerceAtMost(size.height / 2f)
        if (edge <= 0f) return@drawWithContent
        if (canScrollBackward()) {
            drawRect(
                brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = edge),
                size = Size(size.width, edge),
                blendMode = BlendMode.DstIn,
            )
        }
        if (canScrollForward()) {
            drawRect(
                brush = Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - edge, endY = size.height),
                topLeft = Offset(0f, size.height - edge),
                size = Size(size.width, edge),
                blendMode = BlendMode.DstIn,
            )
        }
    }
