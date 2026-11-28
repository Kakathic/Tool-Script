package com.tool.script.ui.main

import android.content.Context
import com.tool.script.R
import androidx.compose.ui.res.stringResource
import com.tool.script.util.AppIcons
import com.tool.script.util.APP_LANGUAGE_ID
import com.tool.script.util.AppLanguage
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
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
import coil3.compose.AsyncImage
import com.tool.script.boot.BootResult
import com.tool.script.config.Item
import com.tool.script.config.ItemType
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
import com.tool.script.ui.render.LocalSelectHost
import com.tool.script.ui.render.PageErrorCard
import com.tool.script.ui.render.RenderEnv
import com.tool.script.ui.render.SelectFullScreenHost
import com.tool.script.ui.render.SelectHost
import com.tool.script.ui.render.isShown
import com.tool.script.ui.theme.DialogButton
import com.tool.script.ui.theme.LocalGlassEnabled
import com.tool.script.ui.theme.ThemeMode
import com.tool.script.util.Util
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text
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

/** Phần dải nền phía sau đảo kính nhô lên trên đỉnh đảo (dp). */
private val FloatingBarBandExtra = 28.dp

/** Lề trái / phải của đảo kính (thanh tab nổi), bằng lề thẻ 16dp để đảo rộng ngang hàng với thẻ. */
private val FloatingBarSideMargin = 16.dp

