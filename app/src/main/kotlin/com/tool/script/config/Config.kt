package com.tool.script.config

import com.tool.script.state.ConditionEvaluator
import com.tool.script.util.APP_LANGUAGE_ID
import com.tool.script.util.AppLanguage
import com.tool.script.util.AppText
import com.tool.script.R
import android.content.Context
import com.tool.script.executor.ExecutorManager
import com.tool.script.util.ShellText
import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlArray
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.TomlTable
import java.io.File
import java.io.IOException

// Model + parser TOML theo docs/schema.md.

enum class ItemType(val key: String) {
    Text("text"), Group("group"), Button("button"), Switch("switch"),
    Input("input"), Slider("slider"), Link("link"),
    Checkbox("checkbox"), Radio("radio"), Progress("progress"), Dropdown("dropdown"),
    Select("select"), AppList("applist");

    companion object {
        fun from(key: String?): ItemType? = entries.firstOrNull { it.key == key }
    }
}

enum class ShellMode { Live, Full, Hidden, Banner, Notice, Snackbar }

/** [app] = true: mục là một ứng dụng đã cài (type `applist`), [value] là package name; màn hình chọn vẽ thêm icon và package.
 * [placeholder] = true: dòng giữ chỗ khi tên app chưa đọc xong, màn hình chọn vẽ khung xương và không cho bấm. */
data class SelectOption(val label: String, val value: String, val app: Boolean = false, val placeholder: Boolean = false)

data class Item(
    val type: ItemType,
    val id: String?,
    val title: String,
    val desc: String?,
    // icon bên trái (mọi type trừ text): đường dẫn trong assets/configs hoặc file ngoài bắt đầu bằng /
    val icon: String?,
    // icon_size (dp, mặc định 24) và icon_tint (true = tô màu theme, false = giữ màu ảnh gốc)
    val iconSize: Float,
    val iconTint: Boolean,
    // visible / readonly / lock (docs/schema.md mục 9); bản -sh chạy lệnh shell, in 0 = sai, 1 hoặc 1|lý do = đúng
    val visible: String?,
    val visibleSh: String?,
    val readonly: String?,
    val readonlySh: String?,
    val lock: String?,
    val lockMsg: String?,
    val lockSh: String?,
    // title-sh / desc-sh: lệnh shell in tiêu đề (dòng đầu) / mô tả (cả đầu ra); title / desc là giá trị tạm và dự phòng
    val titleSh: String?,
    val descSh: String?,
    // refresh: sau khi lệnh của item chạy xong thành công, làm mới cả trang (true) hoặc các id này trong cùng trang.
    // Đọc từ TOML vào refreshAll / refreshIds, rồi resolveRefresh gộp thành refreshIds (id trong trang) + refreshSh (lệnh -sh của các item đó).
    val refreshAll: Boolean,
    val refreshIds: List<String>,
    val refreshSh: List<String>,
    val shell: ShellMode,
    val confirm: String?,
    // null = không giới hạn thời gian chạy (không khai báo timeout); có giá trị thì quá số giây đó lệnh bị dừng
    val timeoutSec: Int?,
    // button / input / slider
    val script: String?,
    // switch / checkbox
    val get: String?,
    // applist: lệnh shell in danh sách app (mỗi dòng một package); chạy lại mỗi lần mở danh sách
    val optionsSh: String?,
    // input
    val hint: String?,
    val default: String?,
    val numberInput: Boolean,
    // radio / dropdown / select
    val options: List<SelectOption>,
    // select: số cột lựa chọn trong hộp thoại (1 hoặc 2)
    val columns: Int,
    // select (multi = true) và applist: dấu phân tách các giá trị đã chọn trong $id
    val separator: String,
    // slider / progress
    val min: Float,
    val max: Float,
    val step: Float,
    // link: tab = id của một [[tab]], page = đường dẫn file trang con
    val tab: String?,
    val page: String?,
    // link page: true = mở dạng dialog toàn màn hình (style = "dialog")
    val dialog: Boolean,
    // group
    val children: List<Item>,
    // item con của group: true = vẽ đường kẻ ngăn cách ngay dưới item này (không vẽ ở item cuối của group)
    val divider: Boolean = false,
    // applist: true (mặc định) = chọn nhiều app, false = chọn một app. Danh sách app do lệnh `get` in ra.
    val multi: Boolean = true,
)

/** [env]: đoạn shell chạy đầu tiên trước mọi lệnh (`[app] env`), null nếu không khai báo. */
data class AppInfo(val name: String, val env: String? = null)

data class PageRef(val id: String, val title: String, val icon: String, val file: String)

/**
 * Một trang đã nạp. [error] khác null nghĩa là file trang bị lỗi parse (trang hiện thông báo lỗi).
 * [warnings] là cảnh báo cấu hình của riêng file này (không gồm lỗi parse, vì [error] đã hiện thành thẻ lỗi).
 */
