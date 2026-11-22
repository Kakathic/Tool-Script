package com.tool.script.ui.main

import com.tool.script.util.AppIcons
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import coil3.compose.AsyncImage
import com.tool.script.boot.BootResult
import com.tool.script.config.Item
import com.tool.script.config.PageRef
import com.tool.script.config.SubPage
import com.tool.script.executor.ExecResult
import com.tool.script.executor.ExecutorManager
import com.tool.script.ui.feedback.Banner
import com.tool.script.ui.feedback.BannerOverlay
import com.tool.script.ui.feedback.FeedbackHost
import com.tool.script.ui.feedback.ConfirmDialog
import com.tool.script.ui.feedback.LogDialog
import com.tool.script.ui.feedback.LogFullScreen
import com.tool.script.ui.feedback.rememberFeedbackHost
import com.tool.script.ui.render.ConfirmRequest
import com.tool.script.ui.render.ItemRenderer
import com.tool.script.ui.render.PageErrorCard
import com.tool.script.ui.render.RenderEnv
import com.tool.script.ui.render.isShown
import com.tool.script.ui.theme.ThemeMode
import com.tool.script.util.Util
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlin.math.min
import kotlin.math.max

// Màn hình chính: tiêu đề = tên app (chữ lớn kiểu Miuix, vuốt lên thì thu thành chữ nhỏ căn trái), HorizontalPager + thanh tab dưới.
// Thanh tab là cửa sổ trượt rộng 4 ô (tối thiểu 2); chỉ 1 tab thì ẩn thanh tab. Logic cửa sổ: TabWindow.kt.

/**
 * @param themeMode chế độ chủ đề hiện tại; [onThemeChange] được gọi khi người dùng chọn chế độ khác.
 * @param onShowDetails mở Trung tâm lỗi với (tiêu đề, nội dung).
 */