/**
 * @param themeMode chế độ chủ đề hiện tại; [onThemeChange] được gọi khi người dùng chọn chế độ khác.
 * @param language ngôn ngữ app đang chọn; [onLanguageChange] được gọi khi item `app_language` trong TOML đổi giá trị.
 * @param onShowDetails mở Trung tâm lỗi với (tiêu đề, nội dung).
 * @param openLogId mã log đã ẩn cần mở lại (khi chạm vào thông báo); mở xong gọi [onLogOpened].
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
    glassEnabled: Boolean,
    onGlassChange: (Boolean) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onShowDetails: (title: String, text: String) -> Unit,
    openLogId: Int? = null,
    onLogOpened: () -> Unit = {},
) {
    val config = result.config
    val pages = config.pages
    val scope = rememberCoroutineScope()
    val feedback = rememberFeedbackHost()
    // Chạm vào thông báo của log đã ẩn: mở lại màn hình log đó.
    LaunchedEffect(openLogId) {
        val id = openLogId ?: return@LaunchedEffect
        if (!feedback.showHiddenLog(id)) feedback.toast(R.string.log_gone)
        onLogOpened()
    }
    val pagerState = rememberPagerState { pages.size }
    val context = LocalContext.current
    var showRestartMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Ngăn xếp trang con (khóa = đường dẫn file); rỗng thì đang ở màn hình chính.
    var stack by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var pendingTab by remember { mutableStateOf<Int?>(null) }
    // Giá trị biến của dialog lúc mở, để khôi phục khi bấm Hủy.
    var dialogSnapshot by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Vị trí cuộn của từng trang (khóa = file): đứng ở MainScreen nên mở trang con rồi quay lại, hoặc đổi tab, vẫn giữ chỗ đang đọc.
    val scrollStates = rememberSaveable(saver = PageScrollStates.Saver) { PageScrollStates() }

    // Item `app_language` trong TOML dùng chung trạng thái với ngôn ngữ app: đổi bên nào, bên kia cập nhật theo.
    LaunchedEffect(language) {
        if (result.store[APP_LANGUAGE_ID] != language.key) result.store[APP_LANGUAGE_ID] = language.key
    }
    val pickedLanguage = result.store[APP_LANGUAGE_ID]
    LaunchedEffect(pickedLanguage) {
        val picked = AppLanguage.fromKey(pickedLanguage)
        if (picked != null && picked != language) onLanguageChange(picked)
    }

    val env = remember(result) {
        RenderEnv(
            store = result.store,
            feedback = feedback,
            scope = scope,
            openTab = { id ->
                val index = pages.indexOfFirst { it.ref.id == id }
                if (index >= 0) {
                    pendingTab = index
                    stack.forEach { scrollStates.forget(it) }
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
                if (action != null) env.run(action, action.script)
            } else {
                dialogSnapshot.forEach { (name, value) -> result.store[name] = value }
            }
        }
        stack.lastOrNull()?.let { scrollStates.forget(it) }
        stack = stack.dropLast(1)
    }

    // Chỉ đóng khi [file] vẫn là trang trên cùng: Back hoặc chạm nền đến muộn lúc sheet đang trượt đóng, hay nút của trang đang thoát, không được đóng nhầm trang bên dưới.
    val closeIfTop: (String, Boolean) -> Unit = { file, confirmed -> if (stack.lastOrNull() == file) closeTop(confirmed) }

    BackHandler(enabled = stack.isNotEmpty()) { closeTop(false) }

    // Cửa sổ tab trượt.
    val visibleTabs = min(MAX_VISIBLE_TABS, pages.size)
    var windowStart by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(pagerState.currentPage) {
        windowStart = nextWindowStart(pagerState.currentPage, windowStart, visibleTabs, pages.size)
    }

    // Thẻ cảnh báo chung chỉ cho lỗi toàn app (script khởi động, main.toml), ẩn được; hiện ở đầu danh sách mỗi tab (xem PageList).
    // Cảnh báo cấu hình của từng trang hiện riêng trong trang đó (xem PageWarningBanner).
    var warningDismissed by rememberSaveable { mutableStateOf(false) }
    val bootScriptWarning = stringResource(R.string.warn_boot_script)
    val mainConfigWarning = stringResource(R.string.warn_main_config_count, config.appWarnings.size)
    val viewLabel = stringResource(R.string.action_view)
    val warningText = buildList {
        result.scriptError?.let { add(bootScriptWarning) }
        if (config.appWarnings.isNotEmpty()) add(mainConfigWarning)
    }.joinToString(" ")

    // "Ẩn" của banner từng trang nhớ theo file (các file ngăn cách bằng xuống dòng).
    var dismissedPages by rememberSaveable { mutableStateOf("") }
    val pageWarningHost = remember(dismissedPages, onShowDetails) {
        PageWarningHost(
            dismissed = dismissedPages.split('\n'),
            onDismiss = { file -> dismissedPages += file + "\n" },
            onShow = { file, list -> onShowDetails(context.getString(R.string.warn_config_title), buildPageDetails(context, file, list)) },
        )
    }

    // Dialog luôn nằm trên cùng ngăn xếp và là lớp phủ riêng (xem FullDialogLayer / CompactDialog): trang bên dưới giữ nguyên, không chạy chuyển trang.
    // Chế độ (nhỏ gọn hay full màn hình) chốt một lần lúc mở theo số mục hiển thị, giữ nguyên tới khi đóng xong.
    val dialogFile = stack.lastOrNull()?.takeIf { config.subPages[it]?.dialog == true }
    val lastDialog = remember { LastDialog() }
    if (dialogFile != null) {
        if (!lastDialog.open || lastDialog.file != dialogFile) {
            lastDialog.compact = config.subPages[dialogFile]?.let { dialogItemCount(it.items, env) < DIALOG_FULL_MIN_ITEMS } == true
        }
        lastDialog.file = dialogFile
        lastDialog.open = true
    } else {
        lastDialog.open = false
    }
    val top = if (dialogFile != null) stack.getOrNull(stack.size - 2) else stack.lastOrNull()
    // pageBackdrop: hình của trang đang mở, làm nền mờ cho dialog sheet và banner.
    // fullBackdrop: hình của cả trang + dialog sheet + banner, làm nền mờ cho mọi OverlayDialog (không có blur thì nền tối như cũ).
    val pageBackdrop = rememberBarBackdrop(blurEnabled)
    val fullBackdrop = rememberBarBackdrop(blurEnabled)
    val blurRequests = remember { BlurRequests() }
    // Màn hình chọn toàn màn hình (singleselect / multiselect từ 6 mục) vẽ ở gốc để dùng chung nền mờ của trang.
    val selectHost = remember { SelectHost() }
    CompositionLocalProvider(
        LocalPageWarnings provides pageWarningHost,
        LocalPageScroll provides scrollStates,
        LocalBlurRequests provides (if (fullBackdrop != null) blurRequests else null),
        LocalGlassEnabled provides (glassEnabled && isBlurSupported()),
        LocalGlassBackdrop provides fullBackdrop,
        LocalSelectHost provides selectHost,
    ) {
        // Scaffold gốc: mọi OverlayDialog của Miuix vẽ ở lớp popup của Scaffold ngoài cùng, nằm trên cả thanh trên / thanh tab.
        Scaffold { _ ->
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().then(if (fullBackdrop != null) Modifier.layerBackdrop(fullBackdrop) else Modifier)) {
                        AnimatedContent(
                            targetState = top,
                            modifier = if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier,
                            transitionSpec = {
                                val from = initialState
                                val to = targetState
                                // zIndex theo độ sâu trong stack (tab = 0): AnimatedContent giữ zIndex đặt lúc trang là đích, nên giá trị cố định +1/-1 làm lần đóng thứ hai bị vẽ sai lớp.
                                val depth = if (to == null) 0f else (stack.lastIndexOf(to) + 1).toFloat()
                                when {
                                    // Trang con thường: mở thì trượt vào từ phải, trang bên dưới lùi nhẹ sang trái;
                                    // đóng thì trang trên trượt ra phải, trang bên dưới trượt về chỗ cũ.
                                    // Trang bị gỡ khỏi stack = đang đóng (kể cả khi bấm link về tab và đóng hết một lượt).
                                    to != null && (from == null || from in stack) ->
                                        (
                                            slideInHorizontally(tween(PAGE_MILLIS, easing = FastOutSlowInEasing)) { it } togetherWith
                                                slideOutHorizontally(tween(PAGE_MILLIS, easing = FastOutSlowInEasing)) { -it / PAGE_PARALLAX }
                                            ).also { it.targetContentZIndex = depth }
                                    from != null && from !in stack ->
                                        (
                                            slideInHorizontally(tween(PAGE_MILLIS, easing = FastOutSlowInEasing)) { -it / PAGE_PARALLAX } togetherWith
                                                slideOutHorizontally(tween(PAGE_MILLIS, easing = FastOutSlowInEasing)) { it }
                                            ).also { it.targetContentZIndex = depth }
                                    else -> (EnterTransition.None togetherWith ExitTransition.None).also { it.targetContentZIndex = depth }
                                }
                            },
                            label = "subPage",
                        ) { file ->
                            val subPage = file?.let { config.subPages[it] }
                            if (subPage != null) {
                                SubPageScreen(subPage, env, feedback, onBack = { closeIfTop(file.orEmpty(), false) }, onConfirm = { closeIfTop(file.orEmpty(), true) }, blur = blurEnabled)
                            } else {
                                val backdrop = rememberBarBackdrop(blurEnabled)
                                // Giao diện kính: thanh tab là viên thuốc kính nổi ở đáy (FloatingTabBar), nội dung cuộn tràn xuống dưới nó.
                                val glassBar = glassEnabled && isBlurSupported()
                                val floatingBar = glassBar && pages.size >= 2
                                var floatingBarHeight by remember { mutableStateOf(0.dp) }
                                val density = LocalDensity.current
                                // Thanh trên kính chỉ hiện chất liệu khi trang hiện tại đã cuộn khỏi đầu.
                                val currentFile = pages.getOrNull(pagerState.currentPage)?.ref?.file
                                // Nút chuyển sang viên kính tròn và dải mờ bật khi đã cuộn quá [GlassBarActivateScroll] (BarBlur.kt), không phải từ pixel đầu tiên.
                                val activatePx = with(density) { GlassBarActivateScroll.roundToPx() }
                                val pageScrolled by remember(currentFile, activatePx) {
                                    derivedStateOf {
                                        currentFile?.let { file ->
                                            val list = scrollStates.of(file)
                                            list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > activatePx
                                        } == true
                                    }
                                }
                                Scaffold(
                                    topBar = {
                                        // Màn hình chính: tên app cố định (không phóng to / thu nhỏ), icon 3 gạch bên trái mở cửa sổ menu.
                                        AppTopBar(
                                            title = config.app.name,
                                            leading = { TopBarIcon(icon = AppIcons.BarMenu, description = stringResource(R.string.menu_title), onClick = { showMenu = true }, pressAlpha = true) },
                                            backdrop = backdrop,
                                            scrolled = pageScrolled,
                                            actions = {
                                                // Nút nguồn (cạnh nút cài đặt): mở màn hình Nguồn toàn màn hình (vẽ ở gốc, bên dưới).
                                                PowerMenu(onOpen = { showRestartMenu = true })
                                                // Nút cài đặt: sát lề phải, mở màn hình Cài đặt toàn màn hình (giao diện, tùy chọn, liên kết).
                                                SettingsButton(onClick = { showSettings = true })
                                            },
                                        )
                                    },
                                    snackbarHost = { SnackbarHost(feedback.snackbarState) },
                                    bottomBar = {
                                        if (pages.size >= 2 && !floatingBar) {
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
                                    Box(Modifier.fillMaxSize()) {
                                        Column(
                                            Modifier
                                                .fillMaxSize()
                                                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                                                // Thanh kính (kể cả khi tắt blur) vẫn để nội dung cuộn tràn lên dưới thanh, như Miuix.
                                                .then(if (backdrop == null && !glassBar) Modifier.padding(top = barTop) else Modifier),
                                        ) {
                                            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
                                                val page = pages[pageIndex]
                                                PageList(
                                                    list = page.items.filter { isShown(it, env) },
                                                    error = page.error,
                                                    file = page.ref.file,
                                                    warnings = page.warnings,
                                                    env = env,
                                                    bottomInset = if (floatingBar) floatingBarHeight else inner.calculateBottomPadding(),
                                                    topInset = if (backdrop != null || glassBar) barTop else 0.dp,
                                                    appBanner = if (bannerShown) {
                                                        {
                                                            Banner(
                                                                message = warningText,
                                                                warning = true,
                                                                actionLabel = viewLabel,
                                                                onAction = { onShowDetails(context.getString(R.string.warn_startup_title), buildDetails(context, result)) },
                                                                onDismiss = { warningDismissed = true },
                                                            )
                                                        }
                                                    } else null,
                                                )
                                            }
                                            FeedbackDialogs(feedback, env)
                                            MenuDialog(show = showMenu, onDismiss = { showMenu = false })
                                        }
                                        if (floatingBar) {
                                            // Dải mờ dần từ trong suốt sang màu nền (giống dải sau nút cập nhật ở Cài đặt) phía sau đảo kính, để đảo nổi lên khỏi
                                            // thẻ cuộn bên dưới. Nằm NGOÀI lớp backdrop nên đảo không làm mờ chính dải này; không nhận chạm nên thẻ phía dưới vẫn cuộn được.
                                            Box(
                                                Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth()
                                                    .height(floatingBarHeight + FloatingBarBandExtra)
                                                    .background(Brush.verticalGradient(listOf(Color.Transparent, MiuixTheme.colorScheme.surface))),
                                            )
                                            FloatingGlassTabBar(
                                                items = pages.map { it.ref },
                                                visibleTabs = visibleTabs,
                                                windowStart = windowStart,
                                                selectedIndex = pagerState.currentPage,
                                                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                                                backdrop = backdrop,
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(horizontal = FloatingBarSideMargin)
                                                    .onSizeChanged { floatingBarHeight = with(density) { it.height.toDp() } },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // Dialog full màn hình: nền mờ + màn hình trượt từ dưới lên (giữ trang cuối trong lúc chạy hiệu ứng đóng).
                        AnimatedVisibility(visible = dialogFile != null && !lastDialog.compact, enter = EnterTransition.None, exit = ExitTransition.None) {
                            val sheetPage = lastDialog.file?.let { config.subPages[it] }
                            if (sheetPage != null) {
                                val sheetFile = lastDialog.file.orEmpty()
                                FullDialogLayer(sheetPage, env, feedback, onBack = { closeIfTop(sheetFile, false) }, onConfirm = { closeIfTop(sheetFile, true) }, backdrop = pageBackdrop)
                            }
                        }
                        // Các banner thông báo xếp chồng ở đầu màn hình, đè lên cả thanh trên, trượt xuống / lên.
                        BannerOverlay(feedback, Modifier.align(Alignment.TopCenter), backdrop = pageBackdrop)
                    }
                    if (fullBackdrop != null) DialogBlurScrim(blurRequests, fullBackdrop)
                }
                // Chọn toàn màn hình: cùng chỗ với log toàn màn hình (ngoài lớp bị menu làm mờ, trên trang và dialog sheet).
                SelectFullScreenHost(selectHost)
                // Dialog nhỏ gọn giữa màn hình: vẽ ở lớp popup của Scaffold gốc, nằm trên nền mờ.
                lastDialog.file?.let { config.subPages[it] }?.takeIf { lastDialog.compact }?.let { compactPage ->
                    val compactFile = lastDialog.file.orEmpty()
                    CompactDialog(
                        show = dialogFile != null,
                        page = compactPage,
                        env = env,
                        onBack = { closeIfTop(compactFile, false) },
                        onConfirm = { closeIfTop(compactFile, true) },
                    )
                }
                // Nguồn và Cài đặt toàn màn hình: ngoài lớp bị menu làm mờ, trên lớp scrim, dưới log toàn màn hình.
                PowerScreen(
                    show = showRestartMenu,
                    onDismiss = { showRestartMenu = false },
                    hasRoot = ExecutorManager.hasRoot,
                    onRestartApp = {
                        showRestartMenu = false
                        env.confirm = ConfirmRequest(context.getString(R.string.confirm_restart_app)) { Util.restartApp(context) }
                    },
                    onCloseApp = {
                        showRestartMenu = false
                        env.confirm = ConfirmRequest(context.getString(R.string.confirm_close_app)) { Util.exitApp(context) }
                    },
                    onDeviceAction = { action ->
                        showRestartMenu = false
                        env.confirm = ConfirmRequest(context.getString(action.confirmRes)) {
                            scope.launch {
                                val result = ExecutorManager.current.exec(action.command, 10)
                                if (!result.ok) feedback.toast(powerErrorText(context, action, result))
                            }
                        }
                    },
                )
                SettingsDialog(
                    show = showSettings,
                    onDismiss = { showSettings = false },
                    current = themeMode,
                    onSelect = onThemeChange,
                    haptic = hapticEnabled,
                    onHapticChange = onHapticChange,
                    blur = blurEnabled,
                    onBlurChange = onBlurChange,
                    glass = glassEnabled,
                    onGlassChange = onGlassChange,
                    logs = feedback.history,
                    onOpenLog = { feedback.openLog(it) },
                    onClearLogs = { feedback.clearFinishedLogs() },
                )
                // Log toàn màn hình nằm ngoài lớp bị menu làm mờ, để menu ba chấm của chính log không làm mờ log.
                LogFullScreen(
                    entry = feedback.log,
                    onDismiss = { feedback.log = null },
                    onCancel = { feedback.log?.cancel() },
                    onHide = { feedback.log?.let { feedback.hideLog(it) } },
                )
            }
        }
    }
}

/** Cỡ chữ tiêu đề thanh trên (tên app, tên trang con). */
private val TopBarTitleSize = 22.sp

