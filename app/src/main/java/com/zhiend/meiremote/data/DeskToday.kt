package com.zhiend.meiremote.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * One read-only snapshot of Mei's desk, from `GET /api/mobile/today`.
 *
 * The desktop builds this for the phone and already trims it (see
 * `desk_for_phone` in litebrowser/services/android_bridge_service.py): a
 * headline, four counters, and blocks whose rows carry only what a small screen
 * needs. Nothing here writes back, so a failed read can never touch local data.
 */
data class DeskToday(
    val date: String,
    val headline: String,
    val pulse: String,
    val onThePlate: Int,
    val waiting: Int,
    val leftOpen: Int,
    val cardsDue: Int,
    val blocks: List<Block>,
) {
    data class Block(
        val key: String,
        val title: String,
        val count: Int,
        val unit: String,
        val rows: List<Row>,
    )

    data class Row(val title: String, val subtitle: String)

    companion object {
        fun from(body: JSONObject?): DeskToday? {
            if (body == null) return null
            val numbers = body.optJSONObject("numbers") ?: JSONObject()
            return DeskToday(
                date = body.optString("date", ""),
                headline = body.optString("headline", ""),
                pulse = body.optString("pulse", ""),
                onThePlate = numbers.optInt("on_the_plate", 0),
                waiting = numbers.optInt("waiting", 0),
                leftOpen = numbers.optInt("left_open", 0),
                cardsDue = numbers.optInt("cards_due", 0),
                blocks = blocks(body.optJSONArray("blocks")),
            )
        }

        private fun blocks(array: JSONArray?): List<Block> {
            if (array == null) return emptyList()
            val out = ArrayList<Block>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                out.add(
                    Block(
                        key = item.optString("key", ""),
                        title = item.optString("title", ""),
                        count = item.optInt("count", 0),
                        unit = item.optString("unit", ""),
                        rows = rows(item.optJSONArray("rows")),
                    )
                )
            }
            return out
        }

        private fun rows(array: JSONArray?): List<Row> {
            if (array == null) return emptyList()
            val out = ArrayList<Row>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val title = item.optString("title", "")
                if (title.isBlank()) continue
                out.add(Row(title, item.optString("subtitle", "")))
            }
            return out
        }
    }
}