@Composable
fun MainScreen(
    result: BootResult,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    hapticEnabled: Boolean,
    onHapticChange: (Boolean) -> Unit,
    blurEnabled: Boolean,
    onBlurChange: (Boolean) -> Unit,
    onShowDetails: (title: String, text: String) -> Unit,
) {
    val config = result.config
    val pages = config.pages
    val scope = rememberCoroutineScope()
    val feedback = rememberFeedbackHost()
    val pagerState = rememberPagerState { pages.size }
    // Trạng thái thu gọn của tiêu đề lớn, dùng chung cho mọi tab (xem LargeTitleTopBar).
    val titleScroll = MiuixScrollBehavior()
    val context = LocalContext.current
    var showRestartMenu by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }

    // Ngăn xếp trang con (khóa = đường dẫn file); rỗng thì đang ở màn hình chính.
    var stack by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var pendingTab by remember { mutableStateOf<Int?>(null) }
    // Giá trị biến của dialog lúc mở, để khôi phục khi bấm Hủy.
    var dialogSnapshot by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val env = remember(result) {
        RenderEnv(
            store = result.store,
            feedback = feedback,
            scope = scope,
            openTab = { id ->
                val index = pages.indexOfFirst { it.ref.id == id }
                if (index >= 0) {
                    pendingTab = index
                    stack = emptyList()
                }
            },
            openPage = { file ->
                val target = config.subPages[file]
                if (target != null && stack.lastOrNull() != file) {
                    if (target.dialog) {
                        val ids = variableIds(target.items)
                        dialogSnapshot = result.store.snapshot().filterKeys { it in ids }
                    }
                    stack = stack + file
                }
            },
        )
    }

    // Chuyển tab sau khi đã về màn hình chính (pager chỉ chạy khi đang hiện).
    LaunchedEffect(pendingTab, stack.isEmpty()) {
        val target = pendingTab
        if (target != null && stack.isEmpty()) {
            // Không đặt pendingTab = null trước khi cuộn: pendingTab là key của effect này,
            // đổi key sẽ hủy coroutine và pager dừng giữa chừng (nhảy xa thì rơi sai tab).
            if (kotlin.math.abs(pagerState.currentPage - target) > 1) {
                pagerState.scrollToPage(target)
            } else {
                pagerState.animateScrollToPage(target)
            }
            pendingTab = null
        }
    }

    // Đóng trang trên cùng. Dialog: Xác nhận thì chạy lệnh của link với các biến đã chọn, Hủy thì khôi phục biến.
    val closeTop: (Boolean) -> Unit = { confirmed ->
        val top = stack.lastOrNull()?.let { config.subPages[it] }
        if (top != null && top.dialog) {
            val action = top.action
            if (confirmed) {
                if (action != null) env.run(action, action.run)
            } else {
                dialogSnapshot.forEach { (name, value) -> result.store[name] = value }
            }
        }
        stack = stack.dropLast(1)
    }

    BackHandler(enabled = stack.isNotEmpty()) { closeTop(false) }

    // Cửa sổ tab trượt.
    val visibleTabs = min(MAX_VISIBLE_TABS, pages.size)
    var windowStart by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(pagerState.currentPage) {
        windowStart = nextWindowStart(pagerState.currentPage, windowStart, visibleTabs, pages.size)
    }

    // Banner chung chỉ cho lỗi toàn app (script khởi động, main.toml), ẩn được.
    // Cảnh báo cấu hình của từng trang hiện riêng trong trang đó (xem PageWarningBanner).
    var warningDismissed by rememberSaveable { mutableStateOf(false) }
    val warningText = buildList {
        result.scriptError?.let { add("Script khởi động lỗi.") }
        if (config.appWarnings.isNotEmpty()) add("${config.appWarnings.size} cảnh báo cấu hình (main.toml).")
    }.joinToString(" ")

    // "Ẩn" của banner từng trang nhớ theo file (các file ngăn cách bằng xuống dòng).
    var dismissedPages by rememberSaveable { mutableStateOf("") }
    val pageWarningHost = remember(dismissedPages, onShowDetails) {
        PageWarningHost(
            dismissed = dismissedPages.split('\n'),
            onDismiss = { file -> dismissedPages += file + "\n" },
            onShow = { file, list -> onShowDetails("Cảnh báo cấu hình", buildPageDetails(file, list)) },
        )
    }

    // Dialog luôn nằm trên cùng ngăn xếp và là lớp phủ riêng (xem DialogLayer): trang bên dưới giữ nguyên, không chạy chuyển trang.
    val dialogFile = stack.lastOrNull()?.takeIf { config.subPages[it]?.dialog == true }
    val lastDialog = remember { LastDialog() }
    if (dialogFile != null) lastDialog.file = dialogFile
    val top = if (dialogFile != null) stack.getOrNull(stack.size - 2) else stack.lastOrNull()
    // pageBackdrop: hình của trang đang mở, làm nền mờ cho dialog sheet và banner.
    // fullBackdrop: hình của cả trang + dialog sheet + banner, làm nền mờ cho mọi OverlayDialog (không có blur thì nền tối như cũ).
    val pageBackdrop = rememberBarBackdrop(blurEnabled)
    val fullBackdrop = rememberBarBackdrop(blurEnabled)
    val blurRequests = remember { BlurRequests() }
    CompositionLocalProvider(
        LocalPageWarnings provides pageWarningHost,
        LocalBlurRequests provides (if (fullBackdrop != null) blurRequests else null),
    ) {
        // Scaffold gốc: mọi OverlayDialog của Miuix vẽ ở lớp popup của Scaffold ngoài cùng, nằm trên cả thanh trên / thanh tab.
        Scaffold { _ ->
            Box(Modifier.fillMaxSize().blurIfPositive(menuBlurRadius(blurRequests))) {
                Box(Modifier.fillMaxSize().then(if (fullBackdrop != null) Modifier.layerBackdrop(fullBackdrop) else Modifier)) {
                    AnimatedContent(
                        targetState = top,
                        modifier = if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier,
                        transitionSpec = {
                            val from = initialState
                            val to = targetState
                            when {
                                // Trang con thường: mở thì trượt vào từ phải, trang bên dưới lùi nhẹ sang trái;
                                // đóng thì trang trên trượt ra phải, trang bên dưới trượt về chỗ cũ.
                                // Trang bị gỡ khỏi stack = đang đóng (kể cả khi bấm link về tab và đóng hết một lượt).
                                to != null && (from == null || from in stack) ->
                                    (
                                        slideInHorizontally(tween(PAGE_MILLIS, easing = FastOutSlowInEasing)) { it } togetherWith
                                            slideOutHorizontally(tween(PAGE_MILLIS, easing = FastOutSlowInEasing)) { -it / PAGE_PARALLAX }
                                        ).also { it.targetContentZIndex = 1f }
                                from != null && from !in stack ->
                                    (
                                        slideInHorizontally(tween(PAGE_MILLIS, easing = FastOutSlowInEasing)) { -it / PAGE_PARALLAX } togetherWith
                                            slideOutHorizontally(tween(PAGE_MILLIS, easing = FastOutSlowInEasing)) { it }
                                        ).also { it.targetContentZIndex = -1f }
                                else -> EnterTransition.None togetherWith ExitTransition.None
                            }
                        },
                        label = "subPage",
                    ) { file ->
                        val subPage = file?.let { config.subPages[it] }
                        if (subPage != null) {
                            SubPageScreen(subPage, env, feedback, onBack = { closeTop(false) }, onConfirm = { closeTop(true) }, blur = blurEnabled)
                        } else {
                            val backdrop = rememberBarBackdrop(blurEnabled)
                            // Rời màn hình chính (mở trang con) thì danh sách cuộn mất vị trí, nên trả tiêu đề về trạng thái mở rộng.
                            DisposableEffect(Unit) {
                                onDispose {
                                    titleScroll.state.heightOffset = 0f
                                    titleScroll.state.contentOffset = 0f
                                }
                            }
                            Scaffold(
                                topBar = {
                                    LargeTitleTopBar(
                                        title = config.app.name,
                                        scrollBehavior = titleScroll,
                                        backdrop = backdrop,
                                        actions = {
                                            // Nút chủ đề: menu tùy chỉnh giao diện, thả xuống ngay dưới icon.
                                            ThemeMenu(
                                                expanded = showThemeMenu,
                                                current = themeMode,
                                                onOpen = { showThemeMenu = true },
                                                onDismiss = { showThemeMenu = false },
                                                onSelect = {
                                                    showThemeMenu = false
                                                    onThemeChange(it)
                                                },
                                                haptic = hapticEnabled,
                                                onHapticChange = onHapticChange,
                                                blur = blurEnabled,
                                                onBlurChange = onBlurChange,
                                            )
                                            // Nút nguồn: menu khởi động lại, thả xuống ngay dưới icon.
                                            PowerMenu(
                                                expanded = showRestartMenu,
                                                onOpen = { showRestartMenu = true },
                                                onDismiss = { showRestartMenu = false },
                                                onRestartApp = {
                                                    showRestartMenu = false
                                                    env.confirm = ConfirmRequest("Khởi động lại ứng dụng?") { Util.restartApp(context) }
                                                },
                                                onRebootDevice = {
                                                    showRestartMenu = false
                                                    env.confirm = ConfirmRequest("Khởi động lại máy? Máy sẽ tắt và bật lại ngay.") {
                                                        scope.launch {
                                                            val result = ExecutorManager.current.exec("reboot", 10)
                                                            if (!result.ok) feedback.toast(rebootErrorText(result))
                                                        }
                                                    }
                                                },
                                            )
                                        },
                                    )
                                },
                                snackbarHost = { SnackbarHost(feedback.snackbarState) },
                                bottomBar = {
                                    if (pages.size >= 2) {
                                        NavigationBar(
                                            modifier = Modifier.barBlur(backdrop),
                                            color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                                        ) {
                                            // Cửa sổ tab trượt mượt: cả hàng tab nằm trong một khung cắt, dịch sang trái / phải theo windowStart.
                                            val slide by animateFloatAsState(
                                                targetValue = windowStart.toFloat(),
                                                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                                                label = "tabWindowSlide",
                                            )
                                            BoxWithConstraints(Modifier.weight(1f).clipToBounds()) {
                                                val itemWidth = maxWidth / visibleTabs
                                                val itemWidthPx = constraints.maxWidth.toFloat() / visibleTabs
                                                Row(
                                                    Modifier
                                                        // Không dùng requiredWidth: khi rộng hơn khung cha nó tự căn GIỮA, làm cả hàng tab lệch nửa ô.
                                                        // Căn từ mép trái (unbounded) để windowStart = 0 khớp đúng ô đầu tiên.
                                                        .wrapContentWidth(Alignment.Start, unbounded = true)
                                                        .width(itemWidth * pages.size)
                                                        .offset { IntOffset((-slide * itemWidthPx).roundToInt(), 0) },
                                                ) {
                                                    pages.forEachIndexed { index, page ->
                                                        TabItem(
                                                            ref = page.ref,
                                                            selected = pagerState.currentPage == index,
                                                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                            ) { inner ->
                                // Có blur: nội dung kéo xuống dưới thanh trên (thẻ cuộn qua thanh thì bị làm mờ), chừa chỗ bằng padding của danh sách.
                                val barTop = inner.calculateTopPadding()
                                val bannerShown = warningText.isNotEmpty() && !warningDismissed
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .nestedScroll(titleScroll.nestedScrollConnection)
                                        .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier.padding(top = barTop)),
                                ) {
                                    if (bannerShown) {
                                        Banner(
                                            message = warningText,
                                            warning = true,
                                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (backdrop != null) barTop + 8.dp else 8.dp),
                                            actionLabel = "Xem",
                                            onAction = { onShowDetails("Cảnh báo khi khởi động", buildDetails(result)) },
                                            onDismiss = { warningDismissed = true },
                                        )
                                    }
                                        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
                                        val page = pages[pageIndex]
                                        PageList(
                                            list = page.items.filter { isShown(it, env.store) },
                                            error = page.error,
                                            file = page.ref.file,
                                            warnings = page.warnings,
                                            env = env,
                                            bottomInset = inner.calculateBottomPadding(),
                                            topInset = if (backdrop != null && !bannerShown) barTop else 0.dp,
                                        )
                                    }
                                    FeedbackDialogs(feedback, env)
                                }
                            }
                        }
                    }
                    // Dialog: nền mờ + sheet trượt từ dưới lên (giữ trang cuối trong lúc chạy hiệu ứng đóng).
                    AnimatedVisibility(visible = dialogFile != null, enter = EnterTransition.None, exit = ExitTransition.None) {
                        val sheetPage = lastDialog.file?.let { config.subPages[it] }
                        if (sheetPage != null) {
                            DialogLayer(sheetPage, env, feedback, onBack = { closeTop(false) }, onConfirm = { closeTop(true) }, backdrop = pageBackdrop)
                        }
                    }
                    // Các banner thông báo xếp chồng ở đầu màn hình, đè lên cả thanh trên, trượt xuống / lên.
                    BannerOverlay(feedback, Modifier.align(Alignment.TopCenter), backdrop = pageBackdrop)
                }
                if (fullBackdrop != null) DialogBlurScrim(blurRequests, fullBackdrop)
            }
        }
    }
}

/** Cỡ chữ tiêu đề thanh trên (tên app, tên trang con). */
private val TopBarTitleSize = 22.sp

/**
 * Thanh trên: tiêu đề sát trái (16dp, khớp lề thẻ), cỡ chữ cố định, không phóng to / thu nhỏ khi cuộn.
 * Có [onBack] thì thêm nút ở đầu thanh: mũi tên quay lại (trang con) hoặc dấu X khi [closeStyle] (dialog).
 * [backdrop] khác null: nền thanh trong suốt và làm mờ phần nội dung cuộn phía dưới (xem BarBlur.kt).
 */
@Composable
private fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    closeStyle: Boolean = false,
    backdrop: LayerBackdrop? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .barBlur(backdrop)
            .background(if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface)
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = if (onBack != null) 4.dp else 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TopBarIcon(
                icon = if (closeStyle) AppIcons.BarClose else AppIcons.BarBack,
                description = if (closeStyle) "Đóng" else "Quay lại",
                onClick = onBack,
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack != null) 4.dp else 0.dp),
            fontSize = TopBarTitleSize,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

// Tiêu đề lớn kiểu Miuix TopAppBar (cùng cỡ chữ, lề 26dp, cùng hành vi thu gọn / mở rộng của MiuixScrollBehavior).
private val BarCollapsedHeight = 56.dp
private val LargeTitleHorizontalPadding = 26.dp
private val LargeTitleBottomPadding = 4.dp

/**
 * Thanh trên của màn hình chính: lúc mở rộng hiện tên app chữ lớn bên dưới hàng icon; vuốt lên thì chữ lớn
 * mờ dần rồi thu lại, tên chữ nhỏ hiện ở vị trí cũ (căn trái, cao 56dp, icon bên phải). Vuốt xuống tới đầu danh sách thì mở rộng lại.
 * Trạng thái nằm trong [scrollBehavior]; gắn `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` vào vùng nội dung cuộn.
 * [backdrop] khác null: nền thanh trong suốt và làm mờ phần nội dung cuộn phía dưới (xem BarBlur.kt).
 */
@Composable
private fun LargeTitleTopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val state = scrollBehavior.state
    // Chữ nhỏ chỉ hiện khi đã thu gần hết (giống Miuix: 1/3 quãng thu gọn).
    val smallVisible by remember(state) { derivedStateOf { state.collapsedFraction * 3f >= 1f } }
    val smallAlpha by animateFloatAsState(
        targetValue = if (smallVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (smallVisible) 220 else 120),
        label = "smallTitleAlpha",
    )
    val smallShift by animateFloatAsState(
        targetValue = if (smallVisible) 0f else 20f,
        animationSpec = tween(durationMillis = if (smallVisible) 220 else 120),
        label = "smallTitleShift",
    )

    Layout(
        content = {
            // Hàng thanh thu gọn: giữ đúng vị trí cũ (lề trái 16dp, icon bên phải).
            Row(
                modifier = Modifier.layoutId("bar").padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            alpha = smallAlpha
                            translationY = smallShift
                        },
                    fontSize = TopBarTitleSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                actions()
            }
            // Chữ lớn nằm dưới hàng thanh; đọc state trong lớp vẽ / bố cục để cuộn không làm recompose.
            Box(
                modifier = Modifier
                    .layoutId("large")
                    .padding(top = BarCollapsedHeight)
                    .padding(horizontal = LargeTitleHorizontalPadding)
                    .graphicsLayer { alpha = 1f - (state.collapsedFraction * 3f).coerceIn(0f, 1f) },
            ) {
                Text(
                    text = title,
                    modifier = Modifier
                        .offset { IntOffset(0, state.heightOffset.roundToInt()) }
                        .onSizeChanged { size ->
                            val limit = -size.height.toFloat()
                            if (state.heightOffsetLimit != limit) state.heightOffsetLimit = limit
                        },
                    fontSize = MiuixTheme.textStyles.title1.fontSize,
                    fontWeight = FontWeight.Normal,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .barBlur(backdrop)
            .background(if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface)
            .statusBarsPadding()
            .clipToBounds(),
    ) { measurables, constraints ->
        val collapsedPx = BarCollapsedHeight.roundToPx()
        val bottomPadPx = LargeTitleBottomPadding.roundToPx()
        val bar = measurables.first { it.layoutId == "bar" }
            .measure(constraints.copy(minHeight = collapsedPx, maxHeight = collapsedPx))
        val large = measurables.first { it.layoutId == "large" }
            .measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        // Phần mở rộng = chiều cao chữ lớn (hộp chữ lớn đã có đệm trên bằng đúng hàng thanh thu gọn).
        val expansion = (large.height - collapsedPx).coerceAtLeast(0)
        val offset = state.heightOffset
        val collapse = if (expansion > 0 && !offset.isNaN()) (kotlin.math.abs(offset) / expansion).coerceIn(0f, 1f) else 1f
        val openPart = 1f - collapse
        val height = collapsedPx + ((expansion + bottomPadPx) * openPart).roundToInt()
        layout(constraints.maxWidth, height) {
            large.placeRelative(0, 0)
            bar.placeRelative(0, 0)
        }
    }
}

/**
 * Thanh trên của dialog sheet (kiểu OverlayBottomSheet của Miuix): tiêu đề ở giữa, X bên trái, tích bên phải.
 * [onCancel] / [onConfirm] = null thì ẩn nút tương ứng (sheet full dùng nút Hủy / Xác nhận ở dưới).
 */
@Composable
private fun SheetTopBar(title: String, onCancel: (() -> Unit)?, onConfirm: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface)
            .statusBarsPadding()
            .height(DIALOG_TOP_BAR_HEIGHT)
            .padding(horizontal = 4.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 52.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onCancel != null) {
            Box(Modifier.align(Alignment.CenterStart)) { TopBarIcon(icon = AppIcons.BarClose, description = "Hủy", onClick = onCancel) }
        }
        if (onConfirm != null) {
            Box(Modifier.align(Alignment.CenterEnd)) { TopBarIcon(icon = AppIcons.BarCheck, description = "Xác nhận", onClick = onConfirm) }
        }
    }
}