data class LoadedPage(
    val ref: PageRef,
    val items: List<Item>,
    val error: String? = null,
    val warnings: List<String> = emptyList(),
)

/** Trang con mở từ item `link` có `page`. [file] là khóa; [title] lấy từ item link đầu tiên trỏ tới nó. */
data class SubPage(
    val file: String,
    val title: String,
    val items: List<Item>,
    val error: String? = null,
    val dialog: Boolean = false,
    /** Item link mở trang này; với dialog, `script` / `shell` / `timeout` / `confirm` của nó chạy khi bấm Xác nhận. */
    val action: Item? = null,
    /** Cảnh báo cấu hình của riêng file này (không gồm lỗi parse). */
    val warnings: List<String> = emptyList(),
)

data class LoadedConfig(
    val app: AppInfo,
    val pages: List<LoadedPage>,
    val subPages: Map<String, SubPage>,
    /** Cảnh báo của `main.toml` (không thuộc trang nào). Cảnh báo của từng trang nằm trong [LoadedPage.warnings] / [SubPage.warnings]. */
    val appWarnings: List<String>,
)

/** Lỗi parse TOML: có tên file, số dòng (nếu parser cung cấp) và mô tả. */
class ConfigParseException(val file: String, val line: Int?, val detail: String) :
    Exception(buildString {
        append(file)
        if (line != null) append(", ").append(AppText.get(R.string.cfg_line, line))
        append(": ").append(detail)
    })

object TomlParser {

    private val LINE_REGEX = Regex("""\(L(\d+)\)""")

    fun parseTable(file: String, text: String): TomlTable = try {
        Toml.parseToTomlTable(text)
    } catch (e: Exception) {
        val msg = e.message ?: e.toString()
        throw ConfigParseException(file, LINE_REGEX.find(msg)?.groupValues?.get(1)?.toIntOrNull(), msg)
    }

    /** Bảng chuỗi `@khóa` đang áp dụng; [ConfigRepository.load] đặt trước khi parse và trả về rỗng sau khi parse xong. */
    @Volatile
    internal var strings: LangTable = LangTable.EMPTY

    /**
     * Khóa TOML mà giá trị không bao giờ bị thay `@khóa`: định danh, kiểu, đường dẫn, biểu thức, số, chế độ...
     * Mọi khóa còn lại (title, desc, hint, confirm, nhãn trong options, lệnh script/get, name, env...) đều được thay.
     */
    private val RAW_KEYS = setOf(
        "id", "type", "icon", "icon_size", "icon_tint", "divider", "shell", "columns", "input_type", "style",
        "tab", "page", "file", "visible", "readonly", "lock", "refresh", "timeout", "min", "max", "step", "default", "separator",
        "lang", "lang-sh", "load-string", "load-string-sh", "multi",
    )

    private val APP_KEYS = setOf("name", "env", "lang", "lang-sh", "load-string", "load-string-sh")
    private val MAIN_KEYS = setOf("app", "tab", "page")
    private val TAB_KEYS = setOf("id", "title", "icon", "file")
    private val PAGE_KEYS = setOf("item", "group")
    private val ITEM_KEYS = setOf(
        "type", "id", "title", "desc", "icon", "icon_size", "icon_tint", "visible", "visible-sh", "readonly", "readonly-sh", "lock", "lock-sh", "refresh", "title-sh", "desc-sh", "confirm", "timeout",
        "script", "get", "hint", "default", "input_type", "min", "max", "step", "options", "options-sh", "separator",
        "columns", "tab", "page", "style", "shell", "divider", "multi",
    )

    /** Khóa không có trong code (thường là gõ sai) đều báo cáo. Bảng vẫn được đọc, chỉ riêng khóa lạ bị bỏ qua. */
    private fun warnUnknownKeys(table: TomlTable, known: Set<String>, label: String, warnings: MutableList<String>) {
        table.keys.filter { it !in known }.forEach { warnings += AppText.get(R.string.cfg_unknown_key, label, it) }
    }

    private fun TomlTable.str(key: String): String? =
        (this[key] as? TomlLiteral)?.content?.let { if (key in RAW_KEYS) it else strings.resolve(it) }

    /** Option `"Nhãn|giá trị"`: chỉ thay phần nhãn, giá trị giữ nguyên (nó được lưu và truyền cho lệnh). Không có `|` thì nhãn = giá trị. */
    private fun resolveOption(raw: String): String {
        val idx = raw.indexOf('|')
        val label = if (idx >= 0) raw.substring(0, idx) else raw
        // Bản dịch chứa `|` sẽ làm lệch cách tách nhãn / giá trị, nên giữ nguyên nhãn gốc trong trường hợp đó.
        val shown = strings.resolve(label).takeIf { '|' !in it } ?: label
        return if (idx >= 0) shown + raw.substring(idx) else "$shown|$raw"
    }

