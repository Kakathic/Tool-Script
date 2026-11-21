package com.tool.script.ui.main

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Scaffold
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

// Màn hình chính: tiêu đề = tên app (căn trái, cỡ cố định), HorizontalPager + thanh tab dưới.
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
    onShowDetails: (title: String, text: String) -> Unit,
) {
    val config = result.config
    val pages = config.pages
    val scope = rememberCoroutineScope()
    val feedback = rememberFeedbackHost()
    val pagerState = rememberPagerState { pages.size }
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

    // Banner cảnh báo khởi động / cấu hình, ẩn được.
    var warningDismissed by rememberSaveable { mutableStateOf(false) }
    val warningText = buildList {
        result.scriptError?.let { add("Script khởi động lỗi.") }
        if (config.warnings.isNotEmpty()) add("${config.warnings.size} cảnh báo cấu hình.")
    }.joinToString(" ")

    // Dialog luôn nằm trên cùng ngăn xếp và là lớp phủ riêng (xem DialogLayer): trang bên dưới giữ nguyên, không chạy chuyển trang.
    val dialogFile = stack.lastOrNull()?.takeIf { config.subPages[it]?.dialog == true }
    val lastDialog = remember { LastDialog() }
    if (dialogFile != null) lastDialog.file = dialogFile
    val top = if (dialogFile != null) stack.getOrNull(stack.size - 2) else stack.lastOrNull()
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = top,
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
                SubPageScreen(subPage, env, feedback, onBack = { closeTop(false) }, onConfirm = { closeTop(true) })
            } else {
                Scaffold(
                    topBar = {
                        AppTopBar(
                            title = config.app.name,
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
                    bottomBar = {
                        if (pages.size >= 2) {
                            NavigationBar {
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
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(top = inner.calculateTopPadding()),
                    ) {
                        if (warningText.isNotEmpty() && !warningDismissed) {
                            Banner(
                                message = warningText,
                                warning = true,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
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
                                env = env,
                                bottomInset = inner.calculateBottomPadding(),
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
                DialogLayer(sheetPage, env, feedback, onBack = { closeTop(false) }, onConfirm = { closeTop(true) })
            }
        }
        // Banner thông báo hiện đè lên cả thanh trên, trượt xuống / lên.
        BannerOverlay(feedback, Modifier.align(Alignment.TopCenter))
    }
}

/** Cỡ chữ tiêu đề thanh trên (tên app, tên trang con). */
private val TopBarTitleSize = 22.sp

/**
 * Thanh trên: tiêu đề sát trái (16dp, khớp lề thẻ), cỡ chữ cố định, không phóng to / thu nhỏ khi cuộn.
 * Có [onBack] thì thêm nút ở đầu thanh: mũi tên quay lại (trang con) hoặc dấu X khi [closeStyle] (dialog).
 */
@Composable
private fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    closeStyle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface)
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = if (onBack != null) 4.dp else 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TopBarIcon(
                icon = if (closeStyle) "icons/bar_close.svg" else "icons/bar_back.svg",
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

/** Màn hình trang con: thanh trên có nút quay lại (dialog: nút đóng), nội dung nạp từ file TOML của trang con. */
@Composable
private fun SubPageScreen(
    page: SubPage,
    env: RenderEnv,
    feedback: FeedbackHost,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    topBarModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
) {
    // Dialog chỉ truyền biến: item không chạy lệnh, nút Xác nhận mới chạy lệnh của link.
    val pageEnv = remember(env, page.dialog) { if (page.dialog) env.asVarsOnly() else env }
    Scaffold(
        topBar = { Box(topBarModifier) { AppTopBar(title = page.title, onBack = onBack, closeStyle = page.dialog) } },
        bottomBar = { if (page.dialog) DialogButtons(onCancel = onBack, onConfirm = onConfirm) },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding())
                .then(contentModifier),
        ) {
            Box(Modifier.weight(1f)) {
                PageList(
                    list = page.items.filter { isShown(it, pageEnv.store) },
                    error = page.error,
                    env = pageEnv,
                    bottomInset = inner.calculateBottomPadding(),
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

/** Lớp phủ của dialog: nền mờ (bấm vào là Hủy) và sheet trượt từ dưới lên; chiều cao tự tính theo nội dung, kéo thanh trên để đổi. */
@Composable
private fun AnimatedVisibilityScope.DialogLayer(
    page: SubPage,
    env: RenderEnv,
    feedback: FeedbackHost,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .animateEnterExit(enter = fadeIn(tween(DIALOG_MILLIS)), exit = fadeOut(tween(DIALOG_MILLIS)))
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBack),
        )
        Box(
            Modifier
                .fillMaxSize()
                .animateEnterExit(
                    enter = slideInVertically(tween(DIALOG_MILLIS)) { it },
                    exit = slideOutVertically(tween(DIALOG_MILLIS)) { it },
                ),
        ) {
            DialogSheet(page, env, feedback, onBack, onConfirm)
        }
    }
}

/**
 * Đo chiều cao tự nhiên của dialog (thanh trên + toàn bộ mục + hai nút) bằng cách dựng nội dung ẩn, không vẽ, không nhận chạm.
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
            // Lề 12dp phải khớp với contentPadding của PageList để chiều cao đo được bằng chiều cao thật.
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                page.error?.let { PageErrorCard(it) }
                page.items.filter { isShown(it, pageEnv.store) }.forEach { ItemRenderer(it, pageEnv) }
            }
            DialogButtons(onCancel = {}, onConfirm = {})
        }
    }
}

/**
 * Sheet của dialog, neo ở đáy màn hình; [heightPx] là chiều cao hiện tại (đáy cố định nên hai nút Hủy / Xác nhận luôn thấy,
 * kéo xuống thì các mục phía trên bị che dần).
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
                heightPx > fullThresholdPx -> if (heightPx < screenPx - 0.5f) animateTo(screenPx, -velocity)
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
                    onDragStarted = { cancelSettle() },
                    onDragStopped = { velocity -> settle(velocity) },
                ),
                contentModifier = Modifier.nestedScroll(connection),
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

/** OverlayDialog của Miuix cần nằm trong Scaffold (nội dung), đặt ngoài thì không hiện. */
@Composable
private fun FeedbackDialogs(feedback: FeedbackHost, env: RenderEnv) {
    LogDialog(entry = feedback.log, onDismiss = { feedback.log = null })
    LogFullScreen(entry = feedback.log, onDismiss = { feedback.log = null })
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
    env: RenderEnv,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (error != null) {
            item { PageErrorCard(error) }
        }
        items(list) { item -> ItemRenderer(item, env) }
    }
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
    if (result.config.warnings.isNotEmpty()) {
        appendLine("== Cảnh báo cấu hình ==")
        result.config.warnings.forEach { appendLine("- $it") }
        appendLine()
    }
    appendLine("== Log khởi động ==")
    result.bootLog.forEach { appendLine(it) }
}

private fun rebootErrorText(result: ExecResult): String {
    if (result.timedOut) return "Khởi động lại máy: hết thời gian chờ"
    val firstLine = result.stderr.lineSequence().firstOrNull { it.isNotBlank() }
    return "Không khởi động lại được máy (cần root)" + (firstLine?.let { ": $it" } ?: "")
}

private const val DIALOG_MILLIS = 280

/** Chiều cao sheet (tỉ lệ chiều cao màn hình): thả dưới mức tối thiểu thì đóng, trên mức "full" thì lên toàn màn hình. */
private const val SHEET_MIN_FRACTION = 0.3f
private const val SHEET_FULL_FRACTION = 0.8f

/** Chiều cao thanh trên của dialog (khớp AppTopBar), dùng khi đo chiều cao nội dung. */
private val DIALOG_TOP_BAR_HEIGHT = 56.dp

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