/** Màn hình trang con: thanh trên có nút quay lại, nội dung nạp từ file TOML của trang con. Dialog sheet dùng [SheetTopBar]. */
@Composable
private fun SubPageScreen(
    page: SubPage,
    env: RenderEnv,
    feedback: FeedbackHost,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    topBarModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    blur: Boolean = false,
    sheetFull: Boolean = false,
) {
    // Dialog chỉ truyền biến: item không chạy lệnh, nút Xác nhận mới chạy lệnh của link.
    val pageEnv = remember(env, page.dialog) { if (page.dialog) env.asVarsOnly() else env }
    // Dialog không làm mờ: chiều cao sheet được đo theo bố cục cũ (xem DialogContentMeasurer).
    val backdrop = rememberBarBackdrop(blur && !page.dialog)
    Scaffold(
        topBar = {
            Box(topBarModifier) {
                if (page.dialog) {
                    // Dialog chưa full: X = Hủy, tích = Xác nhận; full thì hai nút nằm ở thanh dưới.
                    SheetTopBar(page.title, onCancel = if (sheetFull) null else onBack, onConfirm = if (sheetFull) null else onConfirm)
                } else {
                    AppTopBar(title = page.title, onBack = onBack, closeStyle = false, backdrop = backdrop)
                }
            }
        },
        snackbarHost = { SnackbarHost(feedback.snackbarState) },
        bottomBar = { if (page.dialog && sheetFull) DialogButtons(onCancel = onBack, onConfirm = onConfirm) },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier.padding(top = inner.calculateTopPadding()))
                .then(contentModifier),
        ) {
            Box(Modifier.weight(1f)) {
                PageList(
                    list = page.items.filter { isShown(it, pageEnv.store) },
                    error = page.error,
                    file = page.file,
                    warnings = page.warnings,
                    env = pageEnv,
                    bottomInset = inner.calculateBottomPadding(),
                    topInset = if (backdrop != null) inner.calculateTopPadding() else 0.dp,
                )
            }
            FeedbackDialogs(feedback, env)
        }
    }
}

