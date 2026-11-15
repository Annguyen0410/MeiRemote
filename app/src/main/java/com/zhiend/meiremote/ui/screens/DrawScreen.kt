package com.zhiend.meiremote.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiend.meiremote.data.AppState
import com.zhiend.meiremote.ui.components.MeiChipRow
import com.zhiend.meiremote.ui.components.MeiMessageBanner
import com.zhiend.meiremote.ui.components.MeiPageHeader
import com.zhiend.meiremote.ui.components.MeiPrimaryButton
import com.zhiend.meiremote.ui.components.MeiSectionCard
import com.zhiend.meiremote.ui.components.MeiTextField
import com.zhiend.meiremote.ui.theme.LocalMeiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private data class DrawStroke(val color: Long, val width: Float, val points: SnapshotStateList<Offset>)
private data class DrawLayer(val name: String, val strokes: SnapshotStateList<DrawStroke>)

private val SECTIONS = listOf("Drawings", "Personal", "Library")
private val PALETTE = listOf(
    0xFF2E271F.toLong(), 0xFF6F4C37.toLong(), 0xFFC0533F.toLong(),
    0xFF5F7A45.toLong(), 0xFF3A8FB7.toLong(), 0xFF8A6448.toLong(),
    0xFFD6B48A.toLong(), 0xFFFFFFFF.toLong(), 0xFF000000.toLong(),
)
private val BG_COLORS = listOf(
    0xFFFFFFFF.toLong(), 0xFFFAF7F1.toLong(), 0xFFF2E5CF.toLong(),
    0xFF241D17.toLong(), 0xFF000000.toLong(),
)
private val WIDTHS = listOf(4f, 10f, 22f)

