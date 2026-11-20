package com.tool.script.config

import android.content.Context
import com.tool.script.util.ShellText
import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlArray
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.TomlTable

// Model + parser TOML theo docs/schema.md.

enum class ItemType(val key: String) {
    Text("text"), Group("group"), Button("button"), Switch("switch"),
    Input("input"), Select("select"), Slider("slider"), Link("link"),
    Checkbox("checkbox"), Radio("radio"), Progress("progress"), Dropdown("dropdown"),
    MultiSelect("multiselect"), SingleSelect("singleselect");

    companion object {
        fun from(key: String?): ItemType? = entries.firstOrNull { it.key == key }
    }
}

enum class ShellMode { Live, Full, Hidden, Banner, Notice }

enum class LockMode { Readonly, Hide }

data class SelectOption(val label: String, val value: String)

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
    val visibleIf: String?,
    val lock: String?,
    val lockMode: LockMode,
    val shell: ShellMode,
    val confirm: String?,
    val timeoutSec: Int,
    // text
    val content: String?,
    // button / input / select / slider
    val run: String?,
    // switch / checkbox
    val on: String?,
    val off: String?,
    val get: String?,
    // input
    val hint: String?,
    val default: String?,
    val numberInput: Boolean,
    // select / radio / dropdown
    val options: List<SelectOption>,
    // select / multiselect / singleselect: số cột lựa chọn trong hộp thoại (1 hoặc 2)
    val columns: Int,
    // multiselect: dấu phân tách các giá trị đã chọn trong $id
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
)

/** [env]: đoạn shell chạy đầu tiên trước mọi lệnh (`[app] env`), null nếu không khai báo. */
data class AppInfo(val name: String, val version: Int, val lang: String, val env: String? = null)

data class PageRef(val id: String, val title: String, val icon: String, val file: String)

/** Một trang đã nạp. [error] khác null nghĩa là file trang bị lỗi parse (trang hiện thông báo lỗi). */
data class LoadedPage(val ref: PageRef, val items: List<Item>, val error: String? = null)

/** Trang con mở từ item `link` có `page`. [file] là khóa; [title] lấy từ item link đầu tiên trỏ tới nó. */
data class SubPage(
    val file: String,
    val title: String,
    val items: List<Item>,
    val error: String? = null,
    val dialog: Boolean = false,
    /** Item link mở trang này; với dialog, `run` / `shell` / `timeout` / `confirm` của nó chạy khi bấm Xác nhận. */
    val action: Item? = null,
)

data class LoadedConfig(
    val app: AppInfo,
    val pages: List<LoadedPage>,
    val subPages: Map<String, SubPage>,
    val warnings: List<String>,
)

