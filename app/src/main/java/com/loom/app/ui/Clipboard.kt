package com.loom.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** 把文本写进系统剪贴板；失败返回 false，调用方负责提示。 */
fun copyText(context: Context, label: String, text: String): Boolean = runCatching {
    val cm = context.getSystemService(ClipboardManager::class.java)
        ?: return false
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    true
}.getOrDefault(false)
