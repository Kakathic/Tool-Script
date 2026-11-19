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
    Input("input"), Select("select"), Slider("slider"), Link("link");

    companion object {
        fun from(key: String?): ItemType? = entries.firstOrNull { it.key == key }
    }
}

enum class ShellMode { Log, Hidden, Banner, Notice }

enum class LockMode { Readonly, Hide }

data class SelectOption(val label: String, val value: String)

data class Item(
    val type: ItemType,
    val id: String?,
    val title: String,
    val desc: String?,
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
    // switch
    val on: String?,
    val off: String?,
    val get: String?,
    // input
    val hint: String?,
    val default: String?,
    val numberInput: Boolean,
    // select
    val options: List<SelectOption>,
    // slider
    val min: Float,
    val max: Float,
    val step: Float,
    // link
    val page: String?,
    // group
    val children: List<Item>,
)

data class AppInfo(val name: String, val version: Int, val lang: String)

data class PageRef(val id: String, val title: String, val icon: String, val file: String)

/** Một trang đã nạp. [error] khác null nghĩa là file trang bị lỗi parse (trang hiện thông báo lỗi). */
data class LoadedPage(val ref: PageRef, val items: List<Item>, val error: String? = null)

data class LoadedConfig(
    val app: AppInfo,
    val pages: List<LoadedPage>,
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
        )
        val pages = mutableListOf<PageRef>()
        val seen = HashSet<String>()
        val array = root["page"] as? TomlArray
        array?.forEachIndexed { index, element ->
            val t = element as? TomlTable
            val label = "$file: [[page]] #${index + 1}"
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

    fun parsePage(file: String, text: String, warnings: MutableList<String>): List<Item> {
        val root = parseTable(file, text)
        val ids = HashSet<String>()
        return parseItems(file, root["item"] as? TomlArray, warnings, ids, file)
    }

    private fun parseItems(
        file: String,
        array: TomlArray?,
        warnings: MutableList<String>,
        ids: MutableSet<String>,
        path: String,
    ): List<Item> {
        if (array == null) return emptyList()
        val out = mutableListOf<Item>()
        array.forEachIndexed { index, element ->
            val t = element as? TomlTable
            val label = "$path: item #${index + 1}"
            if (t == null) {
                warnings += "$label không phải bảng, bỏ qua"
                return@forEachIndexed
            }
            parseItem(file, t, warnings, ids, label)?.let { out += it }
        }
        return out
    }

    private fun parseItem(
        file: String,
        t: TomlTable,
        warnings: MutableList<String>,
        ids: MutableSet<String>,
        label: String,
    ): Item? {
        val typeKey = t.str("type")
        val type = ItemType.from(typeKey)
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
            type == ItemType.Select || type == ItemType.Slider
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
        if (type == ItemType.Link && t.str("page").isNullOrBlank()) {
            warnings += "$label \"$title\" (link) thiếu page, bỏ qua"
            return null
        }

        val shell = when (t.str("shell")?.lowercase()) {
            null, "log" -> ShellMode.Log
            "hidden" -> ShellMode.Hidden
            "banner" -> ShellMode.Banner
            "notice" -> ShellMode.Notice
            else -> {
                warnings += "$label \"$title\": shell = \"${t.str("shell")}\" không hợp lệ, dùng log"
                ShellMode.Log
            }
        }
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
        if (type == ItemType.Select && options.isEmpty()) {
            warnings += "$label \"$title\" (select) không có options hợp lệ"
        }

        var min = t.float("min") ?: 0f
        var max = t.float("max") ?: 100f
        if (max < min) {
            warnings += "$label \"$title\": max < min, đã đổi chỗ"
            min = max.also { max = min }
        }
        val step = (t.float("step") ?: 1f).takeIf { it > 0f } ?: 1f

        val children = if (type == ItemType.Group) {
            parseItems(file, t["item"] as? TomlArray, warnings, ids, "$label \"$title\"")
        } else {
            emptyList()
        }

        return Item(
            type = type,
            id = id,
            title = title,
            desc = t.str("desc"),
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
            min = min,
            max = max,
            step = step,
            page = t.str("page"),
            children = children,
        )
    }
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
            throw ConfigParseException("main.toml", null, "không có [[page]] hợp lệ nào để hiển thị")
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
        return LoadedConfig(app, pages, warnings)
    }
}