/**
 * Thanh trên: tiêu đề sát trái (16dp, khớp lề thẻ), cỡ chữ cố định, không phóng to / thu nhỏ khi cuộn.
 * Có [onBack] thì thêm nút ở đầu thanh: mũi tên quay lại (trang con) hoặc dấu X khi [closeStyle] (dialog); [leading] là nút tùy ý ở đầu thanh (icon menu của màn hình chính).
 * [backdrop] khác null: nền thanh trong suốt và làm mờ phần nội dung cuộn phía dưới (xem BarBlur.kt).
 */
@Composable
private fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    closeStyle: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    backdrop: LayerBackdrop? = null,
    scrolled: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (LocalGlassEnabled.current) {
        // Giao diện kính: GlassTopAppBar của Miuix; nút bên trong là viên kính (TopBarIcon tự đổi theo LocalInGlassBar).
        GlassTopAppBar(
            title = title,
            isContentScrolled = scrolled,
            expandable = false,
            // Miuix mặc định căn giữa tiêu đề; đặt mốc vật cản rất xa để tiêu đề căn sát trái, ngay sau nút bên trái như thanh cũ.
            centerObstructionStart = 10_000.dp,
            backdrop = backdrop,
            progressiveBlur = backdrop != null,
            bandOverhang = GlassBarBandOverhang,
            navigationIcon = {
                CompositionLocalProvider(LocalInGlassBar provides true) {
                    if (onBack != null) {
                        TopBarIcon(
                            icon = if (closeStyle) AppIcons.BarClose else AppIcons.BarBack,
                            description = stringResource(if (closeStyle) R.string.action_close else R.string.action_back),
                            onClick = onBack,
                        )
                    }
                    leading?.invoke()
                }
            },
            actions = {
                // Các nút kính bên phải cách nhau 8dp (mặc định Miuix xếp sát nhau).
                CompositionLocalProvider(LocalInGlassBar provides true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
                }
            },
        )
        return
    }
    val hasLeading = onBack != null || leading != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .barBlur(backdrop)
            .background(if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface)
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = if (hasLeading) 4.dp else 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TopBarIcon(
                icon = if (closeStyle) AppIcons.BarClose else AppIcons.BarBack,
                description = stringResource(if (closeStyle) R.string.action_close else R.string.action_back),
                onClick = onBack,
            )
        }
        leading?.invoke()
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (hasLeading) 4.dp else 0.dp),
            fontSize = TopBarTitleSize,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/**
 * Thanh trên của trang con: tiêu đề chữ lớn thu nhỏ khi vuốt lên và mở rộng lại khi vuốt xuống tới đầu danh sách
 * (thường: TopAppBar của Miuix; kính: GlassTopAppBar).
 * Trạng thái nằm trong [scrollBehavior]; gắn `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` vào vùng nội dung cuộn.
 * [backdrop] khác null: nền thanh trong suốt và làm mờ phần nội dung cuộn phía dưới (xem BarBlur.kt).
 */