/** Nhớ file dialog cuối cùng đã mở, để còn nội dung hiển thị trong lúc chạy hiệu ứng đóng (lúc đó stack đã bỏ dialog). */
private class LastDialog {
    var file: String? = null
}

/** Trạng thái sheet báo ra ngoài cho lớp nền: [scrim] 0..1 (đầy khi sheet cao từ mức tối thiểu trở lên, nhạt dần khi kéo xuống sắp đóng), [full] = đã full màn hình. */
@Stable
private class SheetState {
    var scrim by mutableFloatStateOf(0f)
    var full by mutableStateOf(false)
}

/**
 * Lớp phủ của dialog: nền tối hoặc làm mờ trang phía sau khi bật blur (bấm vào là Hủy) và sheet trượt từ dưới lên;
 * chiều cao tự tính theo nội dung, kéo thanh trên để đổi. Nền đậm / nhạt theo chiều cao sheet, sheet full thì bỏ nền (không thấy được).
 */
@Composable
private fun AnimatedVisibilityScope.DialogLayer(
    page: SubPage,
    env: RenderEnv,
    feedback: FeedbackHost,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    backdrop: LayerBackdrop? = null,
) {
    val sheet = remember { SheetState() }
    Box(Modifier.fillMaxSize()) {
        if (!sheet.full) {
            Box(
                Modifier
                    .fillMaxSize()
                    .animateEnterExit(enter = fadeIn(tween(DIALOG_MILLIS)), exit = fadeOut(tween(DIALOG_MILLIS)))
                    .graphicsLayer { alpha = sheet.scrim }
                    .then(if (backdrop != null) Modifier.scrimBlur(backdrop) else Modifier.background(Color.Black.copy(alpha = 0.4f)))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBack),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .animateEnterExit(
                    enter = slideInVertically(tween(DIALOG_MILLIS)) { it },
                    exit = slideOutVertically(tween(DIALOG_MILLIS)) { it },
                ),
        ) {
            DialogSheet(page, env, feedback, onBack, onConfirm, sheet)
        }
    }
}

