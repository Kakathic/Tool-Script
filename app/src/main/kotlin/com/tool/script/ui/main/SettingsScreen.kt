package com.tool.script.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tool.script.R
import com.tool.script.ui.theme.BgEffectBackground
import com.tool.script.ui.theme.LocalGlassEnabled
import com.tool.script.ui.theme.isDarkSurface
import com.tool.script.util.AppAbout
import com.tool.script.util.AppIcons
import com.tool.script.util.AppVersion
import com.tool.script.util.UpdateChecker
import com.tool.script.util.UpdateState
import com.tool.script.util.Util
import com.tool.script.util.openUrl
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

// Màn hình Cài đặt theo trang "Giới thiệu" của Miuix (kiểu HyperOS 3):
// - Nền động OS3 phủ kín ([BgEffectBackground]); logo, tên app, phiên bản, giới thiệu nằm cố định phía trên và mờ dần khi cuộn;
//   thanh trên trong suốt, cuộn hết phần đầu thì thành nền đặc (hoặc kính mờ) và hiện tiêu đề.
// - Các thẻ nằm trong cột cuộn, trượt lên che phần đầu.
// - Nút kiểm tra phiên bản mới nổi ở đáy màn hình, không nằm trong danh sách cuộn.
// Chú ý backdrop: mỗi backdrop chỉ được lấy mờ bởi thành phần nằm NGOÀI lớp nó ghi (xem chú thích ở [LocalGlassScreenBackdrop]).
// [bgBackdrop] ghi riêng lớp nền shader (chữ tên app và hàng chọn giao diện lấy từ đây), [contentBackdrop] ghi cột cuộn (chỉ thanh trên lấy từ đây).

private val TopBarHeight = 56.dp
private val HeaderTopGap = 36.dp
private val HeaderBottomGap = 48.dp
private val UpdateButtonHeight = 52.dp
private val ScreenEdge = 12.dp

/**
 * Khung màn hình Cài đặt toàn màn hình. [content] là các nhóm ([SettingsGroup]) xếp dọc trong cột cuộn, cách nhau 12dp.
 * Back hoặc X đều đóng. Nền luôn đặc (không làm mờ trang phía sau như [ToolbarFullScreen]).
 */
@Composable
internal fun SettingsScreen(
    show: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = show, onBack = onDismiss)
    AnimatedVisibility(
        visible = show,
        enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(160)),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
    ) {
        SettingsScreenBody(title = title, onDismiss = onDismiss, content = content)
    }
}

@Composable
private fun SettingsScreenBody(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGlassEnabled.current
    val context = LocalContext.current
    val version = remember { AppVersion.of(context) }
    val surface = MiuixTheme.colorScheme.surface
    val blurOn = LocalBlurRequests.current != null
    val bgBackdrop = rememberBarBackdrop(blurOn)
    val contentBackdrop = rememberBarBackdrop(blurOn)
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val scrollState = rememberScrollState()
    var headerPx by remember { mutableIntStateOf(0) }
    var viewportPx by remember { mutableIntStateOf(0) }
    val gapPx = with(density) { HeaderBottomGap.roundToPx() }
    // Tiến độ cuộn qua phần đầu: 0 = chưa cuộn, 1 = đã cuộn hết khoảng trống phía trên các thẻ.
    val progress by remember {
        derivedStateOf {
            val total = headerPx + gapPx
            if (total <= 0) 0f else (scrollState.value.toFloat() / total).coerceIn(0f, 1f)
        }
    }
    val collapsed by remember { derivedStateOf { progress >= 1f } }
    val showTitle by remember { derivedStateOf { progress >= 0.45f } }

    val isDark = isDarkSurface()
    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportPx = it.height }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        // Lớp 1: nền động OS3, mờ dần khi cuộn.
        BgEffectBackground(
            modifier = Modifier.fillMaxSize(),
            bgModifier = if (bgBackdrop != null) Modifier.layerBackdrop(bgBackdrop) else Modifier,
            alpha = { 1f - progress },
        )

        // Lớp 2: logo, tên app, phiên bản, giới thiệu (cố định, mờ và thu nhỏ dần từng dòng khi cuộn).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { headerPx = it.height }
                .padding(top = topInset + TopBarHeight + HeaderTopGap, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        val p = ((progress - 0.35f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1 - p
                        scaleX = 1 - p * 0.05f
                        scaleY = 1 - p * 0.05f
                    },
            )
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 5.dp)
                    .graphicsLayer {
                        val p = ((progress - 0.20f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1 - p
                        scaleX = 1 - p * 0.05f
                        scaleY = 1 - p * 0.05f
                    }
                    .then(
                        if (bgBackdrop != null) {
                            Modifier.textureBlur(
                                backdrop = bgBackdrop,
                                shape = RoundedCornerShape(16.dp),
                                blurRadius = 100f,
                                colors = BlurDefaults.blurColors(blendColors = logoBlend),
                                contentBlendMode = ComposeBlendMode.DstIn,
                            )
                        } else {
                            Modifier
                        },
                    ),
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )
            Text(
                text = stringResource(R.string.about_version, version.display),
                modifier = Modifier.graphicsLayer {
                    val p = ((progress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                    alpha = 1 - p
                    scaleX = 1 - p * 0.05f
                    scaleY = 1 - p * 0.05f
                },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.about_intro),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .graphicsLayer {
                        val p = ((progress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1 - p
                    },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        // Lớp 3: cột cuộn. Khoảng trống đầu cột bằng chiều cao phần đầu để các thẻ bắt đầu bên dưới;
        // cột thẻ cao ít nhất bằng màn hình để luôn cuộn được hết phần đầu.
        // Hàng chọn giao diện dạng kính lấy backdrop của lớp nền shader (nằm ngoài cột này), không lấy backdrop của chính cột.
        CompositionLocalProvider(LocalGlassBackdrop provides bgBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (contentBackdrop != null) Modifier.layerBackdrop(contentBackdrop) else Modifier)
                    .verticalScroll(scrollState),
            ) {
                Spacer(Modifier.height(with(density) { (headerPx + gapPx).toDp() }))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = with(density) { viewportPx.toDp() })
                        .padding(start = ScreenEdge, end = ScreenEdge, bottom = bottomInset + UpdateButtonHeight + 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }

        // Lớp 4: nút cập nhật nổi ở đáy, đứng yên khi cuộn; dải mờ dần phía sau để chữ của thẻ không lẫn vào nút.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomInset + UpdateButtonHeight + 36.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, surface))),
        )
        SettingsUpdateButton(
            version = version,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )

        // Lớp 5: thanh trên.
        if (glass) {
            GlassTopAppBar(
                title = if (showTitle) title else "",
                // Dải kính chỉ bật khi đã cuộn hết phần đầu (giống trang Giới thiệu của Miuix): lúc đó nền shader đã mờ hết.
                // Bật sớm hơn thì dải làm mờ lấy từ lớp nội dung (chỉ có màu nền surface, không có nền shader / phần đầu nằm ở lớp khác)
                // nên phủ một mảng màu nền đặc, tối đen ở giao diện tối, lên phần đầu còn đang hiện.
                isContentScrolled = collapsed,
                modifier = Modifier.align(Alignment.TopCenter),
                expandable = false,
                backdrop = contentBackdrop,
                progressiveBlur = contentBackdrop != null,
                navigationIcon = {
                    GlassIconButton(onClick = onDismiss) {
                        AsyncImage(
                            model = Util.iconModel(AppIcons.BarClose),
                            contentDescription = stringResource(R.string.action_cancel),
                            modifier = Modifier.size(20.dp),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
                        )
                    }
                },
            )
        } else {
            val barBackground = when {
                !collapsed -> Modifier
                contentBackdrop != null -> Modifier.barBlur(contentBackdrop)
                else -> Modifier.background(surface)
            }
            val titleAlpha by animateFloatAsState(if (showTitle) 1f else 0f, tween(200), label = "settingsTitleAlpha")
            Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().then(barBackground)) {
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .height(TopBarHeight)
                        .padding(horizontal = 10.dp),
                ) {
                    Text(
                        text = title,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 52.dp)
                            .graphicsLayer { alpha = titleAlpha },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    Box(Modifier.align(Alignment.CenterStart)) {
                        TopBarIcon(icon = AppIcons.BarClose, description = stringResource(R.string.action_cancel), onClick = onDismiss, circle = true)
                    }
                }
            }
        }
    }
}