@Composable
private fun LargeTitleTopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    onBack: (() -> Unit)? = null,
    backdrop: LayerBackdrop? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (LocalGlassEnabled.current) {
        // Giao diện kính: GlassTopAppBar có tiêu đề lớn thu gọn theo scrollBehavior, y hệt trang mẫu của Miuix.
        GlassTopAppBar(
            title = title,
            expandable = true,
            largeTitle = title,
            scrollBehavior = scrollBehavior,
            backdrop = backdrop,
            progressiveBlur = backdrop != null,
            bandOverhang = GlassBarBandOverhang,
            navigationIcon = {
                CompositionLocalProvider(LocalInGlassBar provides true) {
                    if (onBack != null) TopBarIcon(icon = AppIcons.BarBack, description = stringResource(R.string.action_back), onClick = onBack)
                }
            },
            actions = {
                // Các nút kính bên phải cách nhau 8dp (mặc định Miuix xếp sát nhau).
                CompositionLocalProvider(LocalInGlassBar provides true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
                }
            },
        )
        return
    }
    // Giao diện thường: TopAppBar của Miuix (tiêu đề lớn thu nhỏ theo scrollBehavior). Có blur thì nền trong suốt và làm mờ nội dung cuộn phía dưới.
    TopAppBar(
        title = title,
        modifier = Modifier.barBlur(backdrop),
        color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (onBack != null) TopBarIcon(icon = AppIcons.BarBack, description = stringResource(R.string.action_back), onClick = onBack)
        },
        actions = actions,
    )
}