    private fun TomlTable.float(key: String): Float? = str(key)?.toFloatOrNull()?.takeIf { it.isFinite() }

    /** "Hiệu năng|performance" -> SelectOption; chỉ tách ở dấu | đầu tiên. Giá trị rỗng thì không hợp lệ. */
    internal fun parseOption(raw: String): SelectOption? {
        val idx = raw.indexOf('|')
        val label = (if (idx >= 0) raw.substring(0, idx) else raw).trim()
        val value = (if (idx >= 0) raw.substring(idx + 1) else raw).trim()
        if (value.isEmpty()) return null
        return SelectOption(label.ifEmpty { value }, value)
    }

    fun parseMain(file: String, text: String, warnings: MutableList<String>): Pair<AppInfo, List<PageRef>> {
        val root = parseTable(file, text)
        val app = root["app"] as? TomlTable
        warnUnknownKeys(root, MAIN_KEYS, file, warnings)
        if (app != null) warnUnknownKeys(app, APP_KEYS, "$file: [app]", warnings)
        val info = AppInfo(
            name = app?.str("name") ?: "Tool Script",
            env = app?.str("env")?.takeIf { it.isNotBlank() },
        )
        val pages = mutableListOf<PageRef>()
        val seen = HashSet<String>()
        if (root["page"] != null) warnings += AppText.get(R.string.cfg_page_renamed, file)
        val array = root["tab"] as? TomlArray
        array?.forEachIndexed { index, element ->
            val t = element as? TomlTable
            val label = "$file: [[tab]] #${index + 1}"
            if (t != null) warnUnknownKeys(t, TAB_KEYS, label, warnings)
            val id = t?.str("id")
            val title = t?.str("title")
            val icon = t?.str("icon")
            val pageFile = t?.str("file")
            when {
                t == null || id.isNullOrBlank() -> warnings += AppText.get(R.string.cfg_tab_missing_id, label)
                title.isNullOrBlank() -> warnings += AppText.get(R.string.cfg_tab_missing_title, label, id)
                icon.isNullOrBlank() -> warnings += AppText.get(R.string.cfg_tab_missing_icon, label, id)
                pageFile.isNullOrBlank() -> warnings += AppText.get(R.string.cfg_tab_missing_file, label, id)
                !seen.add(id) -> warnings += AppText.get(R.string.cfg_tab_duplicate_id, label, id)
                else -> pages += PageRef(id!!, title!!, icon!!, pageFile!!)
            }
        }
        return info to pages
    }

    /** Tên biến môi trường của app (env.sh, biến dựng sẵn) và shell: item đặt id trùng sẽ đè môi trường của mọi lệnh. */
    private val RESERVED_IDS = setOf(
        "PACKAGE", "APP_DIR", "FILES_DIR", "CACHE_DIR", "SCRIPTS_DIR", "STATE_DIR", "HOME", "BIN_DIR", "LIB_DIR",
        "ABI", "ARCH", "EXT_ROOT", "EXT_DIR", "SDK", "ROOT_MODE", "LANGUAGE", "LANGAPP", "COUNTRY",
        "PATH", "LD_LIBRARY_PATH", "IFS", "PWD", "shell_progres",
    )

    /** Dòng mở bảng `[[item]]` / `[[group]]` ở cấp trang (không khớp `[[group.item]]`). */
    private val BLOCK_HEADER = Regex("""^[ \t]*\[\[[ \t]*(item|group)[ \t]*\]\]""", RegexOption.MULTILINE)

    fun parsePage(file: String, text: String, warnings: MutableList<String>): List<Item> {
        val root = parseTable(file, text)
        warnUnknownKeys(root, PAGE_KEYS, file, warnings)
        val ids = HashSet<String>()
        val items = (root["item"] as? TomlArray)?.toList().orEmpty()
        val groups = (root["group"] as? TomlArray)?.toList().orEmpty()

        // TOML không giữ thứ tự giữa hai mảng khác khóa (item / group), nên đọc thứ tự từ các dòng tiêu đề.
        // Đếm không khớp (vd. chuỗi nhiều dòng có chứa "[[item]]") thì xếp item trước, group sau.
        val headers = BLOCK_HEADER.findAll(text).map { it.groupValues[1] }.toList()
        val order = if (headers.count { it == "item" } == items.size && headers.count { it == "group" } == groups.size) {
            headers
        } else {
            List(items.size) { "item" } + List(groups.size) { "group" }
        }

        val out = mutableListOf<Item>()
        var itemCount = 0
        var groupCount = 0
        for (kind in order) {
            val isGroup = kind == "group"
            val number = if (isGroup) ++groupCount else ++itemCount
            val element = if (isGroup) groups[number - 1] else items[number - 1]
            parseEntry(element, isGroup, "$file: $kind #$number", warnings, ids)?.let { out += it }
        }
        return resolveRefresh(file, out, warnings)
    }

