package to.kuudere.anisuge.ui

import androidx.compose.ui.graphics.Color

private val CHAT_USER_PALETTE = listOf(
    Color(0xFFE50914),
    Color(0xFF6C4AB6),
    Color(0xFF3B82F6),
    Color(0xFF10B981),
    Color(0xFFF59E0B),
    Color(0xFFEC4899),
    Color(0xFF14B8A6),
    Color(0xFF8B5CF6),
    Color(0xFFF97316),
    Color(0xFF06B6D4),
)

/** Stable accent per user — matches BFF `chatUserColorHex`. */
fun chatAccentColor(userId: String, isMine: Boolean): Color {
    if (isMine) return Color(0xFF8B1520)
    val hex = userId.trim()
    if (hex.isEmpty()) return Color(0xFF1E1E1E)
    var hash = 0
    for (ch in hex) {
        hash = (hash * 31 + ch.code) and 0x7FFFFFFF
    }
    return CHAT_USER_PALETTE[hash % CHAT_USER_PALETTE.size]
}

fun parseChatColorHex(hex: String?): Color? {
    val raw = hex?.trim()?.removePrefix("#") ?: return null
    if (raw.length != 6) return null
    return runCatching {
        Color(0xFF000000 or raw.toLong(16))
    }.getOrNull()
}

fun chatUsernameColor(userId: String, userColorHex: String?, isMine: Boolean): Color {
    if (isMine) return Color.White.copy(alpha = 0.55f)
    return parseChatColorHex(userColorHex) ?: chatAccentColor(userId, false)
}