/** Độ đục của nền sheet dialog khi full màn hình và có blur (cùng mức với log và chọn toàn màn hình): phần còn lại là trang phía sau đã làm mờ. */
private const val SheetGlassAlpha = 0.6f

/**
 * Thanh trên của dialog full màn hình: tiêu đề ở giữa, X (Hủy) trong nền tròn bên trái; Xác nhận nằm ở thanh nút phía dưới.
 */
@Composable
private fun SheetTopBar(title: String, onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(DIALOG_TOP_BAR_HEIGHT)
            .padding(horizontal = 10.dp),
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
        Box(Modifier.align(Alignment.CenterStart)) { TopBarIcon(icon = AppIcons.BarClose, description = stringResource(R.string.action_cancel), onClick = onCancel, circle = true) }
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
    // Sheet dialog full màn hình + có blur: nền (Scaffold, thanh trên, thanh nút) bán trong suốt, trang phía sau mờ lộ ra; đổi mức thì chuyển dần.
    val glass = page.dialog && sheetFull && LocalBlurRequests.current != null
    val surfaceAlpha by animateFloatAsState(if (glass) SheetGlassAlpha else 1f, tween(200), label = "sheetGlass")
    // Dialog chỉ truyền biến: item không chạy lệnh, nút Xác nhận mới chạy lệnh của link.
    val pageEnv = remember(env, page.dialog) { if (page.dialog) env.asVarsOnly() else env }
    val backdrop = rememberBarBackdrop(blur && !page.dialog)
    // Thanh trên kính (kể cả khi tắt blur) để nội dung cuộn tràn lên dưới thanh.
    val glassBar = LocalGlassEnabled.current && !page.dialog
    // Trang con thường: tiêu đề lớn thu nhỏ khi cuộn (mỗi trang một trạng thái riêng).
    val titleScroll = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            Box(topBarModifier) {
                if (page.dialog) {
                    SheetTopBar(page.title, onCancel = onBack)
                } else {
                    LargeTitleTopBar(title = page.title, scrollBehavior = titleScroll, onBack = onBack, backdrop = backdrop)
                }
            }
        },
        snackbarHost = { SnackbarHost(feedback.snackbarState) },
        bottomBar = { if (page.dialog && sheetFull) DialogButtons(onCancel = onBack, onConfirm = onConfirm) },
        containerColor = MiuixTheme.colorScheme.surface.copy(alpha = surfaceAlpha),
    ) { inner ->
        // Dialog full màn hình: danh sách kết thúc ngay trên thanh nút (không cuộn xuống sau nút), mép mờ nằm sát nút.
        val sheetBottom = if (page.dialog && sheetFull) inner.calculateBottomPadding() else 0.dp
        Column(
            Modifier
                .fillMaxSize()
                .then(if (page.dialog) Modifier else Modifier.nestedScroll(titleScroll.nestedScrollConnection))
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .then(
                    if (backdrop != null) Modifier
                    else if (glassBar) Modifier.padding(bottom = sheetBottom)
                    else Modifier.padding(top = inner.calculateTopPadding(), bottom = sheetBottom),
                )
                .then(contentModifier),
        ) {
            Box(Modifier.weight(1f)) {
                PageList(
                    list = page.items.filter { isShown(it, pageEnv) },
                    error = page.error,
                    file = page.file,
                    warnings = page.warnings,
                    env = pageEnv,
                    bottomInset = if (page.dialog && sheetFull) 0.dp else inner.calculateBottomPadding(),
                    topInset = if (backdrop != null || glassBar) inner.calculateTopPadding() else 0.dp,
                    fadeEdges = page.dialog,
                    // Dialog full màn hình: thanh nút đáy đã nâng theo bàn phím và Scaffold dồn danh sách lên theo, nên danh sách không thêm imePadding nữa (tránh cộng đôi).
                    imePadding = !(page.dialog && sheetFull),
                )
            }
            FeedbackDialogs(feedback, env)
        }
    }
}