    /**
     * Khóa `refresh` chỉ có tác dụng trong cùng trang: `true` thành mọi item của trang, `"id1,id2"` chỉ giữ các id có trong trang
     * (id lạ báo cảnh báo và bỏ qua). Kết quả là `refreshIds` (id để đọc lại `get`) và `refreshSh` (lệnh `-sh` của các item đó).
     */
    private fun resolveRefresh(file: String, items: List<Item>, warnings: MutableList<String>): List<Item> {
        fun flatten(list: List<Item>): List<Item> = list.flatMap { listOf(it) + flatten(it.children) }
        val all = flatten(items)
        val byId = all.mapNotNull { item -> item.id?.let { it to item } }.toMap()
        fun resolve(item: Item): Item {
            val children = item.children.map(::resolve)
            val targets = when {
                item.refreshAll -> all
                item.refreshIds.isNotEmpty() -> item.refreshIds.mapNotNull { id ->
                    byId[id] ?: run {
                        warnings += AppText.get(R.string.cfg_refresh_unknown_id, file, id)
                        null
                    }
                }
                else -> return item.copy(children = children)
            }
            return item.copy(
                children = children,
                refreshIds = targets.mapNotNull { it.id }.distinct(),
                refreshSh = targets.flatMap { listOfNotNull(it.visibleSh, it.readonlySh, it.lockSh, it.titleSh, it.descSh) }.distinct(),
            )
        }
        return items.map(::resolve)
    }

    private fun parseEntry(
        element: Any?,
        isGroup: Boolean,
        label: String,
        warnings: MutableList<String>,
        ids: MutableSet<String>,
    ): Item? {
        val t = element as? TomlTable
        if (t == null) {
            warnings += AppText.get(R.string.cfg_not_table, label)
            return null
        }
        return parseItem(t, warnings, ids, label, isGroup)
    }

    /** Item con của một `[[group]]`, khai báo bằng `[[group.item]]`. */
    private fun parseChildren(
        group: TomlTable,
        warnings: MutableList<String>,
        ids: MutableSet<String>,
        path: String,
    ): List<Item> {
        if (group["group"] != null) warnings += AppText.get(R.string.cfg_nested_group, path)
        val array = group["item"] as? TomlArray ?: return emptyList()
        val out = mutableListOf<Item>()
        array.forEachIndexed { index, element ->
            parseEntry(element, false, "$path: item #${index + 1}", warnings, ids)?.let { out += it }
        }
        return out
    }