/** Lỗi parse TOML: có tên file, số dòng (nếu parser cung cấp) và mô tả. */
class ConfigParseException(val file: String, val line: Int?, val detail: String) :
    Exception(buildString {
        append(file)
        if (line != null) append(", dòng ").append(line)
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

    private fun TomlTable.str(key: String): String? = (this[key] as? TomlLiteral)?.content

    private fun TomlTable.float(key: String): Float? = str(key)?.toFloatOrNull()

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
        val info = AppInfo(
            name = app?.str("name") ?: "Tool Script",
            version = app?.str("version")?.toIntOrNull() ?: 1,
            lang = app?.str("lang") ?: "vi",
            env = app?.str("env")?.takeIf { it.isNotBlank() },
        )
        val pages = mutableListOf<PageRef>()
        val seen = HashSet<String>()
        if (root["page"] != null) warnings += "$file: [[page]] đã đổi thành [[tab]], bỏ qua"
        val array = root["tab"] as? TomlArray
        array?.forEachIndexed { index, element ->
            val t = element as? TomlTable
            val label = "$file: [[tab]] #${index + 1}"
            val id = t?.str("id")
            val title = t?.str("title")
            val icon = t?.str("icon")
            val pageFile = t?.str("file")
            when {
                t == null || id.isNullOrBlank() -> warnings += "$label thiếu id, bỏ qua"
                title.isNullOrBlank() -> warnings += "$label ($id) thiếu title, bỏ tab này"
                icon.isNullOrBlank() -> warnings += "$label ($id) thiếu icon, bỏ tab này"
                pageFile.isNullOrBlank() -> warnings += "$label ($id) thiếu file, bỏ tab này"
                !seen.add(id) -> warnings += "$label trùng id \"$id\", bỏ tab này"
                else -> pages += PageRef(id!!, title!!, icon!!, pageFile!!)
            }
        }
        return info to pages
    }

    /** Dòng mở bảng `[[item]]` / `[[group]]` ở cấp trang (không khớp `[[group.item]]`). */
    private val BLOCK_HEADER = Regex("""^[ \t]*\[\[[ \t]*(item|group)[ \t]*\]\]""", RegexOption.MULTILINE)

    fun parsePage(file: String, text: String, warnings: MutableList<String>): List<Item> {
        val root = parseTable(file, text)
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
        return out
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
            warnings += "$label không phải bảng, bỏ qua"
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
        if (group["group"] != null) warnings += "$path: chưa hỗ trợ group lồng group, bỏ qua [[group.group]]"
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
        val typeKey = t.str("type")
        if (!isGroup && typeKey == ItemType.Group.key) {
            warnings += "$label: type = \"group\" đã đổi thành [[group]], bỏ qua"
            return null
        }
        val type = if (isGroup) ItemType.Group else ItemType.from(typeKey)
        if (type == null) {
            warnings += "$label có type lạ \"${typeKey ?: ""}\", bỏ qua"
            return null
        }
        val title = t.str("title")
        if (title.isNullOrBlank()) {
            warnings += "$label (${type.key}) thiếu title, bỏ qua"
            return null
        }
        val id = t.str("id")?.takeIf { it.isNotBlank() }
        val needsId = type == ItemType.Switch || type == ItemType.Input ||
            type == ItemType.Select || type == ItemType.Slider ||
            type == ItemType.Checkbox || type == ItemType.Radio || type == ItemType.Progress ||
            type == ItemType.Dropdown || type == ItemType.MultiSelect || type == ItemType.SingleSelect
        if (needsId) {
            if (id == null) {
                warnings += "$label \"$title\" (${type.key}) thiếu id, bỏ qua"
                return null
            }
            if (!ShellText.isValidVarName(id)) {
                warnings += "$label \"$title\": id \"$id\" không hợp lệ (chỉ chữ, số, gạch dưới; không bắt đầu bằng số), bỏ qua"
                return null
            }
            if (!ids.add(id)) {
                warnings += "$label \"$title\": trùng id \"$id\", bỏ qua"
                return null
            }
        }
        if (type == ItemType.Link) {
            val hasTab = !t.str("tab").isNullOrBlank()
            val hasPage = !t.str("page").isNullOrBlank()
            if (!hasTab && !hasPage) {
                warnings += "$label \"$title\" (link) thiếu tab hoặc page, bỏ qua"
                return null
            }
            if (hasTab && hasPage) warnings += "$label \"$title\": có cả tab và page, dùng tab"
        }

        val dialog = when (val style = t.str("style")?.lowercase()) {
            null, "page" -> false
            "dialog" -> true
            else -> {
                warnings += "$label \"$title\": style = \"$style\" không hợp lệ, dùng page"
                false
            }
        }

        val shell = when (t.str("shell")?.lowercase()) {
            null, "live" -> ShellMode.Live
            "log" -> {
                warnings += "$label \"$title\": shell = \"log\" đã bỏ, dùng live"
                ShellMode.Live
            }
            "full" -> ShellMode.Full
            "hidden" -> ShellMode.Hidden
            "banner" -> ShellMode.Banner
            "notice" -> ShellMode.Notice
            else -> {
                warnings += "$label \"$title\": shell = \"${t.str("shell")}\" không hợp lệ, dùng live"
                ShellMode.Live
            }
        }
        val columns = when (t.str("columns")) {
            null, "1" -> 1
            "2" -> 2
            else -> {
                warnings += "$label \"$title\": columns = \"${t.str("columns")}\" không hợp lệ (1 hoặc 2), dùng 1"
                1
            }
        }
        val iconSize = when (val raw = t.str("icon_size")) {
            null -> 24f
            else -> raw.toFloatOrNull()?.takeIf { it > 0f } ?: run {
                warnings += "$label \"$title\": icon_size = \"$raw\" không hợp lệ (số dương), dùng 24"
                24f
            }
        }
        val iconTint = when (t.str("icon_tint")?.lowercase()) {
            null, "true" -> true
            "false" -> false
            else -> {
                warnings += "$label \"$title\": icon_tint = \"${t.str("icon_tint")}\" không hợp lệ (true hoặc false), dùng true"
                true
            }
        }
        val separator = t.str("separator")?.takeIf { it.isNotEmpty() } ?: ","
        val lockMode = when (t.str("lock_mode")?.lowercase()) {
            null, "readonly" -> LockMode.Readonly
            "hide" -> LockMode.Hide
            else -> {
                warnings += "$label \"$title\": lock_mode = \"${t.str("lock_mode")}\" không hợp lệ, dùng readonly"
                LockMode.Readonly
            }
        }

        // options = ["Nhãn|giá trị", ...]; không có dấu | thì nhãn = giá trị.
        val options = mutableListOf<SelectOption>()
        (t["options"] as? TomlArray)?.forEachIndexed { i, o ->
            val raw = (o as? TomlLiteral)?.content
            val option = raw?.let(::parseOption)
            if (option == null) {
                warnings += "$label \"$title\": options #${i + 1} không hợp lệ (cần dạng \"nhãn|giá trị\"), bỏ qua"
            } else {
                options += option
            }
        }
        if ((type == ItemType.Select || type == ItemType.Radio || type == ItemType.Dropdown || type == ItemType.MultiSelect ||
            type == ItemType.SingleSelect) &&
            options.isEmpty()
        ) {
            warnings += "$label \"$title\" (${type.key}) không có options hợp lệ"
        }
        if (type == ItemType.MultiSelect && options.any { separator in it.value }) {
            warnings += "$label \"$title\": có giá trị trong options chứa dấu phân tách \"$separator\", kết quả sẽ sai"
        }

        var min = t.float("min") ?: 0f
        var max = t.float("max") ?: 100f
        if (max < min) {
            warnings += "$label \"$title\": max < min, đã đổi chỗ"
            min = max.also { max = min }
        }
        val step = (t.float("step") ?: 1f).takeIf { it > 0f } ?: 1f

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
            visibleIf = t.str("visible_if")?.takeIf { it.isNotBlank() },
            lock = t.str("lock")?.takeIf { it.isNotBlank() },
            lockMode = lockMode,
            shell = shell,
            confirm = t.str("confirm")?.takeIf { it.isNotBlank() },
            timeoutSec = t.str("timeout")?.toIntOrNull()?.takeIf { it > 0 } ?: 30,
            content = t.str("content"),
            run = t.str("run"),
            on = t.str("on"),
            off = t.str("off"),
            get = t.str("get"),
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
        )
    }
}

