package com.loom.app.feature

import com.loom.app.kernel.Kernel
import com.loom.app.kernel.LoadedPlugin
import com.loom.app.kernel.Status
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「一键复制错误」的内容来源：一份 AI 能直接读懂、能据此改代码的诊断。
 *
 * 只放定位问题需要的东西：宿主/项目概要、清单、状态、缺失依赖的提供者、
 * 完整错误（含 traceback），以及入口与界面脚本的原文。
 */
object PluginDiagnostics {

    private const val FILE_LIMIT = 6000

    fun build(kernel: Kernel, targets: List<LoadedPlugin>, appVersion: String): String {
        val sb = StringBuilder()
        sb.append("# Loom 插件诊断\n\n")
        sb.append("- 生成时间：").append(now()).append('\n')
        sb.append("- 宿主：Loom ").append(appVersion)
            .append("（Android ").append(android.os.Build.VERSION.RELEASE).append("）\n")
        kernel.current?.let { sb.append("- 项目：").append(it.name).append('\n') }
        sb.append("- 插件：ACTIVE ").append(kernel.count(Status.ACTIVE))
            .append(" · PENDING ").append(kernel.count(Status.PENDING))
            .append(" · ERROR ").append(kernel.count(Status.ERROR))
            .append(" · DISABLED ").append(kernel.count(Status.DISABLED))
            .append("（共 ").append(kernel.pluginCount()).append("）\n")
        sb.append("\n请根据下面的清单、错误与源码定位问题；需要改动时直接给出修正后的完整文件。\n")
        sb.append("修好后请用 @@LOOM-FILE 格式输出整包，我可以直接粘贴导入。\n")

        // 选中的插件若是 PENDING，连同「声明提供它缺失服务」的插件一起带上：
        // calc.ui 缺 calc，真正要修的是 ERROR 的 calc.logic。
        val ordered = LinkedHashMap<String, LoadedPlugin>()
        targets.forEach { ordered[it.id] = it }
        targets.forEach { lp ->
            lp.missing.forEach { service ->
                kernel.plugins.values
                    .filter { it.id != lp.id && it.manifest.provides.contains(service) }
                    .forEach { if (!ordered.containsKey(it.id)) ordered[it.id] = it }
            }
        }
        if (ordered.size > targets.size) {
            sb.append("\n> 已自动附带缺失依赖的提供者（共 ${ordered.size} 个插件），便于一起定位。\n")
        }

        for (lp in ordered.values) appendPlugin(sb, kernel, lp)
        return sb.toString()
    }

    private fun appendPlugin(sb: StringBuilder, kernel: Kernel, lp: LoadedPlugin) {
        val m = lp.manifest
        sb.append("\n---\n\n## 插件 ").append(m.id).append("（").append(m.name).append("） · ")
            .append(lp.status.code).append("\n\n")
        sb.append("- 版本：v").append(m.version).append('\n')
        sb.append("- 层级：").append(m.layerLabel).append("（layer=").append(m.layer.key)
        m.kind?.let { sb.append(" / kind=").append(it.key) }
        sb.append("）\n")
        sb.append("- 运行时：").append(if (m.runtime == "dex") "DEX" else "Lua")
            .append("，入口 ").append(m.entry).append('\n')
        m.screenEntry?.let { sb.append("- 界面脚本：").append(it).append('\n') }
        sb.append("- inject：").append(listOrNone(m.inject))
            .append("；provides：").append(listOrNone(m.provides)).append('\n')
        sb.append("- permissions：").append(listOrNone(m.permissions)).append('\n')
        sb.append("- 当前状态：").append(lp.status.code)
        if (lp.status == Status.PENDING) sb.append("（依赖未就绪，代码尚未装载）")
        sb.append('\n')

        if (lp.missing.isNotEmpty()) {
            sb.append("- 缺失依赖：").append(lp.missing.joinToString("、")).append('\n')
            lp.missing.forEach { service ->
                val owners = kernel.plugins.values
                    .filter { it.manifest.provides.contains(service) }
                    .joinToString("、") { "${it.id}（${it.status.code}）" }
                    .ifBlank { "没有任何已安装插件声明提供它" }
                sb.append("  - ").append(service).append(" ← ").append(owners).append('\n')
            }
        }

        val full = lp.errorDetail?.takeIf { it.isNotBlank() } ?: lp.error?.takeIf { it.isNotBlank() }
        if (full != null) {
            sb.append("\n### 错误原文\n\n```text\n").append(full.trim()).append("\n```\n")
        } else if (lp.status != Status.ACTIVE) {
            sb.append("\n### 错误原文\n\n（没有记录到错误信息）\n")
        }

        sb.append("\n### plugin.json\n\n```json\n")
            .append(runCatching { m.raw.toString(2) }.getOrDefault("{}"))
            .append("\n```\n")
        appendFile(sb, lp.dir, m.entry, "lua")
        m.screenEntry?.takeIf { it != m.entry }?.let { appendFile(sb, lp.dir, it, "lua") }
    }

    private fun appendFile(sb: StringBuilder, dir: File, rel: String, lang: String) {
        val file = File(dir, rel)
        if (!file.exists()) {
            sb.append("\n### ").append(rel).append("\n\n（文件缺失）\n")
            return
        }
        val text = runCatching { file.readText() }.getOrNull()
        if (text == null) {
            sb.append("\n### ").append(rel).append("\n\n（读取失败）\n")
            return
        }
        val body = if (text.length > FILE_LIMIT) {
            text.take(FILE_LIMIT) + "\n…（已截断，共 ${text.length} 字符）"
        } else {
            text
        }
        sb.append("\n### ").append(rel).append("\n\n```").append(lang).append('\n')
            .append(body).append("\n```\n")
    }

    private fun listOrNone(items: List<String>): String =
        if (items.isEmpty()) "无" else items.joinToString("、")

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
}