/** Nút kiểm tra phiên bản mới nổi ở đáy: đổi nhãn theo [UpdateState]; có bản mới thì bấm để mở trang tải. */
@Composable
private fun SettingsUpdateButton(version: AppVersion, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val labelRes = when (update) {
        UpdateState.Idle -> R.string.update_check
        UpdateState.Checking -> R.string.update_checking
        UpdateState.UpToDate -> R.string.update_latest
        UpdateState.Failed -> R.string.update_failed
        is UpdateState.Available -> R.string.update_available
    }
    val label = (update as? UpdateState.Available)?.let { stringResource(labelRes, it.code) } ?: stringResource(labelRes)
    val onPrimary = MiuixTheme.colorScheme.onPrimary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(UpdateButtonHeight)
            .clip(RoundedCornerShape(UpdateButtonHeight / 2))
            .background(MiuixTheme.colorScheme.primary)
            .clickable {
                when (val state = update) {
                    UpdateState.Checking -> Unit
                    is UpdateState.Available -> openUrl(context, state.url)
                    else -> {
                        update = UpdateState.Checking
                        scope.launch { update = UpdateChecker.check(version) }
                    }
                }
            }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = Util.iconModel(AppIcons.BarRestart),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(onPrimary),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = onPrimary, maxLines = 1)
    }
}

/** Một nhóm: nhãn nhỏ phía trên, thẻ bên dưới ([SettingsCard]). */
@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
        )
        SettingsCard(content)
    }
}

/** Thẻ bo tròn chứa các dòng. Thường: nền surfaceContainer của Miuix; kính: nền trong suốt nhạt để thấy nền phía sau. */
@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val color = if (LocalGlassEnabled.current) MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f) else MiuixTheme.colorScheme.surfaceContainer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color),
        content = content,
    )
}

/** Vạch kẻ mảnh giữa các dòng trong [SettingsCard], chừa lề 16dp hai bên. */
@Composable
internal fun SettingsRowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.2f)),
    )
}

/** Dòng chỉ xem: nhãn bên trái, giá trị bên phải (ví dụ Tác giả). */
@Composable
internal fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(text = value, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions)
    }
}

/** Dòng liên kết: icon bên trái, nhãn, icon mở ra ngoài bên phải. Bấm để mở [url]. */
@Composable
internal fun SettingsLinkItem(icon: String, label: String, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openUrl(context, url) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = Util.iconModel(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = label, fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        AsyncImage(
            model = Util.iconModel(AppIcons.BarExternal),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantSummary),
        )
    }
}