/** Nhớ file dialog cuối cùng đã mở, để còn nội dung hiển thị trong lúc chạy hiệu ứng đóng (lúc đó stack đã bỏ dialog). */
private class LastDialog {
    var file: String? = null
    var open = false
    var compact = false
}

/**
 * Dialog full màn hình: nền mờ trang phía sau khi bật blur và màn hình trượt từ dưới lên / xuống.
 * Thanh trên có X (Hủy), hai nút Hủy / Xác nhận cố định ở đáy; không kéo, không dấu tích.
 */
@Composable
private fun AnimatedVisibilityScope.FullDialogLayer(
    page: SubPage,
    env: RenderEnv,
    feedback: FeedbackHost,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    backdrop: LayerBackdrop? = null,
) {
    Box(Modifier.fillMaxSize()) {
        if (backdrop != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .animateEnterExit(enter = fadeIn(tween(DIALOG_MILLIS)), exit = fadeOut(tween(DIALOG_MILLIS)))
                    .scrimBlur(backdrop),
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
            SubPageScreen(page = page, env = env, feedback = feedback, onBack = onBack, onConfirm = onConfirm, sheetFull = true)
        }
    }
}

/** Số mục hiển thị của dialog: mục trong group tính riêng từng mục con (group không tính). */
private fun dialogItemCount(items: List<Item>, env: RenderEnv): Int =
    items.filter { isShown(it, env) }.sumOf { if (it.type == ItemType.Group) dialogItemCount(it.children, env) else 1 }

