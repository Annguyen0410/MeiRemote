package com.zhiend.meiremote.data

/**
 * The pairing code Mei hands out: `MEI1|<host>|<port>|<token>`.
 *
 * These rules are the desktop's, character for character
 * (`litebrowser/services/android_bridge_service.py:parse_pairing_code`, shared
 * with `Extensions/tab-window-bridge/bridge.js`). Keeping the copy strict means
 * a code that the browser printed is either accepted here or rejected here for a
 * reason the settings screen can name — never accepted here and silently 401'd
 * by the bridge.
 */
object PairingCode {
    const val PREFIX = "MEI1"
    const val MAX_TOKEN_CHARS = 512
    const val MAX_HOST_CHARS = 253

    /** A host carrying any of these cannot be rebuilt into a URL, so it is not a host. */
    private val HOST_JUNK = Regex("[\\s/\\\\|,?#@%]")
    private val IPV6 = Regex("(?:[0-9A-Fa-f]*:){2,}[0-9A-Fa-f.]*")
    private val HOSTNAME = Regex("[A-Za-z0-9.-]+")
    private val PORT = Regex("[0-9]{1,5}")

    data class Info(val host: String, val port: Int, val token: String)

    fun parse(code: String?): Info? {
        val parts = (code ?: "").trim().split("|")
        if (parts.size != 4 || parts[0] != PREFIX) return null

        val host = parts[1].trim()
        val rawPort = parts[2].trim()
        val token = parts[3].trim()

        if (host.isEmpty() || host.length > MAX_HOST_CHARS || HOST_JUNK.containsMatchIn(host)) return null
        if (host.contains(':')) {
            if (!IPV6.matches(host.trim('[', ']'))) return null
        } else if (!HOSTNAME.matches(host)) {
            return null
        }
        if (!PORT.matches(rawPort)) return null
        val port = rawPort.toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        if (token.isEmpty() || token.length > MAX_TOKEN_CHARS) return null
        if (token.any { it == '\r' || it == '\n' || it == '\u0000' }) return null

        return Info(host, port, token)
    }
}