/**
 * Đo chiều cao tự nhiên của dialog (thanh trên + toàn bộ mục + chỗ thanh điều hướng) bằng cách dựng nội dung ẩn, không vẽ, không nhận chạm.
 * Chiều cao được báo qua [onHeight] và tự cập nhật khi các mục đổi (vd điều kiện hiện / ẩn).
 */
@Composable
private fun DialogContentMeasurer(page: SubPage, env: RenderEnv, onHeight: (Int) -> Unit) {
    val pageEnv = remember(env) { env.asVarsOnly() }
    Box(Modifier.fillMaxWidth().height(0.dp).clearAndSetSemantics {}) {
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .alpha(0f)
                .onSizeChanged { onHeight(it.height) },
        ) {
            Spacer(Modifier.height(DIALOG_TOP_BAR_HEIGHT))
            // Lề 16dp và khoảng cách 12dp phải khớp với PageList để chiều cao đo được bằng chiều cao thật.
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (shouldShowPageWarnings(page.file, page.warnings)) PageWarningBanner(page.file, page.warnings)
                page.error?.let { PageErrorCard(it) }
                page.items.filter { isShown(it, pageEnv.store) }.forEach { ItemRenderer(it, pageEnv) }
            }
            // Sheet chưa full không có thanh nút ở dưới, chỉ chừa chỗ thanh điều hướng (khớp Scaffold của SubPageScreen).
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/**
 * Sheet của dialog, neo ở đáy màn hình; [heightPx] là chiều cao hiện tại (kéo xuống thì các mục phía trên bị che dần).
 * Chưa full: thanh trên có X (Hủy) và tích (Xác nhận), không có thanh nút dưới. Full màn hình: hiện hai nút Hủy / Xác nhận ở dưới.
 * Kéo thanh trên xuống từ trạng thái full thì rời full ngay (không bật lại), thả dưới mức tối thiểu thì đóng như Hủy.
 * Chiều cao mở = chiều cao nội dung thật (tối thiểu [SHEET_MIN_FRACTION] màn hình); nếu nội dung vượt [SHEET_FULL_FRACTION] thì mở toàn màn hình.
 * Thả tay: dưới [SHEET_MIN_FRACTION] thì đóng như Hủy, trên [SHEET_FULL_FRACTION] thì lên toàn màn hình, ở giữa thì giữ nguyên chiều cao.
 * Vuốt mạnh (hơn [DISMISS_VELOCITY]): xuống thì đóng như Hủy, lên thì toàn màn hình. Vuốt nhẹ thì chỉ đổi chiều cao theo đà vuốt
 * (giảm không thấp hơn mức tối thiểu; tăng vượt mức full thì lên toàn màn hình).
 * Cuộn nội dung lên khi sheet chưa full thì mở rộng sheet trước; nội dung đã ở đầu mà còn kéo xuống thì thu sheet (không thấp hơn mức tối thiểu, không đóng).
 */
@Composable
private fun DialogSheet(
    page: SubPage,
    env: RenderEnv,
    feedback: FeedbackHost,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    sheet: SheetState,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    val statusPx = WindowInsets.statusBars.getTop(density).toFloat()
    var naturalPx by remember { mutableIntStateOf(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenPx = constraints.maxHeight.toFloat()
        val minPx = SHEET_MIN_FRACTION * screenPx
        val fullThresholdPx = SHEET_FULL_FRACTION * screenPx
        var heightPx by remember(screenPx) { mutableFloatStateOf(0f) }
        var ready by remember(screenPx) { mutableStateOf(false) }
        var movedByGesture by remember { mutableStateOf(false) }
        val jobs = remember { arrayOfNulls<Job>(1) }
        // Lần kéo thanh trên hiện tại bắt đầu khi sheet đang full.
        val startedFull = remember { mutableStateOf(false) }

        DialogContentMeasurer(page, env) { naturalPx = it }

        // Lần đo đầu tiên: đặt chiều cao mở. Sheet ẩn (alpha 0) tới lúc đó để không bị nhảy chiều cao.
        LaunchedEffect(naturalPx, screenPx) {
            if (!ready && naturalPx > 0) {
                val natural = naturalPx.toFloat()
                heightPx = if (natural > fullThresholdPx) screenPx else max(natural, minPx)
                ready = true
            }
        }

        fun cancelSettle() {
            jobs[0]?.cancel()
        }

        fun animateTo(target: Float, velocity: Float, onEnd: () -> Unit = {}) {
            cancelSettle()
            jobs[0] = scope.launch {
                animate(
                    initialValue = heightPx,
                    targetValue = target,
                    initialVelocity = velocity,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                ) { value, _ -> heightPx = value }
                onEnd()
            }
        }

        // Thả tay: velocity (px/s) dương = kéo xuống. Kéo lên làm sheet cao hơn nên vận tốc chiều cao ngược dấu.
        fun settle(velocity: Float) {
            when {
                heightPx < minPx - 1f || velocity > DISMISS_VELOCITY -> animateTo(0f, -velocity) { currentOnBack() }
                velocity < -DISMISS_VELOCITY -> if (heightPx < screenPx - 0.5f) animateTo(screenPx, -velocity)
                velocity > FLING_VELOCITY ->
                    animateTo((heightPx - velocity * FLING_PROJECTION).coerceAtLeast(minPx), -velocity)
                velocity < -FLING_VELOCITY -> {
                    val target = heightPx - velocity * FLING_PROJECTION
                    animateTo(if (target > fullThresholdPx) screenPx else target, -velocity)
                }
                heightPx > fullThresholdPx -> {
                    val leftFull = startedFull.value && heightPx < screenPx * (1f - LEAVE_FULL_FRACTION)
                    if (heightPx < screenPx - 0.5f && !leftFull) animateTo(screenPx, -velocity)
                }
            }
        }

        val dragState = rememberDraggableState { delta ->
            cancelSettle()
            heightPx = (heightPx - delta).coerceIn(0f, screenPx)
        }

        val connection = remember(screenPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val dy = available.y
                    if (dy < 0f && heightPx < screenPx) {
                        val grow = min(-dy, screenPx - heightPx)
                        cancelSettle()
                        heightPx += grow
                        movedByGesture = true
                        return Offset(0f, -grow)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    val dy = available.y
                    if (dy > 0f && heightPx > minPx) {
                        val shrink = min(dy, heightPx - minPx)
                        cancelSettle()
                        heightPx -= shrink
                        movedByGesture = true
                        return Offset(0f, shrink)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!movedByGesture) return Velocity.Zero
                    movedByGesture = false
                    startedFull.value = false
                    settle(available.y)
                    return available
                }
            }
        }

        // topPx = khoảng từ đỉnh màn hình đến mép trên sheet. Chạm tới thanh trạng thái thì hết bo góc, thanh kéo mờ dần.
        val topPx = (screenPx - heightPx).coerceAtLeast(0f)
        val roundness = if (statusPx > 0f) (topPx / statusPx).coerceIn(0f, 1f) else if (topPx > 0f) 1f else 0f
        val corner = SHEET_CORNER * roundness
        // Phần thanh trạng thái nằm phía trên sheet thì báo "đã dùng", để thanh trên bên trong không chừa chỗ lần nữa.
        val consumedTop = with(density) { min(topPx, statusPx).toDp() }
        val sheetFull = heightPx >= screenPx - 1f
        SideEffect {
            sheet.full = sheetFull
            sheet.scrim = if (minPx > 0f) (heightPx / minPx).coerceIn(0f, 1f) else 0f
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .height(with(density) { heightPx.toDp() })
                .graphicsLayer { alpha = if (ready) 1f else 0f }
                .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
                // Chặn chạm xuyên xuống nền mờ (nếu không, chạm vào chỗ trống của sheet sẽ thành Hủy).
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .consumeWindowInsets(PaddingValues(top = consumedTop)),
        ) {
            SubPageScreen(
                page = page,
                env = env,
                feedback = feedback,
                onBack = onBack,
                onConfirm = onConfirm,
                topBarModifier = Modifier.draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        cancelSettle()
                        startedFull.value = heightPx >= screenPx - 1f
                    },
                    onDragStopped = { velocity -> settle(velocity) },
                ),
                contentModifier = Modifier.nestedScroll(connection),
                sheetFull = sheetFull,
            )
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .graphicsLayer { alpha = roundness }
                    .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** Thanh nút cuối dialog: Hủy (khôi phục biến) và Xác nhận (chạy lệnh). */
@Composable
private fun DialogButtons(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(text = "Hủy", onClick = onCancel, modifier = Modifier.weight(1f))
        TextButton(
            text = "Xác nhận",
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

/** Tên biến (id) của các item, kể cả item con trong group. */
private fun variableIds(items: List<Item>): Set<String> = buildSet {
    for (item in items) {
        item.id?.let { add(it) }
        addAll(variableIds(item.children))
    }
}

/** BlurDialog / OverlayDialog của Miuix cần nằm trong Scaffold (nội dung), đặt ngoài thì không hiện. */
@Composable
private fun FeedbackDialogs(feedback: FeedbackHost, env: RenderEnv) {
    LogDialog(entry = feedback.log, onDismiss = { feedback.log = null }, onCancel = { feedback.log?.cancel() })
    LogFullScreen(entry = feedback.log, onDismiss = { feedback.log = null }, onCancel = { feedback.log?.cancel() })
    val confirm = env.confirm
    ConfirmDialog(
        message = confirm?.message,
        onConfirm = {
            env.confirm = null
            confirm?.onConfirm?.invoke()
        },
        onDismiss = { env.confirm = null },
    )
}

@Composable
private fun PageList(
    list: List<com.tool.script.config.Item>,
    error: String?,
    file: String,
    warnings: List<String>,
    env: RenderEnv,
    bottomInset: androidx.compose.ui.unit.Dp,
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val showWarnings = shouldShowPageWarnings(file, warnings)
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp + topInset, bottom = 16.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Banner nằm trong danh sách nên cuộn cùng nội dung, không che mục nào.
        if (showWarnings) {
            item { PageWarningBanner(file, warnings) }
        }
        if (error != null) {
            item { PageErrorCard(error) }
        }
        items(list) { item -> ItemRenderer(item, env) }
    }
}

/** Banner cảnh báo cấu hình của từng trang: "Ẩn" nhớ theo file, "Xem" mở Trung tâm lỗi với danh sách cảnh báo của trang đó. */
private class PageWarningHost(
    val dismissed: List<String>,
    val onDismiss: (file: String) -> Unit,
    val onShow: (file: String, warnings: List<String>) -> Unit,
)

private val LocalPageWarnings = staticCompositionLocalOf<PageWarningHost?> { null }

/** Chỉ trang có cảnh báo (và chưa bị ẩn) mới hiện banner. */
@Composable
private fun shouldShowPageWarnings(file: String, warnings: List<String>): Boolean {
    val host = LocalPageWarnings.current ?: return false
    return warnings.isNotEmpty() && file !in host.dismissed
}

@Composable
private fun PageWarningBanner(file: String, warnings: List<String>) {
    val host = LocalPageWarnings.current ?: return
    Banner(
        message = "${warnings.size} cảnh báo cấu hình ở trang này.",
        warning = true,
        actionLabel = "Xem",
        onAction = { host.onShow(file, warnings) },
        onDismiss = { host.onDismiss(file) },
    )
}

private fun buildPageDetails(file: String, warnings: List<String>): String = buildString {
    appendLine("== Cảnh báo cấu hình: $file ==")
    warnings.forEach { appendLine("- $it") }
}

@Composable
private fun RowScope.TabItem(ref: PageRef, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantActions
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = Util.iconModel(ref.icon),
            contentDescription = ref.title,
            modifier = Modifier.size(26.dp),
            colorFilter = ColorFilter.tint(color),
        )
        Text(
            text = ref.title,
            color = color,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun buildDetails(result: BootResult): String = buildString {
    result.scriptError?.let {
        appendLine("== Script khởi động ==")
        appendLine(it)
        appendLine()
    }
    if (result.config.appWarnings.isNotEmpty()) {
        appendLine("== Cảnh báo cấu hình (main.toml) ==")
        result.config.appWarnings.forEach { appendLine("- $it") }
        appendLine()
    }
    appendLine("== Log khởi động ==")
    result.bootLog.forEach { appendLine(it) }
}

private fun rebootErrorText(result: ExecResult): String {
    if (result.timedOut) return "Khởi động lại máy: hết thời gian chờ"
    val firstLine = result.output.lineSequence().firstOrNull { it.isNotBlank() }
    return "Không khởi động lại được máy (cần root)" + (firstLine?.let { ": $it" } ?: "")
}

private const val DIALOG_MILLIS = 280

/** Chiều cao sheet (tỉ lệ chiều cao màn hình): thả dưới mức tối thiểu thì đóng, trên mức "full" thì lên toàn màn hình. */
private const val SHEET_MIN_FRACTION = 0.3f
private const val SHEET_FULL_FRACTION = 0.8f

/** Chiều cao thanh trên của dialog (khớp SheetTopBar), dùng khi đo chiều cao nội dung. */
private val DIALOG_TOP_BAR_HEIGHT = 56.dp

/** Kéo thanh trên xuống từ trạng thái full quá tỉ lệ này của chiều cao màn hình thì rời full (không tự bật lại). */
private const val LEAVE_FULL_FRACTION = 0.06f

/** Bo góc sheet khi chưa chạm thanh trạng thái. */
private val SHEET_CORNER = 24.dp

/** Vận tốc (px/s): vuốt nhanh hơn mức FLING thì đổi chiều cao theo đà (lên / xuống); nhanh hơn mức DISMISS thì lên toàn màn hình (vuốt lên) hoặc đóng (vuốt xuống). */
private const val FLING_VELOCITY = 800f
private const val DISMISS_VELOCITY = 2200f

/** Thời gian (giây) tính quãng trượt tiếp theo đà khi vuốt nhẹ: đổi chiều cao ≈ vận tốc × hệ số này. */
private const val FLING_PROJECTION = 0.25f

/** Thời gian trượt khi mở / đóng trang con (ms) và độ lùi của trang bên dưới (1/N chiều rộng). */
private const val PAGE_MILLIS = 320
private const val PAGE_PARALLAX = 4
