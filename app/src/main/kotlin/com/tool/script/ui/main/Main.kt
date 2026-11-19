package com.tool.script.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tool.script.boot.BootResult
import com.tool.script.config.PageRef
import com.tool.script.executor.ExecResult
import com.tool.script.executor.ExecutorManager
import com.tool.script.ui.feedback.Banner
import com.tool.script.ui.feedback.ConfirmDialog
import com.tool.script.ui.feedback.LogDialog
import com.tool.script.ui.feedback.rememberFeedbackHost
import com.tool.script.ui.render.ConfirmRequest
import com.tool.script.ui.render.ItemRenderer
import com.tool.script.ui.render.PageErrorCard
import com.tool.script.ui.render.RenderEnv
import com.tool.script.ui.render.isShown
import com.tool.script.ui.theme.ThemeMode
import com.tool.script.util.Util
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.min

// Màn hình chính: tiêu đề = tên app, HorizontalPager + thanh tab dưới.
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
    onShowDetails: (title: String, text: String) -> Unit,
) {
    val config = result.config
    val pages = config.pages
    val scope = rememberCoroutineScope()
    val feedback = rememberFeedbackHost()
    val pagerState = rememberPagerState { pages.size }
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    var showRestartMenu by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }

    val env = remember(result) {
        RenderEnv(
            store = result.store,
            feedback = feedback,
            scope = scope,
            navigateToPage = { id ->
                val index = pages.indexOfFirst { it.ref.id == id }
                if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
            },
        )
    }

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

    // Banner ngắn của shell = "banner" tự ẩn sau vài giây.
    val bannerMessage = feedback.banner
    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(BANNER_MILLIS)
            if (feedback.banner == bannerMessage) feedback.banner = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = config.app.name,
                scrollBehavior = scrollBehavior,
                actions = {
                    // Nút thông tin: menu tùy chỉnh chủ đề, thả xuống ngay dưới icon.
                    ThemeMenu(
                        expanded = showThemeMenu,
                        current = themeMode,
                        onOpen = { showThemeMenu = true },
                        onDismiss = { showThemeMenu = false },
                        onSelect = {
                            showThemeMenu = false
                            onThemeChange(it)
                        },
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
                    for (index in windowStart until windowStart + visibleTabs) {
                        val ref = pages[index].ref
                        TabItem(
                            ref = ref,
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        )
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
            bannerMessage?.let {
                Banner(
                    message = it,
                    warning = false,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    onDismiss = { feedback.banner = null },
                )
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
                val page = pages[pageIndex]
                PageList(
                    list = page.items.filter { isShown(it, env.store) },
                    error = page.error,
                    env = env,
                    scrollBehavior = scrollBehavior,
                    bottomInset = inner.calculateBottomPadding(),
                )
            }
            // OverlayDialog của Miuix cần nằm trong Scaffold (nội dung), đặt ngoài thì không hiện.
            LogDialog(entry = feedback.log, onDismiss = { feedback.log = null })
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
    }
}

@Composable
private fun PageList(
    list: List<com.tool.script.config.Item>,
    error: String?,
    env: RenderEnv,
    scrollBehavior: ScrollBehavior,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomInset),
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

private const val BANNER_MILLIS = 4_000L
