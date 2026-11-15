package com.zhiend.meiremote.data

/**
 * Project Chain — 6 web apps + hub, mirroring chain.json (single source of
 * truth). MeiRemote mở mọi app bằng `remote` (http/s — điện thoại không đọc
 * được file://), và gửi `id` sang MeiBrowser qua bridge "open_app" để mở bản
 * local/remote trên máy tính.
 *
 * `remote` = URL deploy thật của từng web (đồng bộ với chain.json).
 * Project Hub (`hub`) KHÔNG có remote — nó là trang local trên máy tính, không
 * thuộc netlify nào cả; trên điện thoại chỉ mở được qua "Máy tính" (bridge).
 */
data class ChainApp(
    val id: String,
    val name: String,
    val glyph: String,
    val subtitle: String,
    val color: Long,
    val remote: String,
)

object ChainApps {

    val apps: List<ChainApp> = listOf(
        ChainApp(
            id = "linklumina",
            name = "LinkLumina",
            glyph = "🔖",
            subtitle = "Bookmark manager — lưu, sắp xếp và khám phá liên kết.",
            color = 0xFFE06C5B,
            remote = "https://graceful-kangaroo-4ebbee.netlify.app",
        ),
        ChainApp(
            id = "cucquanly",
            name = "Cục Quản Lý",
            glyph = "📋",
            subtitle = "Hub điều phối: năng lượng, lưu trữ, visualgraph, thư viện, trích xuất.",
            color = 0xFF5BC0DE,
            remote = "https://starlit-lily-f90e23.netlify.app",
        ),
        ChainApp(
            id = "mas",
            name = "MAS — Mahoraga",
            glyph = "🔄",
            subtitle = "Huấn luyện thích ứng từng bước — chạy backend trên Render.",
            color = 0xFF8F7AE6,
            remote = "https://mahoraga-adapt-system-mas-v9-0.onrender.com",
        ),
        ChainApp(
            id = "worldleaderboard",
            name = "World Leaderboard",
            glyph = "🏆",
            subtitle = "Xếp hạng toàn cầu theo nhiều chỉ số, biểu đồ và cột mốc.",
            color = 0xFFE6C05B,
            remote = "https://worldleaderboard.netlify.app",
        ),
        ChainApp(
            id = "bimat",
            name = "Bí Mật",
            glyph = "🔮",
            subtitle = "PersonalFrequency — tần số cá nhân và những bí mật.",
            color = 0xFFB06BD1,
            remote = "https://personalfrequencys.netlify.app",
        ),
        ChainApp(
            id = "boitoan",
            name = "Bói Toán Web",
            glyph = "🎴",
            subtitle = "Gieo quẻ & giải đáp — gửi câu hỏi, nhận lời giải từ web.",
            color = 0xFFE0A45B,
            remote = "https://boitoanzaigame.netlify.app",
        ),
        ChainApp(
            id = "hub",
            name = "Project Hub",
            glyph = "☰",
            subtitle = "Trung tâm điều phối toàn chuỗi — trang local trên máy tính.",
            color = 0xFFD8BF95,
            remote = "",
        ),
    )

    fun byId(id: String): ChainApp? = apps.firstOrNull { it.id == id }
}