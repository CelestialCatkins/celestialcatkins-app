package com.loom.app.kernel

/**
 * 错误文本的两种形态：
 *  - [summary]：插件列表里显示的一行，保留「是什么错」；
 *  - [full]：带 cause 链与 Lua traceback 的完整诊断，用于「复制给 AI」。
 *
 * LuaJ 的 traceback 是可选 API，这里用反射读取，避免绑定具体小版本。
 */
object ErrorText {

    fun summary(t: Throwable, limit: Int = 360): String {
        val raw = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
        val line = raw.lineSequence().firstOrNull { it.isNotBlank() } ?: raw
        return if (line.length > limit) line.take(limit) + "…" else line
    }

    fun full(t: Throwable): String {
        val sb = StringBuilder()
        var next: Throwable? = t
        var depth = 0
        while (depth < 5) {
            val cur = next ?: break
            if (depth == 0) {
                sb.append(cur.message?.takeIf { it.isNotBlank() } ?: cur.javaClass.simpleName)
            } else {
                sb.append("\nCaused by: ").append(cur.javaClass.simpleName)
                cur.message?.takeIf { it.isNotBlank() }?.let { sb.append(": ").append(it) }
            }
            tracebackOf(cur)?.takeIf { it.isNotBlank() }?.let { tb ->
                sb.append("\n").append(tb.trim().take(3000))
            }
            next = cur.cause
            depth++
        }
        return sb.toString()
    }

    private fun tracebackOf(t: Throwable): String? = runCatching {
        t.javaClass.methods
            .firstOrNull { it.name == "getTraceback" && it.parameterCount == 0 }
            ?.invoke(t) as? String
    }.getOrNull()
}