/** multiselect: giữ lại các giá trị hợp lệ trong [raw] (tách bằng `separator`), theo thứ tự khai báo trong options. */
fun Item.normalizeMulti(raw: String): String {
    val chosen = raw.split(separator).map { it.trim() }.toSet()
    return options.filter { it.value in chosen }.joinToString(separator) { it.value }
}

/** Đọc `assets/configs`. Nạp từ thư mục ngoài và reload nóng để lại sau (schema mục 12). */
object ConfigRepository {

    private const val ROOT = "configs"

    private fun readAsset(context: Context, path: String): String =
        context.assets.open("$ROOT/$path").bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** Ném [ConfigParseException] nếu `main.toml` lỗi cú pháp; file trang lỗi chỉ làm trang đó hiện lỗi. */
    fun load(context: Context): LoadedConfig {
        val warnings = mutableListOf<String>()
        val mainText = try {
            readAsset(context, "main.toml")
        } catch (e: java.io.IOException) {
            throw ConfigParseException("main.toml", null, "không đọc được file: ${e.message}")
        }
        val (app, refs) = TomlParser.parseMain("main.toml", mainText, warnings)
        if (refs.isEmpty()) {
            throw ConfigParseException("main.toml", null, "không có [[tab]] hợp lệ nào để hiển thị")
        }
        val pages = refs.map { ref ->
            try {
                LoadedPage(ref, TomlParser.parsePage(ref.file, readAsset(context, ref.file), warnings))
            } catch (e: ConfigParseException) {
                warnings += e.message ?: "${ref.file}: lỗi parse"
                LoadedPage(ref, emptyList(), error = e.message)
            } catch (e: java.io.IOException) {
                val msg = "${ref.file}: không đọc được file: ${e.message}"
                warnings += msg
                LoadedPage(ref, emptyList(), error = msg)
            }
        }
        val subPages = loadSubPages(context, pages, warnings)
        return LoadedConfig(app, pages, subPages, warnings)
    }

    /** Nạp mọi trang con được link `page` trỏ tới (kể cả link nằm trong trang con), mỗi file một lần. */
    private fun loadSubPages(
        context: Context,
        tabs: List<LoadedPage>,
        warnings: MutableList<String>,
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
                    if (tab !in tabIds) warnings += "$owner: link \"${link.title}\" trỏ tới tab \"$tab\" không tồn tại"
                } else if (page != null) {
                    val existing = result[page]
                    if (existing != null) {
                        if (existing.dialog != link.dialog) {
                            warnings += "$owner: link \"${link.title}\" khác style với link đầu tiên trỏ tới $page, dùng style của link đầu tiên"
                        }
                        continue
                    }
                    val sub = try {
                        SubPage(page, link.title, dialogItems(page, link, TomlParser.parsePage(page, readAsset(context, page), warnings), warnings), dialog = link.dialog, action = link)
                    } catch (e: ConfigParseException) {
                        warnings += e.message ?: "$page: lỗi parse"
                        SubPage(page, link.title, emptyList(), error = e.message, dialog = link.dialog, action = link)
                    } catch (e: java.io.IOException) {
                        val msg = "$page: không đọc được file: ${e.message}"
                        warnings += msg
                        SubPage(page, link.title, emptyList(), error = msg, dialog = link.dialog, action = link)
                    }
                    result[page] = sub
                    queue += page to sub.items
                }
            }
        }
        return result
    }

    /** Dialog chỉ để truyền biến: bỏ button và link (không có biến để truyền), cảnh báo từng mục bị bỏ. */
    private fun dialogItems(file: String, link: Item, items: List<Item>, warnings: MutableList<String>): List<Item> {
        if (!link.dialog) return items
        fun filter(list: List<Item>): List<Item> = list.mapNotNull { item ->
            when (item.type) {
                ItemType.Button, ItemType.Link -> {
                    warnings += "$file: ${item.type.key} \"${item.title}\" không dùng được trong dialog (dialog chỉ để truyền biến), bỏ qua"
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