/**
 * Dialog nhỏ gọn giữa màn hình (dùng khi có ít hơn [DIALOG_FULL_MIN_ITEMS] mục): tiêu đề, các mục (cuộn nếu cao), rồi Hủy / Xác nhận.
 * Vẽ ở lớp popup của Scaffold gốc nên nằm trên nền mờ, không bị mờ theo. Chạm ra ngoài hoặc Back là Hủy.
 */
@Composable
private fun CompactDialog(show: Boolean, page: SubPage, env: RenderEnv, onBack: () -> Unit, onConfirm: () -> Unit) {
    val pageEnv = remember(env) { env.asVarsOnly() }
    val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    BlurDialog(show = show, onDismissRequest = onBack, title = page.title, centered = true, fullScreen = true, glassStyle = true) {
        // Màn hình chọn của singleselect / multiselect: không dùng host ở gốc (bị popup này che), mở bằng cửa sổ riêng nằm trên dialog.
        CompositionLocalProvider(LocalSelectHost provides null) {
        val contentScroll = rememberScrollState()
        Column(Modifier.fillMaxWidth()) {
            Column(
                // weight(fill = false): Column đo Spacer và hàng nút trước, vùng cuộn chỉ nhận phần chiều cao còn lại.
                // Không có weight thì vùng cuộn ăn hết chiều cao khung dialog và hàng nút bị ép về 0.
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = maxContentHeight).fadingEdges(contentScroll).verticalScroll(contentScroll),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (shouldShowPageWarnings(page.file, page.warnings)) PageWarningBanner(page.file, page.warnings)
                page.error?.let { PageErrorCard(it) }
                page.items.filter { isShown(it, pageEnv) }.forEach { ItemRenderer(it, pageEnv) }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton(text = stringResource(R.string.action_cancel), onClick = onBack, modifier = Modifier.weight(1f))
                DialogButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
            }
        }
        }
    }
}

