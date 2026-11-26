// Chép lại từ GlassNavigationBar của Miuix (miuix-glass, Apache-2.0, Copyright 2026 compose-miuix-ui contributors) để thanh tab nổi
// giống hệt: viền / bóng / chất liệu kính, viên chọn kéo được bằng ngón tay và co dãn theo lò xo.
// Khác bản gốc chỉ ở: icon là SVG / ảnh trong assets (AsyncImage) thay cho ImageVector; chia chiều rộng theo cửa sổ tab
// (visibleTabs / windowStart, xem TabWindow.kt) thay cho toàn bộ tab; bỏ trailingAction.
package com.tool.script.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastRoundToInt
import coil3.compose.AsyncImage
import com.tool.script.config.PageRef
import com.tool.script.util.Util
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassMotion
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.glass.glassPressScale
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassShadow
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Thanh tab nổi dạng kính (Floating NavigationBar, style Glass của Miuix). Viên chọn bám theo ngón tay khi kéo và lò xo về ô gần nhất khi thả.
 * [items] là tất cả tab; [visibleTabs] / [windowStart] là cửa sổ tab đang hiện.
 * [backdrop] = null (tắt "Làm mờ nền") thì viên thuốc nền đặc surfaceContainer.
 */
@Composable
fun FloatingGlassTabBar(
    items: List<PageRef>,
    visibleTabs: Int,
    windowStart: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    defaultWindowInsetsPadding: Boolean = true,
    style: GlassStyle = GlassDefaults.Style,
    shape: GlassShape = GlassShape(CornerSize(50), CornerSize(50), CornerSize(50), CornerSize(50)),
    alpha: Float = 1f,
    visible: Boolean = true,
    stroke: EdgeStroke? = GlassNavigationBarDefaults.stroke(),
    shadow: GlassShadow? = GlassShadows.Float,
    height: Dp = GlassNavigationBarDefaults.Height,
    material: GlassMaterial? = GlassNavigationBarDefaults.material(),
    indicatorColor: Color = GlassNavigationBarDefaults.indicatorColor(),
    indicatorPressedColor: Color = GlassNavigationBarDefaults.indicatorPressedColor(),
    selectedColor: Color = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
    unselectedColor: Color = MiuixTheme.colorScheme.onBackground,
) {
    if (items.isEmpty()) return
    val index = selectedIndex.coerceIn(0, items.lastIndex)
    // Số ô tab nhìn thấy cùng lúc (cửa sổ tab, xem TabWindow.kt); chia chiều rộng thanh theo số này thay cho items.size.
    val slots = visibleTabs.coerceIn(1, items.size)
    val slideSpec = GlassMotion.navIndicator<Float>()
    val slide by animateFloatAsState(windowStart.toFloat(), slideSpec, label = "floatingTabSlide")
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val labelSize = with(density) {
        if (fontScale >= 1.6f) GlassNavigationBarDefaults.LargeLabelSize.toSp() else GlassNavigationBarDefaults.LabelSize.toSp()
    }
    val contentStart = with(density) {
        GlassNavigationBarDefaults.ContentPaddingHorizontal.toPx()
    }
    val contentInset = with(density) { GlassNavigationBarDefaults.ContentPaddingHorizontal.toPx() }
    val overhang = with(density) { GlassNavigationBarDefaults.IndicatorOverhang.toPx() }

    val barWidthState = remember { mutableIntStateOf(0) }
    val pressedIndexState = remember { mutableIntStateOf(-1) }
    val dragging = remember { mutableStateOf(false) }
    var settleGeneration by remember { mutableIntStateOf(0) }

    val showScale = remember { Animatable(if (visible) 1f else GlassMotion.NAV_HIDE_SCALE) }
    var interactionEnabled by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            interactionEnabled = true
            delay(GlassMotion.NAV_SHOW_DELAY_MS.milliseconds)
            showScale.animateTo(1f, GlassMotion.navShowHide())
        } else {
            showScale.animateTo(GlassMotion.NAV_HIDE_SCALE, GlassMotion.navShowHide())
            interactionEnabled = false
        }
    }

    val scope = rememberCoroutineScope()
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    fun animateIndicator(
        newLeft: Float,
        newRight: Float,
        leftSpring: SpringSpec<Float> = GlassMotion.edgeSpring(newLeft <= left.value),
        rightSpring: SpringSpec<Float> = GlassMotion.edgeSpring(newLeft > left.value),
    ) {
        if (left.targetValue == newLeft && right.targetValue == newRight) return
        // Shared springs preserve velocity when the target changes.
        scope.launch { left.animateTo(newLeft, leftSpring) }
        scope.launch { right.animateTo(newRight, rightSpring) }
    }

    // Settle after composition commits the selection returned by onSelect. The even split waits for
    // the bar's size, so its first "no slot yet" result is revisited once it is known.
    LaunchedEffect(index, windowStart, settleGeneration, slots, contentStart, contentInset, overhang) {
        snapshotFlow { barWidthState.intValue }.collectLatest { currentBarWidth ->
            if (dragging.value) return@collectLatest
            if (currentBarWidth <= contentStart + contentInset) return@collectLatest
            val slotWidth = ((currentBarWidth - contentStart - contentInset) / slots).coerceAtLeast(0f)
            val targetLeft = contentStart - overhang + (index - windowStart) * slotWidth
            val targetRight = contentStart + overhang + (index - windowStart + 1) * slotWidth
            if (right.value <= left.value) {
                left.snapTo(targetLeft)
                right.snapTo(targetRight)
            } else {
                animateIndicator(targetLeft, targetRight)
            }
        }
    }
    val chipPressed by remember { computedStateOf { pressedIndexState.intValue >= 0 } }

    val onSelectState by rememberUpdatedState(onSelect)
    val bottomPaddingProvider: (() -> Dp)? = if (defaultWindowInsetsPadding) {
        val navigationBarBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
        val captionBarBottom = with(density) { WindowInsets.captionBar.getBottom(this).toDp() }
        val animatedCaptionBarHeight = animateDpAsState(
            targetValue = captionBarBottom.coerceAtLeast(0.dp),
            animationSpec = tween(durationMillis = 300),
            label = "glassNavigationCaptionBarHeight",
        )
        remember(navigationBarBottom, animatedCaptionBarHeight) {
            {
                val systemBottom = maxOf(navigationBarBottom, animatedCaptionBarHeight.value)
                when {
                    systemBottom <= 16.dp -> 20.dp
                    systemBottom >= 28.dp -> systemBottom
                    else -> 12.dp + systemBottom
                }
            }
        }
    } else {
        null
    }
    val horizontalInsetsModifier = if (defaultWindowInsetsPadding) {
        val cutout = WindowInsets.displayCutout
        val navBars = WindowInsets.navigationBars
        val horizontalInsets = remember(cutout, navBars) {
            cutout.union(navBars).only(WindowInsetsSides.Horizontal)
        }
        Modifier.windowInsetsPadding(horizontalInsets)
    } else {
        Modifier
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .then(horizontalInsetsModifier)
            .then(modifier)
            .fillMaxWidth()
            .then(
                if (bottomPaddingProvider != null) {
                    Modifier.layout { measurable, constraints ->
                        val bottomPx = bottomPaddingProvider().roundToPx().coerceAtLeast(0)
                        val placeable = measurable.measure(constraints.offset(vertical = -bottomPx))
                        layout(
                            constraints.constrainWidth(placeable.width),
                            constraints.constrainHeight(placeable.height + bottomPx),
                        ) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                } else {
                    Modifier
                },
            )
            .graphicsLayer {
                val show = showScale.value
                val visibility = ((show - GlassMotion.NAV_HIDE_SCALE) / (1f - GlassMotion.NAV_HIDE_SCALE))
                    .coerceIn(0f, 1f)
                scaleX = show
                scaleY = show
                this.alpha = visibility
                val blurPx = ((1f - visibility) * GlassMotion.NAV_HIDE_BLUR_DP).dp.toPx()
                renderEffect = if (blurPx > 0.5f) BlurEffect(blurPx, blurPx, TileMode.Decal) else null
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .glassPanel(
                    backdrop = backdrop,
                    shape = shape,
                    style = style,
                    alpha = alpha,
                    material = material,
                    stroke = stroke,
                    shadow = shadow,
                    shading = false,
                    fallback = if (backdrop == null || !isRuntimeShaderSupported()) {
                        Modifier
                            .clip(shape)
                            .background(MiuixTheme.colorScheme.surfaceContainer)
                    } else {
                        null
                    },
                )
                .onSizeChanged { barWidthState.intValue = it.width }
                .then(
                    if (interactionEnabled) {
                        Modifier.pointerInput(
                            slots,
                            windowStart,
                            contentStart,
                            contentInset,
                            overhang,
                            layoutDirection,
                        ) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val selectionWidth = size.width.toFloat()
                                if (selectionWidth <= contentStart + contentInset) return@awaitEachGesture
                                // Freeze hit regions until release; only the indicator previews selection.
                                val selectionSlotWidth = (selectionWidth - contentStart - contentInset) / slots
                                fun logicalX(x: Float) = if (layoutDirection == LayoutDirection.Rtl) size.width - x else x
                                // Kẹp trong các ô đang hiện: kéo quá ô cuối rồi thả vẫn chọn ô cuối, không nhảy sang tab ẩn phía sau.
                                fun indexAt(x: Float): Int = ((x - contentStart) / selectionSlotWidth)
                                    .toInt()
                                    .coerceIn(0, slots - 1)
                                    .plus(windowStart)
                                    .coerceIn(0, items.lastIndex)

                                val minLeft = contentStart - overhang
                                val downX = logicalX(down.position.x)
                                var selectionX = downX
                                var current = indexAt(downX)
                                pressedIndexState.intValue = current
                                dragging.value = true
                                val pressedLeft = minLeft + (current - windowStart) * selectionSlotWidth
                                animateIndicator(pressedLeft, pressedLeft + selectionSlotWidth + overhang * 2f)
                                var grabOffset = Float.NaN
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (change.isConsumed) break
                                        if (!change.pressed) {
                                            if (change.changedToUp()) {
                                                val releaseDelta = change.position.x - change.previousPosition.x
                                                selectionX += if (layoutDirection == LayoutDirection.Rtl) -releaseDelta else releaseDelta
                                                onSelectState(indexAt(selectionX))
                                                change.consume()
                                            }
                                            break
                                        }
                                        // The layout can still resize externally while a pointer is held.
                                        val width = size.width.toFloat()
                                        val chipW = ((width - contentStart - contentInset) / slots)
                                            .coerceAtLeast(0f) + overhang * 2f
                                        // Overshoot the destinations slightly so the rendered edge squeezes.
                                        val travelStart = contentStart - contentInset
                                        val maxLeft = (width - chipW).coerceAtLeast(travelStart)
                                        val x = logicalX(change.position.x)
                                        // Both positions use the current local coordinates, so their
                                        // delta excludes movement caused by the capsule's placement.
                                        val pointerDelta = change.position.x - change.previousPosition.x
                                        val delta = if (layoutDirection == LayoutDirection.Rtl) -pointerDelta else pointerDelta
                                        selectionX += delta
                                        if (grabOffset.isNaN()) {
                                            if (abs(selectionX - downX) < viewConfiguration.touchSlop) {
                                                pressedIndexState.intValue = indexAt(selectionX)
                                                continue
                                            }
                                            // Preserve the press anchor, not the unfinished animation's lag.
                                            grabOffset = downX - pressedLeft
                                        }
                                        val chipLeft = (x - grabOffset).coerceIn(travelStart, maxLeft)
                                        val next = indexAt(selectionX)
                                        val target = navigationDragTarget(chipLeft, chipW, travelStart, width, delta, next != current)
                                        animateIndicator(target.left, target.right, target.leftSpring, target.rightSpring)
                                        current = next
                                        pressedIndexState.intValue = next
                                        change.consume()
                                    }
                                } finally {
                                    pressedIndexState.intValue = -1
                                    dragging.value = false
                                    settleGeneration++
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            GlassNavigationIndicator(
                pressed = chipPressed,
                height = height - GlassNavigationBarDefaults.IndicatorPaddingVertical * 2,
                color = indicatorColor,
                pressedColor = indicatorPressedColor,
                modifier = Modifier
                    .matchParentSize()
                    .padding(vertical = GlassNavigationBarDefaults.IndicatorPaddingVertical)
                    .layout { measurable, constraints ->
                        // Both edges are clamped on every frame, not just at the target. Keep the
                        // spring alive so the other edge still rebounds.
                        val bounds = if (right.value <= left.value && constraints.maxWidth > contentStart + contentInset) {
                            val slotWidth = ((constraints.maxWidth - contentStart - contentInset) / slots).coerceAtLeast(0f)
                            val targetLeft = contentStart - overhang + (index - windowStart) * slotWidth
                            val targetRight = contentStart + overhang + (index - windowStart + 1) * slotWidth
                            navigationIndicatorBounds(
                                targetLeft,
                                targetRight,
                                constraints.maxWidth.toFloat(),
                                contentStart - overhang,
                                contentInset - overhang,
                            )
                        } else {
                            navigationIndicatorBounds(
                                left.value,
                                right.value,
                                constraints.maxWidth.toFloat(),
                                contentStart - overhang,
                                contentInset - overhang,
                            )
                        }
                        val start = bounds.x.fastRoundToInt()
                        val width = (bounds.y.fastRoundToInt() - start).coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = width, maxWidth = width),
                        )
                        layout(constraints.maxWidth, placeable.height) {
                            placeable.placeRelative(IntOffset(start, 0))
                        }
                    },
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(horizontal = GlassNavigationBarDefaults.ContentPaddingHorizontal)
                    .clipToBounds(),
            ) {
                val itemWidth = maxWidth / slots
                val itemWidthPx = constraints.maxWidth.toFloat() / slots
                Row(
                    modifier = Modifier
                        // Không dùng requiredWidth: rộng hơn khung cha sẽ tự căn giữa làm lệch hàng tab; căn từ mép trái (unbounded).
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .width(itemWidth * items.size)
                        .offset { IntOffset((-slide * itemWidthPx).fastRoundToInt(), 0) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val pressedIndexProvider = remember { { pressedIndexState.intValue } }
                    items.forEachIndexed { position, item ->
                        key(item) {
                            FloatingGlassTabItem(
                                item = item,
                                position = position,
                                selected = position == index,
                                pressedIndexProvider = pressedIndexProvider,
                                selectedColor = selectedColor,
                                unselectedColor = unselectedColor,
                                labelSize = labelSize,
                                height = height,
                                interactionEnabled = interactionEnabled,
                                onSelect = onSelectState,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.FloatingGlassTabItem(
    item: PageRef,
    position: Int,
    selected: Boolean,
    pressedIndexProvider: () -> Int,
    selectedColor: Color,
    unselectedColor: Color,
    labelSize: androidx.compose.ui.unit.TextUnit,
    height: Dp,
    interactionEnabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    val tintState = animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        animationSpec = GlassMotion.navContent(),
        label = "glassNavigationTint",
    )
    val tintProducer = remember { ColorProducer { tintState.value } }
    val isPressed by remember(position) {
        computedStateOf { pressedIndexProvider() == position }
    }
    val pressAlpha = animateFloatAsState(
        targetValue = if (isPressed) {
            GlassNavigationBarDefaults.PressedAlpha
        } else {
            1f
        },
        animationSpec = GlassMotion.navContentFloat(),
        label = "glassNavigationPress",
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = height)
            .graphicsLayer { this.alpha = pressAlpha.value }
            .padding(
                horizontal = GlassNavigationBarDefaults.IndicatorOverhang,
                vertical = GlassNavigationBarDefaults.ContentPaddingVertical,
            )
            .then(
                if (interactionEnabled) {
                    Modifier
                        .semantics(mergeDescendants = true) {
                            this.role = Role.Tab
                            this.selected = selected
                            this.contentDescription = item.title
                            onClick {
                                onSelect(position)
                                true
                            }
                        }
                        .onKeyEvent { event ->
                            val activationKey = event.key == Key.Enter ||
                                event.key == Key.NumPadEnter ||
                                event.key == Key.Spacebar
                            if (activationKey) {
                                if (event.type == KeyEventType.KeyUp) onSelect(position)
                                true
                            } else {
                                false
                            }
                        }
                        .focusable()
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GlassNavigationBarDefaults.LabelSpacing),
        ) {
            AsyncImage(
                model = Util.iconModel(item.icon),
                contentDescription = null,
                modifier = Modifier.size(GlassNavigationBarDefaults.IconSize),
                colorFilter = ColorFilter.tint(tintState.value),
            )
            val baseLabelStyle = MiuixTheme.textStyles.footnote2
            val labelTextStyle = remember(baseLabelStyle, labelSize) {
                baseLabelStyle.copy(fontSize = labelSize)
            }
            Text(
                text = item.title,
                color = tintProducer,
                style = labelTextStyle,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Unscaled drag-target bounds, in pixels, before the indicator's press transform. */
private data class NavigationDragTarget(
    val left: Float,
    val right: Float,
    val following: Boolean,
    val movingRight: Boolean,
) {
    val leftSpring: SpringSpec<Float>
        get() = if (following) GlassMotion.navDragFollow() else GlassMotion.edgeSpring(!movingRight)

    val rightSpring: SpringSpec<Float>
        get() = if (following) GlassMotion.navDragFollow() else GlassMotion.edgeSpring(movingRight)
}

/**
 * Computes same-item drag-trail and cross-item edge-spring targets.
 *
 * The chip travels within [minLeft]..[maxRight], which reaches slightly past the destinations so
 * [navigationIndicatorBounds] can squeeze it at either end. The 60px trail cap is intentionally not
 * density-scaled. Raw spring targets may extend beyond the travel; rendered edges are limited.
 * Stretch is disabled when the leading target reaches the travel's boundary.
 */
private fun navigationDragTarget(
    left: Float,
    width: Float,
    minLeft: Float,
    maxRight: Float,
    delta: Float,
    changedItem: Boolean,
): NavigationDragTarget {
    val start = left.coerceIn(minLeft, (maxRight - width).coerceAtLeast(minLeft))
    val end = start + width
    val trail = if (changedItem) 0f else (abs(delta) * 4f).coerceAtMost(60f)
    return NavigationDragTarget(
        left = if (delta > 0f && end < maxRight) start - trail else start,
        right = if (delta < 0f && start > minLeft) end + trail else end,
        following = !changedItem,
        movingRight = delta > 0f,
    )
}

/** Limits rendered edges to the outermost items without stopping the underlying spring. */
private fun navigationIndicatorBounds(
    left: Float,
    right: Float,
    containerWidth: Float,
    inset: Float,
    endInset: Float = inset,
): Offset {
    val min = inset.coerceIn(0f, containerWidth.coerceAtLeast(0f) / 2f)
    val max = (containerWidth - endInset.coerceAtLeast(0f)).coerceAtLeast(min)
    val start = left.coerceIn(min, max)
    return Offset(start, right.coerceIn(start, max))
}

/**
 * The selected destination's own surface, shared by the glass navigation bar and
 * [GlassNavigationRail].
 *
 * It is only ever a tint of the glass under it: this composable draws a capsule and owns the press
 * feedback (the fill and the pressed scale). Size and placement belong to the caller, so the same
 * surface can hug an icon, stretch across a destination or travel between rows.
 *
 * @param pressed Whether the destination the indicator covers is held down.
 * @param modifier The modifier sizing and placing the indicator.
 * @param height The indicator's own height, used to derive the pressed scale.
 * @param color Resting fill.
 * @param pressedColor Fill while the destination is held down.
 * @param scaleOnPress Whether a press scales the indicator down. Disable it when the press belongs
 *   to a row the indicator only spans visually, so the press feedback does not move the indicator.
 */
@Composable
private fun GlassNavigationIndicator(
    pressed: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = GlassNavigationBarDefaults.Height - GlassNavigationBarDefaults.IndicatorPaddingVertical * 2,
    color: Color = GlassNavigationBarDefaults.indicatorColor(),
    pressedColor: Color = GlassNavigationBarDefaults.indicatorPressedColor(),
    scaleOnPress: Boolean = true,
) {
    val density = LocalDensity.current
    val scale = animateFloatAsState(
        targetValue = if (pressed && scaleOnPress) {
            glassPressScale(with(density) { height.toPx() }, density.density)
        } else {
            1f
        },
        animationSpec = if (pressed) GlassMotion.pressDown() else GlassMotion.pressUp(),
        label = "glassNavigationIndicatorPress",
    )
    val fill = animateColorAsState(
        targetValue = if (pressed) pressedColor else color,
        animationSpec = if (pressed) GlassMotion.navPressEnter() else GlassMotion.navPressExit(),
        label = "glassNavigationIndicatorFill",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                val s = scale.value
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .drawBehind { drawRect(fill.value) },
    )
}
