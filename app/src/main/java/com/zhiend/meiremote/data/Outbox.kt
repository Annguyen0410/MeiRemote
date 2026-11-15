package com.zhiend.meiremote.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Local outbox that keeps every capture even when the desktop is offline.
 * Entries are JSON lines under filesDir/mei_outbox/queue.jsonl; binary payloads
 * (files, drawings) live in filesDir/mei_outbox/files/<id>/.
 */
class Outbox(context: Context) {

    private val root = File(context.filesDir, "mei_outbox")
    private val filesDir = File(root, "files")
    private val queueFile = File(root, "queue.jsonl")

    init {
        root.mkdirs()
        filesDir.mkdirs()
    }

    @Synchronized
    fun addNote(category: String, title: String, body: String, tags: List<String>): Int {
        val entry = JSONObject()
            .put("id", newId())
            .put("kind", "note")
            .put("created_at", System.currentTimeMillis())
            .put("category", category)
            .put("title", title)
            .put("body", body)
            .put("tags", JSONArray(tags))
        append(entry)
        return pendingCount()
    }

    @Synchronized
    fun addFile(sourceFile: File, name: String, target: String, caption: String, mime: String): Int {
        val id = newId()
        val stored = File(filesDir, "$id/${safeName(name)}")
        stored.parentFile?.mkdirs()
        sourceFile.copyTo(stored, overwrite = true)
        val entry = JSONObject()
            .put("id", id)
            .put("kind", "file")
            .put("created_at", System.currentTimeMillis())
            .put("filename", name)
            .put("relative_target", target)
            .put("caption", caption)
            .put("mime", mime)
            .put("file", stored.absolutePath)
        append(entry)
        return pendingCount()
    }

    @Synchronized
    fun addTask(title: String, bucket: String, dueAt: String, notes: String): Int {
        val entry = JSONObject()
            .put("id", newId())
            .put("kind", "task")
            .put("created_at", System.currentTimeMillis())
            .put("title", title)
            .put("bucket", bucket)
            .put("due_at", dueAt)
            .put("notes", notes)
        append(entry)
        return pendingCount()
    }

    @Synchronized
    fun addTabs(label: String, urls: List<String>): Int {
        val entry = JSONObject()
            .put("id", newId())
            .put("kind", "tabs")
            .put("created_at", System.currentTimeMillis())
            .put("label", label)
            .put("urls", JSONArray(urls))
        append(entry)
        return pendingCount()
    }

    @Synchronized
    fun addPage(url: String, title: String): Int {
        val entry = JSONObject()
            .put("id", newId())
            .put("kind", "page")
            .put("created_at", System.currentTimeMillis())
            .put("url", url)
            .put("title", title)
        append(entry)
        return pendingCount()
    }

    @Synchronized
    fun addDrawing(title: String, category: String, body: String, pngBytes: ByteArray): Int {
        val id = newId()
        val stored = File(filesDir, "$id/drawing.png")
        stored.parentFile?.mkdirs()
        stored.writeBytes(pngBytes)
        val entry = JSONObject()
            .put("id", id)
            .put("kind", "drawing")
            .put("created_at", System.currentTimeMillis())
            .put("title", title)
            .put("category", category)
            .put("body", body)
            .put("file", stored.absolutePath)
        append(entry)
        return pendingCount()
    }

    @Synchronized
    fun pending(): List<JSONObject> {
        if (!queueFile.exists()) return emptyList()
        val out = mutableListOf<JSONObject>()
        queueFile.forEachLine { line ->
            if (line.isNotBlank()) {
                try {
                    out.add(JSONObject(line))
                } catch (_: Exception) {
                }
            }
        }
        return out
    }

    @Synchronized
    fun pendingCount(): Int = pending().size

    @Synchronized
    fun remove(id: String) {
        if (!queueFile.exists()) return
        val kept = pending().filterNot { it.optString("id") == id }
        queueFile.writeText(kept.joinToString("\n") { it.toString() } + if (kept.isEmpty()) "" else "\n")
        File(filesDir, id).deleteRecursively()
    }

    @Synchronized
    fun clear() {
        queueFile.delete()
        filesDir.deleteRecursively()
        filesDir.mkdirs()
    }

    private fun append(entry: JSONObject) {
        queueFile.appendText(entry.toString() + "\n")
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun safeName(name: String): String {
        val cleaned = name.replace(Regex("[<>:\"/\\\\|?*\\x00-\\x1f]"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim(' ', '.')
        return cleaned.ifBlank { "file" }.take(140)
    }

    companion object {
        fun decodeBase64(data: String): ByteArray = Base64.decode(data, Base64.DEFAULT)
        fun encodeBase64(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)
    }
}