/** Thanh nút cuối dialog: Hủy (khôi phục biến) và Xác nhận (chạy lệnh). Không tự vẽ nền: dùng nền của Scaffold để blur phía sau lộ ra đồng đều. */
@Composable
private fun DialogButtons(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // imePadding đặt trước navigationBarsPadding: khi bàn phím hiện, imePadding đã tính cả phần thanh điều hướng nên không bị cộng đôi.
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DialogButton(text = stringResource(R.string.action_cancel), onClick = onCancel, modifier = Modifier.weight(1f))
        DialogButton(
            text = stringResource(R.string.action_confirm),
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            primary = true,
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
    LogDialog(
        entry = feedback.log,
        onDismiss = { feedback.log = null },
        onCancel = { feedback.log?.cancel() },
        onHide = { feedback.log?.let { feedback.hideLog(it) } },
    )
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
    appBanner: (@Composable () -> Unit)? = null,
    fadeEdges: Boolean = false,
    imePadding: Boolean = true,
) {
    val showWarnings = shouldShowPageWarnings(file, warnings)
    // Lấy trạng thái cuộn theo file từ MainScreen (lấy một lần cho lần dựng này: trang đang trượt ra không bị nhảy về đầu khi vừa bị quên).
    val scrolls = LocalPageScroll.current
    val listState = if (scrolls != null) remember(file, scrolls) { scrolls.of(file) } else rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().then(if (imePadding) Modifier.imePadding() else Modifier).then(if (fadeEdges) Modifier.fadingEdges(listState) else Modifier),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp + topInset, bottom = 16.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Thẻ cảnh báo chung của app (script khởi động, main.toml) nằm đầu danh sách như một mục: cuộn cùng nội dung, không dính thanh trên.
        if (appBanner != null) {
            item { appBanner() }
        }
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

/**
 * Vị trí cuộn của từng trang theo file. Danh sách của trang bị gỡ khỏi composition khi mở trang con (hoặc khi pager chuyển tab)
 * nên không tự giữ được vị trí; trạng thái đặt ở đây để dựng lại là cuộn về đúng chỗ. Lưu qua xoay màn hình.
 */
private class PageScrollStates {
    private val states = mutableMapOf<String, LazyListState>()

    fun of(file: String): LazyListState = states.getOrPut(file) { LazyListState() }

    /** Trang con đã đóng: lần mở sau bắt đầu từ đầu. */
    fun forget(file: String) {
        states.remove(file)
    }

    companion object {
        private const val SEP = '\u0000'

        val Saver = listSaver<PageScrollStates, String>(
            save = { holder ->
                holder.states.map { (file, st) -> "$file$SEP${st.firstVisibleItemIndex}$SEP${st.firstVisibleItemScrollOffset}" }
            },
            restore = { saved ->
                PageScrollStates().also { holder ->
                    saved.forEach { entry ->
                        val parts = entry.split(SEP)
                        val index = parts.getOrNull(1)?.toIntOrNull()
                        val offset = parts.getOrNull(2)?.toIntOrNull()
                        if (parts.size == 3 && index != null && offset != null) holder.states[parts[0]] = LazyListState(index, offset)
                    }
                }
            },
        )
    }
}

private val LocalPageScroll = staticCompositionLocalOf<PageScrollStates?> { null }

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
        message = stringResource(R.string.warn_page_count, warnings.size),
        warning = true,
        actionLabel = stringResource(R.string.action_view),
        onAction = { host.onShow(file, warnings) },
        onDismiss = { host.onDismiss(file) },
    )
}

private fun buildPageDetails(context: Context, file: String, warnings: List<String>): String = buildString {
    appendLine(context.getString(R.string.details_page_warnings, file))
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

private fun buildDetails(context: Context, result: BootResult): String = buildString {
    result.scriptError?.let {
        appendLine(context.getString(R.string.details_boot_script))
        appendLine(it)
        appendLine()
    }
    if (result.config.appWarnings.isNotEmpty()) {
        appendLine(context.getString(R.string.details_main_config))
        result.config.appWarnings.forEach { appendLine("- $it") }
        appendLine()
    }
    appendLine(context.getString(R.string.details_boot_log))
    result.bootLog.forEach { appendLine(it) }
}

private fun powerErrorText(context: Context, action: PowerAction, result: ExecResult): String {
    val label = context.getString(action.labelRes)
    if (result.timedOut) return context.getString(R.string.power_error_timeout, label)
    val firstLine = result.output.lineSequence().firstOrNull { it.isNotBlank() }
    return context.getString(R.string.power_error_failed, label) + (firstLine?.let { ": $it" } ?: "")
}

private const val DIALOG_MILLIS = 280

/** Dialog có từ ngần này mục hiển thị trở lên thì mở full màn hình; ít hơn thì hiện dialog nhỏ gọn giữa màn hình. */
private const val DIALOG_FULL_MIN_ITEMS = 5

/** Chiều cao thanh trên của dialog full màn hình (khớp SheetTopBar). */
private val DIALOG_TOP_BAR_HEIGHT = 56.dp

/** Thời gian trượt khi mở / đóng trang con (ms) và độ lùi của trang bên dưới (1/N chiều rộng). */
private const val PAGE_MILLIS = 320
private const val PAGE_PARALLAX = 4
