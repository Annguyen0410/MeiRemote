package com.zhiend.meiremote.data

/** Pull the first http(s) URL out of a blob of text, or null when none exists. */
fun extractUrl(text: String): String? {
    return text.split(Regex("\\s+")).firstOrNull {
        it.startsWith("http://") || it.startsWith("https://")
    }
}
