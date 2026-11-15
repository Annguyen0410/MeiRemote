package com.zhiend.meiremote.data

/** Mirrors the browser-side pairing code: `MEI1|<host>|<port>|<token>`. */
object PairingCode {
    const val PREFIX = "MEI1"

    data class Info(val host: String, val port: Int, val token: String)

    fun parse(code: String): Info? {
        val parts = code.trim().split("|")
        if (parts.size != 4 || parts[0] != PREFIX) return null
        val port = parts[2].toIntOrNull() ?: return null
        if (parts[1].isBlank() || parts[3].isBlank()) return null
        return Info(parts[1], port, parts[3])
    }
}