    /** [isGroup] = true khi bảng đến từ `[[group]]`: type luôn là group, không đọc khóa `type`. */
    private fun parseItem(
        t: TomlTable,
        warnings: MutableList<String>,
        ids: MutableSet<String>,
        label: String,
        isGroup: Boolean,
    ): Item? {
        warnUnknownKeys(t, if (isGroup) ITEM_KEYS + setOf("item", "group") else ITEM_KEYS, label, warnings)
        val typeKey = t.str("type")
        if (!isGroup && typeKey == ItemType.Group.key) {
            warnings += AppText.get(R.string.cfg_type_group_renamed, label)
            return null
        }
        val type = if (isGroup) ItemType.Group else ItemType.from(typeKey)
        if (type == null) {
            warnings += AppText.get(R.string.cfg_unknown_type, label, typeKey ?: "")
            return null
        }
        val rawTitle = t.str("title")
        val titleSh = t.str("title-sh")?.takeIf { it.isNotBlank() }
        val descSh = t.str("desc-sh")?.takeIf { it.isNotBlank() }
        // `[[group]]` và `text` không bắt buộc title: group không có title thì thẻ không kèm tiêu đề; text không có title thì chỉ hiện đoạn chữ (desc).
        // Có `title-sh` thì không cần title. Item khác vẫn bắt buộc title (hoặc title-sh).
        if (rawTitle.isNullOrBlank() && titleSh == null && !isGroup) {
            if (type != ItemType.Text) {
                warnings += AppText.get(R.string.cfg_item_missing_title, label, type.key)
                return null
            }
            if (t.str("desc").isNullOrBlank() && descSh == null) {
                warnings += AppText.get(R.string.cfg_text_empty, label)
                return null
            }
        }
        val title = rawTitle.orEmpty()
        val id = t.str("id")?.takeIf { it.isNotBlank() }
        // multi: true (mặc định) chọn nhiều, false chọn một; dùng cho `select` và `applist`.
        val multi = when (val raw = t.str("multi")?.lowercase()) {
            null, "true" -> true
            "false" -> false
            else -> {
                warnings += AppText.get(R.string.cfg_invalid_multi, label, title, raw)
                true
            }
        }
        val needsId = type == ItemType.Switch || type == ItemType.Input ||
            type == ItemType.Slider ||
            type == ItemType.Checkbox || type == ItemType.Radio || type == ItemType.Progress ||
            type == ItemType.Dropdown || type == ItemType.Select ||
            type == ItemType.AppList
        if (needsId) {
            if (id == null) {
                warnings += AppText.get(R.string.cfg_item_missing_id, label, title, type.key)
                return null
            }
            if (!ShellText.isValidVarName(id)) {
                warnings += AppText.get(R.string.cfg_invalid_id, label, title, id)
                return null
            }
            val selectType = type == ItemType.Radio || type == ItemType.Dropdown || (type == ItemType.Select && !multi)
            if (id in RESERVED_IDS || (id == APP_LANGUAGE_ID && !selectType)) {
                warnings += AppText.get(R.string.cfg_reserved_id, label, title, id)
                return null
            }
            if (!ids.add(id)) {
                warnings += AppText.get(R.string.cfg_duplicate_id, label, title, id)
                return null
            }
        }
        if (type == ItemType.Link) {
            val hasTab = !t.str("tab").isNullOrBlank()
            val hasPage = !t.str("page").isNullOrBlank()
            if (!hasTab && !hasPage) {
                warnings += AppText.get(R.string.cfg_link_missing_target, label, title)
                return null
            }
            if (hasTab && hasPage) warnings += AppText.get(R.string.cfg_link_both, label, title)
        }

        if (type == ItemType.AppList) {
            // `get` của applist đã đổi tên thành `options-sh`: báo khóa cũ, và thiếu `options-sh` thì bỏ item.
            if (!t.str("get").isNullOrBlank()) warnings += AppText.get(R.string.cfg_applist_get_renamed, label, title)
            if (t.str("options-sh").isNullOrBlank()) {
                warnings += AppText.get(R.string.cfg_applist_missing_options_sh, label, title)
                return null
            }
        }

        val dialog = when (val style = t.str("style")?.lowercase()) {
            null, "page" -> false
            "dialog" -> true
            else -> {
                warnings += AppText.get(R.string.cfg_invalid_style, label, title, style.orEmpty())
                false
            }
        }

        val shell = when (t.str("shell")?.lowercase()) {
            null, "live" -> ShellMode.Live
            "log" -> {
                warnings += AppText.get(R.string.cfg_shell_log_removed, label, title)
                ShellMode.Live
            }
            "full" -> ShellMode.Full
            "hidden" -> ShellMode.Hidden
            "banner" -> ShellMode.Banner
            "notice" -> ShellMode.Notice
            "snackbar" -> ShellMode.Snackbar
            else -> {
                warnings += AppText.get(R.string.cfg_invalid_shell, label, title, t.str("shell").orEmpty())
                ShellMode.Live
            }
        }
        val columns = when (t.str("columns")) {
            null, "1" -> 1
            "2" -> 2
            else -> {
                warnings += AppText.get(R.string.cfg_invalid_columns, label, title, t.str("columns").orEmpty())
                1
            }
        }
        val iconSize = when (val raw = t.str("icon_size")) {
            null -> 24f
            else -> raw.toFloatOrNull()?.takeIf { it > 0f } ?: run {
                warnings += AppText.get(R.string.cfg_invalid_icon_size, label, title, raw)
                24f
            }
        }
        val iconTint = when (t.str("icon_tint")?.lowercase()) {
            null, "true" -> true
            "false" -> false
            else -> {
                warnings += AppText.get(R.string.cfg_invalid_icon_tint, label, title, t.str("icon_tint").orEmpty())
                true
            }
        }
        val divider = when (t.str("divider")?.lowercase()) {
            null, "false" -> false
            "true" -> true
            else -> {
                warnings += AppText.get(R.string.cfg_invalid_divider, label, title, t.str("divider").orEmpty())
                false
            }
        }
        val separator = t.str("separator")?.takeIf { it.isNotEmpty() } ?: "\n"
        // refresh = true (cả trang) hoặc "id1,id2" (các item có id này); false / rỗng = không làm mới.
        val refreshRaw = t.str("refresh")?.trim().orEmpty()
        val refreshAll = refreshRaw.equals("true", ignoreCase = true)
        val refreshIds = if (refreshAll || refreshRaw.equals("false", ignoreCase = true)) {
            emptyList()
        } else {
            refreshRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }

        // lock = "điều kiện|lý do": phần lý do (tùy chọn) được thay @khóa; điều kiện giữ nguyên.
        val (lockCondition, lockRawMsg) = ConditionEvaluator.splitMessage(t.str("lock").orEmpty())

        // options = ["Nhãn|giá trị", ...]; không có dấu | thì nhãn = giá trị.
        val options = mutableListOf<SelectOption>()
        (t["options"] as? TomlArray)?.forEachIndexed { i, o ->
            val raw = (o as? TomlLiteral)?.content?.let(::resolveOption)
            val option = raw?.let(::parseOption)
            if (option == null) {
                warnings += AppText.get(R.string.cfg_invalid_option, label, title, (i + 1).toString())
            } else {
                options += option
            }
        }
        if (id == APP_LANGUAGE_ID) {
            options.removeAll { option ->
                (AppLanguage.fromKey(option.value) == null).also { bad ->
                    if (bad) warnings += AppText.get(R.string.cfg_app_language_option, label, title, option.value)
                }
            }
        }
        if ((type == ItemType.Radio || type == ItemType.Dropdown || type == ItemType.Select) && options.isEmpty()) {
            warnings += AppText.get(R.string.cfg_no_valid_options, label, title, type.key)
        }
        if (type == ItemType.Select && multi && options.any { separator in it.value }) {
            warnings += AppText.get(R.string.cfg_separator_in_value, label, title, separator)
        }

        var min = t.float("min") ?: 0f
        var max = t.float("max") ?: 100f
        if (max < min) {
            warnings += AppText.get(R.string.cfg_max_lt_min, label, title)
            min = max.also { max = min }
        }
        val step = (t.float("step") ?: 1f).takeIf { it > 0f } ?: 1f
        if (type == ItemType.Slider) {
            if (max == min) {
                warnings += AppText.get(R.string.cfg_min_eq_max, label, title)
                max = min + step
            } else {
                val steps = (max - min) / step
                if (kotlin.math.abs(steps - kotlin.math.round(steps)) > 1e-3f) {
                    warnings += AppText.get(R.string.cfg_step_not_divide, label, title, step.toString())
                }
            }
        }

        val children = if (isGroup) {
            parseChildren(t, warnings, ids, "$label \"$title\"")
        } else {
            emptyList()
        }

        return Item(
            type = type,
            id = id,
            title = title,
            desc = t.str("desc"),
            icon = t.str("icon")?.takeIf { it.isNotBlank() && type != ItemType.Text },
            iconSize = iconSize,
            iconTint = iconTint,
            visible = t.str("visible")?.takeIf { it.isNotBlank() },
            visibleSh = t.str("visible-sh")?.takeIf { it.isNotBlank() },
            readonly = t.str("readonly")?.takeIf { it.isNotBlank() },
            readonlySh = t.str("readonly-sh")?.takeIf { it.isNotBlank() },
            lock = lockCondition.takeIf { it.isNotBlank() },
            lockMsg = lockRawMsg?.let { strings.resolve(it) },
            lockSh = t.str("lock-sh")?.takeIf { it.isNotBlank() },
            titleSh = titleSh,
            descSh = descSh,
            refreshAll = refreshAll,
            refreshIds = refreshIds,
            refreshSh = emptyList(),
            shell = shell,
            confirm = t.str("confirm")?.takeIf { it.isNotBlank() },
            timeoutSec = t.str("timeout")?.toIntOrNull()?.takeIf { it > 0 },
            script = if (id == APP_LANGUAGE_ID) null else t.str("script"),
            get = if (type == ItemType.AppList) null else t.str("get"),
            optionsSh = t.str("options-sh")?.takeIf { it.isNotBlank() },
            hint = t.str("hint"),
            default = t.str("default"),
            numberInput = t.str("input_type")?.lowercase() == "number",
            options = options,
            columns = columns,
            separator = separator,
            min = min,
            max = max,
            step = step,
            tab = t.str("tab")?.takeIf { it.isNotBlank() },
            page = t.str("page")?.takeIf { it.isNotBlank() },
            dialog = dialog && t.str("page").isNullOrBlank().not(),
            children = children,
            divider = divider,
            multi = multi,
        )
    }
}

