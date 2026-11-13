package com.loom.app.kernel

/**
 * 「粘贴导入」的文本格式：把一组插件文件写在一段可复制的文本里。
 *
 * ```
 * @@LOOM-FILE calc.logic/plugin.json
 * { ... }
 * @@LOOM-FILE calc.logic/source/main.lua
 * local ctx = ...
 * @@LOOM-END
 * ```
 *
 * 这样 AI 可以把整包插件输出成一个代码块，用户复制一次、粘一次就能装，
 * 不必经过文件管理器，也不需要能打包 zip。
 *
 * 解析只做结构、路径与清单校验，不执行任何插件代码；
 * 真正的装载仍然走内核的 resolveAndActivate，与 zip 安装完全同一条路。
 */
data class PackFile(val path: String, val text: String)

data class PackFolder(
    val root: String,
    val manifest: PluginManifest,
    val files: List<PackFile>,
)

object PluginPack {

    const val FILE_MARK = "@@LOOM-FILE"
    const val END_MARK = "@@LOOM-END"

    /** 单次粘贴的上限：够放几十个 Lua 插件，又不至于拖死主线程。 */
    const val MAX_FILES = 200
    const val MAX_CHARS = 400_000

    fun sample(): String = """
        @@LOOM-FILE demo.ui/plugin.json
        {
          "id": "demo.ui",
          "name": "示例面板",
          "version": "1.0.0",
          "layer": "ui",
          "inject": [],
          "provides": [],
          "permissions": [],
          "entry": "source/main.lua",
          "screen": "source/screen.lua"
        }
        @@LOOM-FILE demo.ui/source/screen.lua
        local ctx = ...
        return {
          title = "示例面板",
          icon = "grid",
          root = { type = "column", children = {
            { type = "headline", text = "粘贴导入成功。" },
          }},
        }
        @@LOOM-END
    """.trimIndent()

    /**
     * 把文本拆成文件列表。容忍从 markdown 代码围栏里复制（自动忽略 ``` 行）。
     * 结构不合法时抛 IllegalArgumentException，消息直接给用户看。
     */
    fun parse(raw: String): List<PackFile> {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) throw IllegalArgumentException("先粘贴内容")
        if (text.length > MAX_CHARS) {
            throw IllegalArgumentException("内容太长（上限 ${MAX_CHARS / 1000}k 字符）")
        }

        val out = ArrayList<PackFile>()
        var current: String? = null
        val body = StringBuilder()

        fun commit() {
            val path = current ?: return
            out.add(PackFile(path, body.toString().trim('\n')))
            body.setLength(0)
        }

        for (line in text.split('\n')) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) continue

            if (trimmed.startsWith(END_MARK)) {
                commit()
                current = null
                continue
            }

            val path = markPath(trimmed)
            if (path != null) {
                commit()
                current = path
                continue
            }

            if (current != null) {
                body.append(line).append('\n')
            }
        }
        commit()

        if (out.isEmpty()) {
            throw IllegalArgumentException("没有找到 $FILE_MARK 标记")
        }
        if (out.size > MAX_FILES) {
            throw IllegalArgumentException("文件太多（上限 $MAX_FILES 个）")
        }

        val seen = HashSet<String>()
        for (f in out) {
            if (f.path.startsWith("/") || f.path.contains("..")) {
                throw IllegalArgumentException("路径不合法：${f.path}")
            }
            if (!seen.add(f.path)) {
                throw IllegalArgumentException("文件重复：${f.path}")
            }
        }
        return out
    }

    /** 按 plugin.json 的位置切分插件；根目录可以是空（单插件、文件直接在顶层）。 */
    fun folders(files: List<PackFile>): List<PackFolder> {
        val roots = files
            .filter { it.path.substringAfterLast('/') == "plugin.json" }
            .map { it.path.substringBeforeLast('/', "") }
            .distinct()
            .sortedByDescending { it.length }

        if (roots.isEmpty()) {
            throw IllegalArgumentException("没有找到 plugin.json")
        }

        val groups = LinkedHashMap<String, MutableList<PackFile>>()
        roots.forEach { groups[it] = ArrayList() }

        for (f in files) {
            val root = roots.firstOrNull { it.isEmpty() || f.path.startsWith("$it/") }
                ?: throw IllegalArgumentException("${f.path} 不属于任何插件目录")
            groups.getValue(root).add(f)
        }

        return groups.map { (root, list) ->
            val prefix = if (root.isEmpty()) "" else "$root/"
            val manifestFile = list.firstOrNull { it.path == prefix + "plugin.json" }
                ?: throw IllegalArgumentException("$root 下缺少 plugin.json")

            // 清单校验交给 PluginManifest：id / layer / kind 的错误消息统一在这里给。
            val manifest = PluginManifest.parse(Json.obj(manifestFile.text))
            val rel = list.map { PackFile(it.path.removePrefix(prefix), it.text) }
            PackFolder(root, manifest, rel)
        }
    }

    private fun markPath(trimmed: String): String? {
        if (!trimmed.startsWith(FILE_MARK)) return null
        var rest = trimmed.substring(FILE_MARK.length).trim()
        rest = rest.removePrefix(":").trim()
        if (rest.isEmpty()) throw IllegalArgumentException("$FILE_MARK 后面要跟文件路径")
        return rest.replace('\\', '/').removePrefix("./")
    }
}