@Composable
fun DrawScreen(state: AppState) {
    val c = LocalMeiColors.current
    val scope = rememberCoroutineScope()

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var color by rememberSaveable { mutableStateOf(PALETTE[0]) }
    var widthIdx by rememberSaveable { mutableStateOf(1) }
    var section by rememberSaveable { mutableStateOf("Drawings") }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var bgColor by rememberSaveable { mutableStateOf(BG_COLORS[0]) }

    val layers = remember { mutableStateListOf(DrawLayer("Lớp 1", mutableStateListOf())) }
    var activeLayer by rememberSaveable { mutableStateOf(0) }
    var current by remember { mutableStateOf<DrawStroke?>(null) }

    val hasInk = layers.any { it.strokes.isNotEmpty() }

    fun renderPng(): ByteArray? {
        if (!hasInk || canvasSize.width <= 0 || canvasSize.height <= 0) return null
        val w = canvasSize.width.coerceAtMost(1600)
        val h = canvasSize.height.coerceAtMost(2200)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val g = android.graphics.Canvas(bmp)
        g.drawColor(bgColor.toInt())
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        layers.forEach { layer ->
            layer.strokes.forEach { s ->
                paint.color = s.color.toInt()
                paint.strokeWidth = s.width
                if (s.points.size == 1) {
                    g.drawCircle(s.points[0].x, s.points[0].y, s.width / 2f, paint)
                } else {
                    val p = Path().apply {
                        moveTo(s.points[0].x, s.points[0].y)
                        for (i in 1 until s.points.size) lineTo(s.points[i].x, s.points[i].y)
                    }
                    g.drawPath(p.asAndroidPath(), paint)
                }
            }
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MeiPageHeader("Vẽ", "Phác thảo nhiều lớp, tô màu nền rồi gửi lên desktop dạng ảnh + ghi chú")

        MeiSectionCard(
            title = "Giấy vẽ",
            subtitle = "Vẽ bằng ngón tay — chọn màu nền và nhiều lớp để vẽ đè",
        ) {
            // Layer strip
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lớp:", color = c.textMuted, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                layers.forEachIndexed { i, layer ->
                    val active = i == activeLayer
                    Text(
                        layer.name,
                        color = if (active) c.text else c.textMuted,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .background(if (active) c.accentSoft else Color.Transparent, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .clickable { activeLayer = i },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "+ Lớp",
                    color = c.accentHover,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            layers.add(DrawLayer("Lớp ${layers.size + 1}", mutableStateListOf()))
                            activeLayer = layers.size - 1
                        }
                        .padding(6.dp),
                )
                if (layers.size > 1) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Xóa lớp",
                        color = c.danger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                if (layers.size > 1) {
                                    layers.removeAt(activeLayer)
                                    activeLayer = (activeLayer - 1).coerceAtLeast(0)
                                }
                            }
                            .padding(6.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .background(Color(bgColor), RoundedCornerShape(12.dp))
                    .border(1.dp, c.inputBorder, RoundedCornerShape(12.dp))
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val layer = layers.getOrNull(activeLayer)
                                if (layer != null) {
                                    val s = DrawStroke(color, WIDTHS[widthIdx], mutableStateListOf(offset))
                                    current = s
                                    layer.strokes.add(s)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                current?.points?.add(change.position)
                            },
                            onDragEnd = { current = null },
                            onDragCancel = { current = null },
                        )
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    layers.forEach { layer ->
                        layer.strokes.forEach { s ->
                            if (s.points.size == 1) {
                                drawCircle(
                                    color = Color(s.color),
                                    radius = s.width / 2f,
                                    center = s.points[0],
                                )
                            } else {
                                val path = Path().apply {
                                    moveTo(s.points[0].x, s.points[0].y)
                                    for (i in 1 until s.points.size) lineTo(s.points[i].x, s.points[i].y)
                                }
                                drawPath(
                                    path,
                                    color = Color(s.color),
                                    style = Stroke(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Background color
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nền:", color = c.textMuted, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                BG_COLORS.forEach { swatch ->
                    val selected = swatch == bgColor
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(26.dp)
                            .background(Color(swatch), CircleShape)
                            .border(
                                if (selected) 3.dp else 1.dp,
                                if (selected) c.accentHover else c.inputBorder,
                                CircleShape,
                            )
                            .clickable { bgColor = swatch },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Color palette
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PALETTE.forEach { swatch ->
                    val selected = swatch == color
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(Color(swatch), CircleShape)
                            .border(
                                if (selected) 3.dp else 1.dp,
                                if (selected) c.accentHover else c.inputBorder,
                                CircleShape,
                            )
                            .clickable { color = swatch },
                    ) {
                        if (selected) {
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .size(8.dp)
                                    .background(if (Color(swatch).luminance() > 0.55f) c.accent else Color.White, CircleShape),
                            )
                        }
                    }
                }
            }

            // Stroke width
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nét:", color = c.textMuted, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                WIDTHS.forEachIndexed { i, w ->
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(if (widthIdx == i) c.accentSoft else Color.Transparent, RoundedCornerShape(50))
                            .padding(6.dp)
                            .clickable { widthIdx = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(w.dp.coerceAtLeast(6.dp))
                                .background(c.text, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Xóa nét",
                    color = c.danger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { layers.getOrNull(activeLayer)?.strokes?.clear() },
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    "Hoàn tác",
                    color = c.accentHover,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        layers.getOrNull(activeLayer)?.strokes?.let { if (it.isNotEmpty()) it.removeAt(it.lastIndex) }
                    },
                )
            }
        }

        MeiSectionCard(
            title = "Lưu bản vẽ",
            subtitle = "Lưu vào SafeVault/files/Drawings kèm ghi chú trên desktop",
        ) {
            MeiChipRow(SECTIONS, section) { section = it }
            Spacer(Modifier.height(8.dp))
            MeiTextField(title, { title = it }, "Tiêu đề bản vẽ", "vd: Ý tưởng thiết kế")
            Spacer(Modifier.height(8.dp))
            MeiTextField(body, { body = it }, "Mô tả (tùy chọn)", "Ghi chú kèm bản vẽ…", minLines = 2, singleLine = false)
        }

        MeiPrimaryButton(
            text = if (sending) "Đang gửi…" else "Gửi bản vẽ lên desktop",
            loading = sending,
            enabled = title.isNotBlank() && hasInk && !sending,
        ) {
            sending = true
            scope.launch {
                val png = withContext(Dispatchers.Default) { renderPng() }
                message = if (png == null) {
                    "Bản vẽ trống"
                } else {
                    withContext(Dispatchers.IO) {
                        state.sendDrawing(title.trim(), section, body.trim(), png)
                    }
                }
                sending = false
                if (message.startsWith("Đã lưu") || message.startsWith("Desktop offline")) {
                    layers.clear()
                    layers.add(DrawLayer("Lớp 1", mutableStateListOf()))
                    activeLayer = 0
                    title = ""
                    body = ""
                }
            }
        }

        MeiMessageBanner(message)
        MeiMessageBanner(state.lastMessage.takeIf { it.isNotBlank() && message.isBlank() })
    }
}