/**
 * select (multi = true): giữ lại các giá trị hợp lệ trong [raw] (tách bằng `separator`), theo thứ tự khai báo trong options.
 * Tách `raw` chỉ theo [Item.separator] (mặc định xuống dòng), nên option có giá trị chứa dấu phẩy vẫn giữ nguyên.
 */
fun Item.normalizeMulti(raw: String): String {
    val parts = raw.split(separator)
    val chosen = parts.map { it.trim() }.toSet()
    return options.filter { it.value in chosen }.joinToString(separator) { it.value }
}

/**
 * applist: tách [raw] theo [Item.separator], bỏ dòng trống và trùng; `multi = false` thì chỉ giữ package đầu tiên.
 * Không đối chiếu với app đã cài (danh sách app chỉ nạp khi mở màn hình chọn, bằng lệnh `get`), nên package đã gỡ vẫn được giữ cho tới lần xác nhận kế tiếp.
 */
fun Item.normalizeApps(raw: String): String {
    val parts = raw.split(separator).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return (if (multi) parts else parts.take(1)).joinToString(separator)
}

/** Đọc `assets/configs`; đường dẫn bắt đầu bằng `/` thì đọc thẳng file ngoài APK. Reload nóng để lại sau (schema mục 12). */
object ConfigRepository {

