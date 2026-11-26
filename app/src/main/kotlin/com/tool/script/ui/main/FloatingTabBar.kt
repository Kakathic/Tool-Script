package com.tool.script.ui.main

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tool.script.config.PageRef
import com.tool.script.util.Util
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Thanh tab nổi dạng kính (Floating NavigationBar, style Glass của Miuix) cho giao diện kính.
// Dựng bằng glassPanel + các mặc định của GlassNavigationBar (chất liệu, viền, bóng, màu viên chọn) nhưng icon là SVG trong assets
// (AsyncImage) thay cho ImageVector mà GlassNavigationBar bắt buộc. Giữ cửa sổ tab trượt như thanh tab thường.
// [backdrop] = null (tắt "Làm mờ nền") thì viên thuốc nền đặc surfaceContainer, tức Floating NavigationBar thường.

/** Chiều cao tối thiểu của viên thuốc (cùng GlassNavigationBar). */
private val FloatingBarHeight = 54.dp

/** Lề ngang giữa viên thuốc và mép màn hình. */
private val FloatingBarOutsideMargin = 24.dp

/** Đệm trong của viên thuốc, tính từ mép tới ô tab đầu / cuối. */
private val FloatingBarInnerPadding = 8.dp

/** Khoảng đệm dưới thanh (cùng quy tắc của GlassNavigationBar: tính từ thanh điều hướng hệ thống). */
@Composable
private fun floatingBarBottomMargin(): androidx.compose.ui.unit.Dp {
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return when {
        systemBottom <= 16.dp -> 20.dp
        systemBottom >= 28.dp -> systemBottom
        else -> 12.dp + systemBottom
    }
}

/**
 * Thanh tab nổi: viên thuốc kính ở đáy màn hình, nội dung trang cuộn tràn xuống dưới nó.
 * [windowStart] / [visibleTabs] là cửa sổ tab đang hiện (xem TabWindow.kt); viên chọn và hàng tab trượt mượt theo.
 */
@Composable
fun FloatingTabBar(
    pages: List<PageRef>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    visibleTabs: Int,
    windowStart: Int,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    if (pages.isEmpty() || visibleTabs <= 0) return
    val shape = remember { GlassShape(CornerSize(50), CornerSize(50), CornerSize(50), CornerSize(50)) }
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val horizontalInsets = WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal)
    val slideSpec = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    val slide by animateFloatAsState(windowStart.toFloat(), slideSpec, label = "floatingTabSlide")
    val indicator by animateFloatAsState(selectedIndex.toFloat(), slideSpec, label = "floatingTabIndicator")
    val selectedColor = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground
    val unselectedColor = MiuixTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(horizontalInsets)
            .padding(start = FloatingBarOutsideMargin, end = FloatingBarOutsideMargin, bottom = floatingBarBottomMargin()),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FloatingBarHeight)
                .glassPanel(
                    backdrop = backdrop,
                    shape = shape,
                    material = GlassNavigationBarDefaults.material(),
                    stroke = GlassNavigationBarDefaults.stroke(),
                    shadow = GlassShadows.Float,
                    shading = false,
                    fallback = Modifier.clip(shape).background(surface),
                )
                .padding(horizontal = FloatingBarInnerPadding)
                .clipToBounds(),
        ) {
            val itemWidth = maxWidth / visibleTabs
            val itemWidthPx = constraints.maxWidth.toFloat() / visibleTabs
            // Viên chọn: nằm sau tab đang chọn, trượt theo cả vị trí tab lẫn cửa sổ tab.
            Box(
                modifier = Modifier
                    .offset { IntOffset(((indicator - slide) * itemWidthPx).roundToInt(), 0) }
                    .width(itemWidth)
                    .height(FloatingBarHeight)
                    .padding(vertical = GlassNavigationBarDefaults.IndicatorPaddingVertical)
                    .clip(RoundedCornerShape(50))
                    .background(GlassNavigationBarDefaults.indicatorColor()),
            )
            Box(
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .width(itemWidth * pages.size)
                    .offset { IntOffset((-slide * itemWidthPx).roundToInt(), 0) },
            ) {
                pages.forEachIndexed { index, page ->
                    FloatingTabItem(
                        page = page,
                        selected = index == selectedIndex,
                        selectedColor = selectedColor,
                        unselectedColor = unselectedColor,
                        onClick = { onSelect(index) },
                        modifier = Modifier.offset(x = itemWidth * index).width(itemWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingTabItem(
    page: PageRef,
    selected: Boolean,
    selectedColor: androidx.compose.ui.graphics.Color,
    unselectedColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(if (pressed) GlassNavigationBarDefaults.PressedAlpha else 1f, label = "floatingTabPress")
    val tint = if (selected) selectedColor else unselectedColor
    Column(
        modifier = modifier
            .heightIn(min = FloatingBarHeight)
            .graphicsLayer { alpha = pressAlpha }
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Tab, onClick = onClick)
            .padding(vertical = GlassNavigationBarDefaults.ContentPaddingVertical),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = Util.iconModel(page.icon),
            contentDescription = page.title,
            modifier = Modifier.size(GlassNavigationBarDefaults.IconSize),
            colorFilter = ColorFilter.tint(tint),
        )
        Text(
            text = page.title,
            color = tint,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