    private const val ROOT = "configs"

    private fun readAsset(context: Context, path: String): String =
        if (path.startsWith("/")) {
            File(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            context.assets.open("$ROOT/$path").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

    /** Thời gian tối đa cho một lệnh shell nạp chuỗi ngôn ngữ (`lang-sh`, `load-string-sh`). */
    private const val LANG_SHELL_TIMEOUT_SEC = 5

    /**
     * Ném [ConfigParseException] nếu `main.toml` lỗi cú pháp; file trang lỗi chỉ làm trang đó hiện lỗi.
     * Nạp bảng chuỗi `@khóa` (`[app]`: lang < lang-sh < load-string < load-string-sh, nguồn sau đè nguồn trước) trước khi parse
     * main.toml và các trang, để mọi chuỗi hiển thị trong TOML đã được thay khi dựng Item. Suspend vì có thể chạy shell.
     */
    suspend fun load(context: Context): LoadedConfig {
        val appWarnings = mutableListOf<String>()
        val mainText = try {
            readAsset(context, "main.toml")
        } catch (e: java.io.IOException) {
            throw ConfigParseException("main.toml", null, AppText.get(R.string.cfg_read_failed, e.message.orEmpty()))
        }
        val rawApp = TomlParser.parseTable("main.toml", mainText)["app"] as? TomlTable
        // Lệnh shell nạp chuỗi dùng cùng môi trường với mọi lệnh khác (env.sh rồi [app] env).
        (rawApp?.get("env") as? TomlLiteral)?.content?.takeIf { it.isNotBlank() }?.let { ExecutorManager.prelude = it }
        TomlParser.strings = loadStrings(context, rawApp, appWarnings)
        try {
            return parseAll(context, mainText, appWarnings)
        } finally {
            TomlParser.strings = LangTable.EMPTY
        }
    }

    private fun TomlTable.raw(key: String): String? = (this[key] as? TomlLiteral)?.content?.takeIf { it.isNotBlank() }

    private suspend fun loadStrings(context: Context, app: TomlTable?, warnings: MutableList<String>): LangTable {
        if (app == null) return LangTable.EMPTY
        val merged = LinkedHashMap<String, String>()
        // 1. lang: tệp văn bản dạng bash (khóa=giá trị)
        app.raw("lang")?.let { path ->
            readLangAsset(context, "lang", path, warnings)?.let { merged += LangTable.parse(it, path, warnings) }
        }
        // 2. lang-sh: tệp shell, những gì nó in ra là bảng chuỗi (đè lên lang)
        app.raw("lang-sh")?.let { path ->
            readLangAsset(context, "lang-sh", path, warnings)?.let { script -> runLangShell("lang-sh", path, script, warnings)?.let { merged += it } }
        }
        // 3. load-string: văn bản nhập thẳng, dùng để bổ sung vài khóa nhỏ (đè lên lang và lang-sh)
        app.raw("load-string")?.let { merged += LangTable.parse(it, "load-string", warnings) }
        // 4. load-string-sh: lệnh shell nhập thẳng (ví dụ echo), đè lên load-string
        app.raw("load-string-sh")?.let { cmd -> runLangShell("load-string-sh", "load-string-sh", cmd, warnings)?.let { merged += it } }
        return LangTable(merged)
    }

    private fun readLangAsset(context: Context, source: String, path: String, warnings: MutableList<String>): String? = try {
        readAsset(context, path)
    } catch (e: IOException) {
        warnings += AppText.get(R.string.cfg_lang_file_failed, source, path, e.message.orEmpty())
        null
    }

    /** Chạy [command], parse đầu ra thành bảng chuỗi. Lỗi, hết giờ hoặc thoát khác 0 thì cảnh báo và bỏ nguồn này. */
    private suspend fun runLangShell(source: String, name: String, command: String, warnings: MutableList<String>): Map<String, String>? {
        val result = try {
            ExecutorManager.current.exec(ShellText.silenceStderr(command), LANG_SHELL_TIMEOUT_SEC)
        } catch (e: Exception) {
            warnings += AppText.get(R.string.cfg_lang_sh_error, source, (e.message ?: e).toString())
            return null
        }
        if (!result.ok) {
            warnings += if (result.timedOut) {
                AppText.get(R.string.cfg_lang_sh_timeout, source, LANG_SHELL_TIMEOUT_SEC)
            } else {
                AppText.get(R.string.cfg_lang_sh_failed, source, result.code)
            }
            return null
        }
        return LangTable.parse(result.output, name, warnings)
    }

    private fun parseAll(context: Context, mainText: String, appWarnings: MutableList<String>): LoadedConfig {
        // Cảnh báo gom theo file để mỗi trang chỉ hiện cảnh báo của chính nó.
        val byFile = LinkedHashMap<String, MutableList<String>>()
        val (app, refs) = TomlParser.parseMain("main.toml", mainText, appWarnings)
        if (refs.isEmpty()) {
            throw ConfigParseException("main.toml", null, AppText.get(R.string.cfg_no_tabs))
        }
        val pages = refs.map { ref ->
            try {
                LoadedPage(ref, TomlParser.parsePage(ref.file, readAsset(context, ref.file), byFile.warningsOf(ref.file)))
            } catch (e: ConfigParseException) {
                LoadedPage(ref, emptyList(), error = e.message ?: AppText.get(R.string.cfg_parse_error, ref.file))
            } catch (e: java.io.IOException) {
                LoadedPage(ref, emptyList(), error = AppText.get(R.string.cfg_file_read_failed, ref.file, e.message.orEmpty()))
            }
        }
        val subPages = loadSubPages(context, pages, byFile)
        return LoadedConfig(
            app = app,
            pages = pages.map { it.copy(warnings = byFile[it.ref.file].orEmpty().toList()) },
            subPages = subPages.mapValues { (file, sub) -> sub.copy(warnings = byFile[file].orEmpty().toList()) },
            appWarnings = appWarnings,
        )
    }

    private fun MutableMap<String, MutableList<String>>.warningsOf(file: String): MutableList<String> =
        getOrPut(file) { mutableListOf() }

    /** Nạp mọi trang con được link `page` trỏ tới (kể cả link nằm trong trang con), mỗi file một lần. */
    private fun loadSubPages(
        context: Context,
        tabs: List<LoadedPage>,
        byFile: MutableMap<String, MutableList<String>>,
    ): Map<String, SubPage> {
        val tabIds = tabs.map { it.ref.id }.toSet()
        val result = LinkedHashMap<String, SubPage>()
        val queue = ArrayDeque<Pair<String, List<Item>>>()
        tabs.forEach { queue += it.ref.file to it.items }
        while (queue.isNotEmpty()) {
            val (owner, items) = queue.removeFirst()
            for (link in links(items)) {
                val tab = link.tab
                val page = link.page
                if (tab != null) {
                    if (tab !in tabIds) byFile.warningsOf(owner).add(AppText.get(R.string.cfg_link_tab_missing, owner, link.title, tab))
                } else if (page != null) {
                    val existing = result[page]
                    if (existing != null) {
                        if (existing.dialog != link.dialog) {
                            byFile.warningsOf(owner).add(AppText.get(R.string.cfg_link_style_mismatch, owner, link.title, page))
                        }
                        continue
                    }
                    val pageWarnings = byFile.warningsOf(page)
                    val sub = try {
                        SubPage(page, link.title, dialogItems(page, link, TomlParser.parsePage(page, readAsset(context, page), pageWarnings), pageWarnings), dialog = link.dialog, action = link)
                    } catch (e: ConfigParseException) {
                        SubPage(page, link.title, emptyList(), error = e.message ?: AppText.get(R.string.cfg_parse_error, page), dialog = link.dialog, action = link)
                    } catch (e: java.io.IOException) {
                        SubPage(page, link.title, emptyList(), error = AppText.get(R.string.cfg_file_read_failed, page, e.message.orEmpty()), dialog = link.dialog, action = link)
                    }
                    result[page] = sub
                    queue += page to sub.items
                }
            }
        }
        return result
    }

    /** Dialog chỉ để truyền biến: bỏ button và link (đã có Hủy / Xác nhận), cảnh báo từng mục bị bỏ. */
    private fun dialogItems(file: String, link: Item, items: List<Item>, warnings: MutableList<String>): List<Item> {
        if (!link.dialog) return items
        fun filter(list: List<Item>): List<Item> = list.mapNotNull { item ->
            when (item.type) {
                ItemType.Button, ItemType.Link -> {
                    warnings += AppText.get(R.string.cfg_dialog_item_unsupported, file, item.type.key, item.title)
                    null
                }
                ItemType.Group -> item.copy(children = filter(item.children))
                else -> item
            }
        }
        return filter(items)
    }

    private fun links(items: List<Item>): List<Item> = items.flatMap {
        if (it.type == ItemType.Group) links(it.children) else if (it.type == ItemType.Link) listOf(it) else emptyList()
    }
}
